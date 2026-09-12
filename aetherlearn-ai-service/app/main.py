import logging
import os
import threading
from contextlib import asynccontextmanager
from pathlib import Path

# 手动启动（PyCharm/IDEA/命令行）时也默认复用服务目录下的模型缓存。
_MODELS_DIR = Path(__file__).resolve().parents[1] / "models" / "huggingface"
os.environ.setdefault("HF_HOME", str(_MODELS_DIR))
# 本地已有模型缓存时默认离线：否则 transformers 会对 HuggingFace 逐个文件发元数据请求，
# 在国内网络下每次都要等到超时，首次请求可能拖到 5 分钟以上并触发 Java 侧读超时。
# 需要重新下载模型时显式设置 HF_HUB_OFFLINE=0 即可。
if _MODELS_DIR.exists() and "HF_HUB_OFFLINE" not in os.environ:
    os.environ["HF_HUB_OFFLINE"] = "1"

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.chat import router as chat_router
from app.api.embeddings import router as embeddings_router
from app.api.knowledge import router as knowledge_router
from app.api.retrieval import router as retrieval_router
from app.api.rerank import router as rerank_router
from app.config import get_settings
from app.llm.redact import install_logging_redaction
from app.services.registry import (
    get_embedding_service,
    get_milvus_service,
    get_reranker_service,
)
from app.utils.native_runtime import preload_native_runtime


settings = get_settings()
logging.basicConfig(
    level=getattr(logging, settings.LOG_LEVEL.upper(), logging.INFO),
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
# 日志脱敏：LLM 的 API Key 会经请求体进入本服务，禁止落盘。
install_logging_redaction()
logger = logging.getLogger(__name__)


def _warm_up_models() -> None:
    """后台预热 Embedding / Reranker。

    后台线程加载模型，避免第一个真实问题额外承担 10-20s 的模型初始化；
    预热失败只记录日志，首次请求仍会自行加载（或返回 503 让 Java 走 BM25 降级）。
    """
    try:
        preload_native_runtime()
        embedding_service.embed_query("预热")
        logger.info("Embedding 预热完成")
        if settings.RERANKER_ENABLED:
            get_reranker_service().rerank("预热", ["预热文本"], top_k=1)
            logger.info("Reranker 预热完成")
    except Exception as exc:  # pragma: no cover - 预热失败不影响服务可用
        logger.warning("模型预热失败，首次请求会自行加载：%s", exc)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """启动时不连接外部服务；Embedding、Reranker 和 Milvus 均在首次使用时加载。"""
    # 打印模型缓存路径，便于排查"找不到模型导致首次请求超时"的问题。
    logger.info(
        "AetherLearn AI 服务进程已启动（延迟加载）；HF_HOME=%s, HF_HUB_OFFLINE=%s, COLLECTION=%s",
        os.environ.get("HF_HOME"),
        os.environ.get("HF_HUB_OFFLINE"),
        settings.COLLECTION_NAME,
    )
    if settings.WARMUP_ON_STARTUP:
        threading.Thread(target=_warm_up_models, name="model-warmup", daemon=True).start()
    yield
    milvus_service.close()


app = FastAPI(
    title="AetherLearn AI 服务",
    version="1.3.0",
    description="知识库向量化、Milvus 混合检索与重排序服务",
    lifespan=lifespan,
)
# 统一走注册表：预热加载的模型与各 router 实际使用的是同一批实例，
# 否则预热等于白做，第一次真实请求还会再加载一份（每个约 2.2 GB）。
milvus_service = get_milvus_service()
embedding_service = get_embedding_service()


@app.middleware("http")
async def request_size_middleware(request: Request, call_next):
    """限制 JSON 请求体大小，避免异常大请求拖垮模型推理。"""
    content_length = request.headers.get("content-length")
    if content_length:
        try:
            if int(content_length) > 1024 * 1024:
                return JSONResponse(
                    status_code=413,
                    content={"detail": "Request body too large"},
                )
        except ValueError:
            pass
    return await call_next(request)


@app.middleware("http")
async def service_api_key_middleware(request: Request, call_next):
    """生产环境通过 X-API-Key 限制 AI 服务被外部滥用。"""
    expected = settings.SERVICE_API_KEY
    # 健康检查与接口文档不要求内部密钥，便于启动脚本和监控探针访问。
    if expected and request.url.path not in {
        "/health/live",
        "/health/ready",
        "/docs",
        "/redoc",
        "/openapi.json",
    }:
        if request.headers.get("X-API-Key") != expected:
            return JSONResponse(status_code=401, content={"detail": "缺少或错误的 X-API-Key"})
    return await call_next(request)


@app.get("/health/live", tags=["Health"])
async def live():
    """存活检查，不依赖 Milvus、模型或网络。"""
    return {"status": "alive"}


@app.get("/health/ready", tags=["Health"])
async def ready():
    """就绪检查：Milvus 可用且集合完整即视为就绪；模型仍按请求延迟加载。

    集合缺失或 Milvus 不可用时返回 503，让启动脚本给出明确告警；
    Java 端仍会在检索失败时自动回退到 MySQL FULLTEXT + BM25。
    """
    stats = milvus_service.get_collection_stats()
    embedding = embedding_service.get_model_info()
    if not stats.get("indexed"):
        return JSONResponse(
            status_code=503,
            content={
                "status": "degraded",
                "service": "aetherlearn-ai-service",
                "detail": "Milvus 或集合尚未就绪，检索将回退到 Java BM25",
                "milvus": stats,
                "embedding": embedding,
            },
        )
    return {
        "status": "ok",
        "service": "aetherlearn-ai-service",
        "milvus": stats,
        "embedding": embedding,
    }


app.include_router(embeddings_router)
app.include_router(retrieval_router)
app.include_router(rerank_router)
app.include_router(knowledge_router)
app.include_router(chat_router)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """请求体校验失败时只回错误位置，不回显原值。

    FastAPI 默认会把 `input`（请求体原值）放进 422 响应体，
    而 LLM 的 API Key 现在会经请求体传入，直接回显等于把密钥写进响应与日志。
    """
    errors = [
        {
            "loc": list(error.get("loc", [])),
            "msg": error.get("msg", ""),
            "type": error.get("type", ""),
        }
        for error in exc.errors()
    ]
    return JSONResponse(status_code=422, content={"detail": errors})
