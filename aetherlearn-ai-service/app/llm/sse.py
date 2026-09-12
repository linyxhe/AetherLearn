"""SSE 帧格式化。

编码约定（必须与 Java 侧解析器、前端 Qa.vue 三方对齐）：
- 一个帧 = `event:` 行 + 一到多条 `data:` 行 + 空行 `\\n\\n`；
- **data 里的换行必须拆成多条 `data:` 行**，消费方用 `\\n` 重新 join，
  否则模型输出里的代码块换行会丢失；
- 不发 `[DONE]` 哨兵；心跳用注释行 `: ping`。
"""

from __future__ import annotations


def format_sse_event(event: str, data: str) -> str:
    """把 (事件名, 数据) 格式化成 SSE 帧。"""
    lines = [f"event: {event}"]
    for line in str(data).split("\n"):
        lines.append(f"data: {line}")
    return "\n".join(lines) + "\n\n"


HEARTBEAT_FRAME = ": ping\n\n"
