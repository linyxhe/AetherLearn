import logging

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from starlette.concurrency import run_in_threadpool

from app.config import get_settings
from app.services.registry import get_milvus_service


logger = logging.getLogger(__name__)
settings = get_settings()
# 走注册表：与检索、健康检查共用同一个 Milvus 客户端
milvus_service = get_milvus_service()
router = APIRouter(prefix="/v1/knowledge", tags=["Knowledge"])


class ChunkVector(BaseModel):
    """带稠密向量的知识切片。"""

    chunk_id: int = Field(..., description="MySQL 知识切片 ID")
    course_id: int = Field(..., description="课程 ID")
    doc_id: int = Field(..., description="文档 ID")
    seq: int = Field(..., description="切片序号")
    content: str = Field(..., description="切片文本")
    embedding: list[float] = Field(..., description="BGE-M3 稠密向量")


class IndexRequest(BaseModel):
    """批量写入 Milvus 的请求。"""

    chunks: list[ChunkVector] = Field(..., min_length=1, max_length=1000)
    # 新文档默认追加，避免先删后写造成向量丢失；显式替换时由调用方开启。
    delete_existing: bool = Field(False, description="写入前是否删除同一文档的旧向量")


class IndexResponse(BaseModel):
    """批量写入结果。"""

    inserted: int = Field(..., description="成功写入数量")
    deleted: int = Field(0, description="删除的旧向量数量")
    status: str = Field("ok", description="ok / degraded")
    error: str | None = Field(default=None, description="降级或错误信息")


class DeleteRequest(BaseModel):
    """删除文档对应向量。"""

    course_id: int = Field(..., description="课程 ID")
    doc_id: int = Field(..., description="文档 ID")


async def _delete_chunks(course_id: int, doc_id: int) -> int:
    """在线程池中删除 Milvus 文档向量。"""
    try:
        return await run_in_threadpool(milvus_service.delete_chunks_by_doc, course_id, doc_id)
    except Exception as exc:
        logger.exception("知识切片删除失败")
        raise HTTPException(status_code=503, detail="向量删除服务暂时不可用") from exc


async def _index_chunks(req: IndexRequest) -> IndexResponse:
    """先校验并写入新向量，再安全清理旧向量。"""
    try:
        chunks = req.chunks
        first = chunks[0]
        payload = [
            {
                "chunk_id": chunk.chunk_id,
                "course_id": chunk.course_id,
                "doc_id": chunk.doc_id,
                "seq": chunk.seq,
                "content": chunk.content,
                "embedding": chunk.embedding,
            }
            for chunk in chunks
        ]

        # 新集合先写入，再按 chunk_id 精确删除旧 ID；旧集合不自动删除，避免破坏历史数据。
        inserted_ids = await run_in_threadpool(milvus_service.insert_chunks, payload)
        deleted = 0
        error = None
        if req.delete_existing:
            if milvus_service.supports_chunk_id:
                new_chunk_ids = {chunk.chunk_id for chunk in chunks}
                old_chunk_ids = await run_in_threadpool(
                    milvus_service.get_existing_chunk_ids,
                    first.course_id,
                    first.doc_id,
                )
                stale_ids = sorted(old_chunk_ids - new_chunk_ids)
                if stale_ids:
                    deleted = await run_in_threadpool(
                        milvus_service.delete_chunks_by_ids, stale_ids
                    )
            else:
                error = "旧集合不支持安全替换，请使用新集合或调用 delete 接口"
                logger.warning(error)

        inserted_count = len(inserted_ids)
        if inserted_count != len(payload):
            error = error or "Milvus 写入数量与请求数量不一致"
            return IndexResponse(
                inserted=inserted_count,
                deleted=deleted,
                status="degraded",
                error=error,
            )
        return IndexResponse(
            inserted=inserted_count,
            deleted=deleted,
            status="degraded" if error else "ok",
            error=error,
        )
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("知识切片入库失败")
        raise HTTPException(status_code=503, detail="向量入库服务暂时不可用") from exc


@router.post("/index", response_model=IndexResponse)
async def index_chunks(req: IndexRequest) -> IndexResponse:
    """批量向量化结果入库；新集合支持先写后删的安全替换。"""
    return await _index_chunks(req)


@router.post("/delete", response_model=IndexResponse)
async def delete_chunks(req: DeleteRequest) -> IndexResponse:
    """删除指定文档在 Milvus 中的全部向量。"""
    deleted = await _delete_chunks(req.course_id, req.doc_id)
    return IndexResponse(inserted=0, deleted=deleted, status="ok")
