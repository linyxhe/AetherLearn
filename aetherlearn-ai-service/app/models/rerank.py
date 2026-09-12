from pydantic import BaseModel, Field
from typing import List, Optional


class RerankRequest(BaseModel):
    """重排序请求"""
    query: str = Field(..., min_length=1, max_length=512, description="查询文本")
    documents: List[str] = Field(..., min_length=1, max_length=100, description="待重排文档列表")
    top_k: int = Field(default=10, ge=1, le=100, description="返回 Top-K")
    return_documents: bool = Field(default=False, description="是否在返回中包含文档内容")


class RerankResult(BaseModel):
    """单条重排序结果"""
    index: int = Field(..., description="原始文档索引")
    score: float = Field(..., description="相关性分数 (0-1)")
    document: Optional[str] = Field(default=None, description="文档内容（仅当 return_documents=true 时）")


class RerankResponse(BaseModel):
    """重排序响应"""
    results: List[RerankResult] = Field(..., description="按分数降序排列的结果")
    model: str = Field(..., description="使用的重排序模型")
    usage: dict = Field(default_factory=dict, description="token 使用统计")