"""Reranker 性能与质量对照脚本。

用途：在真实候选切片上对比三种模式的耗时与排序质量，为是否开启
int8 量化提供数据依据（而不是凭感觉调参）。

用法（在 aetherlearn-ai-service 目录下）：
    python scripts/bench_reranker.py                 # fp32 vs int8，各跑 3 轮
    python scripts/bench_reranker.py --rounds 5
    python scripts/bench_reranker.py --candidates 12 --course-id 4

输出：
    - 每种模式的单轮耗时（取中位数，避免首次加载与抖动干扰）
    - Top-1 / Top-3 命中是否一致、Spearman 排序相关系数、分数最大偏移

注意：脚本会直接构造 RerankerService 实例，不经过单例注册表，
因此不会影响正在运行的服务进程。
"""

from __future__ import annotations

import argparse
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.config import get_settings  # noqa: E402


def load_candidates(course_id: int, limit: int) -> list[str]:
    """从 MySQL 取该课程的真实切片作为候选（与线上精排的输入同源）。"""
    import pymysql

    settings = get_settings()
    conn = pymysql.connect(
        host=settings.DB_HOST,
        port=settings.DB_PORT,
        user=settings.DB_USER,
        password=settings.DB_PASSWORD,
        database=settings.DB_NAME,
        charset="utf8mb4",
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT content FROM knowledge_chunk WHERE course_id=%s LIMIT %s",
                (course_id, limit),
            )
            return [row[0] for row in cur.fetchall()]
    finally:
        conn.close()


def spearman(left: list[float], right: list[float]) -> float:
    """两条分数序列的 Spearman 排序相关系数（无第三方依赖实现）。"""
    def ranks(values: list[float]) -> list[float]:
        order = sorted(range(len(values)), key=lambda i: values[i], reverse=True)
        rank = [0.0] * len(values)
        for position, index in enumerate(order):
            rank[index] = float(position)
        return rank

    a, b = ranks(left), ranks(right)
    mean_a, mean_b = statistics.mean(a), statistics.mean(b)
    cov = sum((x - mean_a) * (y - mean_b) for x, y in zip(a, b))
    var_a = sum((x - mean_a) ** 2 for x in a) ** 0.5
    var_b = sum((y - mean_b) ** 2 for y in b) ** 0.5
    return cov / (var_a * var_b) if var_a and var_b else 1.0


def bench(quantize: bool, documents: list[str], query: str, rounds: int):
    """在给定量化设置下跑多轮，返回 (中位耗时ms, 分数向量)。"""
    settings = get_settings()
    settings.RERANKER_QUANTIZE = quantize
    # 每种模式都要重新导入/构造，避免复用已加载的模型实例
    from app.services.reranker_service import RerankerService

    service = RerankerService()
    service.rerank(query, ["预热文本"], top_k=1)  # 先付掉模型加载成本

    durations, scores = [], []
    for _ in range(rounds):
        started = time.perf_counter()
        results = service.rerank(query, documents, top_k=len(documents))
        durations.append((time.perf_counter() - started) * 1000)
        by_index = {item["index"]: item["score"] for item in results}
        scores = [by_index[i] for i in range(len(documents))]
    return statistics.median(durations), scores


def main() -> int:
    parser = argparse.ArgumentParser(description="Reranker fp32 / int8 对照")
    parser.add_argument("--course-id", type=int, default=4)
    parser.add_argument("--candidates", type=int, default=None, help="候选数，默认取 RERANKER_MAX_CANDIDATES")
    parser.add_argument("--query", default="什么是方法重载？")
    parser.add_argument("--rounds", type=int, default=3)
    args = parser.parse_args()

    settings = get_settings()
    limit = args.candidates or settings.RERANKER_MAX_CANDIDATES
    documents = load_candidates(args.course_id, limit)
    if not documents:
        print(f"课程 {args.course_id} 没有切片，换一个 course-id 再试")
        return 1

    print(f"候选 {len(documents)} 条 · 查询「{args.query}」· 每模式 {args.rounds} 轮 · 设备 {settings.RERANKER_DEVICE}")
    fp32_ms, fp32_scores = bench(False, documents, args.query, args.rounds)
    print(f"  fp32 中位耗时 {fp32_ms:.0f} ms")

    int8_ms, int8_scores = bench(True, documents, args.query, args.rounds)
    print(f"  int8 中位耗时 {int8_ms:.0f} ms")

    print(f"\n加速比 {fp32_ms / int8_ms:.2f}×（{fp32_ms:.0f} ms → {int8_ms:.0f} ms，省 {fp32_ms - int8_ms:.0f} ms/问）")
    print(f"排序相关系数 {spearman(fp32_scores, int8_scores):.4f}（1.0 表示排序完全一致）")
    print(f"分数最大偏移 {max(abs(a - b) for a, b in zip(fp32_scores, int8_scores)):.4f}")

    top_fp32 = sorted(range(len(documents)), key=lambda i: fp32_scores[i], reverse=True)
    top_int8 = sorted(range(len(documents)), key=lambda i: int8_scores[i], reverse=True)
    print(f"Top-1 {'一致' if top_fp32[0] == top_int8[0] else '不一致'} · "
          f"Top-3 交集 {len(set(top_fp32[:3]) & set(top_int8[:3]))}/3")
    print("\n若要启用：在 .env 加 RERANKER_QUANTIZE=true（默认 false）。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
