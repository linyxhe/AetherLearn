"""Reranker 分批逻辑的回归测试。

精排为了省算力会"先按长度排序再分批"（一批要 padding 到批内最长）。
排序改变了送入模型的顺序，因此必须保证返回的 `index` 仍是**原始候选下标**，
否则 Java 侧按 index 取回的切片会张冠李戴——这是最容易悄悄错的一步。
"""

from app.services.reranker_service import RerankerService


class _StubReranker(RerankerService):
    """把打分替换成"按文档内容长度"的确定性函数，绕过真实模型。"""

    def __init__(self):
        super().__init__()
        # 放一个非 None 的占位 tokenizer：让 _ensure_model 直接返回，不去加载 2.2 GB 真模型
        self.tokenizer = object()
        self.seen_batches: list[list[str]] = []

    def _score_batch(self, query, batch_documents):  # type: ignore[override]
        import numpy as np

        self.seen_batches.append(list(batch_documents))
        # 长度越长分越高：分数只取决于文档自身，与批内成员无关
        return np.array([len(document) / 1000.0 for document in batch_documents])


def test_sorted_batching_keeps_original_indices():
    service = _StubReranker()
    import app.services.reranker_service as module

    original = module.settings.RERANKER_BATCH_SIZE
    module.settings.RERANKER_BATCH_SIZE = 2
    try:
        # 故意给出长度乱序的候选，逼出"排序后分批"
        documents = ["短", "中等长度文本", "很长很长很长很长的文本内容", "中等"]
        results = service.rerank("q", documents, top_k=4)
    finally:
        module.settings.RERANKER_BATCH_SIZE = original

    # 分数最长者第一，且 index 指向原始位置 2
    assert results[0]["index"] == 2
    assert len(results) == 4
    assert sorted(item["index"] for item in results) == [0, 1, 2, 3]

    # 每个原始下标都拿回自己的分数：len(documents[i]) / 1000
    by_index = {item["index"]: item["score"] for item in results}
    for index, document in enumerate(documents):
        assert by_index[index] == len(document) / 1000.0


def test_batching_sorts_by_length_to_group_similar():
    service = _StubReranker()
    import app.services.reranker_service as module

    original = module.settings.RERANKER_BATCH_SIZE
    module.settings.RERANKER_BATCH_SIZE = 2
    try:
        documents = ["a" * 10, "b" * 300, "c" * 20, "d" * 280]
        service.rerank("q", documents, top_k=4)
    finally:
        module.settings.RERANKER_BATCH_SIZE = original

    # 长度接近的应当分到同一批：批内长度差不应出现 10 与 300 同批的情况
    for batch in service.seen_batches:
        lengths = sorted(len(document) for document in batch)
        if len(lengths) > 1:
            assert lengths[-1] - lengths[0] <= 20 or max(lengths) <= 300 and min(lengths) >= 280


def test_top_k_limits_results_but_batches_everything():
    service = _StubReranker()
    documents = ["x" * n for n in (5, 40, 12, 33, 21)]
    results = service.rerank("q", documents, top_k=2)

    assert len(results) == 2
    # 全量都打过分的证据：分数最高的两个下标是长度 40 与 33
    assert [item["index"] for item in results] == [1, 3]


def test_empty_documents_short_circuits():
    service = _StubReranker()

    assert service.rerank("q", [], top_k=5) == []
    assert service.seen_batches == []
