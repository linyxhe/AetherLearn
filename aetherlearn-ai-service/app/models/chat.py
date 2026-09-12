"""对话/编排相关的请求与响应模型。"""

from typing import Any, Dict, List, Literal, Optional

from pydantic import BaseModel, Field

from app.llm.config import LlmOverrides


class ChatTurn(BaseModel):
    """一轮对话消息，按 OpenAI messages 语义表达。

    为什么不再用"Java 拼好的一段文本"：那样模型只能从 `学生：/助教：` 的格式去猜
    哪句是自己说的，而真正的多轮消息能明确区分角色，也不需要靠约定文本格式。
    """

    role: Literal["user", "assistant"] = Field(..., description="发言方：user=学生，assistant=助教")
    # 单轮上限：历史里塞进超长文本会挤掉知识上下文，这里只做防御性上限
    content: str = Field(..., min_length=1, max_length=8000, description="该轮内容")


class TestConnectionRequest(BaseModel):
    """管理端连接测试请求。"""

    llm: Optional[LlmOverrides] = Field(default=None, description="可选的管理端 LLM 配置覆盖")


class TestConnectionResponse(BaseModel):
    """连接测试响应。"""

    response: str = Field(..., description="模型返回文本")
    provider: str = Field("python", description="恒为 python；由 Java 侧决定是否使用")
    model: str = Field("", description="实际使用的模型")
    key_fp: str = Field("", description="API Key 指纹，便于确认用的是哪把密钥")
    llm_ms: int = Field(0, description="调用耗时(ms)")


class ChatHealthResponse(BaseModel):
    """编排层自检响应（不调用模型，不消耗 token）。"""

    status: str = Field(..., description="ok / degraded")
    llm_configured: bool = Field(..., description="当前进程是否具备可用的 LLM 配置")
    model: str = Field("", description="当前默认模型")
    base_url: str = Field("", description="当前默认接口地址")
    providers: List[str] = Field(default_factory=list, description="已注册的提示词 key")
    graph_ready: bool = Field(False, description="问答图是否已编译")


class ChunkPayload(BaseModel):
    """Java 传入的检索切片（含文本，供图内拼上下文）。"""

    chunk_id: Optional[int] = Field(default=None, description="MySQL 切片 ID")
    doc_id: Optional[int] = Field(default=None, description="文档 ID")
    seq: Optional[int] = Field(default=None, description="切片序号")
    content: str = Field(..., description="切片文本")
    score: Optional[float] = Field(default=None, description="融合/精排得分")


class QaChatRequest(BaseModel):
    """问答编排请求（Java 传入，检索参数只由 Java 决定）。"""

    question: str = Field(..., min_length=1, max_length=2000, description="学生问题")
    # 多轮历史按 role 交替传入（时间正序，最后一条是上一轮的助教回答）
    history: List[ChatTurn] = Field(default_factory=list, description="最近几轮对话，时间正序")
    course_id: Optional[int] = Field(default=None, description="课程 ID（检索隔离维度）")
    chunks: List[ChunkPayload] = Field(default_factory=list, description="Java 已检索到的切片；为空时由本服务兜底检索")
    top_k: int = Field(30, ge=1, le=100, description="兜底检索的召回数")
    rerank_top_k: int = Field(10, ge=1, le=50, description="兜底检索重排序保留数")
    context_budget: int = Field(2000, ge=200, le=20000, description="上下文总字符预算")
    small_talk: bool = Field(False, description="寒暄/元问题：跳过检索")
    timeout_seconds: Optional[float] = Field(default=None, description="本次调用预算（秒）")
    llm: Optional[LlmOverrides] = Field(default=None, description="管理端 LLM 配置覆盖")
    # ---- ReAct 智能助教（agent 路径）----
    agent: bool = Field(False, description="启用 ReAct 工具调用循环；关闭时走固定流程（改造前的行为）")
    max_tool_rounds: int = Field(3, ge=0, le=6, description="工具调用轮数上限，超过后强制收敛")
    student_context: Optional[Dict[str, Any]] = Field(
        default=None,
        description="学生学情快照（由 Java 预取，供 get_learning_progress 工具使用）",
    )


class QaChatResponse(BaseModel):
    """问答编排响应（同步）。"""

    answer: str = Field("", description="模型回答")
    llm_ms: int = Field(0, description="LLM 阶段耗时(ms)")
    retrieval_ms: int = Field(0, description="检索阶段耗时(ms)")
    retrieval_strategy: str = Field("none", description="hybrid / bm25 / none")
    retrieval_status: str = Field("skipped", description="ok / degraded / skipped")
    fallback_reason: Optional[str] = Field(default=None, description="降级原因")
    provider: str = Field("python", description="实际提供方")
    sources: List[ChunkPayload] = Field(default_factory=list, description="本轮使用的切片（供 Java 解析来源展示）")
    meta: dict = Field(default_factory=dict, description="附加指标")


class TaskPromptRequest(BaseModel):
    """通用任务请求（批改 / 出题 / 报告 / 建议共用，靠 prompt_key 区分）。"""

    prompt_key: str = Field(..., min_length=1, max_length=64, description="提示词注册表中的 key")
    variables: dict = Field(default_factory=dict, description="提示词变量")
    output_format: str = Field("text", description="text / json_object / json_array")
    timeout_seconds: Optional[float] = Field(default=None, description="本次调用预算（秒）")
    llm: Optional[LlmOverrides] = Field(default=None, description="管理端 LLM 配置覆盖")
    system_prompt: Optional[str] = Field(default=None, description="逃生舱口：直接覆盖 system")
    user_prompt: Optional[str] = Field(default=None, description="逃生舱口：直接覆盖 user")


class TaskPromptResponse(BaseModel):
    """通用任务响应。"""

    raw: str = Field("", description="模型原始输出")
    data: Any = Field(default=None, description="按 output_format 解析后的结构化结果")
    llm_ms: int = Field(0, description="LLM 阶段耗时(ms)")
    parse_error: Optional[str] = Field(default=None, description="结构化解析失败原因（非异常）")
    provider: str = Field("python", description="实际提供方")
    model: str = Field("", description="实际模型")
