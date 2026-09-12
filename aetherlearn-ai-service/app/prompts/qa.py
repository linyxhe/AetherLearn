"""问答提示词（逐字迁移自 Java 侧 QaServiceImpl.buildPrompt，行为保持一致）。"""

from app.prompts.registry import PromptSpec, register

SYSTEM_WITH_CONTEXT = (
    "你是 AetherLearn 智能助教。请先直接回答学生的问题，然后如果下方知识库内容与问题相关，可以引用补充说明。"
    "重要规则：你必须用你自己的知识回答问题，绝对不能说'资料中未提及'、'不在知识库中'等话。"
    "知识库只是额外参考，不是你的知识边界。"
)

SYSTEM_NO_CONTEXT = "你是 AetherLearn 智能助教，请友好、简洁地回答学生的问题。"

# 多轮历史不再拼进这两条模板：历史以真正的 user/assistant 消息形式单独传入
# （见 app/agents/graph_qa.generate_node），模型能明确区分哪句是自己说的。
USER_WITH_CONTEXT = (
    "【学生问题】\n{question}"
    "\n\n【知识库内容（仅供参考，不是你的全部知识）】\n{context}"
)

USER_NO_CONTEXT = "【学生问题】\n{question}"

register(
    PromptSpec(
        key="qa_with_context",
        system=SYSTEM_WITH_CONTEXT,
        user_template=USER_WITH_CONTEXT,
        default_output_format="text",
        description="有知识库切片时的问答提示词",
    )
)

register(
    PromptSpec(
        key="qa_no_context",
        system=SYSTEM_NO_CONTEXT,
        user_template=USER_NO_CONTEXT,
        default_output_format="text",
        description="无知识库切片（含寒暄）时的问答提示词",
    )
)
