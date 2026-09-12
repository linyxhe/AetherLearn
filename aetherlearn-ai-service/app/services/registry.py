"""服务单例注册表。

BGE-M3 与 BGE-Reranker 各约 2.2 GB、加载一次要几秒，属于"一个进程只该有一份"的资源。
改造前每个 router 模块各自 `EmbeddingService()` 了一份：

- 启动预热把模型加载进了 `app.main` 的实例；
- 第一次真实检索时 `app.api.retrieval` 又加载了一份（实测多花约 5s，内存里同时两份模型）；
- 图内兜底检索（`app.agents.graph_qa`）每次调用都 `EmbeddingService()`，
  等于每次兜底检索都重新加载一遍 BGE-M3。

这里集中提供带缓存的 getter，所有模块一律经此获取，保证"一个进程一份模型"。
`lru_cache` 内部加锁，本身线程安全，不需要再套一层锁。

导入期安全：本模块只 import 三个 service 类，而这三个模块在 import 期都不触碰
torch / pymilvus（重活都在方法内部延迟导入），因此不会破坏
`app.utils.native_runtime.preload_native_runtime()` 的加载顺序约定。
"""

from functools import lru_cache

from app.services.embedding_service import EmbeddingService
from app.services.milvus_service import MilvusService
from app.services.reranker_service import RerankerService


@lru_cache(maxsize=1)
def get_embedding_service() -> EmbeddingService:
    """BGE-M3 稠密向量服务（进程内唯一）。"""
    return EmbeddingService()


@lru_cache(maxsize=1)
def get_milvus_service() -> MilvusService:
    """Milvus 读写服务（进程内唯一）。"""
    return MilvusService()


@lru_cache(maxsize=1)
def get_reranker_service() -> RerankerService:
    """BGE-Reranker-v2-m3 精排服务（进程内唯一）。"""
    return RerankerService()


def reset_services() -> None:
    """清空单例缓存（仅测试使用，避免跨用例共享模型实例）。"""
    get_embedding_service.cache_clear()
    get_milvus_service.cache_clear()
    get_reranker_service.cache_clear()


__all__ = [
    "get_embedding_service",
    "get_milvus_service",
    "get_reranker_service",
    "reset_services",
]
