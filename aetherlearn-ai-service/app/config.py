from functools import lru_cache
from pydantic import ConfigDict
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # ========== 服务基础配置 ==========
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    LOG_LEVEL: str = "INFO"
    WORKERS: int = 1
    # 内网服务调用密钥；生产环境必须通过环境变量设置，空值仅用于本地开发。
    SERVICE_API_KEY: str = ""

    # ========== Milvus 配置 ==========
    MILVUS_HOST: str = "localhost"
    MILVUS_PORT: int = 19530
    MILVUS_USER: str = ""
    MILVUS_PASSWORD: str = ""
    MILVUS_DB: str = "aetherlearn"
    # 新集合是默认写入目标；旧集合保留给历史数据，不自动改写其 schema。
    COLLECTION_NAME: str = "knowledge_chunks_v2"
    LEGACY_COLLECTION_NAME: str = "knowledge_chunks"
    COLLECTION_COMPATIBILITY: str = "auto"
    MILVUS_CONNECT_TIMEOUT: float = 10.0

    # ========== Embedding (BGE-M3) 配置 ==========
    EMBEDDING_MODEL: str = "BAAI/bge-m3"
    EMBEDDING_DIM: int = 1024
    EMBEDDING_BATCH_SIZE: int = 32
    EMBEDDING_MAX_LENGTH: int = 8192
    EMBEDDING_DEVICE: str = "cuda"  # 或 "cpu"
    EMBEDDING_USE_FP16: bool = True
    EMBEDDING_ENABLE_SPARSE: bool = False

    # ========== Reranker (BGE-Reranker-v2-m3) 配置 ==========
    RERANKER_MODEL: str = "BAAI/bge-reranker-v2-m3"
    RERANKER_MAX_LENGTH: int = 512
    RERANKER_BATCH_SIZE: int = 16
    RERANKER_DEVICE: str = "cuda"
    RERANKER_USE_ONNX: bool = True
    RERANKER_ONNX_PATH: str = "models/reranker.onnx"
    RERANKER_ENABLED: bool = True
    # 启动后在后台线程预热 Embedding/Reranker，避免第一个问题承担模型加载耗时。
    WARMUP_ON_STARTUP: bool = True
    # CPU 上 Cross-Encoder 精排很慢；只重排 RRF 头部候选，0 表示不限制。
    # 实测 20 条约 17s、12 条约 10s，默认取 12 控制单次问答延迟。
    RERANKER_MAX_CANDIDATES: int = 12
    # 模型加载失败后的冷却时间（秒）：冷却期内直接快速失败，避免拖垮检索请求。
    RERANKER_RETRY_INTERVAL: int = 60
    # int8 动态量化：CPU 上实测有明显加速，代价是分数有极小漂移。
    # 默认关闭（优先保证排序质量），需要压延迟时再开。
    RERANKER_QUANTIZE: bool = False

    # ========== LLM 配置 (OpenAI 兼容接口) ==========
    # 默认不绑定真实密钥；请通过环境变量 LLM_API_KEY 注入。
    LLM_API_KEY: str = ""
    LLM_BASE_URL: str = "https://openrouter.ai/api/v1"
    LLM_MODEL: str = "nex-agi/nex-n2.5-mini:free"
    LLM_TEMPERATURE: float = 0.3
    LLM_MAX_TOKENS: int = 4096
    LLM_TIMEOUT: int = 120

    # ========== Java 后端回调配置 ==========
    JAVA_CALLBACK_URL: str = "http://127.0.0.1:8080"

    # ========== MySQL backfill 配置 ==========
    DB_HOST: str = "127.0.0.1"
    DB_PORT: int = 3306
    DB_NAME: str = "aetherlearn"
    DB_USER: str = "root"
    DB_PASSWORD: str = ""

    # ========== 检索默认参数 ==========
    DEFAULT_TOP_K: int = 30
    DEFAULT_RERANK_TOP_K: int = 10
    RRF_K: int = 60

    model_config = ConfigDict(
        env_file=".env",
        case_sensitive=True,
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    """缓存环境变量配置，避免每个请求重复读取。"""
    return Settings()
