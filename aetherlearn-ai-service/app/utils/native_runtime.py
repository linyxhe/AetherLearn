"""本地原生运行时预加载工具。

Windows 上 pymilvus（grpcio/protobuf 原生 DLL）与 sentencepiece 原生模块
不能在同一进程里安全共存：先加载 grpc 再加载 sentencepiece，或反之，
都会在导入阶段触发 access violation（进程直接退出，无 Python 异常栈）。

本模块负责在连接 Milvus 之前先把 torch / transformers 加载好，统一 DLL 顺序；
分词器统一使用 `use_fast=True` 的 tokenizers(Rust) 实现，彻底不加载 sentencepiece。
预加载失败只记录日志，服务仍可启动并让 Java 走 BM25 降级。
"""

import logging
import threading


logger = logging.getLogger(__name__)

_lock = threading.Lock()
_loaded = False


def preload_native_runtime() -> bool:
    """在导入 pymilvus 之前预加载 torch / transformers。

    :return: True 表示预加载成功；False 表示依赖缺失或加载失败。
    """
    global _loaded
    if _loaded:
        return True
    with _lock:
        if _loaded:
            return True
        try:
            import torch  # noqa: F401
            from transformers import AutoModel, AutoTokenizer  # noqa: F401

            _loaded = True
            logger.info("原生运行时预加载完成：torch=%s", torch.__version__)
        except Exception as exc:  # pragma: no cover - 依赖缺失时走降级
            logger.warning("原生运行时预加载失败，Embedding 可能不可用：%s", exc)
    return _loaded
