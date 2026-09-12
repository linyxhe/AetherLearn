"""管理端连接测试提示词（逐字迁移自 Java 侧 LlmClient.testConnection）。"""

from app.prompts.registry import PromptSpec, register

register(
    PromptSpec(
        key="test_connection",
        system="你是连接测试助手。",
        user_template="只回复：连接成功",
        default_output_format="text",
        description="管理端测试模型配置是否可用",
    )
)
