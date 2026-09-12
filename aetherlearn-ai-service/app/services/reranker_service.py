import logging
import time
from pathlib import Path
from typing import List
import threading

from app.config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class RerankerService:
    """BGE-Reranker-v2-m3 重排序服务（Cross-Encoder）。"""

    def __init__(self):
        """保存配置并延迟加载模型，避免服务启动阶段占用大模型内存。"""
        self.enabled = settings.RERANKER_ENABLED
        self.use_onnx = self.enabled and settings.RERANKER_USE_ONNX
        self.device = self._resolve_device()
        self.model = None
        self.tokenizer = None
        self.onnx_session = None
        self._load_lock = threading.Lock()
        self.model_name = settings.RERANKER_MODEL
        # 记录最近一次加载失败的时间和原因，冷却期内直接快速失败。
        self._load_failed_at = 0.0
        self._load_error = None

    def _resolve_device(self):
        """CUDA 不可用时回退 CPU，避免模型初始化直接失败。"""
        import torch

        requested = settings.RERANKER_DEVICE.lower()
        if requested.startswith("cuda") and torch.cuda.is_available():
            return torch.device("cuda")
        if requested.startswith("cuda"):
            logger.warning("CUDA 不可用，Reranker 自动回退到 CPU")
        return torch.device("cpu")

    def _ensure_model(self):
        """首次重排序请求时加载模型，后续请求复用同一实例。

        加载失败时缓存失败状态一段时间，避免每个检索请求都重新尝试下载/初始化：
        HuggingFace 的重试会把请求拖到 Java 侧读超时，导致整条混合检索被误判为不可用。
        """
        if not self.enabled:
            return
        if self._load_error is not None:
            cooldown = max(1, int(settings.RERANKER_RETRY_INTERVAL))
            if time.time() - self._load_failed_at < cooldown:
                raise RuntimeError(f"重排序模型暂不可用：{self._load_error}")
        # 防止多个并发请求重复下载或初始化重排序模型。
        with self._load_lock:
            if self.tokenizer is not None or self.onnx_session is not None:
                return
            try:
                if self.use_onnx:
                    self._init_onnx_model()
                else:
                    self._init_torch_model()
                self._load_error = None
            except Exception as exc:
                self._load_failed_at = time.time()
                self._load_error = str(exc)
                raise

    def _init_onnx_model(self):
        """加载 ONNX 重排序模型；路径不存在时回退 PyTorch。"""
        import onnxruntime as ort
        from transformers import AutoTokenizer

        onnx_path = Path(settings.RERANKER_ONNX_PATH)
        if not onnx_path.exists():
            logger.warning("ONNX 模型路径不存在 (%s)，回退到 PyTorch 模型", onnx_path)
            self._init_torch_model()
            return
        try:
            providers = (
                ["CUDAExecutionProvider", "CPUExecutionProvider"]
                if self.device.type == "cuda"
                else ["CPUExecutionProvider"]
            )
            self.onnx_session = ort.InferenceSession(str(onnx_path), providers=providers)
            # use_fast=True：走 tokenizers(Rust)，不加载 sentencepiece（与 pymilvus 同进程会 access violation）。
            self.tokenizer = AutoTokenizer.from_pretrained(settings.RERANKER_MODEL, use_fast=True)
            self.model_name = settings.RERANKER_MODEL + " (ONNX)"
            logger.info("Loaded BGE-Reranker-v2-m3 ONNX model")
        except Exception as exc:
            logger.warning("ONNX 模型加载失败 (%s)，回退到 PyTorch 模型", exc)
            self.onnx_session = None
            self._init_torch_model()

    def _init_torch_model(self):
        """初始化 PyTorch 版本模型。"""
        import torch
        from transformers import AutoModelForSequenceClassification, AutoTokenizer

        self.tokenizer = AutoTokenizer.from_pretrained(settings.RERANKER_MODEL, use_fast=True)
        self.model = AutoModelForSequenceClassification.from_pretrained(settings.RERANKER_MODEL)
        self.model.to(self.device)
        self.model.eval()
        self.model_name = settings.RERANKER_MODEL + " (PyTorch)"
        if settings.RERANKER_QUANTIZE and self.device.type == "cpu":
            self._quantize_dynamic()
            self.model_name += " +int8"
        logger.info("Loaded BGE-Reranker-v2-m3 PyTorch model on %s", self.device)

    def _quantize_dynamic(self):
        """对线性层做 int8 动态量化（仅 CPU）。

        CPU 上 Cross-Encoder 是算力瓶颈（实测 12 条候选约 10s），
        动态量化把权重压到 int8、激活值运行时量化，通常能换来明显加速；
        代价是分数会有极小漂移，因此默认关闭，由 RERANKER_QUANTIZE 控制。
        """
        import torch

        try:
            from torch.ao.quantization import quantize_dynamic
        except ImportError:  # pragma: no cover - 兼容旧版 torch
            from torch.quantization import quantize_dynamic

        self.model = quantize_dynamic(self.model, {torch.nn.Linear}, dtype=torch.qint8)
        self.model.eval()
        logger.info("Reranker 已启用 int8 动态量化")

    def _score_batch(self, query: str, batch_documents: List[str]):
        """对一批 [query, document] 打分，返回 numpy 一维数组。"""
        import numpy as np
        import torch

        pairs = [[query, document] for document in batch_documents]
        inputs = self.tokenizer(
            pairs,
            padding=True,
            truncation=True,
            max_length=settings.RERANKER_MAX_LENGTH,
            return_tensors="pt",
        )

        if self.use_onnx and self.onnx_session is not None:
            ort_inputs = {
                item.name: inputs[item.name].numpy()
                for item in self.onnx_session.get_inputs()
                if item.name in inputs
            }
            logits = self.onnx_session.run(None, ort_inputs)[0]
        else:
            inputs = {key: value.to(self.device) for key, value in inputs.items()}
            # inference_mode 比 no_grad 更快（不再记录 autograd 版本计数），推理场景只该用它。
            with torch.inference_mode():
                logits = self.model(**inputs).logits.cpu().numpy()

        return 1.0 / (1.0 + np.exp(-np.clip(np.asarray(logits).reshape(-1), -35.0, 35.0)))

    def rerank(
        self,
        query: str,
        documents: List[str],
        top_k: int = 10,
        return_documents: bool = False,
    ) -> List[dict]:
        """按 [query, document] 对候选切片批量打分，并返回原始索引与相关性分数。"""
        if not documents or not self.enabled:
            return []
        self._ensure_model()
        if self.tokenizer is None and self.onnx_session is None:
            raise RuntimeError("重排序模型尚未加载")

        import numpy as np  # noqa: F401 - 保持与本方法内部使用一致的导入位置

        batch_size = max(1, int(settings.RERANKER_BATCH_SIZE))
        # 按字符长度排序后再分批：一批要 padding 到批内最长，长度接近的候选放一起
        # 能少算不少填充 token（实测候选 p25=242 / p50=263 / max=316 token）。
        # 分数与批内成员无关，排序不影响结果，只是省算力。
        order = sorted(range(len(documents)), key=lambda index: len(documents[index]))

        ranked_items = []
        for offset in range(0, len(order), batch_size):
            batch_indices = order[offset:offset + batch_size]
            scores = self._score_batch(query, [documents[index] for index in batch_indices])
            for position, score in enumerate(scores):
                ranked_items.append((batch_indices[position], float(score)))

        ranked_items.sort(key=lambda item: item[1], reverse=True)
        limit = min(max(int(top_k), 1), len(ranked_items))
        results = []
        for index, score in ranked_items[:limit]:
            item = {"index": index, "score": score}
            if return_documents:
                item["document"] = documents[index]
            results.append(item)
        return results
