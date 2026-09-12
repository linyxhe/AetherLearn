"""智能出题提示词（逐字迁移自 Java 侧 AssignmentServiceImpl.autoGenerateQuestions）。

契约：只输出 JSON 数组，元素含 content/options/answer/analysis/knowledgePoint/score；
Java 侧仍用自己的解析与规范化（选项去前缀、答案提字母），Python 的结构化结果只作兜底。
"""

from app.prompts.registry import PromptSpec, register

SYSTEM = (
    "你是 AetherLearn 智能出题助手。请依据下方【知识库内容】生成指定数量和类型的题目。"
    "每道题输出一个 JSON 对象，所有题目用 JSON 数组返回。格式：\n"
    "[{\"content\":\"题目内容\",\"options\":[\"选项1\",\"选项2\",\"选项3\",\"选项4\"],\"answer\":\"A\",\"analysis\":\"解析\",\"knowledgePoint\":\"知识点\",\"score\":5}]\n"
    "硬性要求：单选题和多选题必须给出 4 个 options，options 必须是 JSON 数组，不要带 A/B/C/D 前缀；"
    "单选答案只写一个字母如 A，多选答案写多个字母如 AC；判断/填空/简答 options 使用空数组；"
    "填空题题干用 ___ 表示空格；简答或分析类题目给出参考答案要点；score 默认5分；只输出 JSON 数组，不要其它内容。"
)

USER = (
    "【知识库内容】\n{context}"
    "\n\n【出题要求】\n题型：{type_name}"
    "\n数量：{count}道"
)

register(
    PromptSpec(
        key="generate_questions",
        system=SYSTEM,
        user_template=USER,
        default_output_format="json_array",
        extra={"item_required_keys": ["content", "answer"]},
        description="依据知识库内容自动出题",
    )
)
