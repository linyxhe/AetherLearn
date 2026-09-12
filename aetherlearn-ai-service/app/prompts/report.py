"""AI 学习报告提示词（迁移自 Java 侧 AiReportServiceImpl）。

user 模板与 Java 逐字一致；`overview` / `score_trend` 等变量的取值由 Java 侧
用自己的对象 toString 结果提供，Python 不重写学情数据的格式化逻辑。
"""

from app.prompts.registry import PromptSpec, register

SYSTEM = "你是 AetherLearn 学习分析助手。请基于学生学情数据生成中文学习报告，语气客观、具体、可执行。"

USER = (
    "请生成一份 300 字以内的学习报告，包含总体表现、优势、薄弱点、下周行动建议。\n"
    "概览：{overview}"
    "\n成绩趋势：{score_trend}"
    "\n知识盲区：{knowledge_gaps}"
    "\n学习建议：{suggestions}"
    "\n学习路径：{learning_path}"
)

register(
    PromptSpec(
        key="student_report",
        system=SYSTEM,
        user_template=USER,
        default_output_format="text",
        description="学生 AI 学习报告",
    )
)
