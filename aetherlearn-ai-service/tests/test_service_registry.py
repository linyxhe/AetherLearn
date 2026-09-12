"""服务单例注册表的回归测试。

背景：BGE-M3 与 BGE-Reranker 各约 2.2 GB，改造前 `app.main`（预热）、
`app.api.retrieval`、`app.api.embeddings`、`app.api.knowledge` 各自 new 了一份，
结果是"预热加载的模型没人用、真实请求再加载一份"，首次检索白等约 5s、内存翻倍。

守的不变量是**跨模块身份一致**：所有模块拿到的必须是同一个实例。
断言方式上有个坑：不能拿"再调一次 getter"的结果去比模块属性——
各模块在 import 期就把实例绑到了自己的模块变量上，一旦有人调用
`reset_services()`（仅测试用），getter 会返回新对象而这些绑定不会跟着变。
真正要守的是"这些绑定彼此相同"。
"""

import pytest

from app.services.registry import (
    get_embedding_service,
    get_milvus_service,
    get_reranker_service,
)


def test_each_getter_returns_same_instance():
    """同一 getter 多次调用必须返回同一个对象（否则就是重复加载模型）。"""
    assert get_embedding_service() is get_embedding_service()
    assert get_milvus_service() is get_milvus_service()
    assert get_reranker_service() is get_reranker_service()


def test_different_services_are_distinct():
    """三个 getter 互不串味。"""
    assert get_embedding_service() is not get_milvus_service()
    assert get_reranker_service() is not get_milvus_service()


def test_all_modules_share_one_embedding_and_milvus_instance():
    """各 router 与 main 必须持有同一个 Embedding / Milvus 实例。

    这就是用户可见的收益：预热（main）与真实检索（retrieval）用的是同一个模型对象，
    不会出现"预热白做、首次请求再加载一份"。
    """
    import app.main as main_module
    from app.api import embeddings, knowledge, retrieval

    embedding_instances = {
        id(retrieval.embedding_service),
        id(embeddings.embedding_service),
        id(main_module.embedding_service),
    }
    assert len(embedding_instances) == 1, "BGE-M3 被加载了多份，启动预热将失效"

    milvus_instances = {
        id(retrieval.milvus_service),
        id(knowledge.milvus_service),
        id(main_module.milvus_service),
    }
    assert len(milvus_instances) == 1, "Milvus 客户端被创建了多份"


def test_graph_qa_uses_registry_services():
    """图内兜底检索必须经注册表取实例，不能自己 new。

    做法：把注册表的两个 getter 换成假实例再调用兜底检索。
    能走到假实例上就说明确实用了注册表；若有人把它改回 `EmbeddingService()`，
    这里会绕过 patch 去加载真实 BGE-M3（离线环境直接报错），测试立刻失败。
    """
    import asyncio
    from unittest.mock import patch

    import app.agents.graph_qa as graph_qa

    class _Dummy:
        """假的 Embedding/Milvus 服务：只记录被调用过。"""

        def __init__(self):
            self.embedded = False

        def embed_query(self, query):
            self.embedded = True
            raise RuntimeError("stop-here")

        def hybrid_search(self, *args, **kwargs):  # pragma: no cover - 正常不会走到
            raise AssertionError("不应执行到 Milvus 检索")

    dummy = _Dummy()

    async def run():
        with patch("app.services.registry.get_embedding_service", lambda: dummy), patch(
            "app.services.registry.get_milvus_service", lambda: dummy
        ):
            # 检索不可用会被包成 RetrievalUnavailable，这里只关心"用没用注册表"
            with pytest.raises(Exception):
                await graph_qa._fallback_retrieve(
                    course_id=1, query="问题", top_k=3, rerank_top_k=2
                )

    asyncio.run(run())
    assert dummy.embedded, "兜底检索没有使用注册表里的 Embedding 实例"
