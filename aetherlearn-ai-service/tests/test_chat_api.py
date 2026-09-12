import sys
from pathlib import Path

import pytest

pytest.importorskip("fastapi")
pytest.importorskip("httpx")
pytest.importorskip("langgraph")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage, AIMessageChunk

from app.api import chat as chat_api
from app.config import get_settings
from app.llm.errors import LlmTimeout, RetrievalUnavailable
from app.main import app


def _headers() -> dict:
    key = get_settings().SERVICE_API_KEY
    return {"X-API-Key": key} if key else {}


SOURCE_CHUNK = {"chunk_id": 11, "doc_id": 3, "seq": 0, "content": "Java 通过 JVM 跨平台", "score": 0.9}


class FakeGraph:
    """替身图：按真实事件顺序产出 updates 与 messages。"""

    def __init__(self, answer="你好，我是助教", fail=None):
        self.answer = answer
        self.fail = fail

    async def ainvoke(self, state, config=None):
        if self.fail:
            raise self.fail
        return {
            "answer": self.answer,
            "llm_ms": 42,
            "retrieval_ms": 7,
            "retrieval_strategy": "java",
            "retrieval_status": "ok",
            "sources": [SOURCE_CHUNK],
            "prompt_key": "qa_with_context",
        }

    async def astream(self, state, config=None, stream_mode=None):
        if self.fail:
            raise self.fail
        yield ("updates", {"retrieve": {"retrieval_ms": 7, "retrieval_strategy": "java", "retrieval_status": "ok"}})
        yield ("updates", {"build_context": {"sources": [SOURCE_CHUNK], "prompt_key": "qa_with_context"}})
        for piece in ("你好", "，我是助教"):
            yield ("messages", (AIMessageChunk(content=piece), {"langgraph_node": "generate"}))
        yield ("updates", {"generate": {"answer": self.answer, "llm_ms": 42}})


def _client(monkeypatch, graph) -> TestClient:
    monkeypatch.setattr(chat_api, "build_qa_graph", lambda: graph)
    # agent 路径在 _agent_stream 内部延迟导入图，因此在图模块上打补丁
    monkeypatch.setattr("app.agents.graph_agent.build_agent_graph", lambda: graph)
    return TestClient(app)


def test_requires_api_key():
    client = TestClient(app)
    response = client.get("/v1/chat/health")

    # .env 未设 SERVICE_API_KEY 时中间件放行，此时不做断言之外的处理
    expected_key = get_settings().SERVICE_API_KEY
    if expected_key:
        assert response.status_code == 401
    else:
        assert response.status_code == 200


def test_health_reports_prompt_registry(monkeypatch):
    client = _client(monkeypatch, FakeGraph())
    response = client.get("/v1/chat/health", headers=_headers())

    assert response.status_code == 200
    body = response.json()
    assert body["graph_ready"] is True
    assert "qa_with_context" in body["providers"]


def test_task_endpoint_returns_structured_result(monkeypatch):
    from app.agents.graph_task import TaskResult

    async def fake_invoke_task(prompt_key, variables=None, **kwargs):
        assert prompt_key == "grade_subjective"
        assert variables["full_score"] == 10
        return TaskResult(
            raw='{"score": 8, "feedback": "要点基本齐全"}',
            data={"score": 8, "feedback": "要点基本齐全"},
            llm_ms=12,
            output_format="json_object",
        )

    monkeypatch.setattr(chat_api, "invoke_task", fake_invoke_task)
    client = TestClient(app)
    response = client.post(
        "/v1/chat/task",
        json={
            "prompt_key": "grade_subjective",
            "variables": {
                "question": "简述 JVM",
                "standard_answer": "虚拟机",
                "student_answer": "跑字节码的虚拟机",
                "full_score": 10,
            },
            "output_format": "json_object",
        },
        headers=_headers(),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["data"]["score"] == 8
    assert body["llm_ms"] == 12
    assert body["provider"] == "python"


def test_task_endpoint_rejects_unknown_prompt_key(monkeypatch):
    async def fake_invoke_task(prompt_key, variables=None, **kwargs):
        raise KeyError("未注册的提示词 key：nope")

    monkeypatch.setattr(chat_api, "invoke_task", fake_invoke_task)
    client = TestClient(app)
    response = client.post(
        "/v1/chat/task", json={"prompt_key": "nope"}, headers=_headers()
    )

    assert response.status_code == 400
    assert "prompt_error" in response.json()["detail"]


def test_qa_sync_returns_answer_and_sources(monkeypatch):
    client = _client(monkeypatch, FakeGraph())
    response = client.post(
        "/v1/chat/qa", json={"question": "什么是 JVM"}, headers=_headers()
    )

    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == "你好，我是助教"
    assert body["retrieval_strategy"] == "java"
    assert body["sources"][0]["chunk_id"] == 11
    assert body["provider"] == "python"


def test_qa_sync_empty_answer_is_treated_as_failure(monkeypatch):
    client = _client(monkeypatch, FakeGraph(answer="   "))
    response = client.post("/v1/chat/qa", json={"question": "什么是 JVM"}, headers=_headers())

    assert response.status_code == 502
    assert response.json()["detail"] == "llm_bad_response"


def test_qa_sync_timeout_maps_to_504(monkeypatch):
    client = _client(monkeypatch, FakeGraph(fail=LlmTimeout("超预算")))
    response = client.post("/v1/chat/qa", json={"question": "什么是 JVM"}, headers=_headers())

    assert response.status_code == 504
    assert response.json()["detail"] == "llm_timeout"


def test_qa_sync_retrieval_unavailable_maps_to_503(monkeypatch):
    """检索不可用必须与 LLM 失败区分：Java 要回退整轮检索，而不是只换个生成方式。"""
    client = _client(monkeypatch, FakeGraph(fail=RetrievalUnavailable("milvus down")))
    response = client.post("/v1/chat/qa", json={"question": "什么是 JVM"}, headers=_headers())

    assert response.status_code == 503
    assert response.json()["detail"] == "retrieval_unavailable"


def test_qa_stream_event_order_and_payloads(monkeypatch):
    client = _client(monkeypatch, FakeGraph())
    response = client.post(
        "/v1/chat/qa/stream", json={"question": "什么是 JVM"}, headers=_headers()
    )

    assert response.status_code == 200
    text = response.text
    # sources 必须在 chunk 之前
    assert text.index("event: sources") < text.index("event: chunk")
    assert "event: sources" in text and "event: chunk" in text and "event: done" in text
    assert "event: error" not in text
    assert text.count("event: chunk") == 2
    assert "chunk_id" in text.split("event: sources", 1)[1].split("event: chunk", 1)[0]

    done_payload = text.split("event: done", 1)[1]
    assert '"llmMs": 42' in done_payload or '"llmMs":42' in done_payload
    assert '"provider": "python"' in done_payload or '"provider":"python"' in done_payload


class FakeAgentGraph:
    """替身 ReAct 图：模拟"中间轮要求调用工具 → 工具返回 → 最终轮给答案"。"""

    def __init__(self, answer="带引用的回答", fail=None):
        self.answer = answer
        self.fail = fail

    async def astream(self, state, config=None, stream_mode=None):
        if self.fail:
            raise self.fail
        # 中间轮：只有 tool_calls，没有文本（提示词要求不输出解释性文字）
        yield ("updates", {"agent": {"messages": [AIMessage(content="", tool_calls=[
            {"name": "search_knowledge", "args": {"query": "多态"}, "id": "call_1"}
        ])]}})
        # 工具执行：回灌观察结果并报告步骤
        yield ("updates", {"tools": {
            "messages": [],
            "tool_rounds": 1,
            "steps": [
                {"type": "tool_start", "tool": "search_knowledge", "args": {"query": "多态"}},
                {"type": "tool_end", "tool": "search_knowledge", "ok": True, "elapsed_ms": 320},
            ],
        }})
        # 最终轮：文本答案
        for piece in ("多态指", "同名不同参。"):
            yield ("messages", (AIMessageChunk(content=piece), {"langgraph_node": "agent"}))
        yield ("updates", {"agent": {"messages": [AIMessage(content=self.answer)]}})


def test_agent_stream_emits_steps_sources_and_done(monkeypatch):
    """agent 流：sources → step* → chunk* → done，且 done 标记 strategy=agent。"""
    client = _client(monkeypatch, FakeAgentGraph())
    response = client.post(
        "/v1/chat/qa/stream",
        json={"question": "什么是多态", "course_id": 1, "agent": True},
        headers=_headers(),
    )

    assert response.status_code == 200
    text = response.text
    assert "event: step" in text
    assert text.index("event: sources") < text.index("event: step")
    assert text.index("event: step") < text.index("event: chunk")
    # 中间轮不带文本，所以答案里只有最终轮的两段
    assert text.count("event: chunk") == 2
    assert "tool_start" in text and "tool_end" in text

    done_payload = text.split("event: done", 1)[1]
    assert '"retrievalStrategy": "agent"' in done_payload or '"retrievalStrategy":"agent"' in done_payload
    assert "event: error" not in text


def test_agent_stream_requires_no_chunk_to_report_empty(monkeypatch):
    """agent 一个 token 都没出时照样报 llm_empty，让 Java 整轮回退。"""
    client = _client(monkeypatch, FakeAgentGraph(fail=LlmTimeout("超预算")))
    response = client.post(
        "/v1/chat/qa/stream", json={"question": "问", "agent": True}, headers=_headers()
    )

    assert "event: error" in response.text
    assert "llm_timeout" in response.text
    assert "event: done" not in response.text


def test_qa_stream_error_event_when_graph_fails_before_any_chunk(monkeypatch):
    client = _client(monkeypatch, FakeGraph(fail=LlmTimeout("超预算")))
    response = client.post(
        "/v1/chat/qa/stream", json={"question": "什么是 JVM"}, headers=_headers()
    )

    assert response.status_code == 200
    assert "event: error" in response.text
    assert "llm_timeout" in response.text
    assert "event: done" not in response.text
