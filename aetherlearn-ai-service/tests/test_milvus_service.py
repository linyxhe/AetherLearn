import sys
from pathlib import Path
from unittest.mock import Mock

import pytest

pytest.importorskip("pydantic_settings")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.milvus_service import MilvusService


class FakeMilvusClient:
    """不连接真实 Milvus 的集合客户端替身。"""

    def __init__(self, description, query_results=None):
        self.description = description
        self.inserted = []
        self.deleted = []
        self.query_results = query_results or []

    def describe_collection(self, collection_name):
        return self.description

    def insert(self, collection_name, data):
        self.inserted.extend(data)
        return {"ids": list(range(len(data)))}

    def query(self, collection_name, filter, output_fields, limit):
        return self.query_results

    def flush(self, collection_name):
        return None


def test_legacy_collection_omits_chunk_id():
    client = FakeMilvusClient({
        "schema": {"fields": [{"name": "course_id"}, {"name": "doc_id"}, {"name": "seq"}, {"name": "content"}]},
        "enable_dynamic_field": False,
    })
    service = MilvusService()
    service.client = client
    service._supports_chunk_id = False

    inserted = service.insert_chunks([
        {
            "chunk_id": 42,
            "course_id": 1,
            "doc_id": 2,
            "seq": 0,
            "content": "旧集合内容",
            "embedding": [0.0] * 1024,
        }
    ])

    assert inserted == [0]
    assert "chunk_id" not in service.client.inserted[0]
    assert service._output_fields() == ["id", "course_id", "doc_id", "seq", "content"]


def test_new_collection_includes_chunk_id_and_output_fields():
    client = FakeMilvusClient({
        "schema": {"fields": [{"name": "chunk_id"}]},
        "enable_dynamic_field": True,
    })
    service = MilvusService()
    service.client = client
    service._supports_chunk_id = True

    inserted = service.insert_chunks([
        {
            "chunk_id": 42,
            "course_id": 1,
            "doc_id": 2,
            "seq": 0,
            "content": "新集合内容",
            "embedding": [0.0] * 1024,
        }
    ])

    assert inserted == [0]
    assert "chunk_id" in service.client.inserted[0]
    assert service._output_fields() == ["id", "course_id", "doc_id", "seq", "content", "chunk_id"]


def test_hit_conversion_supports_missing_chunk_id():
    service = MilvusService()
    service._supports_chunk_id = False
    hit = Mock()
    hit.entity = {
        "course_id": 1,
        "doc_id": 2,
        "seq": 3,
        "content": "旧集合命中",
    }
    hit.score = 0.8

    result = service._format_hits([[hit]], 1)

    assert result[0]["chunk_id"] is None
    assert result[0]["doc_id"] == 2
    assert result[0]["seq"] == 3


def test_hit_conversion_supports_milvus_client_dict_shape():
    """PyMilvus 2.5 的 MilvusClient.search 返回 dict，需要与 ORM 对象一样解析。"""
    service = MilvusService()
    service._supports_chunk_id = True
    raw_hits = [[{
        "id": 1,
        "distance": 0.7,
        "entity": {
            "course_id": 1,
            "doc_id": 2,
            "seq": 3,
            "content": "新集合命中",
            "chunk_id": 42,
        },
    }]]

    result = service._format_hits(raw_hits, 1)

    assert result[0]["chunk_id"] == 42
    assert result[0]["content"] == "新集合命中"
    assert result[0]["score"] == 0.7


def test_get_all_chunk_ids_returns_mysql_ids():
    client = FakeMilvusClient(
        {"schema": {"fields": [{"name": "chunk_id"}]}},
        query_results=[{"chunk_id": 11}, {"chunk_id": 12}, {"chunk_id": None}],
    )
    service = MilvusService()
    service.client = client
    service._supports_chunk_id = True

    assert service.get_all_chunk_ids() == {11, 12}


def test_get_all_chunk_ids_returns_empty_for_legacy_collection():
    service = MilvusService()
    service._supports_chunk_id = False

    assert service.get_all_chunk_ids() == set()


def test_collection_ready_requires_bm25_function_and_indexes():
    service = MilvusService()
    complete = {
        "field_names": [
            "course_id",
            "doc_id",
            "seq",
            "content",
            "chunk_id",
            "dense_vector",
            "sparse_vector",
        ],
        "function_names": ["bm25_fn"],
        "index_fields": ["dense_vector", "sparse_vector"],
    }
    assert service._is_collection_ready(complete) is True

    missing_function = dict(complete, function_names=[])
    assert service._is_collection_ready(missing_function) is False

    missing_index = dict(complete, index_fields=["dense_vector"])
    assert service._is_collection_ready(missing_index) is False
