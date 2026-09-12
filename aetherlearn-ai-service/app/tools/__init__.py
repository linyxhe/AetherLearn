"""课程级工具集（ReAct 的动作空间）。

设计要点：
1. **工具按请求构造**：闭包捕获 `course_id` 与学生学情快照，模型既看不到也改不了它们。
   越权参数不是靠校验拦住的，而是根本不暴露——这比"多写一段参数校验"更可靠。
2. **工具不放进图状态**：`QaState`/`AgentState` 只放可 JSON 序列化的数据（既有约定），
   工具实例按请求经 `config["configurable"]["tools"]` 传入，与 LLM 参数同一套机制。
3. **工具失败必须变成可读结果**：工具抛异常会打断整轮问答，因此工具内部自己兜底，
   把失败原因作为观察结果交回模型，由模型决定"换关键词再试"或"如实说明没查到"。
"""

from __future__ import annotations

from typing import Any, Callable, Dict, List, Optional

from langchain_core.tools import BaseTool

from app.tools.knowledge import build_search_knowledge_tool
from app.tools.progress import build_learning_progress_tool


def build_course_tools(
    course_id: Optional[int],
    student_context: Optional[Dict[str, Any]] = None,
    on_hits: Optional[Callable[[List[dict]], None]] = None,
) -> Dict[str, BaseTool]:
    """按请求组装可用工具表（name → tool）。

    - 没有 `course_id`（例如寒暄轮）不给检索工具；
    - 没有学情快照不给学情工具。
    工具表为空时，agent 会退化成"一次纯生成"，与改造前的行为一致。
    """
    tools: List[BaseTool] = []
    if course_id is not None:
        tools.append(build_search_knowledge_tool(course_id, on_hits=on_hits))
    if student_context:
        tools.append(build_learning_progress_tool(student_context))
    return {item.name: item for item in tools}


__all__ = ["build_course_tools", "build_search_knowledge_tool", "build_learning_progress_tool"]
