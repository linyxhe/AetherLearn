"""日志脱敏。

LLM 的 API Key 现在会经由 Java 的请求体进入本服务，属于必须防止落盘的内容。
在 root logger 上挂一个 Filter，把常见密钥形态与已知密钥值替换成 ***。
"""

from __future__ import annotations

import logging
import re

from app.config import get_settings


settings = get_settings()

# 常见密钥形态：sk-xxx、Bearer xxx、api_key=xxx
_PATTERNS = [
    re.compile(r"sk-[A-Za-z0-9_\-]{8,}"),
    re.compile(r"(?i)(bearer\s+)[A-Za-z0-9_\-\.]{8,}"),
    re.compile(r"(?i)(api[_-]?key[\"'\s:=]+)[A-Za-z0-9_\-\.]{8,}"),
]


def redact_text(text: str) -> str:
    """把文本里的密钥替换为 ***。"""
    if not text:
        return text
    result = text
    for pattern in _PATTERNS:
        result = pattern.sub(lambda m: (m.group(1) if m.groups() else "") + "***", result)
    # 本进程已知的密钥值（含 SERVICE_API_KEY 与 LLM_API_KEY）
    for secret in filter(None, [settings.LLM_API_KEY, settings.SERVICE_API_KEY]):
        if len(secret) >= 8:
            result = result.replace(secret, "***")
    return result


class RedactingFilter(logging.Filter):
    """在日志落盘前脱敏。"""

    def filter(self, record: logging.LogRecord) -> bool:
        try:
            message = record.getMessage()
            redacted = redact_text(message)
            if redacted != message:
                record.msg = redacted
                record.args = ()
        except Exception:  # pragma: no cover - 脱敏失败不能影响日志本身
            pass
        return True


def install_logging_redaction() -> None:
    """给 root logger 安装脱敏 Filter（重复调用安全）。"""
    root = logging.getLogger()
    if any(isinstance(item, RedactingFilter) for item in root.filters):
        return
    root.addFilter(RedactingFilter())
