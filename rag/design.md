# DocumentDB 向量存储与向量检索 — 设计方案与实现方案

> 适用范围：`C:\software\cc\team\rag` 知识库原型的生产后端（英文语料，GB 级，部署于 AWS ECS）。
> API 形态均依据官方文档核实（2026-10）：
> [Vector search](https://docs.aws.amazon.com/documentdb/latest/devguide/vector-search.html) ·
> [Text search](https://docs.aws.amazon.com/documentdb/latest/devguide/text-search.html)

---

## 1. 背景与设计约束

| 约束 | 来源 | 对本设计的影响 |
|---|---|---|
| DocumentDB **无原生 hybrid 检索** | 官方文档确认 | 采用「两路查询 + 客户端 RRF 融合」 |
| 向量索引**上限 2000 维** | 官方文档 | 嵌入选 1024 维（Titan V2），留余量 |
| text 索引**仅支持英文** | 官方文档 | 与英文为主语料匹配；中文语料需换方案 |
| 每集合**仅允许一个** text 索引 | 官方文档 | 只对 `text` 字段建索引 |
| 向量检索需 **DocumentDB 5.0+ 实例集群** | 官方文档 | 弹性集群（serverless）不适用，选实例集群 |
| 经典算子不返回相似度分值 | 官方文档 | 融合只用排名（RRF），不依赖分数可比性 |

## 2. 总体架构

```
摄取（一次性/增量，ECS 任务）                         查询（ECS 常驻服务）
──────────────────────────────                      ─────────────────────────────
文档(PDF/DOCX/MD/TXT)                                Agent ──POST /search──► FastAPI
   │ 解析 parse.py（文本+位置元数据）                          │
   │ 切片 chunking.py（~800 token + 120 重叠）                 ├─ 嵌入查询 Bedrock Titan V2
   │ 嵌入 Bedrock Titan Embeddings V2 (1024维)                ├─ 路径A: $search.vectorSearch (HNSW)
   └─ 入库 DocumentDB upsert(幂等)                            ├─ 路径B: $text/$search (text索引)
                                                             └─ RRF融合(k=60) → top-k+来源引用
```

## 3. 数据模型

### 3.1 Chunk 文档结构（集合 `rag.chunks`）

```json
{
  "_id": "a3f9c2e18b04d7f5",          // 确定性 id = sha1(source|location|text前200)[:16]
  "text": "Blue-green deployment is the recommended rollout strategy...",  // 切片正文（text索引字段）
  "source": "docs/runbook/deployment-guide.md",   // 来源文件路径
  "location": "page 3",                // 文件内位置（PDF页码等），答案引用用
  "embedding": [0.012, -0.034, ...]    // 1024 维 float，Titan V2 normalize=true
}
```

设计要点：

- **确定性 `_id`**：重复摄取同一文档时自然幂等（upsert 覆盖），无需先删后插
- **`text` 单字段承载 text 索引**：集合只允许一个 text 索引，全部词法检索集中于正文
- **`source` + `location` 不可省略**：检索结果必须能引用出处（Agent 生成答案时引用）
- 嵌入向量随文档存储（不拆集合），一次读取拿到全部字段，`$project` 排除后无带宽浪费

### 3.2 索引设计

| 索引名 | 类型 | 定义 | 用途 |
|---|---|---|---|
| `_id_`（自带） | 唯一 | `{_id: 1}` | 幂等 upsert、按 id 取 chunk |
| `embedding_hnsw` | HNSW 向量 | `{embedding: "vector"}` + vectorOptions（见 §4） | 语义检索（路径 A） |
| `chunk_text_index` | text | `{text: "text"}` | 关键词检索（路径 B） |

## 4. 向量索引设计（HNSW）

| 参数 | 取值 | 说明 |
|---|---|---|
| `type` | `hnsw` | 无训练步骤，可空集合建索引；查询性能与召回优于 IVFFlat（官方推荐默认） |
| `dimensions` | `1024` | Titan V2 输出维度；上限 2000，留升级余量 |
| `similarity` | `cosine` | Titan V2 已归一化，cosine ≡ dotProduct，语义检索标准选择 |
| `m` | `16` | 官方默认；增大提升召回但增加建索引时间与索引体积 |
| `efConstruction` | `64` | 官方默认；需 ≥ 2×m |
| `efSearch`（查询时） | `128`（可配 `EF_SEARCH`） | 召回/延迟的运行时调节旋钮，范围 [1,1000]，官方默认 40 |

不选 IVFFlat 的理由：IVFFlat 建索引前需先导数据（lists 参数依赖数据量估算：≤100 万文档用 docs/1000，>100 万用 √docs），且召回通常低于 HNSW；GB 级语料（约百万 chunk）处于两种估算的切换边界，HNSW 免除该调参负担。

## 5. 混合检索设计

### 5.1 查询流程

```
query ──► embed_query(Titan V2)
             │
             ├─ 路径A 语义: aggregate([{ $search: { vectorSearch: {
             │            vector, path:"embedding", similarity:"cosine",
             │            k: fetch_k, efSearch:128 } }}, { $project:{embedding:0} }])
             │
             ├─ 路径B 词法: find({ $text: { $search: query } },
             │            { score:{ $meta:"textScore" }, embedding:0 })
             │            .sort(score).limit(fetch_k)
             │
             └─ 融合: RRF  score(d) = Σ 1/(60 + rank_i(d))   → 排序取 top_k
```

- **fetch_k = max(3×top_k, 20)**：两路各超量取回，给 RRF 融合留空间
- **RRF 只用排名**：规避两路分值不可比（cosine vs textScore）与经典向量算子无分值的问题
- **模式开关**：`hybrid`（默认）/ `vector` / `keyword`，API 层透传，便于评测对比

### 5.2 为什么 RRF（k=60）

标准常数来自 RRF 原论文（Cormack et al.）；对单路排名居前的结果给约 1/61 的边际分，
两路同时命中的结果显著增益——恰好鼓励「语义与词法都认可」的高置信 chunk。
k 越大排名差异越平缓（对深度排名更宽容），60 是文献与工业实践通用值。

## 6. 关键限制与容量规划

| 项 | 数值/结论 |
|---|---|
| 向量维度上限（可索引） | 2000（存储不索引可达 16000） |
| 语料规模 → chunk 数 | 1GB 英文文本 ≈ 2.5–3.5 亿 token ≈ 30–45 万 chunk |
| 原始向量体积 | 1024 维 float32 ≈ 4KB/条；100 万 chunk ≈ 4GB 原始向量（另有正文与索引开销） |
| 单文档 text lexeme 上限 | 1MB（800 token 切片远低于此） |
| 实例建议 | r5 系列起步（内存型，HNSW 图常驻内存收益最大），按 p99 延迟横向扩副本 |
| 连接管理 | 每任务连接池 ≤100，避免规模放大时撞实例连接上限（FAQ 文档案例） |

## 7. 实现方案（代码映射）

| 设计点 | 实现位置 | 说明 |
|---|---|---|
| 文档模型/upsert | `app/store/documentdb.py: upsert_chunks()` | `ReplaceOne(_id, doc, upsert=True)`，500 条/批 |
| 索引创建（幂等） | `setup()` / `scripts/create_indexes.py` | `list_indexes` 查名去重；向量索引用 `db.command(createIndexes)`（部分驱动 createIndex 传 vectorOptions 会报错，官方建议 runCommand）；text 索引用 pymongo TEXT |
| 路径 A | `vector_search()` | `$search.vectorSearch` + `$project` 排除 embedding；分值缺省按 -rank 记录（融合只用排名） |
| 路径 B | `keyword_search()` | `$text` + `textScore` 投影/排序 + limit |
| RRF 融合 | `app/fusion.py: rrf_fuse()` | 纯函数，两后端共用 |
| 编排/模式开关 | `app/search.py: search()` | 嵌入 → 两路 → 融合 → 附双路排名返回 |
| 连接/TLS | 构造函数 | `DOCDB_CA_PATH` 配置时启用 `tls=True + tlsCAFile`（global-bundle.pem） |

### 摄取实现要点

1. `rglob` 遍历 → 按扩展名过滤（PDF/DOCX/MD/TXT）
2. 每文件解析为 (text, location) 单元 → 切片（确定性 id）
3. 32 条/批嵌入（Bedrock 线程池并发 8 + 指数退避重试 4 次）
4. 500 条/批 upsert → 全程幂等，可断点重跑

### 部署顺序

1. 建 DocumentDB 5.0+ 实例集群（与 ECS 同 VPC，SG 放行 27017）
2. **先跑 `scripts/create_indexes.py` 建索引再导数据**（text 索引官方建议先建；HNSW 无训练步骤，先后皆可但先建更稳）
3. 跑一次性摄取任务（ECS task）
4. 起常驻 API 服务（ALB 健康检查 `/healthz`）

## 8. 安全与运维

- **传输**：强制 TLS（`tls=true` + `tlsCAFile=global-bundle.pem`）
- **凭证**：`DOCDB_URI` 含密码 → 存 Secrets Manager，ECS 任务定义 secrets 注入，不进镜像/环境明文
- **IAM**：任务角色需 `bedrock:InvokeModel`（`amazon.titan-embed-text-v2:0`）
- **监控**：`/healthz` 的 chunk 计数、两路查询各自 p99、RRF 融合命中率（同一 id 双路同时上榜比例——可作为检索健康度指标）
- **调优入口**：`EF_SEARCH`（召回/延迟）、`CHUNK_TARGET_TOKENS`（切片质量）、`top_k/fetch_k`

## 9. 已知限制与演进方向

| 限制 | 缓解/演进 |
|---|---|
| text 索引仅英文（多语言语料不可用） | 中文/混合语料 → 换 OpenSearch（原生 hybrid + 分词器）或 BGE-M3 稀疏向量方案 |
| 经典向量算子无相似度分值 | 融合已不依赖；如需展示相关度可迁 DocumentDB 8.0 的 `$vectorSearch` 算子 |
| 大文档改动后旧 chunk 残留 | 摄取前按 `source` 前缀删除，或周期性全量 reset 重建 |
| 无重排序 | RRF 后、截断前接入 Cohere Rerank（Bedrock 托管），接口位置已预留 |
| 检索质量未量化 | 上线前建 50–100 条真实问题 golden set，评端到端答案质量（调研结论：勿只看检索指标） |
