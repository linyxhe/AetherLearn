import sys
from pathlib import Path

import pytest

pytest.importorskip("langchain_core")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.llm.json_utils import extract_json_array, extract_json_object, strip_code_fence


def test_extract_json_object_handles_plain_object():
    assert extract_json_object('{"score": 8, "feedback": "不错"}') == {
        "score": 8,
        "feedback": "不错",
    }


def test_extract_json_object_handles_nested_and_escaped_braces():
    """Java 侧原来的正则 \\{[^}]*\\} 处理不了嵌套与 feedback 里的花括号，这里必须能。"""
    raw = '评分如下：{"score": 7, "feedback": "注意 if 条件 {x > 0} 的写法", "detail": {"a": 1}} 完毕'
    parsed = extract_json_object(raw)

    assert parsed["score"] == 7
    assert parsed["detail"] == {"a": 1}
    assert "{x > 0}" in parsed["feedback"]


def test_extract_json_object_handles_markdown_fence():
    raw = '```json\n{"score": 5}\n```'
    assert extract_json_object(raw) == {"score": 5}


def test_extract_json_object_returns_none_when_unparsable():
    assert extract_json_object("模型这次没有给出结构化结果") is None
    assert extract_json_object("") is None


def test_extract_json_array_with_surrounding_text():
    raw = '题目如下：[{"content": "1+1=?", "answer": "2"}, {"content": "2+2=?", "answer": "4"}] 请查收'
    parsed = extract_json_array(raw)

    assert isinstance(parsed, list)
    assert len(parsed) == 2
    assert parsed[0]["content"] == "1+1=?"


def test_extract_json_array_returns_none_for_object_only():
    assert extract_json_array('{"content": "不是数组"}') is None


def test_strip_code_fence_variants():
    assert strip_code_fence('```json\n{"a":1}\n```') == '{"a":1}'
    assert strip_code_fence('```\n{"a":1}\n```') == '{"a":1}'
    assert strip_code_fence('{"a":1}') == '{"a":1}'
