import logging
import time

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from starlette.concurrency import run_in_threadpool

from app.config import get_settings
from app.models.rerank import RerankRequest, RerankResponse
from app.services.registry import get_reranker_service


logger = logging.getLogger(__name__)
settings = get_settings()
router = APIRouter(prefix="/v1/rerank", tags=["Rerank"])


@router.post("", response_model=RerankResponse)
async def rerank(req: RerankRequest) -> RerankResponse:
    """对候选切片执行 Cross-Encoder 重排序。"""
    started = time.perf_counter()
    try:
        results = await run_in_threadpool(
            get_reranker_service().rerank,
            req.query,
            req.documents,
            req.top_k,
            req.return_documents,
        )
    except Exception as exc:
        logger.exception("重排序失败")
        raise HTTPException(status_code=503, detail="重排序服务暂时不可用") from exc
    return RerankResponse(
        results=results,
        model=settings.RERANKER_MODEL,
        usage={"elapsed_ms": int((time.perf_counter() - started) * 1000)},
    )
