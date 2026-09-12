import sys
from pathlib import Path

import pytest

pytest.importorskip("pydantic_settings")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.llm import config as llm_config
from app.llm.config import LlmOverrides, resolve_llm_settings


@pytest.fixture()
def env_settings(monkeypatch):
    """把进程级配置固定成一组已知值，避免测试受本机 .env 影响。"""
    monkeypatch.setattr(llm_config.settings, "LLM_BASE_URL", "https://env.example/v1")
    monkeypatch.setattr(llm_config.settings, "LLM_API_KEY", "sk-env-key-1234567890")
    monkeypatch.setattr(llm_config.settings, "LLM_MODEL", "env-model")
    monkeypatch.setattr(llm_config.settings, "LLM_TEMPERATURE", 0.2)
    monkeypatch.setattr(llm_config.settings, "LLM_MAX_TOKENS", 2048)
    monkeypatch.setattr(llm_config.settings, "LLM_TIMEOUT", 60)
    return llm_config.settings


def test_overrides_win_over_env(env_settings):
    settings = resolve_llm_settings(
        LlmOverrides(base_url="https://override.example/v1", model="override-model", temperature=0.7)
    )

    assert settings.base_url == "https://override.example/v1"
    assert settings.model == "override-model"
    assert settings.temperature == 0.7
    # 未覆盖的字段回落到 env
    assert settings.api_key == "sk-env-key-1234567890"


def test_empty_override_does_not_wipe_env(env_settings):
    """回归：空字符串必须视为"未提供"，否则 Java 传空值会毁掉 Python 侧配置。"""
    settings = resolve_llm_settings(
        LlmOverrides(base_url="", api_key="   ", model="", timeout=None)
    )

    assert settings.base_url == "https://env.example/v1"
    assert settings.api_key == "sk-env-key-1234567890"
    assert settings.model == "env-model"
    assert settings.timeout == 60
    assert settings.available() is True


def test_timeout_is_clamped(env_settings):
    assert resolve_llm_settings(LlmOverrides(timeout=0.1)).timeout == llm_config.MIN_TIMEOUT_SECONDS
    assert resolve_llm_settings(LlmOverrides(timeout=9999)).timeout == llm_config.MAX_TIMEOUT_SECONDS


def test_signature_and_fingerprint(env_settings):
    first = resolve_llm_settings()
    second = resolve_llm_settings()
    changed = resolve_llm_settings(LlmOverrides(model="another-model"))

    assert first.signature() == second.signature()
    assert first.signature() != changed.signature()
    assert len(first.key_fingerprint()) == 8
    # 指纹不可反推原文
    assert "sk-env-key" not in first.key_fingerprint()
    assert first.safe_repr()["key_fp"] == first.key_fingerprint()
    assert "api_key" not in first.safe_repr()


def test_available_requires_all_three(env_settings, monkeypatch):
    monkeypatch.setattr(llm_config.settings, "LLM_API_KEY", "")
    assert resolve_llm_settings().available() is False
