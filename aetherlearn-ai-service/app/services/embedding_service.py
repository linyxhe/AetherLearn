from typing import List
import threading

import logging

from app.config import get_settings


logger = logging.getLogger(__name__)
settings = get_settings()


class EmbeddingService:
    """BGE-M3 稠密向量化服务。

    直接使用 transformers 的 AutoTokenizer + AutoModel，按官方 BGE-M3 稠密检索方式
    （CLS 位置 + L2 归一化）编码。相比 FlagEmbedding 的 BGEM3FlagModel：
    - 不再触发 Windows 上 DataLoader(num_workers=4) 的 multiprocessing spawn 限制；
    - 少依赖 datasets / accelerate / sentence-transformers 等仅训练期需要的包；
    - 批处理由本服务控制，便于按 CPU/GPU 调整 batch_size。
    稀疏向量仍由 Milvus 的 BM25 函数从 content 生成，这里只负责稠密向量。
    """

    def __init__(self):
        """延迟加载模型，避免服务启动时阻塞。"""
        self.model = None
        self.tokenizer = None
        self.device = None
        self._load_lock = threading.Lock()

    def _resolve_device(self):
        """CUDA 不可用时回退 CPU，避免模型初始化直接失败。"""
        import torch

        requested = (settings.EMBEDDING_DEVICE or "cpu").lower()
        if requested.startswith("cuda") and torch.cuda.is_available():
            return torch.device("cuda")
        if requested.startswith("cuda"):
            logger.warning("CUDA 不可用，Embedding 自动回退到 CPU")
        return torch.device("cpu")

    def _ensure_model(self):
        """首次调用时加载 BGE-M3，后续请求复用同一实例。"""
        if self.model is not None:
            return
        # 模型体积较大，必须保证并发请求只加载一次。
        with self._load_lock:
            if self.model is not None:
                return

            import torch
            from transformers import AutoModel, AutoTokenizer

            self.device = self._resolve_device()
            dtype = (
                torch.float16
                if settings.EMBEDDING_USE_FP16 and self.device.type == "cuda"
                else torch.float32
            )
            logger.info(
                "Loading BGE-M3 model: %s on %s (dtype=%s)",
                settings.EMBEDDING_MODEL,
                self.device,
                dtype,
            )
            # use_fast=True 走 tokenizers(Rust) 的 XLMRobertaTokenizerFast，
            # 不加载 sentencepiece 原生模块（Windows 上与 pymilvus/grpcio 同时存在会 access violation）。
            self.tokenizer = AutoTokenizer.from_pretrained(settings.EMBEDDING_MODEL, use_fast=True)
            self.model = AutoModel.from_pretrained(settings.EMBEDDING_MODEL, torch_dtype=dtype)
            self.model.to(self.device)
            self.model.eval()
            logger.info("BGE-M3 model loaded successfully. Device: %s", self.device)

    def _encode(self, texts: List[str]) -> List[List[float]]:
        """按批次编码文本，返回 L2 归一化后的 CLS 稠密向量。"""
        import torch

        self._ensure_model()
        if not texts:
            return []

        batch_size = max(1, settings.EMBEDDING_BATCH_SIZE)
        vectors: List[List[float]] = []
        for start in range(0, len(texts), batch_size):
            batch = texts[start : start + batch_size]
            inputs = self.tokenizer(
                batch,
                padding=True,
                truncation=True,
                max_length=settings.EMBEDDING_MAX_LENGTH,
                return_tensors="pt",
            )
            inputs = {name: value.to(self.device) for name, value in inputs.items()}
            with torch.no_grad():
                outputs = self.model(**inputs)
            # BGE-M3 稠密向量 = 最后一层 CLS 隐状态做 L2 归一化，与官方 dense_vecs 一致。
            dense = outputs.last_hidden_state[:, 0]
            dense = torch.nn.functional.normalize(dense, dim=-1)
            vectors.extend(dense.float().cpu().numpy().tolist())
        return vectors

    def embed_documents(self, texts: List[str]) -> List[List[float]]:
        """文档向量化：用于知识库入库。"""
        return self._encode(texts)

    def embed_query(self, query: str) -> List[float]:
        """查询向量化：与文档向量共享同一编码空间。"""
        vectors = self._encode([query])
        return vectors[0]

    def batch_embed_documents(self, texts_batch: List[List[str]]) -> List[List[List[float]]]:
        """按批次文档向量化，便于未来流式上传或分片处理。"""
        return [self.embed_documents(texts) for texts in texts_batch]

    def get_model_info(self) -> dict:
        """返回模型元信息，用于健康检查。"""
        return {
            "model": settings.EMBEDDING_MODEL,
            "dim": settings.EMBEDDING_DIM,
            "device": str(self.device) if self.device is not None else settings.EMBEDDING_DEVICE,
            "status": "loaded" if self.model is not None else "starting",
            "pooling": "cls+l2_normalize",
        }
