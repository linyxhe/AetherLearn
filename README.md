# AetherLearn 智能教学辅助与学习分析平台

> 本科毕业设计 · Spring Boot 3 + Vue 3 全栈项目

AetherLearn 是一个面向高校教学场景的 **AI 驱动教学辅助平台**，覆盖智能问答、作业自动批改、学情数据分析等教学全流程。系统支持管理员、教师、学生三种角色，通过 RAG 知识检索与大语言模型为教师减负增效、为学生提供 7×24 个性化学习支持。

---

## 核心功能

### 🎓 管理员端
- 用户管理（增删改查、角色分配、密码重置）
- 课程管理（审核、上下架、全局配置）
- 系统配置与数据治理

### 👨‍🏫 教师端
- **课程工作台** — 章节管理、资源上传（PDF/Word/TXT/Markdown）、课程公告发布
- **知识库管理** — 文档上传 → 自动解析切片 → BGE-M3 稠密向量 + BM25 稀疏向量混合检索 → RRF 融合 → BGE-Reranker 重排序
- **作业与测验** — 客观题自动批改（单选/多选/判断/填空）、主观题 AI 批改 + 教师复核
- **智能组卷** — AI 自动出题、手动编辑、章节小测
- **数据看板** — 成绩分布、完成率趋势、知识点掌握热力图、学生排行榜
- **AI 教学建议** — 基于学情数据的个性化教学优化建议

### 👨‍🎓 学生端
- **智能问答** — 基于课程知识库的 RAG 检索问答，SSE 流式输出，附带来源引用
- **课程学习** — 章节资源浏览、PDF 在线预览、全屏沉浸阅读
- **作业作答** — 支持富文本编辑器（WangEditor）、文件/图片内嵌
- **错题本** — 作业错题 + 章节小测错题统一汇总
- **学习计划** — 个人待办与学习任务管理
- **AI 学习报告** — 自动生成个人学情分析与学习建议
- **知识点图谱** — 可视化知识关联关系

---

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| **后端框架** | Java 17 + Spring Boot 3.4.x | RESTful API，分层架构 |
| **持久层** | MyBatis-Plus 3.5.9 + MySQL 8 | 简化 CRUD，软删除 |
| **安全认证** | Spring Security + JWT | 角色权限拦截，Token 鉴权 |
| **AI 模块** | FastAPI + LangGraph + Milvus + BGE-M3 + BGE-Reranker-v2-m3 | 大模型调用与编排全部在 Python 侧；BM25 稀疏检索 + 稠密向量 RRF 融合 + Cross-Encoder 重排序；模型不可用时 Java 侧自动降级为「仅返回检索结果」 |
| **文档解析** | Apache PDFBox / Apache POI | PDF、Word 文本抽取 |
| **前端框架** | Vue 3 + Vite 5 | Composition API + `<script setup>` |
| **UI 组件** | Element Plus + Naive UI | 管理端 Element Plus，教师/学生端 Naive UI |
| **图表** | ECharts 5 | 学情数据可视化 |
| **状态管理** | Pinia | 全局状态 |
| **富文本** | WangEditor | 学生作答编辑器 |

---

## 项目结构

```text
┌─────────────────────────────────────────────┐
│ Spring Boot :8080        HTTP       Python  │
│ └─────────────┘ ─────────────→  ┌────────┐  │
│                                  │ AI服务 │  │
│                                  │ :8000  │  │
│                                  └───┬────┘  │
│                                      │       │
│                               localhost:19530 │
│                                      │       │
│    ┌───────────────────────────────▼───────┐ │
│    │  Docker Desktop                       │ │
│    │  ┌─────────┐  ┌────────┐  ┌────────┐  │ │
│    │  │ Milvus  │  │ MinIO  │  │ (etcd) │  │ │
│    │  │ :19530  │  │ :9000  │  │        │  │ │
│    │  └─────────┘  └────────┘  └────────┘  │ │
│    └───────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```
AetherLearn/
├── AetherLearn_backend/          # 后端 Spring Boot 项目
│   └── src/main/java/com/aetherlearn/
│       ├── ai/                   # AI 模块（LLM 客户端、配置）
│       ├── common/               # 通用类（异常处理、响应封装）
│       ├── config/               # 配置（安全、跨域、文件映射）
│       ├── controller/           # 控制器层（REST API）
│       ├── dto/                  # 数据传输对象
│       ├── entity/               # 实体类
│       ├── hybrid/               # 混合检索编排（向量 + BM25 + RRF）
│       ├── mapper/               # MyBatis-Plus Mapper
│       ├── service/              # 业务接口
│       │   └── impl/             # 业务实现
│       └── util/                 # 工具类
├── aetherlearn-ai-service/       # Python AI 服务（BGE-M3、Milvus、RRF、重排序）
├── AetherLearn_front/            # 前端 Vue 3 项目
│   └── src/
│       ├── components/           # 公共组件
│       ├── layout/               # 布局组件
│       ├── router/               # 路由配置
│       ├── stores/               # Pinia 状态
│       ├── utils/                # 工具函数
│       └── views/                # 页面视图
├── 数据库脚本/
│   └── AetherLearn_init.sql      # 建库脚本 + 测试数据
├── 产品PRD/                      # 产品需求文档 v2.0
└── uploads/                      # 文件上传目录（按类型分桶）
```

---

## 快速开始

### 环境要求

- **JDK 17+**
- **Maven 3.8+**
- **MySQL 8.0+**
- **Node.js 18+** + npm
- **Python 3.11**（用于本地运行 AI 服务；由启动脚本自动准备 Conda 环境）
- Docker / Docker Compose（用于启动 Milvus 与 MinIO）

### 1. 初始化数据库

```bash
# 使用 MySQL 客户端执行建库脚本
mysql -u root -p < AetherLearn/数据库脚本/AetherLearn_init.sql
```

- 库名：`aetherlearn`，字符集：`utf8mb4`
- 默认账号：`root` / `root`（可在 `application.yml` 修改）

### 2. 启动后端

```bash
cd AetherLearn/AetherLearn_backend

# 编译（首次需联网拉取依赖）
mvn -q compile

# 启动（端口 8080，context-path: /）
mvn spring-boot:run
```

> **AI 大模型配置**：大模型调用全部由 Python AI 服务（LangGraph 编排）承担，Java 侧不再直连模型。配置优先级为 **管理端「系统配置 → AI 设置」保存的 `sys_config` > `application-local.yml` 的 `ai.llm.*` > 环境变量 `LLM_*`**。未配置密钥时自动降级为「仅返回知识库检索结果」，可离线跑通核心流程。

> **Python AI 服务地址**：默认 `http://localhost:8000`，可通过 `AI_SERVICE_URL` 覆盖（容器部署可指向 `http://ai-service:8000`）；内部鉴权密钥通过 `AI_SERVICE_API_KEY` 注入，**必须与 AI 服务 `.env` 的 `SERVICE_API_KEY` 完全一致**，否则检索与问答都会返回 401。

### 3. 启动 AI 服务

推荐直接运行开发启动脚本，脚本会依次启动 Docker（Milvus、MinIO）、宿主机 Python AI 服务、Spring Boot 与 Vue：

```powershell
powershell.exe -NoProfile -File "AetherLearn/scripts/start-dev.ps1"
```

首次运行脚本会自动在 `D:\Program Files\AetherLearn\ai-env` 创建 Python 3.11 Conda 环境并安装依赖；也可设置 `AI_PYTHON` 复用已有的 Python 3.11 解释器。默认使用 CPU 模式，CUDA 不可用时 Embedding 和 Reranker 会自动回退到 CPU。

如需手动启动 AI 服务：

```bash
cd AetherLearn/aetherlearn-ai-service
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

也可以把 AI 服务放进容器（首次启动会下载 BGE 模型，较慢）：

```bash
cd AetherLearn/aetherlearn-ai-service
docker compose --profile ai up -d ai-service
```

默认 `docker compose up -d milvus minio` 仍只启动基础设施，不会强制下载大模型。

### 4. 启动前端

```bash
cd AetherLearn/AetherLearn_front

# 安装依赖
npm install

# 启动开发服务器（默认端口 5173）
npm run dev
```

前端自动代理 `/api` 和 `/uploads` 请求到后端 `localhost:8080`。

### 5. 访问系统

- 前端地址：`http://localhost:5173`
- 后端 API：`http://localhost:8080`
- AI 服务健康检查：`http://localhost:8000/health/live`、`http://localhost:8000/health/ready`
- AI 服务文档：`http://localhost:8000/docs`
- 默认管理员账号：`admin` / `123456`
- 默认教师账号：`teacher` / `123456`
- 默认学生账号：`student` / `123456`

---

## 快速验证

```bash
# Python AI 服务：语法检查 + 单元测试 + 路由导入
cd AetherLearn/aetherlearn-ai-service
python -m compileall -q app tests scripts
python -m pytest -q
python -c "import app.main; print('routes ok')"

# 初始化新集合并预览历史切片补全
python scripts/init_milvus.py
python scripts/backfill_milvus.py --dry-run
# 确认后实际补全（默认跳过已有 chunk_id，--force 覆盖）
python scripts/backfill_milvus.py

# 后端单测与打包
mvn -f AetherLearn_backend/pom.xml test
mvn -f AetherLearn_backend/pom.xml -DskipTests package

# 前端生产构建
cd AetherLearn/AetherLearn_front
npm run build:prod

# Docker Compose 校验（含可选 AI 服务 profile）
docker compose -f aetherlearn-ai-service/docker-compose.yml config
docker compose -f aetherlearn-ai-service/docker-compose.yml --profile ai config
```

---

## AI 模块设计

系统采用**分层可降级**的 AI 架构，确保在无外部 API 时仍可正常运行：

```text
┌─────────────────────────────────────────────────────────────┐
│ LLM 生成层（可降级）                                          │
│ nex-agi/nex-n2.5-mini:free / OpenAI 兼容接口 / Ollama          │
│ → 问答生成 / 主观题批改 / 智能出题                           │
├─────────────────────────────────────────────────────────────┤
│ 混合检索层（离线可用）                                        │
│ BGE-M3 稠密向量 + BM25 稀疏向量                              │
│ → Milvus 原生混合检索 → RRFRanker(k=60) 融合                  │
│ → BGE-Reranker-v2-m3 Cross-Encoder 精排                       │
│ → Java MySQL FULLTEXT + BM25 兜底                           │
├─────────────────────────────────────────────────────────────┤
│ 解析层                                                       │
│ PDFBox / POI / 纯文本                                        │
│ → 文档解析 → 段落切片 → MySQL 事务提交后异步向量化入库          │
└─────────────────────────────────────────────────────────────┘
```

- **文档入库**：MySQL 先保存原始切片；事务提交后异步调用 Python AI 服务生成 BGE-M3 稠密向量并写入 Milvus；写入失败最多重试 3 次并指数退避，重试耗尽只记录日志，不影响上传接口。
- **检索链路**：先通过 BGE-M3 查询向量召回，同时通过 Milvus BM25 函数召回；使用 RRF 融合，再使用 BGE-Reranker-v2-m3 精排。
- **降级策略**：Python AI 服务不可用、向量检索无结果或结果无法映射时，Java 端继续使用 MySQL FULLTEXT + BM25，并在响应和问答页面展示降级原因，保证问答不中断。
- **集合迁移**：默认写入新集合 `knowledge_chunks_v2`（含 `chunk_id`、稠密/稀疏向量和 BM25 函数）；旧集合 `knowledge_chunks` 保留作历史数据源，不自动改写 schema。
- **历史切片补全**：进入 `aetherlearn-ai-service` 后先运行 `python scripts/init_milvus.py`，再运行 `python scripts/backfill_milvus.py --dry-run` 预览；确认后运行 `python scripts/backfill_milvus.py`，脚本默认跳过已有 `chunk_id`，可用 `--force` 覆盖。
- **旧 Milvus 集合兼容**：没有 `chunk_id` 的旧集合不写入该字段，Java 通过 `course_id + doc_id + seq` 回查 MySQL。
- **删除一致性**：删除文档时先执行 MySQL 软删除，事务提交后异步清理 Milvus 向量。
- **检索可观测性**：接口和问答页面返回 `retrievalMs`、`embeddingMs`、`milvusMs`、`rerankMs`、`retrievalStrategy`、`fallbackReason`，便于区分混合检索、BM25 降级和模型阶段耗时。
- **Java 客户端约定**：调用 Python 服务时固定使用 HTTP/1.1（JDK HttpClient 默认的 h2c 升级会被 uvicorn 拒绝为 400），请求体统一序列化为 UTF-8 字节发送，读超时 60s（首次请求要加载 BGE-M3 与 Reranker）。
- **重排序降级**：Reranker 模型缺失或加载失败时记录 60s 冷却，期间直接跳过精排并保留 RRF 候选；CPU 上只精排 RRF 头部候选（`RERANKER_MAX_CANDIDATES=20`），避免单次问答耗时过长。
- **内部密钥一致性**：Python 服务的 `SERVICE_API_KEY` 与 Java 的 `AI_SERVICE_API_KEY` 必须相同，否则检索接口返回 401、问答会降级为 BM25（前端会显示「AI 检索服务密钥不匹配」）。启动脚本会自动读取 `aetherlearn-ai-service/.env` 的 `SERVICE_API_KEY` 注入给 Java；`.env` 请使用 UTF-8 编码，脚本已按 UTF-8 读取。
- **在 IDEA 里启动后端时**：不会自动注入上面这个密钥，需要在 Run Configuration 加环境变量 `AI_SERVICE_API_KEY`（值与 `.env` 的 `SERVICE_API_KEY` 相同），或写进 gitignored 的 `application-local.yml` 的 `ai.python-service.api-key`，否则问答必然降级为 BM25。

---

## 关键约定

- **密码加密**：MD5（Hutool `SecureUtil.md5`），初始密码 `123456`
- **文件存储**：项目根目录 `uploads/`，按 `avatar/course/knowledge/answer/export` 分桶
- **软删除**：`knowledge_doc`、`course`、`assignment` 等核心表支持 `is_deleted` 字段
- **角色体系**：`ADMIN(1)` / `TEACHER(2)` / `STUDENT(3)`，int 基本类型
- **中文注释**：所有代码均添加中文类与方法级注释
- **敏感配置**：API Key、数据库密码、JWT Secret 等必须通过环境变量注入，禁止提交到仓库

---

## 文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 数据库脚本 | `AetherLearn/数据库脚本/AetherLearn_init.sql` | 建库脚本 + 测试数据 |
| AI 服务说明 | `AetherLearn/aetherlearn-ai-service/README.md` | FastAPI、BGE-M3、Milvus、RRF、重排序接口与配置 |
| 开发日志 | `AetherLearn/开发日志.md` | 开发进度、验证步骤与踩坑记录 |

---

## 作者

**linyxhe** — 个人开发者

---

## 许可证

本项目仅供学术用途。
