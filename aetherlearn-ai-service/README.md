# AetherLearn AI 服务

> FastAPI 独立服务：负责 BGE-M3 向量化、Milvus 混合检索、RRF 融合、BGE-Reranker-v2-m3 重排序，并为 Spring Boot 后端提供 AI API。

## 架构

```text
Spring Boot
  │
  └── RestTemplate
        │
        ▼
FastAPI AI Service
  ├── BGE-M3：文档/查询向量化
  ├── Milvus：稠密向量 + BM25 稀疏向量混合检索
  ├── RRFRanker(k=60)：召回融合
  └── BGE-Reranker-v2-m3：Cross-Encoder 精排
```

Java 端保留 MySQL FULLTEXT + BM25 兜底：当 Python 服务不可用、向量检索无结果或重排序失败时，继续使用本地检索。

## 目录

```text
aetherlearn-ai-service/
├── app/
│   ├── main.py                 # FastAPI 入口与健康检查
│   ├── config.py               # Pydantic Settings 配置
│   ├── models/                 # Pydantic 请求/响应模型
│   ├── services/               # Embedding / Reranker / Milvus
│   └── api/                    # REST API
├── scripts/
│   ├── init_milvus.py          # Milvus 集合初始化与结构输出
│   └── backfill_milvus.py      # 历史切片向量补全（支持 dry-run / force）
├── tests/                      # 单元测试
├── models/                     # 本地模型目录（默认不提交模型文件）
├── requirements.txt
├── Dockerfile
├── docker-compose.yml
└── .env.example
```

## 模型与检索链路

1. 文档上传后，Java 事务提交后异步调用 `/v1/knowledge/index`。
2. Python 侧使用 `BAAI/bge-m3` 生成文档向量，写入 Milvus。
3. 查询时使用 `input_type="query"` 生成查询向量。
4. Milvus 同时执行：
   - 稠密向量 HNSW + COSINE；
   - BM25 稀疏向量检索；
   - `RRFRanker(k=60)` 融合候选集。
5. 默认使用 `BAAI/bge-reranker-v2-m3` 对候选切片精排。
6. 重排序失败或无候选时保留 RRF 召回结果；Python 服务整体不可用时 Java 回退 MySQL BM25。

## 环境配置

复制模板：

```bash
cp .env.example .env
```

关键变量：

```dotenv
HOST=0.0.0.0
PORT=8000
LOG_LEVEL=INFO
WORKERS=1
SERVICE_API_KEY=change-this-internal-secret
MILVUS_HOST=localhost
MILVUS_PORT=19530
MILVUS_DB=aetherlearn
COLLECTION_NAME=knowledge_chunks_v2
LEGACY_COLLECTION_NAME=knowledge_chunks

EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_DIM=1024
EMBEDDING_DEVICE=cpu
RERANKER_MODEL=BAAI/bge-reranker-v2-m3
RERANKER_DEVICE=cpu
RERANKER_USE_ONNX=true

DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=aetherlearn
DB_USER=root
DB_PASSWORD=

LLM_BASE_URL=https://openrouter.ai/api/v1
LLM_API_KEY=
LLM_MODEL=nex-agi/nex-n2.5-mini:free
AI_SERVICE_API_KEY=change-this-internal-secret
```

说明：

- 默认使用 CPU，CUDA 不可用时 Embedding 与 Reranker 会自动回退 CPU。
- 有 NVIDIA GPU 时可将 `EMBEDDING_DEVICE`、`RERANKER_DEVICE` 设为 `cuda`。
- Reranker 优先尝试 ONNX；ONNX 模型不存在或加载失败时回退 PyTorch；加载失败后进入 60s 冷却（`RERANKER_RETRY_INTERVAL`），冷却期内快速跳过精排、保留 RRF 候选。
- CPU 上 Cross-Encoder 较慢，默认只精排 RRF 头部 `RERANKER_MAX_CANDIDATES=12` 条（实测 30 条约 26.6s、20 条约 17-20s、12 条约 10s）；调大更准但更慢。
- 启动后会在后台线程预热 Embedding 与 Reranker（`WARMUP_ON_STARTUP`，默认开启），首个真实问题不再承担 10-20s 冷启动；想更轻量可设为 `false`。
- 服务基于 uvicorn/h11，只支持 HTTP/1.1：客户端不要开启 HTTP/2 或 h2c 升级（Java JDK HttpClient 需显式 `version(HTTP_1_1)`），请求体请使用 UTF-8 编码。
- Windows 下连接 Milvus 前会先预加载 `torch` / `transformers`，并且分词器固定使用 `use_fast=True` 的 tokenizers(Rust) 实现：`sentencepiece` 原生模块与 `pymilvus(grpcio)` 在同一进程会触发 access violation，因此依赖里不再安装 sentencepiece。
- `LLM_API_KEY` 为空时，LLM 功能不可用，但检索链路仍可独立运行。
- 所有密钥必须通过环境变量注入，禁止提交真实密钥。
- `.env` 请用 UTF-8 编码；服务端 `SERVICE_API_KEY` 必须与 Java 端 `AI_SERVICE_API_KEY` 相同，否则检索接口返回 401，问答会回退到 Java BM25。

## 本地启动

```bash
python -m venv .venv
.venv\Scripts\activate

# Windows 建议先装 CPU 版 torch，避免拉取数 GB 的 CUDA wheel
pip install torch==2.3.0 --index-url https://download.pytorch.org/whl/cpu
pip install -r requirements.txt

# 国内网络可用 HF 镜像下载 BGE 模型；缓存默认在 models/huggingface
set HF_ENDPOINT=https://hf-mirror.com
set HF_HOME=%CD%\models\huggingface

uvicorn app.main:app --host 0.0.0.0 --port 8000
```

首次调用 Embedding 或 Reranker 时会加载模型，BGE-M3 约 2.3 GB，首次启动较慢；`torch` 与 `transformers` 都为延迟加载，模型未下载时服务仍可启动并让 Java 走 BM25 降级。

### 在 PyCharm / IDEA 里手动启动

用 IDE 起 AI 服务时不需要额外配环境变量：`app/main.py` 会自动把 `HF_HOME` 指向服务目录下的 `models/huggingface`，并在该目录已存在时把 `HF_HUB_OFFLINE` 置为 `1`（避免 HuggingFace 元数据请求在无外网环境下拖慢首次请求）。需要重新下载模型时再显式设置 `HF_HUB_OFFLINE=0`。

Run Configuration 建议：

```text
module: uvicorn
parameters: app.main:app --host 127.0.0.1 --port 8000 --workers 1
working directory: aetherlearn-ai-service
environment variables: PYTHONUNBUFFERED=1
env file: aetherlearn-ai-service/.env
```

注意：Java 侧从 IDEA 启动时不会自动拿到 `SERVICE_API_KEY`，需要任选一种方式对齐，否则检索接口返回 401、问答降级为 BM25：

1. IDEA Run Configuration 里加环境变量 `AI_SERVICE_API_KEY`（值与 `.env` 的 `SERVICE_API_KEY` 相同）；
2. 或写进 gitignored 的 `AetherLearn_backend/src/main/resources/application-local.yml`：

```yaml
ai:
  python-service:
    api-key: <与 ai-service/.env 的 SERVICE_API_KEY 一致>
```

3. 或本地开发时把 `.env` 的 `SERVICE_API_KEY` 置空（服务只监听 127.0.0.1，仅限本机调试）。BGE-M3 稠密向量直接使用 `transformers` 的 `AutoModel` 按 CLS + L2 归一化编码，不依赖 FlagEmbedding 的 DataLoader。

## Docker Compose 启动

默认只启动基础设施（AI 服务在宿主机运行）：

```bash
docker compose up -d milvus minio
```

默认启动：

- Milvus Standalone：`19530`
- MinIO：`9000` / `9001`
- Ollama：`11434`（可选，默认注释）

需要容器化 AI 服务时使用可选 profile（首次会下载 BGE 模型，较慢）：

```bash
docker compose --profile ai up -d ai-service
```

AI 服务由项目根目录的 `scripts/start-dev.ps1` / `scripts/start-prod.ps1` 在宿主机启动（端口 `8000`）。Dockerfile 已支持无本地 `models/` 目录构建，模型缓存挂载到 `ai_models` 卷。

健康检查：

- AI 服务存活：`http://localhost:8000/health/live`
- AI 服务就绪：`http://localhost:8000/health/ready`
- AI 文档：`http://localhost:8000/docs`
- Milvus 健康检查：`http://localhost:9091/healthz`
- MinIO 控制台：`http://localhost:9001`

## API 示例

### 批量向量化

```bash
curl -X POST http://localhost:8000/v1/embeddings/batch \
  -H "Content-Type: application/json" \
  -d '{"texts":["这是第一段文本","这是第二段文本"],"input_type":"document"}'
```

### 查询向量化

```bash
curl -X POST http://localhost:8000/v1/embeddings/query \
  -H "Content-Type: application/json" \
  -d '{"query":"如何遍历 Map 集合？"}'
```

### 混合检索

```bash
curl -X POST http://localhost:8000/v1/retrieval/hybrid \
  -H "Content-Type: application/json" \
  -d '{"course_id":1,"query":"Java 中如何遍历 Map？","top_k":30,"rerank_top_k":10,"use_reranker":true}'
```

混合检索响应包含分阶段指标：

```json
{
  "hits": [],
  "retrieval_time_ms": 40,
  "embedding_time_ms": 12,
  "milvus_time_ms": 28,
  "rerank_time_ms": 15,
  "total_candidates": 30,
  "strategy": "hybrid",
  "embedding_status": "ok",
  "milvus_status": "ok",
  "rerank_status": "ok",
  "fallback_reason": null
}
```

### 删除文档向量

```bash
curl -X POST http://localhost:8000/v1/knowledge/delete \
  -H "Content-Type: application/json" \
  -d '{"course_id":1,"doc_id":1}'
```

## 历史切片补全

```bash
# 1) 初始化新集合，查看字段、BM25 函数和索引
python scripts/init_milvus.py

# 2) 只读预览待补全切片，不写 Milvus
python scripts/backfill_milvus.py --dry-run

# 3) 实际补全：默认跳过已有 chunk_id，可重复执行
python scripts/backfill_milvus.py

# 4) 需要覆盖已有向量时使用 --force
python scripts/backfill_milvus.py --force --batch-size 32 --max-chunks 500
```

脚本只补全向量，不删除 MySQL 切片，也不修改旧集合。

## Java 对接

Java 使用 `RestTemplate` 调用 AI 服务，不再使用 WebClient：

```java
@Component
public class PythonAiClient {
    private final RestTemplate restTemplate;

    @Value("${ai.python-service.url:http://localhost:8000}")
    private String baseUrl;

    // embedDocuments / embedQuery / hybridSearch / indexChunks / deleteChunks
}
```

Java 配置示例：

```yaml
ai:
  python-service:
    enabled: true
    url: ${AI_SERVICE_URL:http://localhost:8000}
    api-key: ${AI_SERVICE_API_KEY:}
    timeout: 10s
    top-k: 30
    rerank-top-k: 10
```

## 兼容性说明

- 新集合包含显式 `chunk_id`，Java 可直接按 MySQL 切片 ID 映射。
- 旧集合若没有 `chunk_id` 且未启用动态字段，写入时不写该字段，检索时不请求该字段；Java 通过 `course_id + doc_id + seq` 回查 MySQL。
- 删除文档时先软删除 MySQL 文档，事务提交后异步清理 Milvus 向量。

## 测试

```bash
cd AetherLearn/aetherlearn-ai-service
python -m compileall -q app tests scripts
python -m pytest -q
# 未安装 torch / pymilvus 时也可验证路由导入
python -c "import app.main; print('routes ok')"
```

测试覆盖：

- 空候选时不触发重排序；
- 重排序失败保留 RRF 结果并记录降级原因；
- 重排序空结果记录 `reranker_empty_result`；
- 旧集合不写入 `chunk_id`；
- 旧集合命中不请求不存在的 `chunk_id` 字段；
- `chunk_id` 全量查询与集合就绪判定；
- `/health/live` 不依赖 Milvus，`/health/ready` 未就绪返回 503。
