"""AI 教学建议提示词（迁移自 Java 侧 AiTeachingAdviceServiceImpl）。

user 模板与 Java 逐字一致；看板数据的格式化由 Java 侧提供变量值。
"""

from app.prompts.registry import PromptSpec, register

SYSTEM = "你是 AetherLearn 教学分析助手。请基于教师看板数据生成中文教学建议，语气专业、简洁、可执行。"

USER = (
    "请生成一份面向教师的教学建议，包含总体判断、重点知识点、预警学生和后续教学动作。\n"
    "概览：{overview}"
    "\n成绩分布：{score_distribution}"
    "\n完成率趋势：{completion_trend}"
    "\n知识掌握：{knowledge_mastery}"
    "\n预警学生：{student_ranking}"
)

register(
    PromptSpec(
        key="teacher_advice",
        system=SYSTEM,
        user_template=USER,
        default_output_format="text",
        description="教师 AI 教学建议",
    )
)
