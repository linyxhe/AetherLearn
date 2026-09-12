"""初始化 Milvus 集合并输出可用于验收的结构信息。"""

import sys
from pathlib import Path

# 支持直接执行 `python scripts/init_milvus.py`，无需额外设置 PYTHONPATH。
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.milvus_service import MilvusService


def init_milvus() -> None:
    """创建缺失的新集合，并打印字段、函数、索引和实体统计。"""
    service = MilvusService()
    service.ensure_collection()
    stats = service.get_collection_stats()
    print("Milvus collection initialized:")
    print(stats)
    print(
        "\n历史切片请运行："
        "python scripts/backfill_milvus.py --dry-run；"
        "确认无误后再运行 python scripts/backfill_milvus.py --force。"
    )


if __name__ == "__main__":
    init_milvus()
