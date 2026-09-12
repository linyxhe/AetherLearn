"""通用单节点任务图。

批改 / 出题 / 报告 / 建议 / 连接测试的形状完全一致：一次 LLM 调用 + 可选结构化解析，
差异只体现在 `prompt_key`、`variables` 与 `output_format`。
做成一张参数化的图，而不是每个能力一张图，避免四份重复的错误处理与解析逻辑。
"""

from __future__ import annotations

import asyncio
import logging
import time
from dataclasses import dataclass, field
from functools import lru_cache
from typing import Any, Optional

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.runnables import RunnableConfig
from langgraph.graph import END, START, StateGraph

from app.agents.state import TaskState
from app.llm.client import get_chat_model
from app.llm.config import ResolvedLlmSettings
from app.llm.errors import LlmTimeout, wrap_exception
from app.llm.json_utils import extract_json_array, extract_json_object
from app.prompts.registry import get_prompt


logger = logging.getLogger(__name__)


@dataclass
class TaskResult:
    """通用任务结果。"""

    raw: str = ""
    data: Any = None
    llm_ms: int = 0
    output_format: str = "text"
    error: Optional[str] = None
    meta: dict = field(default_factory=dict)


async def task_node(state: TaskState, config: RunnableConfig) -> dict:
    """执行一次 LLM 调用并按 output_format 解析。"""
    settings: ResolvedLlmSettings = config["configurable"]["llm"]
    output_format = state.get("output_format") or "text"

    system_override = state.get("system_prompt")
    user_override = state.get("user_prompt")
    if system_override and user_override:
        # 逃生舱口：允许调用方直接给定提示词（线上应急覆盖，不必改注册表）
        system_text, user_text = system_override, user_override
    else:
        spec = get_prompt(state["prompt_key"])
        system_text, user_text = spec.render(state.get("variables") or {})

    model = get_chat_model(settings)
    started = time.perf_counter()
    try:
        response = await asyncio.wait_for(
            model.ainvoke(
                [SystemMessage(content=system_text), HumanMessage(content=user_text)]
            ),
            timeout=settings.timeout,
        )
    except asyncio.TimeoutError as exc:
        raise LlmTimeout(f"LLM 调用超过 {settings.timeout:.1f}s 预算") from exc
    except Exception as exc:  # noqa: BLE001 - 统一归类后再抛给 API 层
        raise wrap_exception(exc) from exc

    llm_ms = int((time.perf_counter() - started) * 1000)
    content = response.content
    raw = content if isinstance(content, str) else str(content)

    data: Any = None
    error: Optional[str] = None
    if output_format == "json_object":
        data = extract_json_object(raw)
        if data is None:
            error = "模型输出无法解析为 JSON 对象"
            logger.warning("任务 %s 的模型输出无法解析为 JSON 对象", state.get("prompt_key"))
    elif output_format == "json_array":
        data = extract_json_array(raw)
        if data is None:
            error = "模型输出无法解析为 JSON 数组"
            logger.warning("任务 %s 的模型输出无法解析为 JSON 数组", state.get("prompt_key"))

    return {"raw": raw, "data": data, "llm_ms": llm_ms, "error": error}


@lru_cache(maxsize=1)
def build_task_graph():
    """构建并缓存通用任务图（无状态，可安全复用）。"""
    graph = StateGraph(TaskState)
    graph.add_node("task", task_node)
    graph.add_edge(START, "task")
    graph.add_edge("task", END)
    return graph.compile()


async def invoke_task(
    prompt_key: str,
    variables: Optional[dict] = None,
    *,
    output_format: str = "text",
    settings: ResolvedLlmSettings,
    system_prompt: Optional[str] = None,
    user_prompt: Optional[str] = None,
) -> TaskResult:
    """执行通用任务图并返回结果。"""
    state: TaskState = {
        "prompt_key": prompt_key,
        "variables": variables or {},
        "output_format": output_format,
    }
    if system_prompt and user_prompt:
        state["system_prompt"] = system_prompt
        state["user_prompt"] = user_prompt

    final_state = await build_task_graph().ainvoke(
        state, config={"configurable": {"llm": settings}}
    )
    return TaskResult(
        raw=final_state.get("raw", ""),
        data=final_state.get("data"),
        llm_ms=final_state.get("llm_ms", 0),
        output_format=output_format,
        error=final_state.get("error"),
        meta={"key_fp": settings.key_fingerprint(), "model": settings.model},
    )


__all__ = ["TaskResult", "build_task_graph", "invoke_task", "task_node"]
