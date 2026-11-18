# M5 · 工程化与评估（Engineering & Evaluation）

在 M3 自研 RAG 管道 + M4 手动 ReAct Agent 之上，补齐**生产级工程化能力**：SSE 流式、语义缓存、成本看板、可观测性，以及**双 RAGAS 评估**。本模块独立可运行，不改动 M1–M4。

> 端口 `8083`，集合 `java_knowledge_m5`，JDK 17，Qdrant gRPC `6334`。

---

## 1. 六大交付一览

| 能力 | 实现 | 入口 |
|------|------|------|
| **SSE 流式 Agent** | 轨迹步骤（THOUGHT/ACTION/OBSERVATION/FINAL）整块实时推送 + 最终答案按 token 增量推送（双实时） | `GET /api/agent/stream` |
| **语义缓存** | 问题 embedding 近邻命中（余弦 ≥ 阈值）即回放轨迹+答案，省去检索与生成 | `SemanticCache`（Caffeine） |
| **成本看板** | 每次 LLM 响应 token × 单价累计 + 最近调用明细 | `GET /api/metrics/cost` |
| **可观测** | Micrometer 计时管道各阶段、计数工具/缓存/步数，经 actuator/Prometheus 暴露 | `/actuator/prometheus` |
| **流式问答页** | 深色玻璃拟态，EventSource 实时渲染时间线 + 打字机答案 | `/stream-agent` |
| **双 RAGAS** | Java `LLMJudgeService` 即时代理分 + Python `ragas_eval.py` 真 RAGAS 离线评估 | `/api/eval` · `eval/ragas_eval.py` |

---

## 2. 运行

```bash
# 0) 前置：Qdrant 已起（gRPC 6334），并设置百炼 Key
export DASHSCOPE_API_KEY=sk-xxxx

# 1) 首次入库语料（稠密+稀疏写入 Qdrant）
curl -X POST http://localhost:8083/api/ingest

# 2) 启动
mvn spring-boot:run        # 或 mvn package && java -jar target/m5-engineering-*.jar

# 3) 打开流式问答页
open http://localhost:8083/stream-agent
```

> 先入库（`/api/ingest`）再问答，否则 Agent 检索不到资料会如实说明。

---

## 3. 流式问答（SSE 双推送）

- **轨迹步骤**：`runStream` 在后台线程（`TaskExecutor`）驱动手动 ReAct 循环，每产生一个 `AgentStep` 即通过 `AgentEventSink.emitStep` 以 `event:step` 推到前端时间线。
- **最终答案**：FINAL 轮复用模型真实答案，按 4 字符分片经 `emitToken` 以 `event:token` 逐字推送（打字机效果）。

> 为何分片而非模型重流式：本工作区百炼 OpenAI 兼容端点的「末轮重流式」不稳定（时而为空、且双倍消耗 token），故改为对真实答案做渐进式呈现——内容 100% 来自模型，仅做呈现拆分，不丢内容、不计额外成本。

事件协议（`SseEmitterSink`）：

| 事件 | data | 含义 |
|------|------|------|
| `step` | `AgentStep` JSON | 一个轨迹节点（phase/toolName/input/output） |
| `token` | 文本片段 | 最终答案的增量 token |
| `meta` | `{citations, cacheHit}` | 收尾元信息：引用溯源 + 是否命中语义缓存 |
| `done` | `""` | 正常结束 |
| `error` | 消息 | 异常结束 |

前端 `stream-agent.html` 用 `EventSource` 监听上述事件，实时渲染时间线、增量答案与侧栏审计条。

---

## 4. 语义缓存

`SemanticCache`（Caffeine 有界，默认 `max-size=200`）：

1. 请求先经 `EmbeddingModel` 计算问题 embedding；
2. 与缓存项逐一算余弦相似度，取最大值；
3. 最佳相似度 ≥ `rag.cache.similarity-threshold`（默认 **0.92**）即命中，回放缓存的 `AgentResult`；
4. **仅缓存「含引用」的成功结果**，避免缓存幻觉答案；
5. 命中/未命中各记 Micrometer 计数（`rag.cache.hits` / `rag.cache.misses`）。

缓存命中路径**不重复检索、不计成本**，但仍发 SSE 步骤事件（UI 一致）。

配置（`application.yml`）：
```yaml
rag:
  cache:
    similarity-threshold: 0.92
    max-size: 200
```

---

## 5. 成本看板

`CostMeter` 累计每次 `ChatResponse.getMetadata().getUsage()` 的 prompt/completion tokens，按可配置单价换算成本：

```yaml
rag:
  cost:
    input-price-per-1k: 0.004    # 元 / 1k 输入 token（qwen 近似单价，按实际账单调整）
    output-price-per-1k: 0.012   # 元 / 1k 输出 token
```

查询累计快照（含最近 50 条明细）：
```bash
curl http://localhost:8083/api/metrics/cost
# => {"totalPromptTokens":...,"totalCompletionTokens":...,"totalTokens":...,
#     "totalCalls":...,"totalCost":...,"inputPricePer1k":0.004,"outputPricePer1k":0.012,
#     "recent":[{"time":...,"stage":"agent-loop","promptTokens":...,"completionTokens":...,"cost":...}]}
```
`stage` 取值：`agent-loop`（Agent 循环每轮推理）、`generate`（M3 管道生成）、`eval`（评估调用）。

---

## 6. 可观测（Micrometer + Prometheus）

| 指标 | 含义 |
|------|------|
| `rag.pipeline.stage`（tag `stage=rewrite/retrieve/rerank/generate`） | 管道各阶段耗时 |
| `rag.agent.steps` | Agent 推理步数 |
| `rag.agent.tool.calls` | 工具调用次数 |
| `rag.cache.hits` / `rag.cache.misses` | 语义缓存命中/未命中 |
| `rag.eval.calls` / `rag.eval.errors` | 评估调用/失败次数 |
| `rag.llm.responses` | LLM 响应次数 |

查看：
```bash
curl http://localhost:8083/actuator/prometheus
```

---

## 7. 双 RAGAS 评估

### 7.1 Java 侧（即时 · 代理分）`LLMJudgeService`
用 LLM 当裁判，对 `(问题, 检索上下文, 答案)` 打 0–1 代理分：`faithfulness` / `answer_relevancy` / `context_relevancy`。失败仅返回 `error` 字段，**绝不阻断主问答链路**。

```bash
# 单次
curl -X POST http://localhost:8083/api/eval \
  -H 'Content-Type: application/json' \
  -d '{"question":"Spring 怎么解决循环依赖？","contexts":["..."],"answer":"..."}'

# 批量
curl -X POST http://localhost:8083/api/eval/batch -H 'Content-Type: application/json' -d '[...]'

# 最近评估表
curl http://localhost:8083/api/eval/recent
```

### 7.2 Python 侧（离线 · 真 RAGAS）`eval/ragas_eval.py`
调用真 ·RAGAS 库做权威评估，与 Java 侧代理分互补：
- 读取 `eval/questions.json` 问题集；
- 逐个调 `POST /api/agent` 采集 `finalAnswer` 与 OBSERVATION 里的检索原文作为上下文；
- 用 RAGAS 计算 `faithfulness / answer_relevancy / context_relevancy`；
- 输出 `eval/report.json` 与汇总。

```bash
export DASHSCOPE_API_KEY=sk-xxxx
pip install ragas langchain-openai datasets openai
python3 eval/ragas_eval.py --questions eval/questions.json --out eval/report.json
# 仅采集不跑 RAGAS：python3 eval/ragas_eval.py --no-ragas
```

---

## 8. LLM Wiki 专题（概念 + 在本项目的落地）

### 8.1 什么是 LLM Wiki，与普通 RAG 的区别
- **普通 RAG**：`query → 检索扁平切片 → 拼上下文 → 生成`。知识是隐式的、临时的、无结构的——每次只取 top-K 片段，片段之间无关联。
- **LLM Wiki**：用 LLM 把语料**持续沉淀成结构化、可互链、能演进的知识库**（像自动生成的维基百科）。多出三层普通 RAG 没有的能力：
  1. **结构化**：碎片被整理成「实体/概念页面」+ 关系；
  2. **互链**：页面之间用关系相连（"Wiki"的核心隐喻是超链接/图谱）；
  3. **演进**：新知识到来时**修订旧页面**（版本化、合并关系、追加来源），而非重建索引。

一句话：**RAG 是 LLM Wiki 内部用于"建库/查询"的一个环节；Wiki 在 RAG 之上多了一整层"知识建构与维护"。**

### 8.2 本项目如何实现（M5 新增）
1. **实体/关系抽取** `wiki/EntityExtractor`：入库阶段对每「节」调 LLM，抽取 `{entities:[{name,type,summary}], relations:[[s,r,o],...]}`，复用 `LLMJudgeService` 的健壮 JSON 解析；token 计入 `CostMeter`（stage=`wiki-extract`）。
2. **知识图谱** `wiki/KnowledgeGraph`：`Map<name, EntityNode>`（节点含 type/summary/version/sources/出边），本地 `wiki/kg.json` 持久化 + 启动加载，无外部依赖（存储层可平滑替换为 Neo4j）。
3. **页面树**：顶层分类直接复用 `corpus/{spring,database,distributed,business-scenarios}/` 目录；实体聚合为 WikiPage。
4. **增量修订**：重跑 `/api/ingest` 时按实体名（归一化）合并——关系取并集、摘要追加多来源、来源去重、`version++`，实现"演进而非重建"。
5. **浏览式 UI** `/wiki`：左侧分类/实体树，中间 WikiPage（摘要+出/入关系+溯源），顶部搜索。

### 8.3 端点
```bash
POST /api/ingest                         # 入库时同步构建/修订知识图谱（kg.json 落盘）
GET  /api/wiki/tree                      # 分类 → 实体树
GET  /api/wiki/page?name=Spring%20IoC    # 某实体 WikiPage 详情
GET  /api/wiki/search?q=循环依赖          # 实体名/关键词模糊搜索
GET  /wiki                               # 浏览式 Wiki 页面
```
> 抽取为入库的**附加步骤**：LLM 抽取失败仅跳过该节、记 warn，不影响主 RAG 入库。无 `DASHSCOPE_API_KEY` 时跳过抽取，不阻断问答。

---

## 9. 关键设计点（防回归）

- 沿用并保留 `OpenAiChatOptions.internalToolExecutionEnabled(false)`，手动 ReAct 循环才生效；
- SSE 处理客户端断开（`IOException` → `SseClosedException`）与 `SseEmitter` 超时，避免线程泄漏；
- 语义缓存命中路径同样发 SSE 步骤事件，且不计成本、不重复检索；
- 评估层为旁路：任何失败都降级，不影响主问答链路；Python 脚本与 Java 运行解耦。

## 10. 目录结构
```
src/main/java/com/example/rag/
  agent/   AgentEventSink · AgentOrchestrator(runStream) · SemanticCache
  metrics/ CostMeter
  eval/    LLMJudgeService
  wiki/    EntityExtractor · LlmEntityExtractor · KnowledgeGraph · WikiPage
  web/     AgentController(SSE) · ApiController(/metrics/cost) · EvalController · WikiController · PageController
  rag/     PipelineService(Micrometer 计时) · AnswerService(用量捕获) · HybridSearcher(RRF) · Reranker
src/main/resources/templates/{ask,agent,stream-agent,wiki}.html
src/main/resources/static/css/{stream-agent,wiki}.css
wiki/kg.json                       # 运行时生成：Wiki 知识图谱持久化
eval/ragas_eval.py · eval/questions.json
```
