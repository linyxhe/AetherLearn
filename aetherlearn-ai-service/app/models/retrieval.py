from typing import List, Optional

from pydantic import BaseModel, Field


class RetrievalHit(BaseModel):
    """单条检索结果。"""
    chunk_id: Optional[int] = Field(default=None, description="MySQL 知识切片 ID；旧集合可为空")
    course_id: int = Field(..., description="课程 ID")
    doc_id: int = Field(..., description="文档 ID")
    seq: int = Field(..., description="切片序号")
    content: str = Field(..., description="切片文本内容")
    score: float = Field(..., description="融合后得分")
    dense_score: Optional[float] = Field(default=None, description="稠密向量得分")
    sparse_score: Optional[float] = Field(default=None, description="稀疏向量（BM25）得分")


class HybridSearchRequest(BaseModel):
    """混合检索请求。"""
    course_id: int = Field(..., description="课程 ID（必填，用于隔离）")
    query: str = Field(..., min_length=1, max_length=1000, description="查询文本")
    top_k: int = Field(default=30, ge=1, le=100, description="返回数量")
    rerank_top_k: int = Field(default=10, ge=1, le=50, description="重排序后保留数量")
    use_reranker: bool = Field(default=True, description="是否启用 Cross-Encoder 重排序")


class VectorSearchRequest(BaseModel):
    """纯向量检索请求。"""
    course_id: int = Field(..., description="课程 ID")
    query: str = Field(..., min_length=1, max_length=1000, description="查询文本")
    top_k: int = Field(default=30, ge=1, le=100, description="返回数量")


class SparseSearchRequest(BaseModel):
    """纯稀疏向量（BM25）检索请求。"""
    course_id: int = Field(..., description="课程 ID")
    query: str = Field(..., min_length=1, max_length=1000, description="查询文本")
    top_k: int = Field(default=30, ge=1, le=100, description="返回数量")


class RetrievalResponse(BaseModel):
    """检索响应，分阶段耗时用于前端展示和故障定位。"""
    hits: List[RetrievalHit] = Field(..., description="检索结果列表")
    retrieval_time_ms: int = Field(0, description="Embedding + Milvus 召回总耗时(ms)")
    embedding_time_ms: int = Field(0, description="Embedding 耗时(ms)")
    milvus_time_ms: int = Field(0, description="Milvus 检索耗时(ms)")
    rerank_time_ms: int = Field(0, description="重排序耗时(ms)")
    total_candidates: int = Field(0, description="重排序前召回候选数")
    strategy: str = Field("hybrid", description="检索策略: hybrid/vector/sparse")
    embedding_status: str = Field("pending", description="Embedding 状态: ok/degraded/failed")
    milvus_status: str = Field("pending", description="Milvus 状态: ok/degraded/failed")
    rerank_status: str = Field("pending", description="重排序状态: ok/skipped/degraded")
    fallback_reason: Optional[str] = Field(default=None, description="降级原因，正常链路为空")
