"""批量补全历史知识切片到 Milvus。

默认模式只连接 MySQL 并输出待迁移统计，不会修改 Milvus；传入 --force 后才会写入向量。
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
import time
from pathlib import Path
from typing import Any

import pymysql
from pymysql.cursors import DictCursor

# 支持直接执行 `python scripts/backfill_milvus.py`，无需额外设置 PYTHONPATH。
SERVICE_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SERVICE_DIR))
# 默认复用服务目录下的模型缓存，避免手动执行时找不到 BGE-M3。
os.environ.setdefault("HF_HOME", str(SERVICE_DIR / "models" / "huggingface"))

from app.config import get_settings
from app.services.embedding_service import EmbeddingService
from app.services.milvus_service import MilvusService


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="批量补全历史知识切片到 Milvus")
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--max-chunks", type=int, default=0, help="0 表示处理全部数据")
    parser.add_argument("--collection", default=None, help="目标 Milvus 集合名，默认使用配置值")
    parser.add_argument("--force", action="store_true", help="允许覆盖已有 chunk_id 的向量")
    parser.add_argument("--dry-run", action="store_true", help="只读取 MySQL 并打印样例，不写入 Milvus")
    return parser.parse_args()


def connect_mysql(settings: Any) -> pymysql.connections.Connection:
    return pymysql.connect(
        host=settings.DB_HOST,
        port=settings.DB_PORT,
        user=settings.DB_USER,
        password=settings.DB_PASSWORD,
        database=settings.DB_NAME,
        charset="utf8mb4",
        cursorclass=DictCursor,
        connect_timeout=10,
        read_timeout=30,
        write_timeout=30,
    )


def query_chunks(settings: Any, max_chunks: int) -> list[dict[str, Any]]:
    """读取历史切片，max_chunks=0 时读取全部数据。"""
    # knowledge_chunk 本身没有 is_deleted，软删除标记在 knowledge_doc 上。
    sql = (
        "SELECT c.id, c.doc_id, c.course_id, c.seq, c.content, c.char_len "
        "FROM knowledge_chunk c "
        "JOIN knowledge_doc d ON c.doc_id = d.id "
        "WHERE d.is_deleted = 0"
    )
    if max_chunks > 0:
        sql += " LIMIT %s"
    with connect_mysql(settings) as connection:
        with connection.cursor() as cursor:
            cursor.execute(sql, (max_chunks,) if max_chunks > 0 else ())
            return list(cursor.fetchall())


def main() -> int:
    args = parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    logger = logging.getLogger(__name__)

    settings = get_settings()
    collection_name = args.collection or settings.COLLECTION_NAME
    batch_size = max(1, args.batch_size)

    if args.dry_run:
        rows = query_chunks(settings, args.max_chunks)
        logger.info(
            "dry-run: 待迁移 %d 条；将写入集合 %s",
            len(rows),
            collection_name,
        )
        for row in rows[:5]:
            logger.info(
                "sample: id=%s doc_id=%s course_id=%s seq=%s content=%r",
                row["id"],
                row["doc_id"],
                row["course_id"],
                row["seq"],
                row["content"],
            )
        return 0

    embedding_service = EmbeddingService()
    milvus_service = MilvusService(collection_name=collection_name)
    milvus_service.ensure_collection()

    existing_ids = milvus_service.get_all_chunk_ids()

    rows = query_chunks(settings, args.max_chunks)
    total = len(rows)
    skipped = 0
    inserted = 0
    failed = 0
    started = time.perf_counter()

    for start in range(0, total, batch_size):
        batch = rows[start : start + batch_size]
        batch_skipped = 0
        batch_inserted = 0
        batch_failed = 0
        payload: list[dict[str, Any]] = []

        for row in batch:
            chunk_id = int(row["id"])
            if chunk_id in existing_ids:
                batch_skipped += 1
                continue

            payload.append(
                {
                    "chunk_id": chunk_id,
                    "course_id": int(row["course_id"]),
                    "doc_id": int(row["doc_id"]),
                    "seq": int(row["seq"]),
                    "content": row["content"],
                }
            )

        if not payload:
            # 整批都已存在时也要记账，保证重复执行时 skipped 统计准确。
            skipped += batch_skipped
            logger.info(
                "batch=%d/%d inserted=0 skipped=%d failed=0",
                start + len(batch),
                total,
                batch_skipped,
            )
            continue

        try:
            # 先完成向量化，避免删除旧向量后模型失败导致数据丢失。
            logger.info("embedding %d chunks (max_length=%d)", len(payload), settings.EMBEDDING_MAX_LENGTH)
            vectors = embedding_service.embed_documents([row["content"] for row in payload])
            if len(vectors) != len(payload):
                raise ValueError("向量化结果数量与切片数量不一致")
            for item, vector in zip(payload, vectors):
                item["embedding"] = vector
            # --force 时先删除同 chunk_id 的旧向量，再写入新向量，保证覆盖语义。
            if args.force:
                milvus_service.delete_chunks_by_ids([int(row["chunk_id"]) for row in payload])
            milvus_service.insert_chunks(payload)
            batch_inserted = len(payload)
        except Exception as exc:
            batch_failed += len(payload)
            logger.exception("批量向量化失败，保留 MySQL 数据：%s", exc)

        skipped += batch_skipped
        inserted += batch_inserted
        failed += batch_failed
        logger.info(
            "batch=%d/%d inserted=%d skipped=%d failed=%d",
            start + len(batch),
            total,
            batch_inserted,
            batch_skipped,
            batch_failed,
        )

    elapsed = time.perf_counter() - started
    logger.info(
        "backfill complete: total=%d inserted=%d skipped=%d failed=%d elapsed=%.2fs",
        total,
        inserted,
        skipped,
        failed,
        elapsed,
    )
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
