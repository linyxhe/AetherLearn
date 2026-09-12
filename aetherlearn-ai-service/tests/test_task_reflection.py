"""任务图反思（Reflexion）测试。

守四件事：
1. **只在结构化输出上反思**：`text` 任务永不反思（自由文本无对错可判）；
2. **反思是补救不是主路径**：第一次就对时只花 1 次调用；
3. **有界**：一直给错也不会无限重试，最终带着 `error` 正常返回（不抛异常）；
4. **批评要具体**：重试时模型必须看到"上次自己错在哪"，这是 Reflexion 生效的关键。
"""

import asyncio
import json
import sys
from pathlib import Path
from typing import List

import pytest

pytest.importorskip("langgraph")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app.agents import graph_task
from app.llm.config import ResolvedLlmSettings


def _settings(timeout: float = 20.0) -> ResolvedLlmSettings:
    return ResolvedLlmSettings(
        base_url="http://fake/v1",
        api_key="sk-fake-1234567890",
        model="fake-model",
        temperature=0.3,
        max_tokens=512,
        timeout=timeout,
    )


class ScriptedModel:
    """按脚本依次返回内容；记录每次收到的消息序列，供断言反思上下文。

    `delays` 用于模拟真实耗时：预算记账必须用"真的花了时间"才能测出来，
    否则瞬时返回的假模型永远有剩余预算，测不出"预算不足就不重试"。
    """

    def __init__(self, outputs: List[str], delays: List[float] | None = None):
        self.outputs = list(outputs)
        self.delays = list(delays or [])
        self.calls = 0
        self.seen_messages: List[List] = []

    async def ainvoke(self, messages, **kwargs):
        self.seen_messages.append(list(messages))
        self.calls += 1
        if self.delays:
            await asyncio.sleep(self.delays.pop(0))
        text = self.outputs.pop(0) if self.outputs else ""
        return AIMessage(content=text)


def _run(model, monkeypatch, *, prompt_key="grade_subjective", output_format="json_object",
         max_reflections=1, timeout=20.0, variables=None):
    monkeypatch.setattr(graph_task, "get_chat_model", lambda settings: model)
    return asyncio.run(
        graph_task.invoke_task(
            prompt_key,
            variables if variables is not None else {
                "question": "q", "standard_answer": "a", "student_answer": "b", "full_score": "10",
            },
            output_format=output_format,
            settings=_settings(timeout),
            max_reflections=max_reflections,
        )
    )


GOOD_GRADE = json.dumps({"score": 8, "feedback": "基本正确"}, ensure_ascii=False)


def test_valid_output_costs_one_call(monkeypatch):
    """第一次就给对 → 只调一次，反思次数为 0（反思不该成为主路径）。"""
    model = ScriptedModel([GOOD_GRADE])

    result = _run(model, monkeypatch)

    assert model.calls == 1
    assert result.reflections == 0
    assert result.error is None
    assert result.data["score"] == 8


def test_invalid_json_triggers_one_reflection_and_recovers(monkeypatch):
    """第一次输出不是 JSON → 反思一次并成功。"""
    model = ScriptedModel(["我觉得这个学生答得还行，给 8 分吧", GOOD_GRADE])

    result = _run(model, monkeypatch)

    assert model.calls == 2
    assert result.reflections == 1
    assert result.error is None
    assert result.data["feedback"] == "基本正确"


def test_reflection_feeds_back_specific_error_and_own_output(monkeypatch):
    """重试时必须带上"上次的输出 + 具体错在哪"——这是 Reflexion 生效的关键。"""
    model = ScriptedModel(["给 8 分", GOOD_GRADE])

    _run(model, monkeypatch)

    retry_messages = model.seen_messages[1]
    # 序列：system → 原始 user → 上次的 AI 输出 → 批评
    # （system 与原始 user 必须在，否则重试时模型不知道自己要干什么）
    assert [type(m).__name__ for m in retry_messages] == [
        "SystemMessage", "HumanMessage", "AIMessage", "HumanMessage",
    ]
    assert retry_messages[1].content                  # 原始任务描述还在
    assert retry_messages[2].content == "给 8 分"      # 模型能看到自己上次说了什么
    critique = retry_messages[3].content
    assert "不符合要求" in critique
    assert "JSON 对象" in critique                          # 说清要什么形状
    assert "不要 Markdown 代码围栏" in critique              # 给出可执行的改正指引


def test_missing_required_key_is_treated_as_failure(monkeypatch):
    """是合法 JSON 但缺 score 字段 → 同样算失败（与 Java 的 parseLlmGrade 口径一致）。"""
    model = ScriptedModel([json.dumps({"feedback": "缺了分数"}, ensure_ascii=False), GOOD_GRADE])

    result = _run(model, monkeypatch)

    assert model.calls == 2
    assert result.reflections == 1
    retry_messages = model.seen_messages[1]
    # 批评里要指名缺哪个字段，而不是笼统"格式不对"
    assert "score" in retry_messages[3].content


def test_reflection_is_bounded_and_returns_error(monkeypatch):
    """一直给错：重试到上限就停，带 error 正常返回（不抛异常，让 Java 走既有降级）。"""
    model = ScriptedModel(["错的"] * 5)

    result = _run(model, monkeypatch, max_reflections=1)

    assert model.calls == 2          # 首次 + 1 次反思
    assert result.reflections == 1
    assert result.error is not None
    assert result.data is None
    assert result.raw == "错的"      # 原始输出仍然带回去，Java 侧解析仍是权威


def test_max_reflections_zero_disables_reflection(monkeypatch):
    """max_reflections=0 → 退回改造前的行为（失败立即降级），用于对照验证。"""
    model = ScriptedModel(["错的"] * 3)

    result = _run(model, monkeypatch, max_reflections=0)

    assert model.calls == 1
    assert result.reflections == 0
    assert result.error is not None


def test_text_output_never_reflects(monkeypatch):
    """自由文本不反思：报告/建议的质量无法自动判定，重试等于白烧一次调用。"""
    model = ScriptedModel(["这是一段正常的学习报告文本", "不该被用到的第二次输出"])

    result = _run(model, monkeypatch, prompt_key="student_report", output_format="text",
                  variables={"overview": "o", "score_trend": "s", "knowledge_gaps": "k",
                             "suggestions": "g", "learning_path": "p"})

    assert model.calls == 1
    assert result.reflections == 0
    assert result.error is None
    assert "学习报告" in result.raw


def test_json_array_validates_item_fields(monkeypatch):
    """出题：数组项缺 answer → 判失败并反思（与 Java 解析器的必需字段一致）。"""
    bad = json.dumps([{"content": "题干", "options": ["A", "B"]}], ensure_ascii=False)
    good = json.dumps([{"content": "题干", "options": ["A", "B"], "answer": "A"}], ensure_ascii=False)
    model = ScriptedModel([bad, good])

    result = _run(model, monkeypatch, prompt_key="generate_questions", output_format="json_array",
                  variables={"context": "c", "type_name": "单选题", "count": "1"})

    assert model.calls == 2
    assert result.reflections == 1
    assert result.data[0]["answer"] == "A"
    assert "answer" in model.seen_messages[1][3].content


def test_empty_json_array_is_a_failure(monkeypatch):
    """空数组不算成功：出题返回 [] 等于没出题，必须反思或如实标记失败。"""
    model = ScriptedModel(["[]", "[]"])

    result = _run(model, monkeypatch, prompt_key="generate_questions", output_format="json_array",
                  variables={"context": "c", "type_name": "单选题", "count": "3"})

    assert model.calls == 2
    assert result.error is not None
    assert "空" in result.error


def test_reflection_respects_remaining_budget(monkeypatch):
    """预算不足时不重试：一次必然超时的调用只会拖长用户等待，还会击穿 Java 总预算。"""
    # 首次调用真的花掉 2.5s（总预算 3.0s），剩余 0.5s 已不足 MIN_RETRY_SECONDS
    model = ScriptedModel(["错的", GOOD_GRADE], delays=[2.5])

    result = _run(model, monkeypatch, max_reflections=3, timeout=3.0)

    assert result.reflections == 0, "预算不足时不该反思"
    assert model.calls == 1


def test_reflections_exposed_in_result(monkeypatch):
    """反思次数要能被调用方看到（Java 侧据此记录"自动修复了几次"）。"""
    model = ScriptedModel(["错", "还是错", GOOD_GRADE])

    result = _run(model, monkeypatch, max_reflections=2)

    assert result.reflections == 2
    assert result.error is None
    assert result.data["score"] == 8
