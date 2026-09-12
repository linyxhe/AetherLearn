import sys
from pathlib import Path

import pytest

pytest.importorskip("fastapi")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.api.retrieval import apply_reranker, apply_reranker_with_status


class FakeReranker:
    """返回固定索引顺序的重排序器替身。"""

    def rerank(self, query, documents, top_k):
        return [
            {"index": 1, "score": 0.9},
            {"index": 0, "score": 0.8},
        ]


def test_reranker_reorders_candidates_and_updates_scores():
    candidates = [
        {"content": "第一份切片", "score": 0.1},
        {"content": "第二份切片", "score": 0.2},
    ]

    with pytest.MonkeyPatch.context() as patch:
        patch.setattr("app.api.retrieval.get_reranker_service", lambda: FakeReranker())
        reranked, elapsed = apply_reranker(candidates, "问题", 2, True)

    assert reranked[0]["content"] == "第二份切片"
    assert reranked[1]["content"] == "第一份切片"
    assert reranked[0]["score"] == 0.9
    assert elapsed >= 0


def test_empty_candidates_do_not_call_reranker():
    calls = []

    def fail_if_called(*args, **kwargs):
        calls.append((args, kwargs))
        return []

    with pytest.MonkeyPatch.context() as patch:
        patch.setattr("app.api.retrieval.get_reranker_service", fail_if_called)
        reranked, elapsed = apply_reranker([], "问题", 2, True)

    assert reranked == []
    assert elapsed == 0
    assert calls == []


def test_reranker_failure_keeps_rrf_candidates():
    def fail(*args, **kwargs):
        raise RuntimeError("模型加载失败")

    candidates = [{"content": "原始召回", "score": 0.5}]

    with pytest.MonkeyPatch.context() as patch:
        patch.setattr("app.api.retrieval.get_reranker_service", fail)
        reranked, elapsed = apply_reranker(candidates, "问题", 2, True)
        hits, _, status, reason = apply_reranker_with_status(candidates, "问题", 2, True)

    assert reranked == candidates
    assert hits == candidates
    # 失败仍记录实际耗时，便于前端区分“未启用”和“重排序异常”。
    assert elapsed >= 0
    assert status == "degraded"
    assert reason == "reranker_failed"


def test_reranker_empty_result_keeps_rrf_candidates():
    class EmptyReranker:
        """返回空结果的重排序器替身。"""

        def rerank(self, query, documents, top_k):
            return []

    candidates = [{"content": "原始召回", "score": 0.5}]

    with pytest.MonkeyPatch.context() as patch:
        patch.setattr("app.api.retrieval.get_reranker_service", lambda: EmptyReranker())
        hits, _, status, reason = apply_reranker_with_status(candidates, "问题", 2, True)

    assert hits == candidates
    assert status == "degraded"
    assert reason == "reranker_empty_result"


def test_reranker_disabled_keeps_rrf_candidates():
    candidates = [{"content": "原始召回", "score": 0.5}]

    reranked, elapsed = apply_reranker(candidates, "问题", 2, False)

    assert reranked == candidates
    assert elapsed == 0
