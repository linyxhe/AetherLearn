"""LLM 调用相关的异常类型。

统一在这里定义，便于 API 层把不同失败映射成稳定的 HTTP 状态码，
也便于 Java 侧据此归类降级原因（timeout / unreachable / bad_response）。
"""


class LlmError(Exception):
    """LLM 调用异常基类。"""


class LlmConfigIncomplete(LlmError):
    """缺少 base_url / api_key / model，无法发起调用。"""


class LlmTimeout(LlmError):
    """调用超过请求预算。"""


class LlmUnavailable(LlmError):
    """连接失败、鉴权失败或上游 5xx，属于"稍后可能恢复"的失败。"""


class LlmBadResponse(LlmError):
    """模型返回了内容但无法解析（JSON 期望场景）。"""


class RetrievalUnavailable(LlmError):
    """图内兜底检索不可用（Milvus 未启动、连接失败等）。

    与 LLM 失败区分开：这类失败应让 Java 用自己的检索链（含 BM25 兜底）整轮接管，
    降级原因也必须是"检索不可用"而不是含糊的"LLM 调用异常"。
    """


def wrap_exception(exc: Exception) -> LlmError:
    """把上游 SDK 的异常归类成本项目的四种类型。

    用类名判断而不是 isinstance，避免在基础设施层硬绑 openai 的具体版本。
    """
    if isinstance(exc, LlmError):
        return exc

    name = type(exc).__name__
    message = str(exc) or name

    if isinstance(exc, (TimeoutError,)) or "Timeout" in name:
        return LlmTimeout(message)
    if "Authentication" in name or "PermissionDenied" in name:
        return LlmUnavailable(f"鉴权失败：{message}")
    if "RateLimit" in name:
        return LlmUnavailable(f"触发限流：{message}")
    if "Connection" in name or "APIStatus" in name or "InternalServer" in name:
        return LlmUnavailable(message)
    return LlmError(message)

