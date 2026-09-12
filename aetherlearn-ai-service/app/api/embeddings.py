import logging

from fastapi import APIRouter, HTTPException
from starlette.concurrency import run_in_threadpool

from app.config import get_settings
from app.models.embedding import EmbeddingQueryRequest, EmbeddingQueryResponse, EmbeddingResponse, EmbeddingRequest
from app.services.registry import get_embedding_service


logger = logging.getLogger(__name__)
settings = get_settings()
# 走注册表：与预热、检索共用同一个 BGE-M3 实例，避免重复加载（约 2.2 GB / 5s）
embedding_service = get_embedding_service()
router = APIRouter(prefix="/v1/embeddings", tags=["Embedding"])


@router.post("/batch", response_model=EmbeddingResponse)
async def batch_embed(req: EmbeddingRequest) -> EmbeddingResponse:
    """批量向量化，支持 document 入库和 query 检索两种输入类型。"""
    try:
        if req.input_type == "document":
            embeddings = await run_in_threadpool(embedding_service.embed_documents, req.texts)
        else:
            embeddings = await run_in_threadpool(
                lambda: [embedding_service.embed_query(text) for text in req.texts]
            )
        return EmbeddingResponse(
            embeddings=embeddings,
            dim=settings.EMBEDDING_DIM,
            model=settings.EMBEDDING_MODEL,
            usage={"input_tokens": sum(len(text) for text in req.texts)},
        )
    except Exception as exc:
        logger.exception("批量向量化失败")
        raise HTTPException(status_code=503, detail="向量化服务暂时不可用") from exc


@router.post("/query", response_model=EmbeddingQueryResponse)
async def embed_query(req: EmbeddingQueryRequest) -> EmbeddingQueryResponse:
    """单条查询向量化，供检索接口使用。"""
    try:
        embedding = await run_in_threadpool(embedding_service.embed_query, req.query)
        return EmbeddingQueryResponse(
            embedding=embedding,
            dim=settings.EMBEDDING_DIM,
            model=settings.EMBEDDING_MODEL,
        )
    except Exception as exc:
        logger.exception("查询向量化失败")
        raise HTTPException(status_code=503, detail="查询向量化服务暂时不可用") from exc
