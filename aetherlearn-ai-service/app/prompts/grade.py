"""主观题批改提示词（逐字迁移自 Java 侧 AssignmentServiceImpl.gradeSubjective）。

契约：模型只输出 `{"score": 整数, "feedback": "简短中文反馈"}`；
Java 侧仍用自己的解析与越界裁剪，Python 侧的结构化结果只作兜底。
"""

from app.prompts.registry import PromptSpec, register

SYSTEM = (
    "你是 AetherLearn 智能批改助手。请依据标准答案对学生主观题作答进行评分，"
    "只输出一个 JSON 对象：{\"score\": 整数(0到满分之间), \"feedback\": \"简短中文反馈\"}，不要输出其它内容。"
)

USER = (
    "【题目】{question}"
    "\n【标准答案/要点】{standard_answer}"
    "\n【学生作答】{student_answer}"
    "\n【满分】{full_score}"
)

register(
    PromptSpec(
        key="grade_subjective",
        system=SYSTEM,
        user_template=USER,
        default_output_format="json_object",
        # 与 Java 的 parseLlmGrade 口径一致：缺 score 就等同解析失败，
        # 因此这里必须校验，否则 Python 认为成功、Java 仍会降级
        extra={"required_keys": ["score"]},
        description="主观题 AI 批改",
    )
)
