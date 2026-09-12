"""LLM 编排接口：连接测试、健康自检（问答/批改/出题等端点在后续阶段加入）。

错误映射约定（Java 侧据此归类降级原因）：
- 503 llm_config_incomplete / llm_unavailable → python_llm_unreachable 类
- 504 llm_timeout                            → python_llm_timeout
- 502 llm_bad_response                       → python_llm_empty（模型没给出可用内容）
"""

import asyncio
import json
import logging
from typing import Any, List

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import StreamingResponse
from langchain_core.messages import AIMessage

from app.agents.graph_qa import build_qa_graph
from app.agents.graph_task import invoke_task
from app.agents.state import QaState
from app.llm.config import ResolvedLlmSettings, resolve_llm_settings
from app.llm.errors import (
    LlmBadResponse,
    LlmConfigIncomplete,
    LlmError,
    LlmTimeout,
    LlmUnavailable,
    RetrievalUnavailable,
)
from app.llm.sse import HEARTBEAT_FRAME, format_sse_event
from app.models.chat import (
    ChatHealthResponse,
    ChunkPayload,
    QaChatRequest,
    QaChatResponse,
    TaskPromptRequest,
    TaskPromptResponse,
    TestConnectionRequest,
    TestConnectionResponse,
)
from app.prompts import all_prompts
from app.tools import build_course_tools


logger = logging.getLogger(__name__)
router = APIRouter(prefix="/v1/chat", tags=["Chat"])

# 心跳间隔：让 Java 侧的空闲看门狗始终有数据可读（检索阶段可能十几秒没有 token）。
HEARTBEAT_SECONDS = 15.0

SSE_HEADERS = {
    "Cache-Control": "no-cache",
    "Connection": "keep-alive",
    "X-Accel-Buffering": "no",
}


def to_http_error(exc: Exception) -> HTTPException:
    """把编排异常映射为稳定的 HTTP 状态码与机器可读的 detail。"""
    if isinstance(exc, RetrievalUnavailable):
        # 与 LLM 失败区分：Java 应回退到自己的检索链（含 BM25），而不是仅替换生成
        return HTTPException(status_code=503, detail="retrieval_unavailable")
    if isinstance(exc, LlmConfigIncomplete):
        return HTTPException(status_code=503, detail="llm_config_incomplete")
    if isinstance(exc, LlmTimeout):
        return HTTPException(status_code=504, detail="llm_timeout")
    if isinstance(exc, LlmUnavailable):
        return HTTPException(status_code=503, detail=f"llm_unavailable: {exc}")
    if isinstance(exc, LlmBadResponse):
        return HTTPException(status_code=502, detail="llm_bad_response")
    if isinstance(exc, LlmError):
        return HTTPException(status_code=502, detail=f"llm_error: {exc}")
    logger.exception("LLM 编排出现未归类异常")
    return HTTPException(status_code=500, detail="llm_internal_error")


@router.get("/health", response_model=ChatHealthResponse)
async def chat_health() -> ChatHealthResponse:
    """编排层自检：不调用模型、不消耗 token。"""
    settings = resolve_llm_settings()
    graph_ready = False
    try:
        from app.agents.graph_qa import build_qa_graph

        build_qa_graph()
        graph_ready = True
    except Exception as exc:  # noqa: BLE001 - 图未就绪不应影响自检接口
        logger.warning("问答图未就绪：%s", exc)

    return ChatHealthResponse(
        status="ok" if settings.available() else "degraded",
        llm_configured=settings.available(),
        model=settings.model,
        base_url=settings.base_url,
        providers=sorted(all_prompts().keys()),
        graph_ready=graph_ready,
    )


@router.post("/test-connection", response_model=TestConnectionResponse)
async def test_connection(req: TestConnectionRequest) -> TestConnectionResponse:
    """管理端连接测试：确认配置可用并返回模型原话。"""
    settings = resolve_llm_settings(req.llm)
    try:
        result = await invoke_task("test_connection", {}, settings=settings)
    except Exception as exc:  # noqa: BLE001 - 统一映射状态码
        raise to_http_error(exc) from exc

    text = (result.raw or "").strip()
    if not text:
        raise HTTPException(
            status_code=502,
            detail="模型已连接，但没有返回内容，请检查模型是否支持对话接口",
        )
    return TestConnectionResponse(
        response=text,
        provider="python",
        model=settings.model,
        key_fp=settings.key_fingerprint(),
        llm_ms=result.llm_ms,
    )


@router.post("/task", response_model=TaskPromptResponse)
async def chat_task(req: TaskPromptRequest) -> TaskPromptResponse:
    """通用 LLM 任务：批改 / 出题 / 报告 / 建议共用同一张任务图，靠 prompt_key 区分。

    做成一个端点而不是五个：形状完全一致（一次调用 + 可选结构化解析），
    新增能力只需在提示词注册表里加一条。
    """
    settings = resolve_llm_settings(req.llm)
    try:
        result = await invoke_task(
            req.prompt_key,
            req.variables,
            output_format=req.output_format,
            settings=settings,
            system_prompt=req.system_prompt,
            user_prompt=req.user_prompt,
            max_reflections=req.max_reflections,
        )
    except KeyError as exc:
        # 未知 key 或缺变量：调用方的编程错误，返回 400 而不是 5xx
        raise HTTPException(status_code=400, detail=f"prompt_error: {exc}") from exc
    except Exception as exc:  # noqa: BLE001 - 统一映射状态码
        raise to_http_error(exc) from exc

    return TaskPromptResponse(
        raw=result.raw,
        data=result.data,
        llm_ms=result.llm_ms,
        parse_error=result.error,
        reflections=result.reflections,
        provider="python",
        model=settings.model,
    )


def _sse_error_code(exc: Exception) -> str:
    """SSE 错误事件里使用的机器可读原因，Java 侧据此归类降级原因。"""
    if isinstance(exc, RetrievalUnavailable):
        return "retrieval_unavailable"
    if isinstance(exc, LlmConfigIncomplete):
        return "llm_config_incomplete"
    if isinstance(exc, LlmTimeout):
        return "llm_timeout"
    if isinstance(exc, LlmUnavailable):
        return "llm_unavailable"
    if isinstance(exc, LlmBadResponse):
        return "llm_bad_response"
    return "llm_error"


def _initial_state(req: QaChatRequest) -> QaState:
    """把请求转成图状态。"""
    return {
        "question": req.question,
        # 历史按 role 交替传递；只取需要的两个字段，避免把校验用的其他信息带进图状态
        "history": [{"role": turn.role, "content": turn.content} for turn in req.history],
        "course_id": req.course_id,
        "top_k": req.top_k,
        "rerank_top_k": req.rerank_top_k,
        "context_budget": req.context_budget,
        "small_talk": req.small_talk,
        "chunks": [chunk.model_dump() for chunk in req.chunks],
    }


def _last_answer(messages: List[Any]) -> str:
    """从消息序列里取"最终答案"。

    agent 循环里最后一条未必是答案（可能是带 tool_calls 的 AIMessage 或 ToolMessage），
    因此从后往前找第一条**有文本内容**的 AIMessage——`force_final` 之后模型没再产出内容
    这类边界情况也能拿到上一轮已生成的文本，而不是回一个空字符串。
    """
    for message in reversed(messages or []):
        if isinstance(message, AIMessage):
            content = message.content
            text = (content if isinstance(content, str) else str(content)).strip()
            if text:
                return text
    return ""


def _agent_hits_collector() -> tuple:
    """返回 (收集列表, 回调)：工具命中切片累积到列表里，供来源组装使用。"""
    collected: List[dict] = []

    def _on_hits(hits: List[dict]) -> None:
        collected.extend(hits or [])

    return collected, _on_hits



async def _run_agent(req: QaChatRequest, settings: ResolvedLlmSettings) -> dict:
    """执行一轮 ReAct 智能助教（同步入口）。

    与固定流程的差别：检索可以由模型在循环里再次发起，因此 `sources` 只回
    **工具检索到的**切片（Java 已持有自己的首轮切片，重复回传会让它做无谓的
    MySQL 回查并误报"补充了来源"）。合并由 Java 负责——它才是来源展示的所有权方。
    """
    from app.agents.graph_agent import build_agent_graph, build_agent_input
    from app.agents.graph_qa import select_context

    context, _first_pass_sources = select_context(
        [chunk.model_dump() for chunk in req.chunks], req.context_budget
    )
    tool_hits, on_hits = _agent_hits_collector()
    tools = build_course_tools(req.course_id, req.student_context, on_hits=on_hits)

    final_state = await build_agent_graph().ainvoke(
        {"messages": build_agent_input(req.question, [t.model_dump() for t in req.history], context)},
        config={
            "configurable": {
                "llm": settings,
                "tools": tools,
                "max_tool_rounds": req.max_tool_rounds,
            }
        },
    )
    messages = final_state.get("messages") or []
    return {
        "answer": _last_answer(messages),
        "sources": tool_hits,
        "steps": final_state.get("steps") or [],
        "tool_rounds": final_state.get("tool_rounds") or 0,
    }


@router.post("/qa", response_model=QaChatResponse)
async def qa_chat(req: QaChatRequest) -> QaChatResponse:
    """问答编排（同步）。Java 侧拿到完整答案后再决定如何回给前端。"""
    settings = resolve_llm_settings(req.llm)

    if req.agent:
        try:
            agent_result = await _run_agent(req, settings)
        except Exception as exc:  # noqa: BLE001 - 统一映射状态码
            raise to_http_error(exc) from exc
        answer = (agent_result["answer"] or "").strip()
        if not answer:
            # 空回答等同于失败：让 Java 走检索结果降级，而不是把空白答案回给前端。
            raise HTTPException(status_code=502, detail="llm_bad_response")
        return QaChatResponse(
            answer=answer,
            llm_ms=0,
            retrieval_ms=0,
            retrieval_strategy="agent",
            retrieval_status="ok",
            provider="python",
            sources=[ChunkPayload(**chunk) for chunk in agent_result["sources"]],
            meta={
                "prompt_key": "qa_agent",
                "tool_rounds": agent_result["tool_rounds"],
                "steps": agent_result["steps"],
                "key_fp": settings.key_fingerprint(),
            },
        )

    try:
        final_state = await build_qa_graph().ainvoke(
            _initial_state(req), config={"configurable": {"llm": settings}}
        )
    except Exception as exc:  # noqa: BLE001 - 统一映射状态码
        raise to_http_error(exc) from exc

    answer = (final_state.get("answer") or "").strip()
    if not answer:
        # 空回答等同于失败：让 Java 走检索结果降级，而不是把空白答案回给前端。
        raise HTTPException(status_code=502, detail="llm_bad_response")

    return QaChatResponse(
        answer=answer,
        llm_ms=final_state.get("llm_ms") or 0,
        retrieval_ms=final_state.get("retrieval_ms") or 0,
        retrieval_strategy=final_state.get("retrieval_strategy") or "none",
        retrieval_status=final_state.get("retrieval_status") or "skipped",
        provider="python",
        sources=[ChunkPayload(**chunk) for chunk in (final_state.get("sources") or [])],
        meta={
            "prompt_key": final_state.get("prompt_key"),
            "key_fp": settings.key_fingerprint(),
        },
    )


async def _sse_from_astream(request: Request, astream_factory, on_item):
    """把图的 `astream` 转成 SSE 帧（问答图与 ReAct 图共用这一套骨架）。

    抽出来的原因：心跳、断连检测、异常归类、"一个 token 都没出"的兜底
    这些是**与图无关**的通用逻辑，两条链路各写一遍必然漂移。
    各图只需提供 `on_item(mode, payload) -> [(event, data), …]`：
    - `mode == "__end__"` 表示流结束（用于"没发出任何 chunk 就失败"的判定）；
    - 抛出的异常由外层统一映射成 `error` 事件。
    """
    queue: asyncio.Queue = asyncio.Queue()

    async def produce():
        try:
            async for mode, payload in astream_factory():
                await queue.put(("graph", (mode, payload)))
        except Exception as exc:  # noqa: BLE001 - 归类后交给消费者发 error 事件
            await queue.put(("error", exc))
        finally:
            await queue.put(("end", None))

    async def beat():
        while True:
            await asyncio.sleep(HEARTBEAT_SECONDS)
            await queue.put(("heartbeat", None))

    produce_task = asyncio.create_task(produce())
    beat_task = asyncio.create_task(beat())
    try:
        while True:
            kind, payload = await queue.get()

            if kind == "heartbeat":
                yield HEARTBEAT_FRAME
                continue

            if kind == "error":
                yield format_sse_event("error", _sse_error_code(payload))
                return

            if kind == "end":
                for event, data in on_item("__end__", None):
                    yield format_sse_event(event, data)
                return

            for event, data in on_item(*payload):
                yield format_sse_event(event, data)

            if await request.is_disconnected():
                return
    finally:
        produce_task.cancel()
        beat_task.cancel()


def _qa_stream_frames(accumulated: dict, state: dict):
    """问答图（固定流程）的帧生产者。"""
    emitted = {"chunk": False}

    def on_item(mode: str, payload):
        frames = []
        if mode == "__end__":
            if not emitted["chunk"]:
                # 一个 token 都没出：明确告知失败，让 Java 整轮回退
                frames.append(("error", "llm_empty"))
            return frames

        if mode == "updates":
            for node, update in (payload or {}).items():
                if isinstance(update, dict):
                    accumulated.update(update)
                if node == "build_context":
                    frames.append(("sources", json.dumps(update.get("sources") or [], ensure_ascii=False)))
                elif node == "generate":
                    answer_text = (update.get("answer") or "").strip()
                    if not answer_text:
                        frames.append(("error", "llm_empty"))
                        return frames
                    emitted["chunk"] = True
                    frames.append(("done", json.dumps({
                        "llmMs": update.get("llm_ms") or 0,
                        "retrievalMs": accumulated.get("retrieval_ms") or 0,
                        "retrievalStrategy": accumulated.get("retrieval_strategy") or "none",
                        "retrievalStatus": accumulated.get("retrieval_status") or "skipped",
                        "promptKey": accumulated.get("prompt_key"),
                        "provider": "python",
                        "fallbackReason": None,
                    }, ensure_ascii=False)))
        else:  # messages
            message_chunk, metadata = payload
            if metadata.get("langgraph_node") != "generate":
                return frames
            text = message_chunk.content
            if isinstance(text, str) and text:
                emitted["chunk"] = True
                frames.append(("chunk", text))
        return frames

    return on_item


def _agent_stream_frames(state: dict, tool_hits: List[dict]):
    """ReAct 图的帧生产者。

    与问答图的差别：
    - 任何一个 `agent` 节点的**文本**都是答案的一部分（中间轮的文本在提示词里被要求为空，
      万一模型还是说了一句"我先查一下"，它会作为过程说明流出，不会被当成错误）；
    - 工具调用与返回额外发 `step` 事件，前端可以显示"正在检索知识库…"；
    - 工具检索到的切片合并进 `sources` 再发一次（来源可能比首轮更多）。
    """
    emitted = {"chunk": False}

    def on_item(mode: str, payload):
        frames = []
        if mode == "__end__":
            if not emitted["chunk"]:
                frames.append(("error", "llm_empty"))
            return frames

        if mode == "updates":
            for node, update in (payload or {}).items():
                if not isinstance(update, dict):
                    continue
                state.update(update)
                if node == "tools":
                    for step in update.get("steps") or []:
                        frames.append(("step", json.dumps(step, ensure_ascii=False)))
                    if tool_hits:
                        # 与同步链路同口径：只发**工具检索到的**切片，由 Java 与首轮合并
                        frames.append(("sources", json.dumps(tool_hits, ensure_ascii=False)))
                elif node == "agent":
                    last = (update.get("messages") or [None])[-1]
                    calls = list(getattr(last, "tool_calls", None) or [])
                    if calls:
                        # 中间轮：只报告"要调用什么"，不当作答案
                        frames.append(("step", json.dumps(
                            {"type": "tool_calls", "tools": [c.get("name") for c in calls]},
                            ensure_ascii=False,
                        )))
                        return frames
                    answer_text = _last_answer(update.get("messages") or [])
                    if not answer_text:
                        frames.append(("error", "llm_empty"))
                        return frames
                    emitted["chunk"] = True
                    frames.append(("done", json.dumps({
                        "llmMs": 0,
                        "retrievalMs": 0,
                        "retrievalStrategy": "agent",
                        "retrievalStatus": "ok",
                        "promptKey": "qa_agent",
                        "provider": "python",
                        "fallbackReason": None,
                        "toolRounds": state.get("tool_rounds") or 0,
                    }, ensure_ascii=False)))
        else:  # messages
            message_chunk, metadata = payload
            if metadata.get("langgraph_node") != "agent":
                return frames
            text = message_chunk.content
            if isinstance(text, str) and text:
                emitted["chunk"] = True
                frames.append(("chunk", text))
        return frames

    return on_item


async def _agent_stream(req: QaChatRequest, request: Request, settings: ResolvedLlmSettings):
    """ReAct 智能助教的 SSE 流。"""
    from app.agents.graph_agent import build_agent_graph, build_agent_input
    from app.agents.graph_qa import select_context

    context, first_pass_sources = select_context(
        [chunk.model_dump() for chunk in req.chunks], req.context_budget
    )
    tool_hits: List[dict] = []
    tools = build_course_tools(req.course_id, req.student_context, on_hits=tool_hits.extend)
    state: dict = {"tool_rounds": 0}
    graph_input = {
        "messages": build_agent_input(req.question, [t.model_dump() for t in req.history], context)
    }

    def factory():
        return build_agent_graph().astream(
            graph_input,
            config={
                "configurable": {
                    "llm": settings,
                    "tools": tools,
                    "max_tool_rounds": req.max_tool_rounds,
                }
            },
            stream_mode=["updates", "messages"],
        )

    # 首轮来源先发，前端不会有一段空白期（与固定流程的行为一致）
    yield format_sse_event("sources", json.dumps(first_pass_sources, ensure_ascii=False))
    async for frame in _sse_from_astream(request, factory, _agent_stream_frames(state, tool_hits)):
        yield frame


@router.post("/qa/stream")
async def qa_stream(req: QaChatRequest, request: Request) -> StreamingResponse:
    """问答编排（SSE 流式）。

    事件顺序：`sources` → （agent 时可能有 `step`*）→ `chunk`* → `done`；
    失败时发 `error`（机器可读原因）。心跳用注释行发出，避免 Java 侧空闲看门狗误判。
    """
    settings = resolve_llm_settings(req.llm)

    if req.agent:
        return StreamingResponse(
            _agent_stream(req, request, settings),
            media_type="text/event-stream",
            headers=SSE_HEADERS,
        )

    state = _initial_state(req)
    config = {"configurable": {"llm": settings}}
    accumulated: dict = {}

    def factory():
        return build_qa_graph().astream(state, config, stream_mode=["updates", "messages"])

    return StreamingResponse(
        _sse_from_astream(request, factory, _qa_stream_frames(accumulated, state)),
        media_type="text/event-stream",
        headers=SSE_HEADERS,
    )
