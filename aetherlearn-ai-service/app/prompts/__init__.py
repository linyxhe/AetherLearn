"""提示词注册表。

各能力模块（qa / agent / grade / question / report / advice / connection）在 import 时把自己
注册进 registry；新增 LLM 能力 = 新增一个 PromptSpec，无需改动图或端点。
"""

from app.prompts import advice  # noqa: F401  注册教学建议提示词
from app.prompts import agent  # noqa: F401  注册 ReAct 智能助教提示词
from app.prompts import connection  # noqa: F401  注册连接测试提示词
from app.prompts import grade  # noqa: F401  注册主观题批改提示词
from app.prompts import qa  # noqa: F401  注册问答提示词
from app.prompts import question  # noqa: F401  注册智能出题提示词
from app.prompts import report  # noqa: F401  注册学习报告提示词
from app.prompts.registry import PromptSpec, all_prompts, get_prompt, register  # noqa: F401
