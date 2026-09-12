"""LangGraph 图的状态定义。

设计约定：
- 状态只放可 JSON 序列化的数据，方便将来接 checkpointer；
- **不放模型对象、不放 api_key**——LLM 参数走 `config["configurable"]["llm"]`，
  避免密钥落进 checkpoint 或日志。
"""

from __future__ import annotations

import operator
from typing import Annotated, Any, Dict, List, Optional, TypedDict

from langgraph.graph.message import add_messages


class Chunk(TypedDict, total=False):
    """一条检索命中的切片。"""

    chunk_id: Optional[int]
    doc_id: int
    seq: int
    content: str
    score: float


class AgentState(TypedDict, total=False):
    """ReAct 智能助教图状态。

    约定同 QaState：只放可 JSON 序列化的数据。
    **工具实例与 LLM 参数都走 `config["configurable"]`**，不进状态——
    否则将来启用 checkpointer 时会把工具对象与密钥写进 checkpoint。
    """

    # messages 用 add_messages 归并：循环里每轮的 AI/Tool 消息都要累积而不是覆盖
    messages: Annotated[List[Any], add_messages]
    # 已执行的工具轮数（用于轮数上限；每轮由 tools 节点自增）
    tool_rounds: int
    # 中间步骤事件（工具调用/返回），仅供可观测性与流式展示，累积保留
    steps: Annotated[List[Dict[str, Any]], operator.add]


class QaState(TypedDict, total=False):
    """问答图状态：Java 传入输入，节点写入输出。"""

    # ---- 输入（来自 Java，检索参数只由 Java 提供，Python 不另立默认值）----
    question: str
    # 多轮历史：[{"role": "user"|"assistant", "content": "..."}]，时间正序。
    # 用纯 dict 而不是 LangChain 消息对象，保持"状态只放可 JSON 序列化的数据"这一约定。
    history: List[Dict[str, str]]
    course_id: Optional[int]
    top_k: int
    rerank_top_k: int
    context_budget: int
    small_talk: bool
    # 预留：将来若启用 checkpointer，用 (user_id, course_id) 作 thread_id
    thread_id: Optional[str]

    # ---- retrieve 节点写入 ----
    chunks: List[Chunk]
    retrieval_ms: int
    retrieval_strategy: str
    retrieval_status: str
    retrieval_fallback_reason: Optional[str]

    # ---- build_context 节点写入 ----
    context: str
    sources: List[Chunk]
    system_prompt: str
    user_prompt: str
    prompt_key: str

    # ---- generate 节点写入 ----
    answer: str
    llm_ms: int
    error: Optional[str]


class TaskState(TypedDict, total=False):
    """通用单节点任务图状态：批改 / 出题 / 报告 / 建议 / 连接测试。"""

    prompt_key: str
    variables: Dict[str, Any]
    output_format: str  # text | json_object | json_array
    # 逃生舱口：直接给定提示词时覆盖注册表（线上应急用）
    system_prompt: Optional[str]
    user_prompt: Optional[str]

    raw: str
    data: Any
    llm_ms: int
    error: Optional[str]
