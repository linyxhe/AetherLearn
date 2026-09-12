import sys
from pathlib import Path

import pytest

pytest.importorskip("fastapi")
pytest.importorskip("httpx")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi.testclient import TestClient

from app.main import app, milvus_service


def test_health_live_does_not_depend_on_milvus():
    """存活检查只验证进程，不应触发 Milvus 或模型加载。"""
    client = TestClient(app)
    response = client.get("/health/live")

    assert response.status_code == 200
    assert response.json()["status"] == "alive"


def test_health_ready_returns_503_when_collection_not_ready(monkeypatch):
    """Milvus 未就绪时返回 503，让启动脚本明确告警。"""
    monkeypatch.setattr(
        milvus_service,
        "get_collection_stats",
        lambda: {"indexed": False, "status": "degraded", "error": "milvus unavailable"},
    )
    client = TestClient(app)
    response = client.get("/health/ready")

    assert response.status_code == 503
    assert response.json()["status"] == "degraded"


def test_health_ready_returns_ok_when_collection_ready(monkeypatch):
    """集合完整时返回 ok，模型仍保持延迟加载。"""
    monkeypatch.setattr(
        milvus_service,
        "get_collection_stats",
        lambda: {"indexed": True, "status": "ok", "num_entities": 3},
    )
    client = TestClient(app)
    response = client.get("/health/ready")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["milvus"]["num_entities"] == 3
    assert body["embedding"]["status"] in {"starting", "loaded"}
