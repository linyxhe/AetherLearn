"""LangChain ChatModel 工厂。

两个关键点：
1. `max_retries=0`：LangChain 默认重试 2 次，会把 Java 传下来的 3.5s 预算放大成 10s+，
   直接击穿上游的业务超时预算，因此必须关掉，由上层用预算来控制重试与否。
2. `stream_usage=False`：避免触发 tiktoken 的 BPE 词表下载（国内网络 + 离线环境下会挂住数十秒）。
"""

from __future__ import annotations

import logging
import threading
from collections import OrderedDict
from typing import Optional

from langchain_core.language_models.chat_models import BaseChatModel

from app.llm.config import ResolvedLlmSettings
from app.llm.errors import LlmConfigIncomplete


logger = logging.getLogger(__name__)

# 管理端频繁改配置时不至于把连接池堆满，因此给缓存设上限。
_CACHE_MAX = 4
_CACHE_LOCK = threading.Lock()
_CACHE: "OrderedDict[str, BaseChatModel]" = OrderedDict()


def build_chat_model(settings: ResolvedLlmSettings) -> BaseChatModel:
    """按参数新建一个 ChatOpenAI 实例。"""
    if not settings.available():
        raise LlmConfigIncomplete(
            "LLM 配置不完整：需要 base_url / api_key / model 三项"
        )

    from langchain_openai import ChatOpenAI

    return ChatOpenAI(
        model=settings.model,
        api_key=settings.api_key,
        base_url=settings.base_url,
        temperature=settings.temperature,
        max_tokens=settings.max_tokens,
        timeout=settings.timeout,
        max_retries=0,
        stream_usage=False,
    )


def get_chat_model(settings: ResolvedLlmSettings) -> BaseChatModel:
    """按 signature 复用模型实例。"""
    signature = settings.signature()
    with _CACHE_LOCK:
        cached = _CACHE.get(signature)
        if cached is not None:
            _CACHE.move_to_end(signature)
            return cached

    model = build_chat_model(settings)

    with _CACHE_LOCK:
        _CACHE[signature] = model
        _CACHE.move_to_end(signature)
        while len(_CACHE) > _CACHE_MAX:
            _CACHE.popitem(last=False)
    logger.info("创建 LLM 客户端：%s", settings.safe_repr())
    return model


def clear_model_cache() -> None:
    """清空模型缓存（配置变更或测试用）。"""
    with _CACHE_LOCK:
        _CACHE.clear()


def cached_signatures() -> list[str]:
    """返回当前缓存的 signature 列表（测试与运维排查用）。"""
    with _CACHE_LOCK:
        return list(_CACHE.keys())
