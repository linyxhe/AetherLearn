"""ReAct 智能助教图的测试。

重点守三件事：
1. **成本与需求成比例**：不需要工具时只花一次模型调用（与改造前同价）；
2. **一定有界**：模型反复要求调用工具也不会死循环，最终必定收敛出一个答案；
3. **工具失败不打断整轮**：异常变成可读的观察结果交回模型。

用假模型（FakeToolModel）精确控制"哪一轮要求调用哪个工具"，不依赖真实网络与真实模型。
"""

import asyncio
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

import pytest

pytest.importorskip("langgraph")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langchain_core.tools import StructuredTool

from app.agents import graph_agent
from app.llm.config import ResolvedLlmSettings


def _settings(timeout: float = 5.0) -> ResolvedLlmSettings:
    return ResolvedLlmSettings(
        base_url="http://fake/v1",
        api_key="sk-fake-1234567890",
        model="fake-model",
        temperature=0.3,
        max_tokens=256,
        timeout=timeout,
    )


def _tool(name: str, result: str, raises: Optional[Exception] = None) -> StructuredTool:
    async def _run(query: str = "", **_: Any) -> str:
        if raises is not None:
            raise raises
        return result

    return StructuredTool.from_function(coroutine=_run, name=name, description=f"测试工具 {name}")


class FakeToolModel:
    """按脚本产出：前几轮要求调用工具，最后一轮给文本答案。

    关键真实性：**没有绑定工具时不可能产出 tool_calls**（真实模型没有工具 schema 就选不出工具）。
    这一点决定了"预算用尽 → 必然收敛"，测试必须照此模拟，否则会验出一个假的循环上界。
    """

    def __init__(self, script: List[Dict[str, Any]], final_text: str = "最终答案"):
        self.script = list(script)
        self.final_text = final_text
        self.calls = 0
        # 每次调用收到的工具表（用于断言"工具预算用尽后不再绑定工具"）
        self.bound_tool_names: List[List[str]] = []

    def bind_tools(self, tools):
        names = [t.name for t in tools]

        async def _invoke(messages):
            self.bound_tool_names.append(names)
            return await self._next(allow_tools=True)

        return _BoundModel(_invoke)

    async def ainvoke(self, messages):
        self.bound_tool_names.append([])
        return await self._next(allow_tools=False)

    async def _next(self, *, allow_tools: bool):
        self.calls += 1
        if allow_tools and self.script:
            step = self.script.pop(0)
            tool_calls = [
                {"name": name, "args": args or {}, "id": f"call_{self.calls}_{index}"}
                for index, (name, args) in enumerate(step.get("tools", []))
            ]
            return AIMessage(content=step.get("content", ""), tool_calls=tool_calls)
        return AIMessage(content=self.final_text)


class _BoundModel:
    """bind_tools 之后的假模型（只需要 ainvoke）。"""

    def __init__(self, invoke):
        self._invoke = invoke

    async def ainvoke(self, messages):
        return await self._invoke(messages)


def _run(model, tools: Dict[str, Any], *, max_rounds: int = 3, question: str = "问题"):
    graph_agent.build_agent_graph.cache_clear()
    return asyncio.run(
        graph_agent.build_agent_graph().ainvoke(
            {"messages": [HumanMessage(content=question)]},
            config={
                "configurable": {"llm": _settings(), "tools": tools, "max_tool_rounds": max_rounds}
            },
        )
    )


def test_no_tool_call_costs_exactly_one_model_call(monkeypatch):
    """资料够用时不该用工具：一次模型调用，与改造前同价。"""
    model = FakeToolModel([], final_text="直接回答")
    monkeypatch.setattr(graph_agent, "get_chat_model", lambda settings: model)

    state = _run(model, {"search_knowledge": _tool("search_knowledge", "命中 1 条")})

    assert model.calls == 1
    # 没走 tools 节点，状态里就不会有 tool_rounds（缺省即 0 轮）
    assert not state.get("tool_rounds")
    assert state["messages"][-1].content == "直接回答"


def test_tool_call_then_answer_produces_two_rounds(monkeypatch):
    """需要工具时：一轮工具 + 一轮作答，工具结果以 ToolMessage 回灌。"""
    model = FakeToolModel([{"tools": [("search_knowledge", {"query": "多态"})]}], final_text="带引用的回答")
    monkeypatch.setattr(graph_agent, "get_chat_model", lambda settings: model)

    state = _run(model, {"search_knowledge": _tool("search_knowledge", "命中 1 条：多态指…")})

    assert model.calls == 2
    assert state["tool_rounds"] == 1
    tool_messages = [m for m in state["messages"] if isinstance(m, ToolMessage)]
    assert len(tool_messages) == 1
    assert "多态指" in tool_messages[0].content
    # 工具复用模型的 tool_call id，否则 OpenAI 兼容接口会报错
    assert tool_messages[0].tool_call_id == state["messages"][1].tool_calls[0]["id"]


def test_loop_is_bounded_and_always_converges(monkeypatch):
    """模型每轮都要求调用工具时，必须在轮数上限处收敛并给出答案。"""
    # 脚本给 6 轮工具请求，但上限是 2 → 只应执行 2 轮工具
    model = FakeToolModel(
        [{"tools": [("search_knowledge", {"query": f"q{i}"})]} for i in range(6)],
        final_text="收敛后的答案",
    )
    monkeypatch.setattr(graph_agent, "get_chat_model", lambda settings: model)

    state = _run(model, {"search_knowledge": _tool("search_knowledge", "命中")}, max_rounds=2)

    # 只执行了 2 轮工具，模型多要的 4 轮被上限拦住
    assert state["tool_rounds"] == 2
    assert len([m for m in state["messages"] if isinstance(m, ToolMessage)]) == 2
    # 第 3 次调用起不再绑定工具（没有 schema 就不可能再产出 tool_calls）
    assert model.bound_tool_names[:2] == [["search_knowledge"], ["search_knowledge"]]
    assert model.bound_tool_names[-1] == []
    # 关键：一定有答案，不能返回空
    assert state["messages"][-1].content == "收敛后的答案"
    # 有界：2 轮工具 + 1 次最终生成 = 3 次模型调用
    assert model.calls == 3


def test_max_rounds_zero_means_no_tools_at_all(monkeypatch):
    """max_tool_rounds=0 → 不绑定工具，退化成一次纯生成。"""
    model = FakeToolModel([{"tools": [("search_knowledge", {"query": "x"})]}], final_text="纯生成答案")
    monkeypatch.setattr(graph_agent, "get_chat_model", lambda settings: model)

    state = _run(model, {"search_knowledge": _tool("search_knowledge", "命中")}, max_rounds=0)

    assert model.calls == 1
    assert model.bound_tool_names == [[]]
    assert state["messages"][-1].content == "纯生成答案"


def test_tool_failure_becomes_readable_observation(monkeypatch):
    """工具抛异常不能让整轮问答失败：转成观察结果交回模型。"""
    model = FakeToolModel([{"tools": [("search_knowledge", {"query": "x"})]}], final_text="失败后仍能作答")
    monkeypatch.setattr(graph_agent, "get_chat_model", lambda settings: model)

    state = _run(model, {"search_knowledge": _tool("search_knowledge", "", raises=RuntimeError("milvus down"))})

    tool_messages = [m for m in state["messages"] if isinstance(m, ToolMessage)]
    assert len(tool_messages) == 1
    assert "milvus down" in tool_messages[0].content
    assert state["messages"][-1].content == "失败后仍能作答"
    steps = [s for s in state["steps"] if s["type"] == "tool_end"]
    assert steps and steps[0]["ok"] is False


def test_unknown_tool_name_is_reported_back(monkeypatch):
    """模型编出不存在的工具名时如实回灌，而不是抛异常或卡死。"""
    model = FakeToolModel([{"tools": [("delete_everything", {})]}], final_text="事后回答")
    monkeypatch.setattr(graph_agent, "get_chat_model", lambda settings: model)

    state = _run(model, {"search_knowledge": _tool("search_knowledge", "命中")})

    tool_messages = [m for m in state["messages"] if isinstance(m, ToolMessage)]
    assert "不存在" in tool_messages[0].content
    assert "search_knowledge" in tool_messages[0].content  # 提示可用工具


def test_steps_record_tool_lifecycle(monkeypatch):
    """中间步骤事件要能被前端消费：有开始、有结束、带耗时。"""
    model = FakeToolModel([{"tools": [("search_knowledge", {"query": "多态"})]}], final_text="答案")
    monkeypatch.setattr(graph_agent, "get_chat_model", lambda settings: model)

    state = _run(model, {"search_knowledge": _tool("search_knowledge", "命中")})

    kinds = [s["type"] for s in state["steps"]]
    assert kinds == ["tool_start", "tool_end"]
    assert state["steps"][0]["tool"] == "search_knowledge"
    assert state["steps"][0]["args"] == {"query": "多态"}
    assert state["steps"][1]["ok"] is True
    assert state["steps"][1]["elapsed_ms"] >= 0


def test_agent_input_builds_system_history_user():
    """消息序列：system → 历史(user/assistant) → 本轮 user（含首轮资料）。"""
    messages = graph_agent.build_agent_input(
        "那它和重写有什么区别？",
        [
            {"role": "user", "content": "什么是方法重载？"},
            {"role": "assistant", "content": "同名不同参。"},
        ],
        "【资料】方法重载指…",
    )

    assert [type(m).__name__ for m in messages] == [
        "SystemMessage",
        "HumanMessage",
        "AIMessage",
        "HumanMessage",
    ]
    assert "首轮检索" in messages[-1].content or "方法重载指" in messages[-1].content
    # 空历史/脏历史不能造出空消息
    assert len(graph_agent.build_agent_input("q", [{"role": "assistant", "content": "  "}], "")) == 2


def test_agent_prompt_is_registered():
    from app.prompts import get_prompt

    spec = get_prompt("qa_agent")
    assert spec.placeholders() == {"question", "context"}
    # agent 提示词必须允许"资料里没有"——这与 RAG 提示词的硬性禁止是有意区分的
    assert "课程资料里暂时没有" in spec.system
