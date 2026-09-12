"""LangGraph 图定义。

- `graph_qa`：问答图（retrieve → build_context → generate），承载整轮 RAG；
- `graph_task`：通用单节点任务图，供批改 / 出题 / 报告 / 建议 / 连接测试复用。

本包在 import 期**不得**引入 pymilvus / torch；需要向量检索的节点在函数内部延迟导入，
以保持 native_runtime 的 DLL 加载顺序约定。
"""
