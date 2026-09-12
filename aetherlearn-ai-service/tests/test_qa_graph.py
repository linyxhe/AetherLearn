import asyncio
import sys
from pathlib import Path

import pytest

pytest.importorskip("langgraph")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from langchain_core.messages import AIMessage

from app.agents import graph_qa
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


class FakeChatModel:
    """只实现 ainvoke 的假模型，避免测试依赖真实网络。"""

    def __init__(self, text="这是回答"):
        self.text = text
        self.calls = 0
        # 记录最后一次收到的消息序列，用于断言多轮历史以角色消息形式下发
        self.last_messages = []

    async def ainvoke(self, messages, **kwargs):
        self.calls += 1
        self.last_messages = list(messages)
        return AIMessage(content=self.text)


def test_retrieve_node_passes_through_java_chunks(monkeypatch):
    """Java 已是权威检索方：给了切片就不能再检索一次。"""

    def boom(*args, **kwargs):
        raise AssertionError("不应调用兜底检索")

    monkeypatch.setattr(graph_qa, "_fallback_retrieve", boom)
    state = {
        "question": "什么是 JVM",
        "chunks": [{"chunk_id": 1, "doc_id": 1, "seq": 0, "content": "JVM 是虚拟机", "score": 0.9}],
    }

    update = asyncio.run(graph_qa.retrieve_node(state, {}))

    assert update["retrieval_strategy"] == "java"
    assert update["retrieval_status"] == "ok"
    assert update["retrieval_ms"] == 0
    assert len(update["chunks"]) == 1


def test_retrieve_node_skips_for_small_talk():
    update = asyncio.run(graph_qa.retrieve_node({"question": "你好呀", "small_talk": True}, {}))

    assert update["retrieval_strategy"] == "none"
    assert update["retrieval_status"] == "skipped"
    assert update["chunks"] == []


def test_retrieve_node_falls_back_when_java_provides_nothing(monkeypatch):
    async def fake_fallback(course_id, query, top_k, rerank_top_k):
        assert course_id == 1
        assert top_k == 30
        return [{"chunk_id": 7, "doc_id": 3, "seq": 0, "content": "兜底命中", "score": 0.5}]

    monkeypatch.setattr(graph_qa, "_fallback_retrieve", fake_fallback)

    update = asyncio.run(
        graph_qa.retrieve_node({"question": "Java 集合", "course_id": 1, "top_k": 30, "rerank_top_k": 10}, {})
    )

    assert update["retrieval_strategy"] == "hybrid"
    assert update["chunks"][0]["chunk_id"] == 7


def test_build_context_respects_budget_and_selects_prompt():
    state = {
        "question": "问题",
        "history": [],
        "context_budget": 12,
        "chunks": [
            {"chunk_id": 1, "doc_id": 1, "seq": 0, "content": "一二三四五", "score": 1.0},
            {"chunk_id": 2, "doc_id": 1, "seq": 1, "content": "六七八九十", "score": 0.9},
        ],
    }

    update = asyncio.run(graph_qa.build_context_node(state, {}))

    assert update["prompt_key"] == "qa_with_context"
    assert update["context"] == "一二三四五"
    assert len(update["sources"]) == 1
    assert "一二三四五" in update["user_prompt"]


def test_build_context_without_chunks_uses_no_context_prompt():
    update = asyncio.run(graph_qa.build_context_node({"question": "你好", "history": []}, {}))

    assert update["prompt_key"] == "qa_no_context"
    assert update["context"] == ""
    assert update["sources"] == []
    assert "你好" in update["user_prompt"]


def test_generate_node_sends_history_as_role_messages(monkeypatch):
    """多轮历史必须变成 system → user → assistant → … → 本轮 user 的消息序列。

    这是"记忆与上下文"改造的核心：以前历史是拼进 user 提示词的一段纯文本，
    模型只能靠"学生：/助教："的格式猜哪句是自己说的。
    """
    from langchain_core.messages import AIMessage as _AI, HumanMessage as _Human, SystemMessage as _Sys

    fake = FakeChatModel("多轮回答")
    monkeypatch.setattr(graph_qa, "get_chat_model", lambda settings: fake)

    history = [
        {"role": "user", "content": "什么是方法重载？"},
        {"role": "assistant", "content": "同一个类中同名不同参。"},
        {"role": "user", "content": "那重写呢？"},
        {"role": "assistant", "content": "发生在父子类之间。"},
    ]
    asyncio.run(
        graph_qa.generate_node(
            {
                "system_prompt": "系统设定",
                "user_prompt": "【学生问题】\n它俩区别是什么",
                "history": history,
            },
            {"configurable": {"llm": _settings()}},
        )
    )

    roles = [type(m).__name__ for m in fake.last_messages]
    assert roles == ["SystemMessage", "HumanMessage", "AIMessage", "HumanMessage", "AIMessage", "HumanMessage"]
    assert isinstance(fake.last_messages[0], _Sys)
    assert isinstance(fake.last_messages[1], _Human)
    assert isinstance(fake.last_messages[2], _AI)
    # 顺序与内容都不能变：上一轮助教的话不能跑到学生前面
    assert fake.last_messages[1].content == "什么是方法重载？"
    assert fake.last_messages[2].content == "同一个类中同名不同参。"
    assert fake.last_messages[-1].content == "【学生问题】\n它俩区别是什么"


def test_generate_node_drops_malformed_history():
    """脏历史不应把非法消息发给模型：空内容丢弃，未知角色丢弃。"""
    messages = graph_qa._history_messages(
        [
            {"role": "user", "content": "有效问题"},
            {"role": "assistant", "content": "   "},  # 空白 → 丢弃
            {"role": "system", "content": "越权角色"},  # 非法角色 → 丢弃
            {"role": "assistant", "content": "有效回答"},
            {},  # 缺字段 → 丢弃
        ]
    )

    assert [type(m).__name__ for m in messages] == ["HumanMessage", "AIMessage"]
    assert messages[0].content == "有效问题"
    assert messages[1].content == "有效回答"


def test_generate_node_without_history_sends_two_messages(monkeypatch):
    """没有历史时退化成 system + 本轮 user 两条，不能多出空消息。"""
    fake = FakeChatModel("首轮回答")
    monkeypatch.setattr(graph_qa, "get_chat_model", lambda settings: fake)

    asyncio.run(
        graph_qa.generate_node(
            {"system_prompt": "sys", "user_prompt": "第一问", "history": []},
            {"configurable": {"llm": _settings()}},
        )
    )

    assert [type(m).__name__ for m in fake.last_messages] == ["SystemMessage", "HumanMessage"]


def test_generate_node_returns_answer_and_latency(monkeypatch):
    fake = FakeChatModel("生成结果")
    monkeypatch.setattr(graph_qa, "get_chat_model", lambda settings: fake)

    update = asyncio.run(
        graph_qa.generate_node(
            {"system_prompt": "sys", "user_prompt": "user"},
            {"configurable": {"llm": _settings()}},
        )
    )

    assert update["answer"] == "生成结果"
    assert update["llm_ms"] >= 0
    assert fake.calls == 1


def test_graph_end_to_end_with_fake_model(monkeypatch):
    fake = FakeChatModel("端到端回答")
    monkeypatch.setattr(graph_qa, "get_chat_model", lambda settings: fake)

    final_state = asyncio.run(
        graph_qa.build_qa_graph().ainvoke(
            {
                "question": "什么是 JVM",
                "history": [],
                "chunks": [{"chunk_id": 1, "doc_id": 1, "seq": 0, "content": "JVM 是虚拟机", "score": 0.9}],
            },
            config={"configurable": {"llm": _settings()}},
        )
    )

    assert final_state["answer"] == "端到端回答"
    assert final_state["retrieval_strategy"] == "java"
    assert len(final_state["sources"]) == 1
