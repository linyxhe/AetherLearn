"""通用任务图：一次 LLM 调用 + 结构化校验，失败时自动反思重试（Reflexion）。

批改 / 出题 / 报告 / 建议 / 连接测试的形状完全一致：一次 LLM 调用 + 可选结构化解析，
差异只体现在 `prompt_key`、`variables` 与 `output_format`。
做成一张参数化的图，而不是每个能力一张图，避免四份重复的错误处理与解析逻辑。

**为什么加反思（Reflexion）**：批改与出题要求模型输出严格 JSON，而"格式错误"恰恰是
**最可恢复**的一类失败——把解析错误回灌给模型（"你上次输出的不是合法 JSON，错误是 X，
请只输出 JSON"）通常一次就能改对。改造前这类失败直接降级：批改退化成关键词命中 +
待复核，出题直接抛 500。现在改成"失败 → 带着错误重试一次 → 仍失败才降级"。

三条边界（与 ReAct 图同一套纪律）：
1. **只在结构化输出上反思**：`text`（报告/建议）的质量无法自动判定，反思只会变成
   "再生成一遍"，纯烧钱——因此这类任务永不反思。
2. **默认只反思一次**（`max_reflections`，上限 3）：反思是补救，不是主路径。
3. **预算共享**：所有尝试共用 Java 下发的同一个预算。每次尝试只用**剩余预算**，
   剩余不足就不重试——否则"两次各用满超时"会击穿 Java 的总预算，
   变成 Java 侧超时降级（比不反思还差）。
"""

from __future__ import annotations

import asyncio
import logging
import operator
import time
from dataclasses import dataclass, field
from functools import lru_cache
from typing import Annotated, Any, List, Optional

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_core.runnables import RunnableConfig
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages

from app.agents.state import TaskState
from app.llm.client import get_chat_model
from app.llm.config import ResolvedLlmSettings
from app.llm.errors import LlmTimeout, wrap_exception
from app.llm.json_utils import extract_json_array, extract_json_object
from app.prompts.registry import get_prompt


logger = logging.getLogger(__name__)

DEFAULT_MAX_REFLECTIONS = 1
MAX_REFLECTIONS_LIMIT = 3
# 剩余预算低于此值就不再重试：一次必然超时的调用毫无意义，只会拖长用户等待。
MIN_RETRY_SECONDS = 2.0


@dataclass
class TaskResult:
    """通用任务结果。"""

    raw: str = ""
    data: Any = None
    llm_ms: int = 0
    output_format: str = "text"
    error: Optional[str] = None
    meta: dict = field(default_factory=dict)
    # 实际发生的反思次数：>0 表示模型第一次没给对、靠重试救回来了
    reflections: int = 0


def _max_reflections(config: RunnableConfig) -> int:
    """取反思轮数上限（默认 1，硬上限 3）。"""
    raw = (config.get("configurable") or {}).get("max_reflections")
    try:
        value = int(raw)
    except (TypeError, ValueError):
        return DEFAULT_MAX_REFLECTIONS
    return max(0, min(value, MAX_REFLECTIONS_LIMIT))


def _remaining_seconds(state: TaskState, settings: ResolvedLlmSettings) -> float:
    """本次尝试可用的剩余预算（秒）。"""
    used_ms = int(state.get("elapsed_ms") or 0)
    return settings.timeout - used_ms / 1000.0


def _validate(prompt_key: str, output_format: str, data: Any) -> Optional[str]:
    """按提示词声明的契约校验解析结果，返回错误说明（None 表示通过）。

    校验口径必须与 Java 的解析保持一致，否则会出现"Python 认为成功、Java 仍降级"——
    反思就白做了。因此契约写在提示词上（`required_keys` / `item_required_keys`），
    与 Java 解析器一一对应。
    """
    if output_format == "text":
        return None  # 自由文本无法自动判定对错，明确不校验

    spec = get_prompt(prompt_key)
    required = list(spec.extra.get("required_keys") or [])
    item_required = list(spec.extra.get("item_required_keys") or [])

    if output_format == "json_object":
        if not isinstance(data, dict):
            return "没有解析出 JSON 对象"
        missing = [key for key in required if key not in data]
        if missing:
            return f"JSON 对象缺少必需字段：{'、'.join(missing)}"
        return None

    if output_format == "json_array":
        if not isinstance(data, list):
            return "没有解析出 JSON 数组"
        if not data:
            return "JSON 数组为空"
        for index, item in enumerate(data):
            if not isinstance(item, dict):
                return f"数组第 {index + 1} 项不是 JSON 对象"
            missing = [key for key in item_required if key not in item or item[key] in (None, "", [])]
            if missing:
                return f"数组第 {index + 1} 项缺少字段：{'、'.join(missing)}"
        return None

    return None


def _parse(prompt_key: str, output_format: str, raw: str) -> tuple:
    """解析 + 校验，返回 (data, error)。"""
    data: Any = None
    if output_format == "json_object":
        data = extract_json_object(raw)
        error = "模型输出无法解析为 JSON 对象" if data is None else None
    elif output_format == "json_array":
        data = extract_json_array(raw)
        error = "模型输出无法解析为 JSON 数组" if data is None else None
    else:
        error = None

    if error is None:
        error = _validate(prompt_key, output_format, data)
    return data, error


def _initial_messages(state: TaskState) -> List[Any]:
    """首次调用的消息序列：system + user（提示词注册表或调用方覆盖）。"""
    system_override = state.get("system_prompt")
    user_override = state.get("user_prompt")
    if system_override and user_override:
        # 逃生舱口：允许调用方直接给定提示词（线上应急覆盖，不必改注册表）
        system_text, user_text = system_override, user_override
    else:
        spec = get_prompt(state["prompt_key"])
        system_text, user_text = spec.render(state.get("variables") or {})
    return [SystemMessage(content=system_text), HumanMessage(content=user_text)]


async def task_node(state: TaskState, config: RunnableConfig) -> dict:
    """执行一次 LLM 调用并按 output_format 解析 + 按契约校验。"""
    settings: ResolvedLlmSettings = config["configurable"]["llm"]
    output_format = state.get("output_format") or "text"

    incoming = list(state.get("messages") or [])
    first_attempt = not incoming
    # 首次必须把 system + user 一起落进状态：反思重试是"在已有对话上追加"，
    # 只把模型响应写回状态会让第二次调用丢掉系统提示与原始任务描述。
    messages = incoming if incoming else _initial_messages(state)

    remaining = _remaining_seconds(state, settings)
    if remaining <= 0:
        raise LlmTimeout(f"任务已耗尽 {settings.timeout:.1f}s 预算")

    model = get_chat_model(settings)
    started = time.perf_counter()
    try:
        response = await asyncio.wait_for(model.ainvoke(messages), timeout=remaining)
    except asyncio.TimeoutError as exc:
        raise LlmTimeout(f"LLM 调用超过剩余的 {remaining:.1f}s 预算") from exc
    except Exception as exc:  # noqa: BLE001 - 统一归类后再抛给 API 层
        raise wrap_exception(exc) from exc

    elapsed_ms = int((time.perf_counter() - started) * 1000)
    content = response.content
    raw = content if isinstance(content, str) else str(content)
    data, error = _parse(state.get("prompt_key", ""), output_format, raw)
    if error:
        logger.warning("任务 %s 校验未通过：%s（第 %d 次尝试）",
                       state.get("prompt_key"), error, int(state.get("reflections") or 0) + 1)

    return {
        "messages": [*messages, response] if first_attempt else [response],
        "raw": raw,
        "data": data,
        "llm_ms": elapsed_ms,
        "error": error,
        "elapsed_ms": int(state.get("elapsed_ms") or 0) + elapsed_ms,
    }


def should_reflect(state: TaskState, config: RunnableConfig) -> str:
    """条件边：结构化输出没通过、且还有反思额度与剩余预算 → 反思重试。"""
    if not state.get("error"):
        return END
    if (state.get("output_format") or "text") == "text":
        return END  # 自由文本不反思（见模块文档）
    if int(state.get("reflections") or 0) >= _max_reflections(config):
        return END
    settings: ResolvedLlmSettings = config["configurable"]["llm"]
    if _remaining_seconds(state, settings) < MIN_RETRY_SECONDS:
        logger.info("剩余预算不足 %.1fs，放弃反思重试", MIN_RETRY_SECONDS)
        return END
    return "reflect"


async def reflect_node(state: TaskState, config: RunnableConfig) -> dict:
    """反思节点：把"上次输出 + 具体错在哪"回灌给模型，让它自己修。

    关键是把**错误原因**说清楚（缺字段还是格式错、第几项），而不是笼统地说"请重试"——
    泛泛的重试等于让模型再猜一次，命中率远低于告诉它错在哪。
    """
    error = state.get("error") or "输出不符合要求的格式"
    prompt_key = state.get("prompt_key") or ""
    spec = get_prompt(prompt_key)
    output_format = state.get("output_format") or "text"

    if output_format == "json_object":
        shape = "一个 JSON 对象"
    elif output_format == "json_array":
        shape = "一个 JSON 数组"
    else:
        shape = "规定的格式"

    critique = (
        f"你上一次的输出不符合要求：{error}。\n"
        f"请重新输出{shape}，并严格遵守：\n"
        "1. 只输出 JSON 本身，不要任何解释性文字、不要 Markdown 代码围栏（```）、不要前后缀；\n"
        "2. 所有必需字段都要出现，字符串用双引号，不要在 JSON 里写注释；\n"
        "3. 需要修正的是格式，不是内容——内容按原来的要求作答即可。"
    )
    reflections = int(state.get("reflections") or 0) + 1
    logger.info("对任务 %s 发起第 %d 次反思重试（原因：%s）", prompt_key, reflections, error)

    return {
        "messages": [HumanMessage(content=critique)],
        "reflections": reflections,
        "steps": [
            {
                "type": "reflection",
                "attempt": reflections,
                "error": error,
                "output_format": output_format,
                "hint": spec.extra.get("required_keys") or spec.extra.get("item_required_keys") or [],
            }
        ],
    }


@lru_cache(maxsize=1)
def build_task_graph():
    """构建并缓存通用任务图（无状态，可安全复用）。"""
    graph = StateGraph(TaskState)
    graph.add_node("task", task_node)
    graph.add_node("reflect", reflect_node)
    graph.add_edge(START, "task")
    graph.add_conditional_edges("task", should_reflect, {"reflect": "reflect", END: END})
    graph.add_edge("reflect", "task")
    return graph.compile()


async def invoke_task(
    prompt_key: str,
    variables: Optional[dict] = None,
    *,
    output_format: str = "text",
    settings: ResolvedLlmSettings,
    system_prompt: Optional[str] = None,
    user_prompt: Optional[str] = None,
    max_reflections: Optional[int] = None,
) -> TaskResult:
    """执行通用任务图并返回结果。"""
    state: TaskState = {
        "prompt_key": prompt_key,
        "variables": variables or {},
        "output_format": output_format,
        "messages": [],
        "reflections": 0,
        "elapsed_ms": 0,
    }
    if system_prompt and user_prompt:
        state["system_prompt"] = system_prompt
        state["user_prompt"] = user_prompt

    final_state = await build_task_graph().ainvoke(
        state,
        config={
            "configurable": {
                "llm": settings,
                "max_reflections": (
                    DEFAULT_MAX_REFLECTIONS if max_reflections is None else max_reflections
                ),
            }
        },
    )
    return TaskResult(
        raw=final_state.get("raw", ""),
        data=final_state.get("data"),
        llm_ms=final_state.get("elapsed_ms", final_state.get("llm_ms", 0)),
        output_format=output_format,
        error=final_state.get("error"),
        reflections=int(final_state.get("reflections") or 0),
        meta={
            "key_fp": settings.key_fingerprint(),
            "model": settings.model,
            "steps": final_state.get("steps") or [],
        },
    )


__all__ = [
    "TaskResult",
    "build_task_graph",
    "invoke_task",
    "task_node",
    "reflect_node",
    "should_reflect",
]
