# AI Intelligent Job Hunting

面向 **Java 后端求职** 场景的 AI 求职平台：JWT 认证、简历分析、岗位匹配、AI 模拟面试，以及基于 LangChain4j 的 **RAG 知识库**（PDF / Markdown → Embedding → Chroma → DeepSeek → Sources / SSE）。

> **定位：** Spring Boot 做业务与 AI 主体；Python FastAPI 仅承载简历分析 / 岗位匹配 / 面试对话等能力，不负责 RAG 主链路。

---

## 技术亮点

```text
AI Job Platform
│
├── 用户认证 JWT
├── 简历解析 + AI 分析
├── 岗位匹配
├── AI 模拟面试
│   ├── Redis Session（TTL 7200s + MySQL 降级）
│   ├── SSE Streaming
│   └── MySQL 持久化
│
└── RAG 知识库
    ├── PDF / Markdown
    ├── PDFBox / MdTextExtractor
    ├── LangChain4j
    ├── 本地 Embedding（AllMiniLmL6V2 · 384d）
    ├── Chroma（Docker）
    ├── Top-K Retrieval（document_id + user_id 隔离）
    ├── DeepSeek（Context 注入）
    ├── Sources 溯源
    └── SSE Streaming
```

---

## 系统架构

### 总体架构（Java 为主）

```text
                     Vue3 + TypeScript
                            │
                         JWT / API
                            │
                   ┌────────▼────────┐
                   │   Spring Boot   │
                   │ Auth / Business │
                   │ Resume / Job    │
                   │ Interview       │
                   │ Knowledge RAG   │
                   └───┬─────────┬───┘
                       │         │
                    MySQL      Redis
                       │
               ┌───────▼────────┐
               │   RAG Module   │
               │  LangChain4j   │
               └───────┬────────┘
                       │
            ┌──────────┼──────────┐
            │          │          │
       Embedding     Chroma    DeepSeek
            │          │          │
            └──────────┴──────────┘
                       │
                  RAG Answer
                       │
                   SSE Stream
                       │
                      Vue
```

### Python 边界（面试 / 分析侧车）

```text
Spring Boot
     │
     └── Python FastAPI（:8001）
              │
           DeepSeek
              │
     简历分析 / 岗位匹配 / 面试 SSE
```

RAG（文档解析、切分、Embedding、向量库、问答）全部在 **Java + LangChain4j** 内完成。

---

## 功能模块

| 模块 | 能力 | 关键技术 |
|------|------|----------|
| 认证 | 注册 / 登录 / JWT | Spring Security |
| 简历 | PDF 上传、PDFBox 解析、AI 分析落库 | PDFBox + FastAPI |
| 岗位匹配 | JD vs 简历对比、历史查询 | FastAPI + MySQL |
| AI 面试 | 多轮问答、会话缓存、流式输出、报告 | Redis TTL 7200s、SSE、MySQL |
| 知识库 RAG | PDF/MD 上传→解析→切分→向量入库→问答 | LangChain4j、Chroma、Sources、SSE |
| 权限 | 资源级隔离；越权统一 **HTTP 403** | ForbiddenException |

### RAG Pipeline

```text
PDF / Markdown
      ↓
PDFBox / MdTextExtractor
      ↓
Chunk（~700 字 / overlap ~100）
      ↓
本地 Embedding（384 维）
      ↓
Chroma（按 user_id + document_id）
      ↓
Top-K 检索 → Context
      ↓
DeepSeek（同步 ask / SSE stream）
      ↓
Answer + Sources
```

Markdown 清洗原则：保留标题文字与代码正文；去掉 YAML frontmatter、链接/图片 URL、代码围栏与语言标记；`pageCount` 对 MD 为 `null`。

---

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3、TypeScript、Vite、Element Plus |
| 后端 | Java 21、Spring Boot 3.5、MyBatis-Plus、Spring Security、JWT |
| 数据 | MySQL、Redis |
| AI（Java） | LangChain4j、DeepSeek（OpenAI 兼容）、本地 Embedding、Chroma |
| AI（Python） | FastAPI、DeepSeek（简历 / 匹配 / 面试） |
| 基础设施 | Docker（Chroma） |

---

## 仓库结构

```text
AI-Intelligent-Job-Hunting/
├── ai-job-web/              # Vue3 前端
├── ai-job-server/           # Spring Boot 主服务（含 RAG）
│   ├── docker-compose.chroma.yml
│   ├── scripts/             # Chroma 启动脚本
│   ├── sql/                 # 建表脚本
│   └── src/
├── ai-python-service/       # FastAPI（简历/匹配/面试）
└── README.md
```

---

## 快速启动

> 当前推荐：基础设施用 Docker 起 **Chroma**；MySQL / Redis 本机或自行容器化。完整 `docker compose`（MySQL+Redis+Chroma）见后续整理。

### 0. 前置

- JDK 21、Maven、Node.js 18+、Python 3.10+、Docker Desktop
- 本机 MySQL（库名 `ai_job_platform`）、Redis（默认 `6379`）
- DeepSeek API Key

### 1. 初始化数据库

按顺序执行 `ai-job-server/sql/` 下脚本（用户、简历、岗位匹配、面试、知识库等）。

### 2. 配置密钥

```bash
# ai-python-service/.env（可参考 .env.example）
DEEPSEEK_API_KEY=你的密钥
DEEPSEEK_MODEL=deepseek-v4-flash
```

Spring Boot 通过**同名环境变量**读取 `DEEPSEEK_API_KEY`（不要写进仓库配置文件）。

### 3. 启动 Chroma

```bash
cd ai-job-server
docker compose -f docker-compose.chroma.yml up -d
# 或 Windows：scripts\start-chroma-docker.cmd
```

默认：`http://127.0.0.1:8000`

### 4. 启动 Python AI 服务

```bash
cd ai-python-service
python -m venv venv
# Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8001 --reload
```

### 5. 启动 Spring Boot

```bash
cd ai-job-server
# 确保进程环境已注入 DEEPSEEK_API_KEY
mvn spring-boot:run
# 或 java -jar target/ai-job-server-0.0.1-SNAPSHOT.jar
```

默认：`http://127.0.0.1:8080`

### 6. 启动前端

```bash
cd ai-job-web
npm install
npm run dev
```

默认：`http://127.0.0.1:5173`

---

## 核心演示路径（验收）

1. 注册 / 登录（JWT）
2. 上传简历 → AI 分析
3. 输入 JD → 岗位匹配
4. 开始 AI 面试 → Redis Session → SSE 流式回答 → 面试报告
5. 知识库上传 **PDF / Markdown** → 解析 `PARSED` → 向量入库 `READY`
6. RAG 提问 → 中文 Answer + Sources（可走 SSE）
7. 用户 B 访问用户 A 资源 → **HTTP 403**

建议验证问题（知识库已喂入 Java 后端面试向资料时）：HashMap、ConcurrentHashMap、JVM、Spring Boot、MySQL、Redis。

---

## API 速览（知识库）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/documents` | 上传 PDF / MD（自动解析） |
| POST | `/api/knowledge/documents/{id}/ingest` | Chunk + Embedding + 写入 Chroma |
| POST | `/api/knowledge/ai/ask` | 同步 RAG 问答 |
| POST | `/api/knowledge/ai/ask/stream` | SSE 流式 RAG（`meta` / `delta` / `done` / `error`） |
| POST | `/api/knowledge/ai/retrieve` | 仅 Top-K 检索 |

更多示例见 [`ai-job-server/test.http`](ai-job-server/test.http)。

---

## 设计取舍（面试可讲）

- **RAG 放在 Java：** 与主业务同进程、同权限模型；Python 不继续扩张 AI 核心。
- **本地 Embedding + Docker Chroma：** 避免 Windows 本机 Chroma 崩溃；向量持久化用 Docker Volume。
- **幂等入库：** 按 `document_id` + `user_id` 先清 MySQL Chunk / Chroma 向量再写入。
- **越权统一 403：** 简历、匹配、面试、知识库共用 `ForbiddenException`。

---

## License

本仓库代码用于学习与求职作品展示。第三方知识资料（如 JavaGuide）请遵循其原仓库许可，勿直接提交进 Git。
