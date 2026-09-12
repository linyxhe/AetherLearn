import logging
import time

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from starlette.concurrency import run_in_threadpool

from app.config import get_settings
from app.models.retrieval import (
    HybridSearchRequest,
    RetrievalResponse,
    RetrievalHit,
    SparseSearchRequest,
    VectorSearchRequest,
)
from app.services.registry import (
    get_embedding_service,
    get_milvus_service,
    get_reranker_service,
)


logger = logging.getLogger(__name__)
settings = get_settings()
# 一律走注册表：模型与 Milvus 客户端在进程内只应存在一份（否则预热白做、内存翻倍）。
milvus_service = get_milvus_service()
embedding_service = get_embedding_service()
router = APIRouter(prefix="/v1/retrieval", tags=["Retrieval"])


def apply_reranker_with_status(candidates, query: str, top_k: int, use_reranker: bool):
    """对召回候选执行可选重排序，并返回可供响应展示的阶段状态。"""
    hits = list(candidates)
    rerank_time_ms = 0
    status = "skipped"
    fallback_reason = None
    if not (use_reranker and hits):
        return hits, rerank_time_ms, status, fallback_reason

    # CPU 上 Cross-Encoder 很慢：只对 RRF 头部候选做精排，其余保留在召回结果之外。
    max_candidates = int(settings.RERANKER_MAX_CANDIDATES)
    pool = hits if max_candidates <= 0 else hits[:max_candidates]

    rerank_started = time.perf_counter()
    try:
        ranked = get_reranker_service().rerank(
            query=query,
            documents=[hit["content"] for hit in pool],
            top_k=top_k,
        )
        if ranked:
            hits = [pool[int(item["index"])] for item in ranked]
            for hit, item in zip(hits, ranked):
                hit["score"] = item["score"]
            rerank_time_ms = int((time.perf_counter() - rerank_started) * 1000)
            status = "ok"
        else:
            logger.warning("重排序未返回结果，保留 RRF 召回结果")
            status = "degraded"
            fallback_reason = "reranker_empty_result"
    except Exception:
        # 重排序模型故障不应使混合检索完全不可用。
        logger.exception("重排序失败，保留 RRF 召回结果")
        rerank_time_ms = int((time.perf_counter() - rerank_started) * 1000)
        status = "degraded"
        fallback_reason = "reranker_failed"
    return hits, rerank_time_ms, status, fallback_reason


def apply_reranker(candidates, query: str, top_k: int, use_reranker: bool):
    """对召回候选执行可选重排序，兼容原有测试和调用方。"""
    hits, elapsed, _, _ = apply_reranker_with_status(candidates, query, top_k, use_reranker)
    return hits, elapsed


async def _embed_query_with_metrics(query: str):
    """在线程池中执行同步模型推理，并记录 Embedding 阶段耗时和状态。"""
    started = time.perf_counter()
    try:
        vector = await run_in_threadpool(embedding_service.embed_query, query)
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return vector, elapsed_ms, "ok"
    except ValueError as exc:
        raise HTTPException(status_code=400, detail="查询文本无效") from exc
    except Exception as exc:
        logger.exception("查询向量化失败")
        raise HTTPException(status_code=503, detail="向量化服务暂时不可用") from exc


async def _call_milvus_with_metrics(call, *args):
    """封装 Milvus 同步调用，并记录召回阶段耗时和状态。"""
    started = time.perf_counter()
    try:
        results = await run_in_threadpool(call, *args)
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return results, elapsed_ms, "ok"
    except Exception as exc:
        logger.exception("Milvus 检索失败")
        raise HTTPException(status_code=503, detail="向量检索服务暂时不可用") from exc


@router.post("/hybrid", response_model=RetrievalResponse)
async def hybrid_search(req: HybridSearchRequest) -> RetrievalResponse:
    """执行稠密向量 + BM25 稀疏检索，并通过 RRF 融合后可选重排序。"""
    embedding_started = time.perf_counter()
    dense_vector, embedding_time_ms, embedding_status = await _embed_query_with_metrics(req.query)
    milvus_started = time.perf_counter()
    candidates, milvus_time_ms, milvus_status = await _call_milvus_with_metrics(
        milvus_service.hybrid_search,
        req.course_id,
        req.query,
        dense_vector,
        req.top_k,
    )
    # 召回耗时不包含重排序，便于区分模型、数据库和精排成本。
    retrieval_time_ms = int((milvus_started - embedding_started) * 1000)
    candidate_count = len(candidates)
    hits, rerank_time_ms, rerank_status, fallback_reason = await run_in_threadpool(
        apply_reranker_with_status,
        candidates,
        req.query,
        req.rerank_top_k,
        req.use_reranker,
    )

    return RetrievalResponse(
        hits=[RetrievalHit(**hit) for hit in hits],
        retrieval_time_ms=retrieval_time_ms,
        embedding_time_ms=embedding_time_ms,
        milvus_time_ms=milvus_time_ms,
        rerank_time_ms=rerank_time_ms,
        total_candidates=candidate_count,
        strategy="hybrid",
        embedding_status=embedding_status,
        milvus_status=milvus_status,
        rerank_status=rerank_status,
        fallback_reason=fallback_reason,
    )


@router.post("/vector", response_model=RetrievalResponse)
async def vector_search(req: VectorSearchRequest) -> RetrievalResponse:
    """执行纯稠密向量检索。"""
    embedding_started = time.perf_counter()
    dense_vector, embedding_time_ms, embedding_status = await _embed_query_with_metrics(req.query)
    milvus_started = time.perf_counter()
    hits, milvus_time_ms, milvus_status = await _call_milvus_with_metrics(
        milvus_service.vector_search,
        req.course_id,
        req.query,
        dense_vector,
        req.top_k,
    )
    return RetrievalResponse(
        hits=[RetrievalHit(**hit) for hit in hits],
        retrieval_time_ms=int((milvus_started - embedding_started) * 1000),
        embedding_time_ms=embedding_time_ms,
        milvus_time_ms=milvus_time_ms,
        rerank_time_ms=0,
        total_candidates=len(hits),
        strategy="vector",
        embedding_status=embedding_status,
        milvus_status=milvus_status,
        rerank_status="skipped",
        fallback_reason=None,
    )


@router.post("/sparse", response_model=RetrievalResponse)
async def sparse_search(req: SparseSearchRequest) -> RetrievalResponse:
    """执行纯 BM25 稀疏检索，原始文本由 Milvus BM25 函数编码。"""
    milvus_started = time.perf_counter()
    hits, milvus_time_ms, milvus_status = await _call_milvus_with_metrics(
        milvus_service.sparse_search,
        req.course_id,
        req.query,
        req.top_k,
    )
    return RetrievalResponse(
        hits=[RetrievalHit(**hit) for hit in hits],
        retrieval_time_ms=milvus_time_ms,
        embedding_time_ms=0,
        milvus_time_ms=milvus_time_ms,
        rerank_time_ms=0,
        total_candidates=len(hits),
        strategy="sparse",
        embedding_status="skipped",
        milvus_status=milvus_status,
        rerank_status="skipped",
        fallback_reason=None,
    )


@router.get("/stats")
async def collection_stats() -> dict:
    """返回 Milvus 集合状态，便于健康检查和运维排查。"""
    return milvus_service.get_collection_stats()
