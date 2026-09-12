"""学情查询工具（ReAct 的动作之二）。

数据来源：**Java 侧预先取好的学情快照**，随问答请求下发。
为什么不做 Python→Java 回调：业务数据访问与鉴权归 Java，让 Java 预取快照既保住了这条边界，
又省掉一次 Java→Python→Java 的嵌套调用（嵌套超时是最难排查的一类线上问题）。
代价是快照在本次请求内是静态的——对本场景（本轮问答）完全够用。
"""

from __future__ import annotations

from typing import Any, Dict

from langchain_core.tools import StructuredTool


def _format_progress(context: Dict[str, Any]) -> str:
    """把学情快照整理成模型好读的文本。"""
    lines = ["本人该课程的学习情况："]
    course_name = context.get("course_name")
    if course_name:
        lines.append(f"- 课程：{course_name}")

    overview = {
        "平均正确率": context.get("avg_accuracy"),
        "学习活动次数": context.get("activity_count"),
        "错题数": context.get("wrong_count"),
        "待完成任务": context.get("pending_todos"),
    }
    for label, value in overview.items():
        if value is not None:
            lines.append(f"- {label}：{value}")

    scores = context.get("recent_scores") or []
    if scores:
        lines.append("- 最近成绩：" + "；".join(
            f"{item.get('title', '作业')} {item.get('score')}"
            + (f"/{item.get('full_score')}" if item.get("full_score") is not None else "")
            for item in scores[:5]
        ))

    weak_points = context.get("weak_points") or []
    if weak_points:
        lines.append("- 薄弱知识点：" + "；".join(
            f"{item.get('knowledge_point')}（错误率 {item.get('error_rate')}%）"
            for item in weak_points[:5]
        ))

    if len(lines) == 1:
        lines.append("（暂无学情数据：该学生在本课程还没有作业或练习记录）")
    return "\n".join(lines)


def build_learning_progress_tool(student_context: Dict[str, Any]) -> StructuredTool:
    """构造学情查询工具（数据来自 Java 预取的快照）。"""

    async def _get_learning_progress() -> str:
        """查询本人在当前课程的学习情况。"""
        return _format_progress(student_context or {})

    return StructuredTool.from_function(
        coroutine=_get_learning_progress,
        name="get_learning_progress",
        description=(
            "查询**提问学生本人**在当前课程的学习情况：平均正确率、最近作业成绩、"
            "薄弱知识点、错题数、待完成任务。"
            "当学生问'我考得怎么样''我哪里比较弱''我还差什么'这类关于自身学习数据的问题时调用；"
            "它与课程知识库内容无关。"
        ),
    )
