from pydantic import BaseModel, Field
from typing import List, Optional, Literal


class EmbeddingRequest(BaseModel):
    """单条/批量文本向量化请求"""
    texts: List[str] = Field(..., min_length=1, max_length=1000, description="待向量化文本列表")
    input_type: Literal["document", "query"] = Field(
        default="document",
        description="输入类型：document(入库用) / query(检索用)，BGE-M3 区分两者"
    )


class EmbeddingQueryRequest(BaseModel):
    """单条查询向量化请求"""
    query: str = Field(..., min_length=1, max_length=8192, description="查询文本")


class EmbeddingResponse(BaseModel):
    """向量化响应"""
    embeddings: List[List[float]] = Field(..., description="向量列表，形状 [n_texts, dim]")
    dim: int = Field(..., description="向量维度")
    model: str = Field(..., description="使用的模型名称")
    usage: dict = Field(default_factory=dict, description="token 使用统计")


class EmbeddingQueryResponse(BaseModel):
    """单条查询向量化响应"""
    embedding: List[float] = Field(..., description="查询向量")
    dim: int = Field(..., description="向量维度")
    model: str = Field(..., description="使用的模型名称")