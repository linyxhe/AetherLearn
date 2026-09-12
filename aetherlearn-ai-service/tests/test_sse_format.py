import sys
from pathlib import Path

import pytest

pytest.importorskip("langchain_core")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.llm.sse import HEARTBEAT_FRAME, format_sse_event


def test_single_line_event():
    assert format_sse_event("chunk", "你好") == "event: chunk\ndata: 你好\n\n"


def test_multiline_data_is_split_into_multiple_data_lines():
    """与 Java 解析器的 join("\\n") 对偶：代码块换行必须能原样还原。"""
    frame = format_sse_event("chunk", "第一行\n第二行\n第三行")

    assert frame == "event: chunk\ndata: 第一行\ndata: 第二行\ndata: 第三行\n\n"
    data_lines = [line[6:] for line in frame.strip().split("\n") if line.startswith("data: ")]
    assert "\n".join(data_lines) == "第一行\n第二行\n第三行"


def test_data_with_blank_line_keeps_the_gap():
    frame = format_sse_event("chunk", "a\n\nb")
    assert frame == "event: chunk\ndata: a\ndata: \ndata: b\n\n"


def test_empty_data_still_produces_a_frame():
    assert format_sse_event("chunk", "") == "event: chunk\ndata: \n\n"


def test_heartbeat_frame_is_a_comment():
    assert HEARTBEAT_FRAME == ": ping\n\n"
    assert HEARTBEAT_FRAME.startswith(":")
