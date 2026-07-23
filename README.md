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
- **知识库管理** — 文档上传 → 自动解析切片 → BM25 检索入库
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
| **AI 模块** | LangChain4j + BM25 检索 | RAG 问答、自动批改、智能出题；无 Key 时优雅降级 |
| **文档解析** | Apache PDFBox / Apache POI | PDF、Word 文本抽取 |
| **前端框架** | Vue 3 + Vite 5 | Composition API + `<script setup>` |
| **UI 组件** | Element Plus + Naive UI | 管理端 Element Plus，教师/学生端 Naive UI |
| **图表** | ECharts 5 | 学情数据可视化 |
| **状态管理** | Pinia | 全局状态 |
| **富文本** | WangEditor | 学生作答编辑器 |

---

## 项目结构

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
│       ├── mapper/               # MyBatis-Plus Mapper
│       ├── service/              # 业务接口
│       │   └── impl/             # 业务实现
│       └── util/                 # 工具类
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

# 编译（首次需联网拉依赖）
mvn -q compile

# 启动（端口 8085，context-path: /）
mvn spring-boot:run
```

> **AI 大模型配置**：在 `application-local.yml` 中配置 `langchain4j.open-ai.chat-model.api-key`（已被 `.gitignore` 忽略）。支持 OpenRouter、DeepSeek、小米 MiMo 等 OpenAI 兼容接口。无 Key 时自动降级为「仅知识库检索」模式，可离线跑通核心流程。

### 3. 启动前端

```bash
cd AetherLearn/AetherLearn_front

# 安装依赖
npm install

# 启动开发服务器（默认端口 5173）
npm run dev
```

前端自动代理 `/api` 和 `/uploads` 请求到后端 `localhost:8085`。

### 4. 访问系统

- 前端地址：`http://localhost:5173`
- 后端 API：`http://localhost:8085`
- 默认管理员账号：`admin` / `123456`
- 默认教师账号：`teacher` / `123456`
- 默认学生账号：`student` / `123456`

---

## AI 模块设计

系统采用**分层可降级**的 AI 架构，确保在无外部 API 时仍可正常运行：

```
┌─────────────────────────────────────────┐
│          LLM 生成层（可降级）              
│  OpenAI 兼容接口 / Ollama 本地模型         
│  → 问答生成 / 主观题批改 / 智能出题          
├─────────────────────────────────────────┤
│          检索层（离线可用）                 
│  BM25 全文检索 + 中文分词                  
│  → 知识库相关切片召回                       
├─────────────────────────────────────────┤
│          解析层                          
│  PDFBox / POI / 纯文本                   
│  → 文档解析 → 段落切片 → 入库               
└─────────────────────────────────────────┘
```

- **有 API Key**：完整 RAG 流程（检索 → 上下文注入 → LLM 生成带来源答案）
- **无 API Key**：自动降级为纯知识库检索模式，返回相关切片片段

---

## 关键约定

- **密码加密**：MD5（Hutool `SecureUtil.md5`），初始密码 `123456`
- **文件存储**：项目根目录 `uploads/`，按 `avatar/course/knowledge/answer/export` 分桶
- **软删除**：`knowledge_doc`、`course`、`assignment` 等核心表支持 `is_deleted` 字段
- **角色体系**：`ADMIN(1)` / `TEACHER(2)` / `STUDENT(3)`，int 基本类型
- **中文注释**：所有代码均添加中文类与方法级注释

---

## 文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 数据库脚本 | `AetherLearn/数据库脚本/AetherLearn_init.sql` | 建表 + 测试数据 |

---

## 作者

**linyxhe** — 个人开发者

---

## 许可证

本项目仅供学术用途。
