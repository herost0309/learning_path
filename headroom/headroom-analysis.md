# Headroom 集成与安装详尽分析报告

> **对象工具**：Headroom —— AI Agent 的「上下文压缩层」（Context Compression Layer）
> **仓库**：[`chopratejas/headroom`](https://github.com/chopratejas/headroom)（作者 Tejas Chopra，媒体中常被描述为 Netflix 工程师；Apache 2.0）
> **官方文档**：<https://headroom-docs.vercel.app/docs> · **PyPI**：<https://pypi.org/project/headroom-ai/> · **npm**：<https://www.npmjs.com/package/headroom-ai>
> **报告生成日期**：2026-06-29
> **适用环境说明**：本报告面向 **Windows 11 + Git Bash** 环境编写，并针对团队重度使用 **Claude Code** 的场景给出落地建议。

---

## 目录

1. [概述：Headroom 是什么 / 解决什么问题](#1-概述headroom-是什么--解决什么问题)
2. [核心原理（CCR 架构与压缩管线）](#2-核心原理ccr-架构与压缩管线)
3. [四种使用形态总览](#3-四种使用形态总览)
4. [安装（Installation）](#4-安装installation)
5. [集成（Integration）—— 四种模式详解](#5-集成integration四种模式详解)
6. [配置（Configuration）](#6-配置configuration)
7. [验证与可观测性](#7-验证与可观测性)
8. [效果基准（官方 Benchmarks）](#8-效果基准官方-benchmarks)
9. [Windows 11 特别注意事项](#9-windows-11-特别注意事项)
10. [团队落地建议（推荐路径）](#10-团队落地建议推荐路径)
11. [安全、隐私与合规](#11-安全隐私与合规)
12. [何时用 / 何时跳过 / 局限性](#12-何时用--何时跳过--局限性)
13. [常见问题与排查（Troubleshooting）](#13-常见问题与排查troubleshooting)
14. [参考资料](#14-参考资料)

---

## 1. 概述：Headroom 是什么 / 解决什么问题

**Headroom 是一个开源的「上下文优化层」**，位于 AI Agent / 应用与 LLM 之间。它在**内容进入模型之前**，对 Agent 读取的所有内容进行压缩——包括工具调用输出（tool outputs）、日志（logs）、文件（files）、RAG 检索片段（RAG chunks）、对话历史（conversation history）。

**核心承诺**：

- 在保持答案质量不变的前提下，**减少 60%–95% 的 token 用量**（即降低 API 成本与延迟）。
- **可逆压缩（Reversible）**：原始内容本地缓存，模型可按需取回，因此可以放心地激进压缩。
- **本地优先（Local-first）**：以库、代理、MCP server 形态在本地运行，数据不外发（默认无遥测）。
- **License**：Apache 2.0（可商用、可修改、可再分发）。

**作者自述的动机**（Show HN）：运行带工具调用的 Agent 每天花 \$200，根因是工具返回的巨型 JSON（搜索结果、DB 查询、文件清单）不断撑爆上下文——到第 10 轮，每次调用都要为 10 万+ token 买单。

**它解决的典型「上下文太脏」问题**（综合官方 README 与实战指南）：

- `grep` / `rg` / 日志查询一次返回数百上千行；
- RAG 检索片段重复、冗余、格式化噪音多；
- JSON、堆栈、SQL 结果中有大量低价值字段；
- 多轮调试后旧输出长期占用上下文；
- Claude Code / Codex / Cursor / Aider 各自维护上下文，难以共享记忆。

> Headroom 是「进入模型前的清洁工」。它**不替代 LLM，也不替代 RAG**，而是在 LLM 前面增加一层压缩、路由、缓存与可追溯检索。

---

## 2. 核心原理（CCR 架构与压缩管线）

### 2.1 一句话架构

```
 你的 Agent / 应用 (Claude Code, Cursor, Codex, LangChain, Agno, Strands, 自研代码…)
   │  prompts · tool outputs · logs · RAG results · files
   ▼
┌─────────────────────────────────────────────────────────────┐
│  Headroom  (本地运行 —— 你的数据留在本机)                          │
│  CacheAligner  →  ContentRouter  →  CCR                      │
│                      ├─ SmartCrusher    (JSON)              │
│                      ├─ CodeCompressor  (AST / tree-sitter) │
│                      └─ Kompress-base   (文本, HF 模型)        │
│  + 跨 Agent 记忆  ·  headroom learn  ·  MCP                  │
└─────────────────────────────────────────────────────────────┘
   │  压缩后的 prompt  +  检索工具(headroom_retrieve)
   ▼
 LLM Provider (Anthropic · OpenAI · Bedrock · Vertex · Azure · OpenRouter …)
```

### 2.2 关键组件

| 组件 | 作用 | 适用内容 |
| --- | --- | --- |
| **ContentRouter** | 检测内容类型，自动选择最合适的压缩器 | 所有内容（路由入口） |
| **SmartCrusher** | 通用 JSON 统计压缩（保留错误 100%、统计异常、与用户 query 相关项 BM25/embedding、首尾项） | JSON 数组 / 嵌套对象 / 混合类型 |
| **CodeCompressor** | 基于 **tree-sitter** 的 AST 感知压缩（保留 imports、签名、类型，**输出保证语法合法**） | Python / JS / Go / Rust / Java / C++ |
| **Kompress-base** | Headroom 自研 HuggingFace 文本压缩模型（基于 ModernBERT） | 纯文本、搜索结果、日志、diff |
| **CacheAligner** | 稳定 prompt 前缀，使 Provider 的 **KV 缓存真正命中**（降低缓存未命中成本） | 所有 prompt |
| **IntelligentContext** | 多因子重要性评分的上下文裁剪（见下） | 超限上下文管理 |
| **CCR** | Compress-Cache-Retrieve：原始内容本地缓存，注入 `headroom_retrieve` 工具供模型按需取回 | 所有被压缩的内容 |

### 2.3 CCR —— 让「激进压缩」变安全

CCR 是 Headroom 区别于「截断 / 摘要」方案的核心：

- **截断（Truncation）**：快，但可能切掉模型需要的数据；
- **摘要（Summarization）**：慢（~500ms）且有损、不可逆；
- **CCR**：**激进压缩 + 保留检索路径**——你无法预先知道哪些数据重要，所以先压缩，模型需要细节时通过 `headroom_retrieve` 在 **<1ms** 内取回原文。

> 实践中模型几乎不需要 retrieve，因为智能压缩已经保留了关键信息；但需要时可以拿到。这就是「可逆压缩」的含义——**原文不删除，按需可取**。

**CCR 缓存 TTL**（重要，决定原文保留时长）：

- MCP 本地 store：**1 小时**
- Proxy store：**5 分钟**

### 2.4 IntelligentContext（智能上下文管理）

默认的 `IntelligentContextManager` 按以下因子对消息打分，决定保留/丢弃：

- **recency**（新近度）、**semantic_similarity**（与近期上下文的语义相似度）
- **toin_importance**（TOIN 学习到的检索模式）、**error_indicator**（错误字段类型）
- **forward_reference**（被后续消息引用的消息）、**token_density**（信息密度）

被丢弃的消息会进入 CCR，仍可 retrieve。

### 2.5 请求生命周期（Pipeline Internals）

Headroom 在 `compress()`、SDK、proxy 三种形态下共享**同一个稳定请求生命周期**：

```
Setup → Pre-Start → Post-Start → Input Received → Input Cached →
Input Routed → Input Compressed → Input Remembered → Pre-Send → Post-Send → Response Received
```

- **Transforms**：CacheAligner、ContentRouter、SmartCrusher、CodeCompressor、Kompress-base、IntelligentContext/RollingWindow；
- 可通过 `on_pipeline_event(...)` 在生命周期阶段做观察/定制。

---

## 3. 四种使用形态总览

| 形态 | 命令/接口 | 适用场景 | 是否改代码 |
| --- | --- | --- | --- |
| **库（Library）** | Python/TS `compress(messages)` | 内嵌到任意应用，最细粒度控制 | 改代码 |
| **代理（Proxy）** | `headroom proxy --port 8787` | 任何 OpenAI/Anthropic 兼容客户端，零改动 | **不改** |
| **Agent 包裹（wrap）** | `headroom wrap claude\|codex\|cursor\|aider\|copilot\|gemini` | 一键包裹现有 CLI Agent | **不改** |
| **MCP Server** | `headroom mcp install`（提供 `headroom_compress`/`headroom_retrieve`/`headroom_stats`） | Claude Code / Cursor 等 MCP 宿主 | **不改** |

**附加能力**：

- **跨 Agent 记忆（Cross-agent memory）**：Claude/Codex/Gemini 共享本地存储，自动去重（每项目 SQLite + HNSW 向量库，项目间不串数据）。
- **`headroom learn`**：挖掘失败会话，自动把修正写入 `CLAUDE.md` / `AGENTS.md` / `GEMINI.md`。
- **Output token reduction（输出 token 削减）**：不只压缩你发送的，还削减**模型写回的内容**（去开场白、不重复已展示代码、常规步骤降低 thinking 强度）——在 Opus 级模型上输出成本是输入的 5 倍，价值显著。

---

## 4. 安装（Installation）

### 4.1 前置条件

- **Python 3.10+**（Python 形态，含 proxy/MCP/库）
- **Node.js 18+**（TypeScript SDK 形态；注意 **TS SDK 必须依赖一个运行中的 Python proxy** 做实际压缩）

> ⚠️ **TypeScript SDK 的关键限制**：TS SDK 本身不包含压缩引擎，它通过 HTTP 把消息发给本地 Python proxy 做压缩。因此用 TS SDK 前，必须先启动 proxy（见 5.1）。

### 4.2 Python 安装（主力方式）

```bash
# 核心包（含 compress()、SmartCrusher、CacheAligner、IntelligentContext，依赖轻）
pip install headroom-ai

# 全量（所有可选 extras）
pip install "headroom-ai[all]"
```

**Extras 清单**（按需组合）：

| Extra | 增加的能力 | 安装命令 |
| --- | --- | --- |
| `proxy` | 代理服务器 + MCP 工具 + HTTP API | `pip install "headroom-ai[proxy]"` |
| `ml` | Kompress 文本压缩（ModernBERT，需 PyTorch） | `pip install "headroom-ai[ml]"` |
| `code` | CodeCompressor（tree-sitter AST） | `pip install "headroom-ai[code]"` |
| `mcp` | MCP 工具三件套 | `pip install "headroom-ai[mcp]"` |
| `langchain` | LangChain `HeadroomChatModel` | `pip install "headroom-ai[langchain]"` |
| `agno` | Agno `HeadroomAgnoModel` | `pip install "headroom-ai[agno]"` |
| `evals` | 评测框架（GSM8K/SQuAD/BFCL） | `pip install "headroom-ai[evals]"` |
| `all` | 以上全部 | `pip install "headroom-ai[all]"` |

组合示例：`pip install "headroom-ai[proxy,langchain,ml]"`

**用 pipx 安装时需显式指定解释器**：

```bash
pipx install --python python3.13 "headroom-ai[all]"
```

**验证安装**：

```bash
python -c "import headroom; print(headroom.__version__)"
```

### 4.3 TypeScript / Node 安装

```bash
npm install headroom-ai
# 或
pnpm add headroom-ai
yarn add headroom-ai
```

**验证**：

```bash
node -e "const h = require('headroom-ai'); console.log('headroom-ai loaded')"
```

### 4.4 Docker 安装

官方预构建镜像发布在 GitHub Container Registry：

```bash
docker pull ghcr.io/chopratejas/headroom:latest
docker run -p 8787:8787 ghcr.io/chopratejas/headroom:latest
```

**镜像标签矩阵**：

| Tag | Extras | 基础镜像 | 说明 |
| --- | --- | --- | --- |
| `latest` / `<version>` | `proxy` | Debian slim | 默认，跑 proxy |
| `nonroot` | `proxy` | Debian slim | 以非 root 运行 |
| `code` / `code-nonroot` | `proxy,code` | Debian slim | 含 tree-sitter 代码压缩 |
| `slim` / `slim-nonroot` | `proxy` | Distroless | 最小镜像，无 shell |
| `code-slim` / `code-slim-nonroot` | `proxy,code` | Distroless | 代码压缩 + 最小 |

**从源码构建**（多变体，用 Docker Bake）：

```bash
docker buildx bake --list targets              # 列出所有 target
docker buildx bake runtime-default             # 构建默认运行时镜像
docker buildx bake runtime-code-slim-nonroot \ # 构建指定变体并推到自定义 registry
  --set '*.tags=my-registry/headroom:code-slim-nonroot'
```

**自定义 Dockerfile 示例**（基于官方 proxy 文档）：

```dockerfile
FROM python:3.11-slim
RUN apt-get update && apt-get install -y --no-install-recommends build-essential \
    && pip install "headroom-ai[proxy]" \
    && apt-get purge -y build-essential && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*
EXPOSE 8787
CMD ["headroom", "proxy", "--host", "0.0.0.0"]
```

> ⚠️ **构建依赖**：`build-essential` 在安装时必需，因为 `headroom-ai` 含 `hnswlib`（需从源码编译的 C++ 扩展）。安装完成后可移除以减小镜像体积。

### 4.5 升级（Updating）

```bash
headroom update          # 自动检测 pip / pipx / uv tool 并原地升级
headroom update --check  # 仅报告最新版本，不升级
headroom update --pre    # 包含预发布版
```

`headroom update` 会判断 Headroom 当初是怎么装的（pip/venv、`pip --user`、pipx、uv tool），并在 macOS/Linux/Windows 上执行对应的升级；对 git checkout、可编辑安装、Docker 镜像、PEP 668 受管系统 Python，会打印正确的手动步骤而非乱猜。

proxy 启动时也会显示一行「有更新可用」提示（后台每天最多查一次 PyPI，不阻塞；可用 `HEADROOM_UPDATE_CHECK=off` 关闭，`--stateless` 模式与 CI 中自动跳过）。

### 4.6 企业 / SSL 审查（SSL-inspection）环境

若 `pip install "headroom-ai[all]"` 报 `CERTIFICATE_VERIFY_FAILED`（`unable to get local issuer certificate`），说明你的网络做了 **SSL 审查**（公司 CA 的 MITM 代理），而构建后端 `maturin` 在拉 `rustup` 时 TLS 不信任公司 CA。**先装好 Rust，避免安装时再去拉**：

```bash
# macOS / Linux
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh && rustup default stable
# Windows
winget install Rustlang.Rustup && rustup default stable
```

重启 shell 后再 `pip install "headroom-ai[all]"`。若存在预构建 wheel，可完全跳过 Rust 构建：
`pip install --only-binary headroom-ai headroom-ai`。

两个运行时资产走 TLS 拉取，若被拦截，请用公司 CA（`REQUESTS_CA_BUNDLE` / `SSL_CERT_FILE` / `CURL_CA_BUNDLE`）：

- **`cdn.pyke.io`** —— Rust 核心的 ONNX Runtime。也可用 `ORT_STRATEGY=system` + `ORT_LIB_LOCATION=/path/to/onnxruntime` 预提供。
- **`huggingface.co`** —— `kompress-base` 压缩模型。可预下载后用 `HF_HUB_OFFLINE=1`，或设 `HF_ENDPOINT` 指向可信镜像。

> 若仅以「纯网关、关闭压缩」方式运行，则**两者都不需要**。

---

## 5. 集成（Integration）—— 四种模式详解

### 5.1 模式 A：库（Library）—— 内嵌到代码

最细粒度、最可控。直接对 messages 调 `compress()`，再把压缩后的 messages 喂给任意 LLM。

**Python**：

```python
from headroom import compress
import json

messages = [
    {"role": "system", "content": "You analyze search results."},
    {"role": "user", "content": "Search for Python tutorials."},
    {
        "role": "assistant",
        "content": None,
        "tool_calls": [{
            "id": "call_1", "type": "function",
            "function": {"name": "search", "arguments": '{"q": "python"}'},
        }],
    },
    {
        "role": "tool", "tool_call_id": "call_1",
        "content": json.dumps({
            "results": [
                {"title": f"Result {i}", "snippet": f"Description {i}", "score": 100 - i}
                for i in range(500)
            ]
        }),
    },
    {"role": "user", "content": "What are the top 3 results?"},
]

result = compress(messages, model="gpt-4o")

# 像原来一样把压缩后的 messages 发给模型
from openai import OpenAI
client = OpenAI()
response = client.chat.completions.create(model="gpt-4o", messages=result.messages)
print(response.choices[0].message.content)

# 查看节省
print(result.tokens_before, result.tokens_after, result.tokens_saved, f"{result.compression_ratio:.0%}")
print(result.transforms_applied)   # 例如 ['smart_crusher', 'cache_aligner']
```

**TypeScript**（⚠️ 需先启动 Python proxy）：

```bash
# 先启动 proxy
pip install "headroom-ai[proxy]"
headroom proxy --port 8787
```

```typescript
import { compress } from 'headroom-ai';

const result = await compress(messages, {
  model: 'gpt-4o',
  baseUrl: 'http://localhost:8787',   // 指向本地 proxy
});
```

### 5.2 模式 B：代理（Proxy）—— 零代码改动

启动一个本地 HTTP proxy，把任意兼容客户端指向它，所有请求自动过压缩管线。**对 Claude Code 最推荐、改动最小。**

```bash
# 启动 proxy
headroom proxy --port 8787

# 把 Claude Code 指向它（Git Bash）
ANTHROPIC_BASE_URL=http://localhost:8787 claude

# 或任意 OpenAI 兼容客户端
OPENAI_BASE_URL=http://localhost:8787/v1 your-app
```

**Proxy 核心命令行参数**：

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `--host` | `127.0.0.1` | 绑定地址 |
| `--port` | `8787` | 绑定端口 |
| `--no-optimize` | false | 关闭优化（passthrough） |
| `--no-cache` | false | 关闭语义缓存 |
| `--no-rate-limit` | false | 关闭限流 |
| `--log-file` | None | JSONL 日志路径 |
| `--budget` | None | 每日预算上限（美元） |
| `--openai-api-url` | `https://api.openai.com` | 自定义 OpenAI URL |
| `--no-intelligent-context` | false | 回退到 RollingWindow（最旧优先丢弃） |
| `--llmlingua` | false | 启用 LLMLingua-2 ML 压缩（`--llmlingua-device auto\|cuda\|cpu\|mps`，`--llmlingua-rate 0.3`） |

> LLMLingua 成本较高：约 +2GB 依赖（torch/transformers）、10–30s 冷启动、~1GB RAM；仅在追求极限压缩时启用。

**多后端**（除 Anthropic/OpenAI 外）：

```bash
headroom proxy --backend bedrock --region us-east-1     # AWS Bedrock
headroom proxy --backend vertex_ai --region us-central1 # Google Vertex AI
headroom proxy --backend azure                          # Azure OpenAI
OPENROUTER_API_KEY=sk-or-... headroom proxy --backend openrouter  # OpenRouter（400+ 模型）
```

**生产级部署（gunicorn）**：

```bash
pip install gunicorn
gunicorn headroom.proxy.server:app \
  --workers 4 --bind 0.0.0.0:8787 \
  --worker-class uvicorn.workers.UvicornWorker
```

### 5.3 模式 C：Agent 包裹（`headroom wrap`）—— 一键包裹现有 CLI

对编程 Agent 最省事，**一条命令**包裹并启动：

```bash
headroom wrap claude      # 包裹 Claude Code（可加 --memory --code-graph）
headroom wrap codex       # 包裹 OpenAI Codex（与 Claude 共享记忆）
headroom wrap cursor      # 包裹 Cursor（打印配置，粘贴一次）
headroom wrap aider       # 包裹 Aider（启动 proxy + 启动 Agent）
headroom wrap copilot     # 包裹 Copilot CLI（启动 proxy + 启动）
```

**Agent 兼容矩阵**：

| Agent | `headroom wrap` | 备注 |
| --- | :---: | --- |
| Claude Code | ✅ | 支持 `--memory` · `--code-graph` |
| Codex | ✅ | 与 Claude 共享记忆 |
| Cursor | ✅ | 打印配置，粘贴一次 |
| Aider | ✅ | 启动 proxy + 启动 |
| Copilot CLI | ✅ | 启动 proxy + 启动 |
| OpenClaw | ✅ | 作为 ContextEngine 插件安装 |

> 任何 OpenAI 兼容客户端都可通过 `headroom proxy` 接入；MCP 原生宿主用 `headroom mcp install`。

**`headroom wrap` 的热同步特性**：输出 token 削减的开关是**每个请求实时读取**的。若 wrap 复用了已在运行的 proxy（而非新启动），则后续 export 的环境变量不会立即生效（环境在启动时已快照）。`headroom wrap` 现在通过 loopback `POST /admin/runtime-env` 把当前设置**热同步**给运行中的 proxy，无需重启、无冷启动、不丢缓存。**建议：先 export 设置，再 wrap。**（共享 proxy 上这些覆盖是全局的，最后显式设置者生效。）

### 5.4 模式 D：MCP Server —— Claude Code / Cursor 等 MCP 宿主

无需 proxy，以 MCP 工具形式暴露压缩、检索、统计能力，由模型**按需调用**。

```bash
# 仅 MCP 工具（轻量）
pip install "headroom-ai[mcp]"

# 一次性注册到 Claude Code
headroom mcp install

# 启动 Claude Code —— 现在它有 headroom 工具了
claude
```

**三个 MCP 工具**：

- **`headroom_compress`**：按需压缩大块内容（文件/JSON/日志/搜索结果）；返回压缩文本 + `hash`（用于取回）+ token 统计。原始内容本地缓存 1 小时。
- **`headroom_retrieve`**：凭 `hash` 取回原文（可带 `query` 只返回匹配项）；先查本地 store，回退到 proxy store。
- **`headroom_stats`**：会话压缩统计（compressions/retrievals/tokens_saved/savings_percent/estimated_cost_saved_usd，含子 Agent 聚合）。

**MCP + Proxy 的分工**（不重复压缩）：

```bash
# 终端 1
headroom proxy
# 终端 2
ANTHROPIC_BASE_URL=http://127.0.0.1:8787 claude
```

- **Proxy** 在 HTTP 层压缩**所有**流量（模型看到内容之前）；
- **MCP 工具** 在模型收到内容**之后**按需操作。两者处理不同数据，不重复压缩。

**远程 / HTTP 传输**（Docker、云上 Agent、不同机器）：

```bash
# proxy 自动暴露 /mcp
headroom proxy   # → http://host:8787/mcp

# 远程 Agent 连接（在宿主 MCP 配置中）
{
  "mcpServers": {
    "headroom": { "url": "http://proxy-host:8787/mcp" }
  }
}

# 远程安装到 Claude Code（写 URL 形式配置）
headroom mcp install --remote http://proxy-host:8787/mcp

# 或独立 HTTP server（不带完整 proxy）
headroom mcp serve --transport http --port 8080
```

**常用 MCP 命令**：`headroom mcp install [--force] [--remote URL] [--proxy-url URL]` · `headroom mcp serve [--transport http] [--debug]` · `headroom mcp status` · `headroom mcp uninstall`。

### 5.5 框架集成速查表（一行接入）

| 你的技术栈 | 接入方式 |
| --- | --- |
| 任意 Python 应用 | `compress(messages, model=…)` |
| 任意 TypeScript 应用 | `await compress(messages, { model })`（需 proxy） |
| Anthropic SDK | `withHeadroom(new Anthropic())` |
| OpenAI SDK | `withHeadroom(new OpenAI())` |
| Vercel AI SDK | `wrapLanguageModel({ model, middleware: headroomMiddleware() })` |
| LiteLLM | `litellm.callbacks = [HeadroomCallback()]`（支持 100+ provider） |
| LangChain | `HeadroomChatModel(your_llm)` |
| Agno | `HeadroomAgnoModel(your_model)` |
| Strands | 见 Strands 指南（hook 式工具输出压缩） |
| ASGI 应用 | `app.add_middleware(CompressionMiddleware)` |
| 多 Agent | `SharedContext().put / .get`（压缩态跨 Agent 传递） |
| MCP 客户端 | `headroom mcp install` |

---

## 6. 配置（Configuration）

Headroom 可通过 **SDK 构造函数 / proxy 命令行 / 环境变量 / 单请求覆盖** 四级配置，后者覆盖前者。

### 6.1 运行模式（Mode）

| 模式 | 行为 | 用途 |
| --- | --- | --- |
| `audit` | 仅观察并记录，不修改 | 生产监控、基线测量 |
| `optimize` | 应用安全、确定性的变换 | **生产优化（默认）** |
| `simulate` | 返回计划但不调用 API | 测试、成本预估 |
| `passthrough` | 不优化，纯转发 | 关闭压缩当网关 |

### 6.2 关键环境变量

**Provider 凭据**：`OPENAI_API_KEY` · `ANTHROPIC_API_KEY` · `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`（Bedrock）· `GOOGLE_APPLICATION_CREDENTIALS`（Vertex）· `OPENROUTER_API_KEY`

**Proxy / SDK 配置**：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `HEADROOM_PORT` / `HEADROOM_HOST` | `8787` / `0.0.0.0` | proxy 监听端口/地址 |
| `HEADROOM_MODE` | `optimize` | 默认模式 |
| `HEADROOM_LOG_LEVEL` | `INFO` | 日志级别 |
| `HEADROOM_DEFAULT_MODE` | `optimize` | 默认模式（环境变量形式） |
| `HEADROOM_STORE_URL` | 临时目录 | 数据库 URL |
| `HEADROOM_BASE_URL` | `http://localhost:8787` | TS SDK 指向的 proxy URL |
| `HEADROOM_API_KEY` | — | proxy 认证 / Headroom Cloud |
| `HEADROOM_SAVINGS_PATH` | `~/.headroom/proxy_savings.json` | 持久化节省记录 |
| `HEADROOM_MODEL_LIMITS` | — | 自定义模型配置（JSON 字符串或文件路径） |
| `HEADROOM_TELEMETRY` | `on` | 设 `off` 关闭匿名遥测 |
| `HEADROOM_UPDATE_CHECK` | `on` | 设 `off` 关闭更新检查 |

### 6.3 输出 Token 削减（Output Token Reduction）

```bash
export HEADROOM_OUTPUT_SHAPER=1     # 默认关闭；开启后削减模型写回的内容
headroom proxy --port 8787
```

- **Verbosity steering**：在 system prompt 末尾追加「简洁、不要复述上下文」提示（不影响 prompt cache 命中）。
- **Effort routing**：当一轮只是模型在工具结果后恢复（读文件、测试通过），自动降低 thinking 强度；新问题和错误保持全力度。

**学习适合你的简洁度**（从历史会话推断）：

```bash
headroom learn --verbosity            # 预览（dry run）
headroom learn --verbosity --apply    # 保存，proxy 此后自动使用
headroom output-savings               # 查看输出节省（带 95% 置信区间）
```

> 输出节省是「反事实」估计（我们看不到模型本会写什么），Headroom 如实报告**估计值 + 置信区间**。想要**实测值**：留 10% 会话不压缩作对照组 `export HEADROOM_OUTPUT_HOLDOUT=0.1`。

### 6.4 细粒度变换配置（Python）

**SmartCrusher（JSON 压缩）**：

```python
from headroom.transforms import SmartCrusherConfig
config = SmartCrusherConfig(
    max_items_after_crush=15,     # 压缩后保留的最大条目数
    min_tokens_to_crush=200,      # 触发压缩的最小 token 数
    relevance_tier="bm25",        # 相关性评分："bm25"(快) 或 "embedding"(准)
    preserve_fields=["error", "warning", "failure"],  # 始终保留这些字段值的条目
)
```

**CacheAligner（前缀稳定）**：

```python
from headroom.transforms import CacheAlignerConfig
config = CacheAlignerConfig(
    enabled=True,
    dynamic_patterns=[r"Today is \w+ \d+, \d{4}", r"Current time: .*"],  # 从 system prompt 提取并稳定的动态模式
)
```

**IntelligentContext（评分权重，自动归一化为和=1）**：

```python
from headroom.config import IntelligentContextConfig, ScoringWeights
weights = ScoringWeights(
    recency=0.20, semantic_similarity=0.20, toin_importance=0.25,
    error_indicator=0.15, forward_reference=0.15, token_density=0.05,
)
config = IntelligentContextConfig(
    enabled=True, keep_system=True, keep_last_turns=2,
    output_buffer_tokens=4000, use_importance_scoring=True,
    scoring_weights=weights, toin_integration=True,
    recency_decay_rate=0.1, compress_threshold=0.1,
)
```

**自定义/新模型**：保存为 `~/.headroom/models.json` 或设 `HEADROOM_MODEL_LIMITS`：

```json
{
  "anthropic": {
    "context_limits": { "claude-4-opus-20250301": 200000 },
    "pricing": { "claude-4-opus-20250301": { "input": 15.00, "output": 75.00, "cached_input": 1.50 } }
  },
  "openai": { "context_limits": { "gpt-5": 256000 } }
}
```

未知模型按命名模式自动推断：`*opus*` / `*sonnet*` / `*haiku*` → 200K 上下文 + 对应定价；`gpt-4o*` → 128K；`o1*`/`o3*` → 200K 推理模型定价。

**单请求覆盖**（按工具粒度跳过/定制压缩）：

```python
response = client.chat.completions.create(
    model="gpt-4o", messages=messages,
    headroom_tool_profiles={
        "important_tool": {"skip_compression": True},
        "search_tool": {"max_items_after_crush": 25},
    },
)
```

**启动时校验配置**：`result = client.validate_setup()`。

### 6.5 配置覆盖优先级

1. 内置默认 → 2. `~/.headroom/models.json` → 3. `HEADROOM_MODEL_LIMITS` 环境变量 → 4. SDK 构造参数 → 5. 单请求覆盖。

---

## 7. 验证与可观测性

### 7.1 一键看效果

```bash
headroom perf   # 对典型负载测算能省多少 token（验证可用性的最快方式）
```

### 7.2 Proxy HTTP 端点

```bash
curl http://localhost:8787/health
# {"status":"healthy","optimize":true,"stats":{"total_requests":42,"tokens_saved":15000,"savings_percent":45.2}}

curl http://localhost:8787/stats           # 实时会话统计 + 持久化累计节省
curl http://localhost:8787/stats-history   # 小时/日/周/月聚合（驱动 /dashboard）
curl http://localhost:8787/metrics         # Prometheus 格式指标
```

**`/metrics` 示例**：

```
headroom_requests_total{mode="optimize"} 1234
headroom_tokens_saved_total 5678900
headroom_compression_ratio_bucket{le="0.5"} 890
headroom_latency_seconds_bucket{le="0.01"} 800
headroom_cache_hits_total 456
```

### 7.3 仅压缩端点（供 TS SDK / 自测）

```bash
curl -X POST http://localhost:8787/v1/compress -d '{
  "messages": [{"role":"user","content":"..."}],
  "model": "gpt-4o"
}'
# {"messages":[...],"tokens_before":15000,"tokens_after":3500,
#  "tokens_saved":11500,"compression_ratio":0.23,
#  "transforms_applied":["router:smart_crusher:0.35"],"ccr_hashes":["a1b2c3"]}
```

> 用 `x-headroom-bypass: true` 头可跳过压缩（对照测试）。

---

## 8. 效果基准（官方 Benchmarks）

### 8.1 真实 Agent 工作负载节省

| 工作负载 | 压缩前 | 压缩后 | 节省 |
| --- | ---: | ---: | ---: |
| 代码搜索（100 结果） | 17,765 | 1,408 | **92%** |
| SRE 故障排查 | 65,694 | 5,118 | **92%** |
| GitHub issue 分诊 | 54,174 | 14,761 | **73%** |
| 代码库探索 | 78,502 | 41,254 | **47%** |

作者自测：搜索结果（1000 条）45k → 4.5k tokens（90%）；带工具的 Agent（10 次调用）100k → 15k tokens（85%）；**开销仅 1–5ms/请求**。

### 8.2 各内容类型典型节省

| 内容类型 | 压缩器 | 典型节省 |
| --- | --- | --- |
| JSON 数组 | SmartCrusher | 70%–90% |
| 源代码 | CodeCompressor（AST） | 40%–70% |
| 构建/测试日志 | LogCompressor | 80%–95% |
| 搜索结果 | SearchCompressor | 60%–80% |
| 纯文本 | Kompress | 30%–50% |
| 图片 | 图片压缩（ML router） | 40%–90% |

### 8.3 精度保持（标准基准）

| 基准 | 类别 | N | 基线 | Headroom | Delta |
| --- | --- | ---: | ---: | ---: | ---: |
| GSM8K | 数学 | 100 | 0.870 | 0.870 | **±0.000** |
| TruthfulQA | 事实 | 100 | 0.530 | 0.560 | **+0.030** |
| SQuAD v2 | QA | 100 | — | **97%** | 压缩 19% |
| BFCL | 工具 | 100 | — | **97%** | 压缩 32% |

可复现：`python -m headroom.evals suite --tier 1`

---

## 9. Windows 11 特别注意事项

> 你的环境是 **Windows 11 + Git Bash（win32）**，下面是 Windows 特定要点。

1. **Python 解释器**：Windows 上常需 `py` 启动器或显式 `python`。验证用 `python -c "import headroom; print(headroom.__version__)"`。建议用 venv 或 pipx 隔离，避免 PEP 668 受管 Python 报错。

2. **C++ 扩展编译**：`hnswlib` 需编译，Windows 上需安装 **"Desktop development with C++"**（Visual Studio Build Tools）才能 `pip install "headroom-ai[all]"`；若失败，优先尝试预构建 wheel：`pip install --only-binary headroom-ai headroom-ai`。

3. **Rust 构建**：Windows 装 Rust 用 `winget install Rustlang.Rustup && rustup default stable`（企业 SSL 审查环境尤其要先装）。

4. **环境变量语法差异**（Git Bash 用 `export`，PowerShell 用 `$env:`）：
   ```bash
   # Git Bash
   export ANTHROPIC_BASE_URL=http://localhost:8787
   export HEADROOM_OUTPUT_SHAPER=1
   claude
   ```
   ```powershell
   # PowerShell
   $env:ANTHROPIC_BASE_URL = "http://localhost:8787"
   $env:HEADROOM_OUTPUT_SHAPER = "1"
   claude
   ```

5. **ANTHROPIC_BASE_URL 持久化**：若希望 Claude Code 长期走 proxy，可在用户环境变量里设置 `ANTHROPIC_BASE_URL=http://127.0.0.1:8787`，并让 proxy 作为后台/服务常驻（官方安装指南提供 **PowerShell 持久化服务 / devcontainer** 方案，建议参考官方 [Installation](https://headroom-docs.vercel.app/docs/installation) 的 Windows 段落配置开机自启）。

6. **bash 路径**：本环境用正斜杠路径与 Unix 语法（如 `/dev/null`），CCR 缓存目录在 `~/.headroom/`（Git Bash 下即 `C:\Users\ldd\.headroom\`）。

7. **Copilot 订阅模式**（如用 GitHub Copilot CLI）：Windows Credential Manager 路径「已实现或计划中，但仍需真实 OS 验证」。Docker/CI 场景建议直接显式传 `GITHUB_COPILOT_TOKEN` / `GITHUB_COPILOT_GITHUB_TOKEN`，不要依赖宿主 keychain。

---

## 10. 团队落地建议（推荐路径）

结合团队现状（Java/Spring Boot + Python 项目并存；重度使用 Claude Code；Windows 11 工作站），**推荐分三步渐进落地**：

### 第 0 步：环境准备与验证（不改任何现有工作流）

```bash
pip install "headroom-ai[proxy,mcp,code]"   # proxy + MCP + 代码压缩，够用且不过重
headroom perf                                 # 看典型负载能省多少（基线）
```

> 先 `headroom perf` 拿到**真实数字**，再决定是否值得接入，避免「只看 demo」。

### 第 1 步：Claude Code 接入（最低成本、最高收益）

二选一：

- **方案 1（推荐，零侵入）**：proxy + 包裹
  ```bash
  headroom proxy --port 8787 &
  headroom wrap claude          # 或手动 ANTHROPIC_BASE_URL=http://localhost:8787 claude
  ```
- **方案 2（模型按需压缩）**：MCP
  ```bash
  headroom mcp install
  claude                        # 模型需要时调 headroom_compress / headroom_retrieve
  ```

> 两者可组合：proxy 做全量压缩，MCP 做按需补充（不重复压缩）。对 Claude Code，**优先 proxy/wrap**，因为它能在模型看到内容**之前**压缩所有工具输出——这正是 Claude Code 烧 token 的主因。

### 第 2 步：开启输出削减 + 学习

```bash
export HEADROOM_OUTPUT_SHAPER=1
headroom learn --verbosity --apply   # 从你的历史会话学简洁度
headroom output-savings              # 看输出节省（带置信区间）
```

### 第 3 步：Python / 自研应用集成（落地手册）

> 本节给出**可直接复制粘贴**的落地步骤，覆盖 Python 库模式、`HeadroomClient` SDK、proxy 模式、LangChain/LangGraph、FastAPI/ASGI，以及 Java/Spring Boot 经 proxy 接入。示例贴合团队工程（以 `dailyManagement` 为参照）。

#### 3.0 先决策：选哪种集成模式

| 你的场景 | 推荐模式 | 是否改代码 | 是否需 proxy |
| --- | --- | :---: | :---: |
| 自研 Python 应用，想精确控制压缩时机 | **库模式 `compress()`** | 改少量 | 否（本地 in-process） |
| 想要「预估/校验/统计」一站式 SDK | **`HeadroomClient`** | 改少量 | 否 |
| 已有 LLM 调用，零逻辑改动 | **proxy 模式** | 只换 base_url | 是 |
| 用 LangChain / LangGraph | **LangChain 集成** | 包一层 | 否 |
| FastAPI / ASGI 服务 | **`CompressionMiddleware`** | 加一行中间件 | 否 |
| Java / Spring Boot | **proxy 模式**（无原生 SDK） | 配置 base_url | 是 |

> 关键差异：**Python 的 `compress()` / LangChain 集成是本地 in-process 运行，无需起 proxy**；而 TypeScript SDK、Java、以及「全量透明代理」才需要 proxy。

---

#### 3.1 准备：把 Headroom 加入 Python 工程（以 `dailyManagement` 为例）

**关键澄清**：`dm`（`dailyManagement`）当前是 Jira + Confluence + ServiceNow 的**集成 CLI**，**本身不调用 LLM**。因此 Headroom 的接入点 = 你为它**新增 AI 能力**的那一刻（例如「批量工单 AI 分诊/摘要」「根据 ServiceNow 发布记录生成 Release Notes」）。这些场景会把 Jira/ServiceNow 返回的大块 JSON 喂给模型，正是 SmartCrusher 的主场。

**① 版本兼容性**：`dm` 要求 `Python >=3.11`，Headroom 要求 `>=3.10` —— **完全兼容**。`dm` 已有的 `httpx`、`pydantic`、`structlog` 依赖均可复用。

**② 在 `pyproject.toml` 增加 AI 可选依赖**（沿用 `dm` 的 optional-dependencies 风格，不污染核心依赖）：

```toml
[project.optional-dependencies]
dev = [ "pytest>=7.0", "pytest-mock>=3.0", "respx>=0.20", "pytest-cov>=4.0" ]
ai = [
    "headroom-ai[code]>=0.1",   # code=tree-sitter 代码/结构化压缩；按需再加 [ml]
    "openai>=1.0",              # （或 anthropic）调用真实模型
]
# 想用极限文本压缩再追加："headroom-ai[ml]"
```

**③ 安装**（开发模式，与 `dm` 的 `pip install -e ".[dev]"` 一致）：

```bash
pip install -e ".[dev,ai]"
python -c "import headroom; print(headroom.__version__)"   # 验证
```

---

#### 3.2 模式 A：库模式 `compress()`（最通用、最可控，无需 proxy）

新增一个薄封装 `src/dm/ai/headroom.py`，供所有 AI 能力复用：

```python
# src/dm/ai/headroom.py
"""Headroom 上下文压缩封装。

dm 本身不调用 LLM；本模块服务于新增的 AI 能力（如批量工单 AI 分诊/摘要），
把 Jira/ServiceNow 返回的大块 JSON 在进入模型前先压缩。
压缩是可逆的（CCR）：原文本地缓存，模型可经 headroom_retrieve 取回。
"""
from __future__ import annotations

import logging
from typing import Any

from headroom import compress

log = logging.getLogger(__name__)


def compress_messages(
    messages: list[dict[str, Any]],
    model: str = "gpt-4o",
) -> list[dict[str, Any]]:
    """压缩 OpenAI 格式的 messages，返回压缩后的 messages。

    Python 的 compress() 在进程内本地运行，无需起 proxy。
    原文由 CCR 本地缓存（默认 TTL），模型需要时可 retrieve。
    """
    result = compress(messages, model=model)
    saved = (result.tokens_saved / result.tokens_before * 100) if result.tokens_before else 0.0
    log.info(
        "headroom.compress tokens_before=%d tokens_after=%d saved=%.1f%% transforms=%s",
        result.tokens_before, result.tokens_after, saved, result.transforms_applied,
    )
    return result.messages
```

**在 AI 能力里使用**（以「Jira 批量工单 AI 分诊」为例）：

```python
# src/dm/ai/triage.py
import json
from openai import OpenAI

from dm.ai.headroom import compress_messages

_client = OpenAI()  # OPENAI_API_KEY 走环境变量，沿用 dm 的 .env 惯例


def triage_jira_batch(issues: list[dict], question: str) -> str:
    # issues 可能是上百条工单的 JSON —— 正是 SmartCrusher 的主场（典型省 70%~90%）
    messages = [
        {"role": "system", "content": "你是敏捷教练，根据 Jira 工单做分诊与优先级建议。"},
        {"role": "user", "content": f"工单数据：\n{json.dumps(issues, ensure_ascii=False)}"},
        {"role": "user", "content": question},
    ]
    messages = compress_messages(messages, model="gpt-4o")  # 进模型前压缩
    resp = _client.chat.completions.create(model="gpt-4o", messages=messages)
    return resp.choices[0].message.content
```

> `compress()` 返回对象字段（均已官方验证）：`messages`、`tokens_before`、`tokens_after`、`tokens_saved`、`compression_ratio`、`transforms_applied`、`ccr_hashes`。

**更底层的可控方式**（按需组合变换，官方 API）：可直接用 `SmartCrusher`、`CacheAligner`、`RollingWindow` 或 `TransformPipeline`：

```python
from headroom import TransformPipeline, SmartCrusher, CacheAligner, RollingWindow

pipeline = TransformPipeline([SmartCrusher(), CacheAligner(), RollingWindow()])
result = pipeline.transform(messages)
```

---

#### 3.3 模式 B：`HeadroomClient` SDK（带 预估 / 校验 / 统计）

当你需要「先看会省多少再决定是否真调模型」「启动校验配置」「汇总节省」时，用 `HeadroomClient`（官方 API Reference 的主入口）：

```python
from headroom import HeadroomClient

client = HeadroomClient()  # 具体构造参数（config / provider 注入）以官方 API Reference 为准

# 1) 预估：不真正调用模型，只看压缩计划
plan = client.chat.completions.simulate(model="gpt-4o", messages=messages)
print(f"Tokens: {plan.tokens_before} -> {plan.tokens_after}")
print(f"Savings: {plan.savings_percent:.1f}%   Transforms: {plan.transforms_applied}")

# 2) 真正调用（自动优化）
# resp = client.chat.completions.create(model="gpt-4o", messages=messages)

# 3) 启动时校验配置
result = client.validate_setup()
if not result["valid"]:
    for issue in result["issues"]:
        print("  -", issue)

# 4) 汇总统计
summary = client.get_summary()
# {'total_requests','total_tokens_saved','avg_compression_ratio','total_cost_saved_usd'}
stats = client.get_stats()      # {'session','config','transforms'}
```

> 单请求按工具粒度跳过/定制压缩（已在 [6.4](#64-细粒度变换配置python) 给出），可对敏感工具设 `skip_compression: True`。

---

#### 3.4 模式 C：proxy 模式（零逻辑改动，适配已有的 LLM 调用）

适合「已有 LLM 调用代码，不想动逻辑」或非 Python 客户端。`dm` 已有 `httpx`，改造极小。

```bash
# 1) 起本地 proxy（一次性）
pip install "headroom-ai[proxy]"
headroom proxy --port 8787

# 2) 用环境变量把既有客户端的 base_url 指向 proxy（无需改代码）
#    Git Bash
export OPENAI_BASE_URL=http://localhost:8787/v1
export OPENAI_API_KEY=sk-...
```

```python
# src/dm/ai/llm_client.py —— 既有代码只把 base_url 换成可配置项
import os
import httpx

base = os.getenv("DM_LLM_BASE_URL", "https://api.openai.com")  # 平时指向真实 API；要压缩时设为 http://localhost:8787/v1
client = httpx.Client(
    base_url=base,
    headers={"Authorization": f"Bearer {os.environ['OPENAI_API_KEY']}"},
)
# resp = client.post("/v1/chat/completions", json={...})
```

**A/B 对照**：给单次请求加 `x-headroom-bypass: true` 头即跳过压缩，便于做有/无 Headroom 的对照实验。

**仅压缩、不调模型**（供你压缩后用自己的客户端发）：调 proxy 的 `POST /v1/compress`，返回 `tokens_before/after/saved`、`transforms_applied`、`ccr_hashes`。

**proxy 生产部署**：用 gunicorn 多 worker（见 [5.2](#52-模式-b代理proxy零代码改动)）或 Docker（[4.4](#44-docker-安装) 镜像）；建议配 `--budget`（每日预算上限，美元）+ `--log-file`（JSONL 审计日志）。

---

#### 3.5 模式 D：LangChain / LangGraph 集成（团队若用 LangChain）

官方提供全套 Python 集成，**无需 proxy**：

```bash
pip install "headroom-ai[langchain]"
```

**① 一行包裹任意 chat model**：

```python
from langchain_openai import ChatOpenAI
from headroom.integrations import HeadroomChatModel

llm = HeadroomChatModel(ChatOpenAI(model="gpt-4o"))
response = llm.invoke("Hello!")
print(llm.get_metrics())   # {'tokens_saved': 12500, 'savings_percent': 45.2, 'requests': 50}
```

**② 压缩工具输出**（Agent 场景收益最大）：

```python
from langchain_core.tools import tool
from headroom.integrations import wrap_tools_with_headroom

@tool
def search_database(query: str) -> str:
    """Search the database."""
    import json
    return json.dumps({"results": [...], "total": 1000})  # 大结果，自动压缩

wrapped_tools = wrap_tools_with_headroom([search_database], min_chars_to_compress=1000)
```

**③ RAG：检索多、保留精**：

```python
from langchain.retrievers import ContextualCompressionRetriever
from headroom.integrations import HeadroomDocumentCompressor

base_retriever = vectorstore.as_retriever(search_kwargs={"k": 50})   # 召回 50
compressor = HeadroomDocumentCompressor(max_documents=10, min_relevance=0.3, prefer_diverse=True)
retriever = ContextualCompressionRetriever(base_compressor=compressor, base_retriever=base_retriever)
docs = retriever.invoke("...")   # 留下最优 10
```

**④ LangGraph：在 tools 与 agent 之间插一个压缩节点**：

```python
from langgraph.graph import StateGraph, MessagesState, START, END
from headroom.integrations.langchain import create_compress_tool_messages_node

graph = StateGraph(MessagesState)
graph.add_node("agent", agent_node)
graph.add_node("tools", tools_node)
graph.add_node("compress", create_compress_tool_messages_node(min_tokens_to_compress=100))

graph.add_edge(START, "agent")
graph.add_edge("tools", "compress")   # tools -> compress -> agent
graph.add_edge("compress", "agent")
```

**⑤ 自定义压缩配置**：

```python
from headroom import HeadroomConfig, HeadroomMode

config = HeadroomConfig(default_mode=HeadroomMode.OPTIMIZE, smart_crusher_target_ratio=0.3)
llm = HeadroomChatModel(ChatOpenAI(model="gpt-4o"), headroom_config=config)
```

> 全套支持 async：`await llm.ainvoke(...)`、`async for chunk in llm.astream(...)`。还提供 `HeadroomChatMessageHistory`（长会话压缩记忆）、`get_tool_metrics()`（每工具统计）。

---

#### 3.6 模式 E：FastAPI / ASGI 中间件

若 AI 能力以 HTTP 服务暴露，加一行中间件即可对所有出站 LLM 流量压缩：

```python
# dm 若新增 FastAPI 服务（dm 当前是 CLI，此为扩展场景）
from fastapi import FastAPI
from headroom.integrations import CompressionMiddleware   # 具体导入路径以官方 ASGI/Proxy 文档为准

app = FastAPI()
app.add_middleware(CompressionMiddleware)
```

> 中间件本质等价于「内嵌 proxy」，无需单独起 `headroom proxy` 进程。

---

#### 3.7 Java / Spring Boot 经 proxy 接入（团队 `ddd`/`architect/order-service` 等）

Java 侧**无原生 Headroom SDK**，统一走 **proxy 模式**：Java 客户端把 base URL 指向本地 proxy，proxy 压缩后用你的 API key 转发到真实 provider。proxy 同时暴露 **`/v1/chat/completions`**（OpenAI 兼容）与 **`/v1/messages`**（Anthropic 兼容），请求/响应格式与直连 provider 完全一致，**业务代码零改动**。

> **通用两种接法**：① 若项目已用 **Spring AI**，直接在 `application.yml` 设 `spring.ai.openai.base-url: http://localhost:8787`、`api-key: ${OPENAI_API_KEY}`（按你的 Spring AI 版本确认 base-url 与 `/v1` 路径拼接）；② 若未用 Spring AI，用 Spring Boot 自带的 **`RestClient`** 直连 proxy（下方 order-service 示例采用此法，**对 Boot 3.2.5 无额外依赖**）。

---

##### 3.7.1 `order-service` 完整集成示例（可直接落地）

**背景**：`architect/order-service` 是 **Spring Boot 3.2.5 + Java 17 + Axon（CQRS/SAGA/六边形）+ JPA + PostgreSQL/H2** 的演示项目。它当前**不调用 LLM**；Headroom 的接入点 = 新增一个 AI 能力。这里以**「订单风控/问询助手」**为例：把一笔订单及其 **SAGA 事件历史**（往往是大段 JSON/事件流）喂给模型做风险评估——这正是 Headroom 压缩的主场，而 Java 侧只需把请求打到本地 proxy。

**① 配置（`order-service/src/main/resources/application.yml`）**

```yaml
app:
  llm:
    # 平时指向真实 provider；想压缩时改 http://localhost:8787（Headroom proxy）
    base-url: ${LLM_BASE_URL:http://localhost:8787}
    api-key: ${OPENAI_API_KEY}
    model:   ${LLM_MODEL:gpt-4o}
```

**② 配置属性类（`LlmProperties`）**

```java
package com.example.orderservice.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** LLM（经 Headroom proxy）连接配置。 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(String baseUrl, String apiKey, String model) {}
```

在主类上加 `@ConfigurationPropertiesScan`（或 `@EnableConfigurationProperties(LlmProperties.class)`）。

**③ OpenAI 兼容的请求/响应 DTO（record）**

```java
package com.example.orderservice.ai;

import java.util.List;

public record ChatMessage(String role, String content) {}

public record ChatRequest(String model, List<ChatMessage> messages, boolean stream) {
    public ChatRequest(String model, List<ChatMessage> messages) {
        this(model, messages, false);
    }
}

public record ChatResponse(List<Choice> choices, Usage usage) {}
public record Choice(int index, ChatMessage message) {}
public record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {}
```

**④ 业务服务（`OrderInsightService`）—— 用 `RestClient` 经 proxy 调模型**

```java
package com.example.orderservice.ai;

import com.example.orderservice.query.OrderSnapshot;
import com.example.orderservice.query.OrderSnapshotView;   // 你既有的查询侧读模型
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 订单风控/问询助手：把订单 + SAGA 事件历史拼成大上下文，经 Headroom proxy 调 LLM。
 * 代理在请求进入真实模型前自动压缩（SmartCrusher 处理 JSON/事件流，CCR 保留可取回原文）。
 */
@Service
public class OrderInsightService {

    private final RestClient restClient;
    private final LlmProperties props;
    private final OrderSnapshotView snapshotView;

    public OrderInsightService(LlmProperties props, OrderSnapshotView snapshotView) {
        this.props = props;
        this.snapshotView = snapshotView;
        // Boot 3.2 自带 RestClient；baseUrl 指向 Headroom proxy（或真实 provider）
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /** 评估单笔订单风险；bypass=true 时跳过 Headroom 压缩，用于 A/B 对照。 */
    public String assess(String orderId, String question, boolean bypass) {
        OrderSnapshot snapshot = snapshotView.load(orderId);   // 订单 + SAGA 事件历史
        List<ChatMessage> messages = List.of(
                new ChatMessage("system", "你是订单风控助手，结合订单与 SAGA 事件历史给出风险判断。"),
                new ChatMessage("user", "订单与事件历史（JSON）：\n" + snapshot.toJson()),  // 大块 JSON → 自动压缩
                new ChatMessage("user", question)
        );
        ChatRequest req = new ChatRequest(props.model(), messages);

        return restClient.post()
                .uri("/v1/chat/completions")                  // proxy 的 OpenAI 兼容端点
                .header("x-headroom-bypass", Boolean.toString(bypass))  // A/B：true=跳过压缩
                .body(req)
                .retrieve()
                .body(ChatResponse.class)
                .choices().get(0).message().content();
    }
}
```

> `OrderSnapshot.toJson()` 里聚合的是查询侧读模型（CQRS 的 Query 端）+ Axon 事件历史——天然是大段 JSON，压缩收益最大。`OrderSnapshotView` 复用你既有的查询服务，无需改动领域/命令侧。

**⑤ 启动 proxy 与验证**

```bash
# A) 本地起 proxy（或见 ⑥ 用 docker-compose 与 order-service 同启）
headroom proxy --port 8787

# B) 启动 order-service（LLM_BASE_URL 默认指向 proxy）
./mvnw spring-boot:run

# C) 触发一次 AI 评估，观察 proxy 统计
curl -s http://localhost:8787/stats
# {"total_requests":1,"tokens_saved":18000,"savings_percent":82.3,...}
```

**A/B 对照**：对同一订单分别调 `assess(id, q, false)`（压缩）与 `assess(id, q, true)`（bypass），比对答案质量与 token 消耗。**快速降级**：把 `LLM_BASE_URL` 改回真实 provider（如 `https://api.openai.com`）重启即可，业务代码无需改动。

**⑥ 与 order-service 同栈部署（`docker-compose.yml` 追加 headroom 服务）**

```yaml
services:
  headroom:                              # Headroom proxy，与 order-service 同网络
    image: ghcr.io/chopratejas/headroom:latest
    ports: ["8787:8787"]
    environment:
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      HEADROOM_TELEMETRY: "off"          # 关闭匿名遥测
      HEADROOM_MODE: "optimize"
  order-service:
    # ... 你既有配置 ...
    environment:
      LLM_BASE_URL: http://headroom:8787   # 容器内经服务名访问 proxy
      OPENAI_API_KEY: ${OPENAI_API_KEY}
    depends_on: [headroom]
```

**⑦ 可观测**：proxy 暴露 `GET /stats`、`GET /metrics`（Prometheus）、`GET /stats-history`（小时/日/周/月聚合）。把 `/metrics` 接入团队既有监控即可看到 `headroom_tokens_saved_total`、`headroom_compression_ratio_bucket` 等。

> **与 dm（Python）的一致性**：两边都走「base_url 可切换 + bypass 头做 A/B + 环境变量管 key」的同一套约定，便于团队统一管控与一键回滚。

---

#### 3.8 配置落地（贴合 dm 的三层配置 + .env 惯例）

`dm` 已有成熟的三层配置（`DM_` 环境变量 → `~/.dm/config.toml` → `.dm.toml`）与「Token 走环境变量、`.env`/`.dm.toml` 入 `.gitignore`」的安全规范。Headroom 的配置**无缝沿用同一套**：

```bash
# .env（新增 AI 段，沿用既有约定；记得加入 .gitignore）
OPENAI_API_KEY=sk-...
# —— Headroom 开关 ——
DM_LLM_BASE_URL=http://localhost:8787/v1   # 想压缩时启用；不压缩则不设或指向真实 API
HEADROOM_MODE=optimize                      # optimize | audit | simulate | passthrough
HEADROOM_OUTPUT_SHAPER=1                    # 削减模型输出 token（见 6.3）
HEADROOM_TELEMETRY=off                      # 关闭匿名遥测
HEADROOM_SAVINGS_PATH=~/.headroom/proxy_savings.json
```

**自定义模型/定价**：保存到 `~/.headroom/models.json` 或设 `HEADROOM_MODEL_LIMITS`（JSON 字符串/文件路径），覆盖优先级见 [6.5](#65-配置覆盖优先级)。

**敏感工具保护**：对含 PII/机密的工具输出，用 per-request `headroom_tool_profiles={"sensitive_tool": {"skip_compression": True}}` 跳过压缩（见 [6.4](#64-细粒度变换配置python)）。

---

#### 3.9 验证与上线清单（Checklist）

```text
[ ] 依赖：pip install -e ".[dev,ai]" 成功；python -c "import headroom" 通过
[ ] 基线：headroom perf 看典型负载省多少（或 client.chat.completions.simulate() 预估）
[ ] 接入：选定模式 A~E，跑通一个真实 AI 任务（如 triage_jira_batch）
[ ] 对照：单请求 x-headroom-bypass: true 做有/无 Headroom 对照，确认答案质量不降
[ ] 实测：export HEADROOM_OUTPUT_HOLDOUT=0.1 留 10% 对照组，看实测节省与任务完成率
[ ] 可观测：client.get_summary() / curl localhost:8787/stats / /metrics（Prometheus）
[ ] 安全：OPENAI_API_KEY 走 .env 且入 .gitignore；敏感工具 skip_compression；确认请求链路合规
[ ] 降级：保留「改 base_url 回真实 provider」或 passthrough 模式作为一键回滚
[ ] 团队：统一常驻 proxy，汇总 headroom_tokens_saved_total；headroom learn --apply 沉淀简洁度
```

> 完成后即进入「日常跑 + 持续度量」阶段：定期看 `get_summary()` 的 `total_cost_saved_usd`，并在答案质量出现回归时用 holdout 数据定位。

### 团队协作与度量

- **统一 proxy**：团队共享一台常驻 proxy（dev 机或内网小服务），成员各自把客户端指向它，便于汇总 `headroom_tokens_saved_total`。
- **对照组实测**：开 `HEADROOM_OUTPUT_HOLDOUT=0.1` 留 10% 不压缩，对比**任务完成率 / 误判率**，不只看 token 节省。
- **`headroom learn`**：把团队沉淀的修正写入 `CLAUDE.md` / `AGENTS.md`，配合团队已有的 `team-shared-skills` 沉淀流程。

---

## 11. 安全、隐私与合规

- **本地优先**：库 / proxy / MCP 在本地运行，原始内容与缓存都在本机，默认**无遥测**（`HEADROOM_TELEMETRY=off` 可关）。
- **数据出境的真正边界**：压缩后的内容**仍会发往云端模型**（Anthropic/OpenAI 等）。所以「local-first」指的是中间层本地化，**不等于数据不出企业**。压缩后内容可能仍含敏感信息，须按企业数据安全要求评估。
- **缓存与留存策略**：CCR 原文有 TTL（MCP 1 小时 / proxy 5 分钟），缓存目录 `~/.headroom/`；团队须定义**本地缓存、会话记录、敏感数据留存**策略，必要时缩短 TTL 或定期清理。
- **凭据**：`ANTHROPIC_API_KEY` / `OPENAI_API_KEY` 等以环境变量传入 proxy，注意不要写入日志或提交到仓库（团队项目已有 `.env.example` 惯例，沿用）。
- **请求链路**：用 proxy 模式时，务必确认请求经过哪些本地与云端环节（尤其多后端 Bedrock/Vertex/Azure/OpenRouter 时），确保走企业合规出口。

---

## 12. 何时用 / 何时跳过 / 局限性

### ✅ 适合

- 每天跑 AI 编程 Agent，想在不改代码的前提下省钱；
- 跨多个 Agent 工作且需要共享记忆（Claude / Codex / Gemini）；
- 需要可逆压缩——原文可经 CCR 在 TTL 内取回；
- Claude Code / Codex / Cursor 常因工具输出过长而变慢变贵；
- RAG 检索结果冗余严重；
- SRE 排障要把日志、trace、配置、命令输出塞给模型。

### ❌ 跳过

- 只用单一 provider 的原生 compaction，且不需要跨 Agent 记忆；
- 工作在**无法运行本地进程的沙箱环境**（proxy/MCP 需要本地进程）；
- 偶尔几轮聊天、prompt 很短——收益有限。

### ⚠️ 已知局限

- CCR 增加内存开销（LRU 缓存，可配置）；
- AST 压缩依赖 tree-sitter（约 50MB）；
- **尚未在所有边缘场景上经受过实战考验**（作者自承）；
- 压缩策略不当时，模型可能拿不到关键细节——**务必对真实任务验证 retrieve 是否可靠**；
- LLMLingua（极限压缩）代价高：+2GB 依赖、10–30s 冷启动、~1GB RAM；
- 建议把 Headroom 当作「**先用真实任务小范围验证，再推广到敏感/生产任务**」的工具，而非开箱即生产。

---

## 13. 常见问题与排查（Troubleshooting）

| 现象 | 原因 / 解决 |
| --- | --- |
| Claude Code 里看不到 headroom 工具 | `headroom mcp status` 检查 → 重启 Claude Code → 在 Claude Code 内 `/mcp` 验证 |
| "MCP SDK not installed" | `pip install "headroom-ai[mcp]"` |
| "Proxy not running" | 另开终端 `headroom proxy`（仅 proxy 回退检索需要） |
| "Entry not found or expired" | 本地内容过期（1 小时）/ proxy 内容过期（5 分钟），重新 compress |
| `pip install` 报 `CERTIFICATE_VERIFY_FAILED` | 企业 SSL 审查，先装 Rust（见 [4.6](#46-企业--ssl-审查ssl-inspection环境)），或用 `--only-binary` |
| 拉 ONNX Runtime / HuggingFace 模型被拦 | 设公司 CA（`REQUESTS_CA_BUNDLE` 等）或 `HF_HUB_OFFLINE=1` / `ORT_STRATEGY=system` |
| Windows 上 `hnswlib` 编译失败 | 装 VS C++ Build Tools，或用预构建 wheel `--only-binary` |
| proxy 复用时新 export 的开关不生效 | `headroom wrap` 已支持热同步；仍建议**先 export 再 wrap** |
| 想完全关闭压缩当网关 | `headroom proxy --no-optimize` 或 `HEADROOM_MODE=passthrough` |

---

## 14. 参考资料

**官方（权威，优先）**

- GitHub 仓库：[chopratejas/headroom](https://github.com/chopratejas/headroom)
- 官方文档站：[Headroom Docs](https://headroom-docs.vercel.app/docs)
  - [Quickstart](https://headroom-docs.vercel.app/docs/quickstart) · [Installation](https://headroom-docs.vercel.app/docs/installation) · [Proxy Server](https://headroom-docs.vercel.app/docs/proxy) · [MCP Tools](https://headroom-docs.vercel.app/docs/mcp) · [Configuration](https://headroom-docs.vercel.app/docs/configuration)
- [官方 llms.txt（文档索引）](https://headroom-docs.vercel.app/llms.txt) · [llms-full.txt（全量拼接）](https://headroom-docs.vercel.app/llms-full.txt)
- [PyPI: headroom-ai](https://pypi.org/project/headroom-ai/) · [npm: headroom-ai](https://www.npmjs.com/package/headroom-ai)
- 作者 Show HN 帖（含原理自述与用法）：[Hacker News #46628278](https://news.ycombinator.com/item?id=46628278)

**技术解读与实战（社区）**

- [Headroom: Cut Your LLM Token Usage by Up to 95%（dev.to）](https://dev.to/arshtechpro/headroom-cut-your-llm-token-usage-by-up-to-95-without-changing-your-answers-5g06)
- [Compress AI Agent Context for Claude Code, Codex, and MCP（knightli.com）](https://knightli.com/en/2026/06/06/headroom-ai-context-compression/)
- [Building Cost-Efficient Agents with Headroom（Subrat Pati, Medium）](https://subratpati.medium.com/building-cost-efficient-agents-with-headroom-context-compression-for-llm-applications-b665128153b6)
- [Cutting LLM Token Costs with rtk, headroom, and caveman（Codepointer）](https://codepointer.substack.com/p/cutting-llm-token-costs-with-rtk)
- [Headroom: The Context Compression Layer Your AI Agent Needs（alphamatch.ai）](https://www.alphamatch.ai/blog/headroom-context-compression-ai-agents-2026)

---

> **免责声明**：本报告基于 2026-06-29 时点的官方仓库 README、官方文档站与作者公开说明撰写。Headroom 仍在快速迭代（命令、参数、镜像标签可能变化），落地前请以[官方文档站](https://headroom-docs.vercel.app/docs)与[仓库](https://github.com/chopratejas/headroom)最新版本为准；关键生产配置务必用真实任务做对照验证后再推广。
