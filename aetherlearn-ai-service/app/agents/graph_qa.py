"""问答图：retrieve → build_context → generate。

职责边界（生产约定）：
- **Java 是权威检索方**：Java 已完成鉴权、软删除过滤与 BM25 降级，正常情况下会把切片
  随请求传进来，`retrieve` 节点直接透传，不再重复检索（省一次 Embedding + Milvus + 精排）。
- `retrieve` 节点只在"Java 没给切片且不是寒暄"时兜底检索：复用本服务既有的
  BGE-M3 + Milvus 混合检索 + 精排，**不重写 BM25**——检索失败直接抛错，
  由 Java 整轮回退到自己的检索 + 检索结果降级。
"""

from __future__ import annotations

import asyncio
import logging
import time
from functools import lru_cache
from typing import List, Optional

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage
from langchain_core.runnables import RunnableConfig
from langgraph.graph import END, START, StateGraph
from starlette.concurrency import run_in_threadpool

from app.agents.state import Chunk, QaState
from app.llm.client import get_chat_model
from app.llm.config import ResolvedLlmSettings
from app.llm.errors import LlmTimeout, RetrievalUnavailable, wrap_exception
from app.prompts.registry import get_prompt


logger = logging.getLogger(__name__)


async def retrieve_node(state: QaState, config: RunnableConfig) -> dict:
    """确定本轮使用的切片。

    寒暄直接跳过；Java 已给切片则透传；否则用本服务的混合检索兜底。
    """
    if state.get("small_talk"):
        return {
            "chunks": [],
            "retrieval_ms": 0,
            "retrieval_strategy": "none",
            "retrieval_status": "skipped",
        }

    provided: List[Chunk] = list(state.get("chunks") or [])
    if provided:
        # Java 已经是权威检索方（含 BM25 降级），这里不再重复检索。
        return {
            "chunks": provided,
            "retrieval_ms": 0,
            "retrieval_strategy": "java",
            "retrieval_status": "ok",
        }

    course_id = state.get("course_id")
    if course_id is None:
        return {
            "chunks": [],
            "retrieval_ms": 0,
            "retrieval_strategy": "none",
            "retrieval_status": "skipped",
        }

    started = time.perf_counter()
    chunks = await _fallback_retrieve(
        course_id=int(course_id),
        query=state.get("question") or "",
        top_k=int(state.get("top_k") or 30),
        rerank_top_k=int(state.get("rerank_top_k") or 10),
    )
    return {
        "chunks": chunks,
        "retrieval_ms": int((time.perf_counter() - started) * 1000),
        "retrieval_strategy": "hybrid",
        "retrieval_status": "ok",
    }


async def _fallback_retrieve(course_id: int, query: str, top_k: int, rerank_top_k: int) -> List[Chunk]:
    """Java 未提供切片时的兜底检索：复用既有 Embedding / Milvus / Reranker。

    检索不可用时抛 RetrievalUnavailable，交由 Java 用自己的检索链（含 BM25 兜底）整轮接管。
    """
    # 延迟导入：保持 app.agents 在 import 期不触碰 pymilvus / torch 的约定
    from app.api.retrieval import apply_reranker_with_status
    from app.services.registry import get_embedding_service, get_milvus_service

    # 走注册表：这里每次新建实例会让每次兜底检索都重新加载一遍 BGE-M3（约 2.2 GB / 5s）。
    embedding_service = get_embedding_service()
    milvus_service = get_milvus_service()

    try:
        dense = await run_in_threadpool(embedding_service.embed_query, query)
        candidates = await run_in_threadpool(
            milvus_service.hybrid_search, course_id, query, dense, top_k
        )
        hits, _, _, _ = await run_in_threadpool(
            apply_reranker_with_status, candidates, query, rerank_top_k, True
        )
    except Exception as exc:  # noqa: BLE001 - 统一归类为"检索不可用"
        logger.warning("图内兜底检索失败：%s", exc)
        raise RetrievalUnavailable(str(exc)) from exc

    return [_to_chunk(hit) for hit in hits]


def _to_chunk(hit: dict) -> Chunk:
    """把检索 hit 归一化成图内的 Chunk 结构。"""
    return Chunk(
        chunk_id=hit.get("chunk_id"),
        doc_id=int(hit.get("doc_id") or 0),
        seq=int(hit.get("seq") or 0),
        content=str(hit.get("content") or ""),
        score=float(hit.get("score") or 0.0),
    )


def select_context(chunks: Optional[List[dict]], budget: int) -> tuple:
    """按预算挑选切片并拼成上下文，返回 (context, sources)。

    问答图与 ReAct 图共用这一份规则——"预算怎么算"只允许有一个实现，
    否则两条链路的截断行为迟早会漂移（这正是本次重构要避免的问题）。
    """
    context_parts: List[str] = []
    sources: List[Chunk] = []
    used = 0

    for chunk in chunks or []:
        text = chunk.get("content") or ""
        if not text:
            continue
        # 至少保留第一条，避免单条超预算时上下文为空
        if context_parts and used + len(text) + 2 > budget:
            break
        used += len(text) + 2
        context_parts.append(text)
        sources.append(_to_chunk(chunk))

    return "\n\n".join(context_parts), sources


async def build_context_node(state: QaState, config: RunnableConfig) -> dict:
    """按预算拼上下文并选提示词。"""
    budget = int(state.get("context_budget") or 2000)
    context, sources = select_context(state.get("chunks"), budget)
    prompt_key = "qa_with_context" if context else "qa_no_context"
    system_prompt, user_prompt = get_prompt(prompt_key).render(
        {
            "question": state.get("question") or "",
            "context": context,
        }
    )

    return {
        "context": context,
        "sources": sources,
        "prompt_key": prompt_key,
        "system_prompt": system_prompt,
        "user_prompt": user_prompt,
    }


def _history_messages(history: Optional[List[dict]]) -> List[BaseMessage]:
    """把 Java 传来的多轮历史转成 role 明确的 LangChain 消息。

    以前是 Java 拼成一段"【对话历史】学生：…助教：…"的纯文本塞进 user 提示词，
    模型只能靠格式猜哪句是自己说的；现在是真正的 user / assistant 消息交替。
    角色非法或内容为空的消息直接丢弃，避免把脏数据发给模型。
    """
    messages: List[BaseMessage] = []
    for turn in history or []:
        content = str((turn or {}).get("content") or "").strip()
        if not content:
            continue
        role = (turn or {}).get("role")
        if role == "user":
            messages.append(HumanMessage(content=content))
        elif role == "assistant":
            messages.append(AIMessage(content=content))
        else:
            logger.warning("忽略未知历史角色：%r", role)
    return messages


async def generate_node(state: QaState, config: RunnableConfig) -> dict:
    """执行一次生成。

    消息序列：system → 多轮历史（user/assistant 交替）→ 本轮 user（问题 + 知识库内容）。
    """
    settings: ResolvedLlmSettings = config["configurable"]["llm"]
    model = get_chat_model(settings)

    messages: List[BaseMessage] = [
        SystemMessage(content=state.get("system_prompt") or ""),
        *_history_messages(state.get("history")),
        HumanMessage(content=state.get("user_prompt") or ""),
    ]

    started = time.perf_counter()
    try:
        response = await asyncio.wait_for(
            model.ainvoke(messages),
            timeout=settings.timeout,
        )
    except asyncio.TimeoutError as exc:
        raise LlmTimeout(f"问答生成超过 {settings.timeout:.1f}s 预算") from exc
    except Exception as exc:  # noqa: BLE001 - 统一归类后交给 API 层映射状态码
        raise wrap_exception(exc) from exc

    content = response.content
    answer = (content if isinstance(content, str) else str(content)).strip()
    if not answer:
        # 空回答在生产上主要是"推理模型把 max_tokens 花在思维链上"：
        # 记下 finish_reason 与用量，运维才能看出是预算不够还是模型异常，
        # 否则只会看到一个没有线索的 llm_bad_response。
        metadata = getattr(response, "response_metadata", None) or {}
        usage = metadata.get("token_usage") or metadata.get("usage") or {}
        logger.warning(
            "模型返回空内容：finish_reason=%s, usage=%s, max_tokens=%s, prompt_key=%s",
            metadata.get("finish_reason"),
            usage,
            settings.max_tokens,
            state.get("prompt_key"),
        )
    return {"answer": answer, "llm_ms": int((time.perf_counter() - started) * 1000)}


@lru_cache(maxsize=1)
def build_qa_graph():
    """构建并缓存问答图（无状态，可安全复用）。"""
    graph = StateGraph(QaState)
    graph.add_node("retrieve", retrieve_node)
    graph.add_node("build_context", build_context_node)
    graph.add_node("generate", generate_node)
    graph.add_edge(START, "retrieve")
    graph.add_edge("retrieve", "build_context")
    graph.add_edge("build_context", "generate")
    graph.add_edge("generate", END)
    return graph.compile()


__all__ = ["build_qa_graph", "retrieve_node", "build_context_node", "generate_node"]
