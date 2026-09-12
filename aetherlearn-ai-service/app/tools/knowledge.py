"""知识库检索工具（ReAct 的动作之一）。

职责边界（与既有约定一致）：
- **工具在 Python 侧执行，但用的是与 `/v1/retrieval/hybrid` 完全相同的检索代码**
  （同一个 `EmbeddingService` / `MilvusService` / `apply_reranker_with_status`），
  不存在第二套检索实现。
- 工具只返回**文本化的命中摘要 + chunk_id**；把 chunk_id 映射成带 docTitle 的来源、
  以及软删除过滤，仍然归 Java——来源展示的所有权没有转移。
- `course_id` 由闭包捕获，**不作为工具参数暴露给模型**：模型无法通过编造参数去检索别的课程
  （越权不是"校验"出来的，而是根本不给出入口）。

延迟说明：一次工具检索 = Embedding（约 0.13s）+ Milvus（约 0.01s + RRF 融合），实测约 0.3s。

**刻意不把精排交给模型**。Cross-Encoder 精排在 CPU 上约 10.8s/次，agent 一轮里可能检索多次，
而"要不要花这 10.8s"是成本决策，模型做得不可靠：实测放开 `rerank` 参数后它立刻把两次调用
都开成 `rerank=true`，单轮问答从 10.5s 涨到 30.9s。高精度排序由"Java 首轮检索（带精排）"
提供，agent 的工具检索取 RRF 融合顺序即可——它的用途是"让模型看清有什么内容"，
而不是产出最终排序。
"""

from __future__ import annotations

import logging
from typing import Callable, List, Optional

from langchain_core.tools import StructuredTool
from starlette.concurrency import run_in_threadpool

logger = logging.getLogger(__name__)

# 单条命中的摘要长度：给模型足够判断相关性，又不至于把上下文塞满
_EXCERPT_CHARS = 240
# 一次工具调用的返回上限：防止模型要求 top_k=50 把上下文一次性撑爆
_MAX_TOP_K = 10


def _format_hits(hits: List[dict], limit: int) -> str:
    """把命中整理成模型好读的文本（而非 JSON：模型读文本更稳，也省 token）。"""
    lines = [f"命中 {len(hits[:limit])} 条："]
    for index, hit in enumerate(hits[:limit], start=1):
        content = (hit.get("content") or "").replace("\n", " ").strip()
        excerpt = content[:_EXCERPT_CHARS] + ("…" if len(content) > _EXCERPT_CHARS else "")
        score = hit.get("score")
        score_text = f"{float(score):.4f}" if score is not None else "n/a"
        lines.append(f"[{index}] chunk_id={hit.get('chunk_id')} 相关度={score_text}\n{excerpt}")
    if not hits:
        lines.append("（该课程知识库中没有匹配内容，可以换一个说法再试，或直接告知学生资料里没有）")
    return "\n".join(lines)


def build_search_knowledge_tool(
    course_id: int,
    on_hits: Optional[Callable[[List[dict]], None]] = None,
) -> StructuredTool:
    """构造绑定到指定课程的检索工具。

    :param course_id: 课程隔离维度，由 Java 鉴权后传入，模型不可见亦不可改
    :param on_hits: 命中回调，用于把"本轮实际检索到哪些切片"上报给来源组装与指标
    """

    async def _search_knowledge(query: str, top_k: int = 5) -> str:
        """在课程知识库中检索资料。"""
        # 延迟导入：保持 import 期不触碰 pymilvus / torch 的约定
        from app.services.registry import get_embedding_service, get_milvus_service

        cleaned = (query or "").strip()
        if not cleaned:
            return "查询词为空，请给出具体的关键词或问题。"
        limit = max(1, min(int(top_k or 5), _MAX_TOP_K))

        embedding_service = get_embedding_service()
        milvus_service = get_milvus_service()
        try:
            dense = await run_in_threadpool(embedding_service.embed_query, cleaned)
            hits = await run_in_threadpool(
                milvus_service.hybrid_search, course_id, cleaned, dense, max(limit * 3, 10)
            )
            hits = list(hits)[:limit]
        except Exception as exc:  # noqa: BLE001 - 工具失败必须变成可读结果，不能打断整轮
            logger.warning("search_knowledge 执行失败：%s", exc)
            return f"知识库检索暂时不可用（{exc}）。请基于你自己的知识回答，并说明未能检索到课程资料。"

        if on_hits is not None:
            try:
                on_hits(hits)
            except Exception as exc:  # noqa: BLE001 - 回调失败不影响工具结果
                logger.warning("search_knowledge 命中回调失败：%s", exc)

        return _format_hits(hits, limit)

    return StructuredTool.from_function(
        coroutine=_search_knowledge,
        name="search_knowledge",
        description=(
            "在当前课程的知识库里检索讲义/教材内容。"
            "当你不确定课程里的具体定义、示例或章节内容，或需要引用课程资料作答时调用。"
            "query 用具体的关键词或完整问句；一次没检索到可以换关键词再试一次。"
        ),
    )
