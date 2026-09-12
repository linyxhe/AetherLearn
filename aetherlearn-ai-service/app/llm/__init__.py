"""LLM 基础设施层：参数合并、模型工厂、JSON 提取与日志脱敏。

本包在 import 期**不得**引入 pymilvus / torch，以保持 native_runtime 的 DLL 加载顺序约定。
"""
