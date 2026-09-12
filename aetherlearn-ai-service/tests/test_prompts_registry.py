import sys
from pathlib import Path

import pytest

pytest.importorskip("langchain_core")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.prompts import all_prompts, get_prompt
from app.prompts.registry import PromptSpec, register


def test_expected_prompt_keys_registered():
    keys = set(all_prompts().keys())

    assert {"qa_with_context", "qa_no_context", "test_connection"}.issubset(keys)


def test_placeholders_match_templates():
    """自动抓"调用方少传一个变量"这类线上炸点。"""
    qa_with_context = get_prompt("qa_with_context")
    qa_no_context = get_prompt("qa_no_context")

    # 多轮历史不再进模板：它以真正的 user/assistant 消息单独传入（见 test_qa_graph 的消息序列断言）
    assert qa_with_context.placeholders() == {"question", "context"}
    assert qa_no_context.placeholders() == {"question"}
    assert get_prompt("test_connection").placeholders() == set()


def test_capability_prompts_registered_with_expected_placeholders():
    assert get_prompt("grade_subjective").placeholders() == {
        "question",
        "standard_answer",
        "student_answer",
        "full_score",
    }
    assert get_prompt("generate_questions").placeholders() == {"context", "type_name", "count"}
    assert get_prompt("student_report").placeholders() == {
        "overview",
        "score_trend",
        "knowledge_gaps",
        "suggestions",
        "learning_path",
    }
    assert get_prompt("teacher_advice").placeholders() == {
        "overview",
        "score_distribution",
        "completion_trend",
        "knowledge_mastery",
        "student_ranking",
    }


def test_capability_prompts_declare_output_format():
    assert get_prompt("grade_subjective").default_output_format == "json_object"
    assert get_prompt("generate_questions").default_output_format == "json_array"
    assert get_prompt("student_report").default_output_format == "text"


def test_render_produces_system_and_user():
    system, user = get_prompt("qa_no_context").render({"question": "什么是 JVM"})

    assert system.startswith("你是 AetherLearn 智能助教")
    assert user.endswith("【学生问题】\n什么是 JVM")


def test_render_reports_missing_variable():
    with pytest.raises(KeyError):
        get_prompt("qa_with_context").render({"question": "缺变量"})


def test_duplicate_registration_is_rejected():
    with pytest.raises(ValueError):
        register(PromptSpec(key="qa_no_context", system="x", user_template="y"))
