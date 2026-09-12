"""LLM 运行参数的解析与合并。

优先级（高 → 低）：
1. 请求体 `llm.*`：来自 Java 管理端 sys_config（管理员在配置中心改的值）
2. 环境变量 `LLM_*`：本进程 .env（config.Settings 已声明但此前从未被引用）
3. app/config.py 的字面默认值

硬规则：**空字符串 / None 一律视为"未提供"，不参与覆盖**。
否则 Java 侧传一个空字符串就会把 Python 自己的可用配置覆盖成空，直接导致调用失败。
"""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from typing import Optional

from pydantic import BaseModel, Field

from app.config import get_settings

settings = get_settings()

# 单次调用的超时上下限：下限避免毫秒级超时导致必然失败，上限必须小于 Java SseEmitter 的 120s。
MIN_TIMEOUT_SECONDS = 1.0
MAX_TIMEOUT_SECONDS = 110.0


def _clean(value) -> Optional[str]:
    """把空字符串/纯空白视作未提供。"""
    if value is None:
        return None
    text = str(value).strip()
    return text or None


class LlmOverrides(BaseModel):
    """请求体里可选的 LLM 覆盖项（全部来自 Java 的管理端配置）。"""

    base_url: Optional[str] = Field(default=None, description="OpenAI 兼容接口地址")
    # repr=False：避免异常栈或调试输出里带出真实密钥。
    api_key: Optional[str] = Field(default=None, repr=False, description="API Key")
    model: Optional[str] = Field(default=None, description="模型名称")
    temperature: Optional[float] = Field(default=None, description="温度")
    max_tokens: Optional[int] = Field(default=None, description="最大生成 token")
    timeout: Optional[float] = Field(default=None, description="本次调用的超时预算（秒）")


@dataclass(frozen=True)
class ResolvedLlmSettings:
    """解析后的调用参数快照。"""

    base_url: str
    api_key: str
    model: str
    temperature: float
    max_tokens: int
    timeout: float

    def signature(self) -> str:
        """用于模型实例缓存：参数变了就重建客户端。"""
        return "|".join(
            [
                self.base_url,
                self.model,
                str(self.temperature),
                str(self.max_tokens),
                str(self.timeout),
                self.key_fingerprint(),
            ]
        )

    def key_fingerprint(self) -> str:
        """密钥指纹，只用于日志比对，不可反推原文。"""
        if not self.api_key:
            return "none"
        return hashlib.sha256(self.api_key.encode("utf-8")).hexdigest()[:8]

    def available(self) -> bool:
        """三项齐全才认为可调用。"""
        return bool(self.base_url and self.api_key and self.model)

    def safe_repr(self) -> dict:
        """可安全写日志的摘要。"""
        return {
            "base_url": self.base_url,
            "model": self.model,
            "temperature": self.temperature,
            "timeout": self.timeout,
            "key_fp": self.key_fingerprint(),
        }


def _resolve_timeout(override: Optional[float], fallback: int) -> float:
    raw = override if override is not None else fallback
    try:
        value = float(raw)
    except (TypeError, ValueError):
        value = float(fallback)
    return max(MIN_TIMEOUT_SECONDS, min(value, MAX_TIMEOUT_SECONDS))


def resolve_llm_settings(overrides: Optional[LlmOverrides] = None) -> ResolvedLlmSettings:
    """按优先级合并出最终参数；空值不覆盖。"""
    overrides = overrides or LlmOverrides()

    base_url = (
        _clean(overrides.base_url)
        or _clean(settings.LLM_BASE_URL)
        or ""
    )
    api_key = (
        _clean(overrides.api_key)
        or _clean(settings.LLM_API_KEY)
        or ""
    )
    model = (
        _clean(overrides.model)
        or _clean(settings.LLM_MODEL)
        or ""
    )

    temperature = (
        overrides.temperature
        if overrides.temperature is not None
        else settings.LLM_TEMPERATURE
    )
    max_tokens = (
        overrides.max_tokens
        if overrides.max_tokens is not None
        else settings.LLM_MAX_TOKENS
    )
    timeout = _resolve_timeout(overrides.timeout, settings.LLM_TIMEOUT)

    return ResolvedLlmSettings(
        base_url=base_url.rstrip("/"),
        api_key=api_key,
        model=model,
        temperature=float(temperature),
        max_tokens=int(max_tokens),
        timeout=timeout,
    )
