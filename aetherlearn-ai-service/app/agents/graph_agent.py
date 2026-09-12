"""ReAct 智能助教图：agent ⇄ tools 循环，最后收敛到一次纯生成。

为什么是"真 agent"而不是流程编排：**下一步做什么由模型决定**——
它可以选择不调用工具直接回答（1 次模型调用，与改造前同价）、
调用 `search_knowledge` 换关键词再查、或调用 `get_learning_progress` 拿学生本人数据。
控制流不再由代码里的 if/else 决定。

三条硬边界（生产约束，缺一不可）：
1. **轮数上限**：`max_tool_rounds`（默认 3）。超过后强制一次"不再给工具"的生成，
   保证一定能给出答案，不会无限自我循环。
2. **总预算**：整轮受 `timeout_seconds` 约束（`asyncio.wait_for` 包住整个循环），
   每次模型调用再受 LLM 自身 timeout 约束——双层，避免"每轮都不超时、合起来超时"。
3. **工具不放进状态**：工具实例按请求经 `config["configurable"]["tools"]` 传入
   （与 LLM 参数同一套机制），状态只保留可 JSON 序列化的数据，将来接 checkpointer 不受阻。

状态里记录 `tool_rounds` 与 `steps`：前者用于轮数控制，后者是给前端展示的中间步骤。
**来源组装**走工具构造时传入的 `on_hits` 回调（而不是状态）——agent 路径下"模型实际看了
哪些切片"只有工具自己知道，回调把 chunk_id 交给调用方，由 Java 去 MySQL 补 docTitle
与软删除过滤，来源展示的所有权仍然在 Java。
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
from functools import lru_cache
from typing import Any, Dict, List, Optional

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, ToolMessage
from langchain_core.runnables import RunnableConfig
from langgraph.graph import END, START, StateGraph

from app.agents.state import AgentState
from app.llm.client import get_chat_model
from app.llm.config import ResolvedLlmSettings
from app.llm.errors import LlmTimeout, wrap_exception
from app.prompts.agent import render_agent_prompts
from app.tools import build_course_tools

logger = logging.getLogger(__name__)

DEFAULT_MAX_TOOL_ROUNDS = 3
# 工具结果回灌给模型的截断长度：工具已各自限长，这里只兜底异常大的返回
_MAX_TOOL_RESULT_CHARS = 4000


def _step_event(kind: str, **payload: Any) -> Dict[str, Any]:
    """构造一条"中间步骤"事件（前端展示用，不参与答案组装）。"""
    return {"type": kind, **payload}


def _tools_from_config(config: RunnableConfig) -> Dict[str, Any]:
    """从 config 取本请求的工具表（缺失时视为无工具，退化成纯生成）。"""
    return dict((config.get("configurable") or {}).get("tools") or {})


def _max_rounds(config: RunnableConfig) -> int:
    raw = (config.get("configurable") or {}).get("max_tool_rounds")
    try:
        value = int(raw)
    except (TypeError, ValueError):
        return DEFAULT_MAX_TOOL_ROUNDS
    return max(0, min(value, 6))


async def agent_node(state: AgentState, config: RunnableConfig) -> dict:
    """推理节点：模型看到当前消息序列，决定"直接回答"还是"调用工具"。"""
    settings: ResolvedLlmSettings = config["configurable"]["llm"]
    tools = _tools_from_config(config)
    model = get_chat_model(settings)

    # 还有工具预算就绑定工具；用光了就绑空工具表 → 模型只能给出最终答案
    used_rounds = int(state.get("tool_rounds") or 0)
    rounds_left = _max_rounds(config) - used_rounds
    bound = model.bind_tools(list(tools.values())) if (tools and rounds_left > 0) else model

    try:
        response = await asyncio.wait_for(
            bound.ainvoke(state.get("messages") or []),
            timeout=settings.timeout,
        )
    except asyncio.TimeoutError as exc:
        raise LlmTimeout(f"智能助教推理超过 {settings.timeout:.1f}s 预算") from exc
    except Exception as exc:  # noqa: BLE001 - 统一归类后交给 API 层映射
        raise wrap_exception(exc) from exc

    return {"messages": [response]}


async def tools_node(state: AgentState, config: RunnableConfig) -> dict:
    """执行节点：逐个执行模型请求的工具，把结果作为 ToolMessage 回灌。

    工具失败**不抛异常**：转成一句可读的观察结果交回模型，让它自己决定换关键词重试
    还是如实告知学生——这是 ReAct 能自愈的关键，也是"一个工具挂了不该让整轮问答失败"。
    """
    tools = _tools_from_config(config)
    last = (state.get("messages") or [])[-1]
    tool_calls = list(getattr(last, "tool_calls", None) or [])

    messages: List[ToolMessage] = []
    steps: List[Dict[str, Any]] = []
    for call in tool_calls:
        name = call.get("name") or ""
        call_id = call.get("id") or ""
        args = call.get("args") or {}
        steps.append(_step_event("tool_start", tool=name, args=args))
        started = time.perf_counter()

        tool = tools.get(name)
        if tool is None:
            # 模型可能编出一个不存在的工具名：如实回灌，不让它卡死
            messages.append(
                ToolMessage(content=f"工具 {name} 不存在，可用工具：{sorted(tools)}", tool_call_id=call_id)
            )
            steps.append(_step_event("tool_end", tool=name, ok=False, error="unknown_tool"))
            continue

        try:
            result = await tool.ainvoke(args)
            text = result if isinstance(result, str) else json.dumps(result, ensure_ascii=False)
        except Exception as exc:  # noqa: BLE001 - 工具异常不能打断整轮
            logger.warning("工具 %s 执行异常：%s", name, exc)
            text = f"工具执行失败（{exc}），请换一种方式或基于已有信息回答。"
            steps.append(_step_event("tool_end", tool=name, ok=False, error=str(exc)))
            messages.append(ToolMessage(content=text, tool_call_id=call_id))
            continue

        elapsed_ms = int((time.perf_counter() - started) * 1000)
        steps.append(_step_event("tool_end", tool=name, ok=True, elapsed_ms=elapsed_ms))
        messages.append(ToolMessage(content=text[:_MAX_TOOL_RESULT_CHARS], tool_call_id=call_id))

    return {
        "messages": messages,
        "tool_rounds": int(state.get("tool_rounds") or 0) + 1,
        "steps": steps,
    }


def should_continue(state: AgentState, config: RunnableConfig) -> str:
    """条件边：还有工具调用且没超轮数 → 去执行；否则结束（模型已给出答案）。

    收敛性论证（这里刻意只留**一个**机制，不留"备用收尾节点"之类的死代码）：
    - `agent_node` 在 `tool_rounds >= max_rounds` 时**不绑定任何工具**，
      模型拿不到工具 schema，就不可能再产出 `tool_calls` → 必然走到 `END`；
    - 每次进入 `tools` 都会让 `tool_rounds` +1（有上界），因此循环次数有上界；
    - 万一某个网关在没有 schema 的情况下仍返回 `tool_calls`（现实中确实出现过），
      这里也不会执行工具、不会再回到 `agent`，直接结束——不会死循环。
    """
    last = (state.get("messages") or [])[-1]
    tool_calls = list(getattr(last, "tool_calls", None) or [])
    if not tool_calls:
        return END
    if int(state.get("tool_rounds") or 0) >= _max_rounds(config):
        logger.warning("工具轮数已用尽但模型仍返回 tool_calls，直接结束（工具未执行）")
        return END
    return "tools"


@lru_cache(maxsize=1)
def build_agent_graph():
    """构建并缓存 ReAct 图（图本身无状态，工具与 LLM 参数按请求经 config 传入）。"""
    graph = StateGraph(AgentState)
    graph.add_node("agent", agent_node)
    graph.add_node("tools", tools_node)
    graph.add_edge(START, "agent")
    graph.add_conditional_edges("agent", should_continue, {"tools": "tools", END: END})
    graph.add_edge("tools", "agent")
    return graph.compile()


def build_agent_input(
    question: str,
    history: Optional[List[dict]],
    context: str,
) -> List[Any]:
    """组装初始消息序列：system → 多轮历史 → 本轮 user（问题 + 首轮检索资料）。

    空资料由 `render_agent_prompts` 换成一句行动指令（"必须先调用 search_knowledge"）——
    实测写"（无）"会让模型直接回答"资料里没有"，而不是去检索。
    """
    system_prompt, user_prompt = render_agent_prompts(question, context)
    messages: List[Any] = [SystemMessage(content=system_prompt)]
    for turn in history or []:
        content = str((turn or {}).get("content") or "").strip()
        if not content:
            continue
        role = (turn or {}).get("role")
        if role == "user":
            messages.append(HumanMessage(content=content))
        elif role == "assistant":
            messages.append(AIMessage(content=content))
    messages.append(HumanMessage(content=user_prompt))
    return messages


async def invoke_agent(
    question: str,
    history: Optional[List[dict]],
    context: str,
    settings: ResolvedLlmSettings,
    *,
    course_id: Optional[int] = None,
    student_context: Optional[Dict[str, Any]] = None,
    max_tool_rounds: int = DEFAULT_MAX_TOOL_ROUNDS,
    on_hits=None,
) -> dict:
    """同步执行一轮 ReAct（非流式入口，便于单测与同步问答复用）。"""
    tools = build_course_tools(course_id, student_context, on_hits=on_hits)
    return await build_agent_graph().ainvoke(
        {"messages": build_agent_input(question, history, context)},
        config={
            "configurable": {
                "llm": settings,
                "tools": tools,
                "max_tool_rounds": max_tool_rounds,
            }
        },
    )


__all__ = [
    "build_agent_graph",
    "build_agent_input",
    "invoke_agent",
    "agent_node",
    "tools_node",
    "should_continue",
]
