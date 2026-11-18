# 学习计划说明（给自己）

## 一、项目背景与双角色
我是从业 15 年的 Java 后端求职者，本项目既是**初创作品**，也是**学习载体**，未来要**分享给别人**。目标：通过实际项目掌握 RAG 与 Agent，产出可面试、可分享的作品集。

## 二、总体学习目标（分层）
1. **认知**：搞清 RAG/Agent 是什么、解决什么、与微调区别。
2. **单点突破**：独立跑通 RAG 全链路 + Agent 编排。
3. **工程化**：流式 / 缓存 / 成本 / 可观测，体现后端功力。
4. **作品集**：交付可面试的生产级 Java 技术知识助手。

## 三、知识点地图
- LLM 基础：Transformer 直觉、Token、RAG vs 微调 vs 提示工程选型
- RAG 核心：解析 → 切片 → 稠密/稀疏/混合检索 → Rerank → Query 改写 → 评估（RAGAS）
- Agent 编排：ReAct / Plan-Execute / Multi-Agent / Tool Calling / MCP
- 工程化：SSE 流式、语义缓存、成本看板、可观测性（OTel）
- Java 工具链：Spring AI（主线）+ LangChain4j（对照）

## 四、整体学习计划（7 里程碑）
| 阶段 | 主题 | 交付 | 状态 |
|------|------|------|------|
| M1 env-setup | 环境一次配好 | Qdrant + 百炼 Key + Spring Boot 骨架 + 状态页 | ✅ 已完成 |
| M2 basic-rag | 基础 RAG 跑通 | 解析/切片/embedding/检索/生成 + 问答页 | ✅ 已完成 |
| M3 rag-pipeline | 自研知识库 | 混合检索 + Rerank + Query 改写 + 增强问答页 | ✅ 已完成 |
| M4 agent | Agent 编排 | ReAct / Tool Calling / 自主 ReAct + 结构性护栏 + 过程可视化页（MCP 留作扩展） | ✅ 已完成 |
| M5 engineering | 工程化与评估 | SSE 双实时推送 + 语义缓存 + 成本看板 + Micrometer 可观测 + 流式问答页 + 双 RAGAS（Java LLM-judge / Python 真 RAGAS）+ **LLM Wiki**（实体/关系抽取→知识图谱/页面树→增量修订 Agent→浏览式 UI） | ✅ 已完成 |
| M6 platform-compare | 平台对比 | 自研 Spring AI 方案 vs 百炼/Dify/RAGFlow/Milvus 对比 + TCO 笔记 + 决策框架（研究型，产出 `docs/platform-compare.md`，百炼列经官方文档校验） | ✅ 已完成 |
| M7 portfolio | 作品集整合 | 独立子项目 `stages/M7-portfolio`（端口 8084/集合 java_knowledge_m7，以 M5 为基线）+ 统一门户首页（7 能力卡片）+ 企业平移说明（`docs/enterprise-migration.md` 四层解耦/TCO/决策框架/MCP 扩展路径） | ✅ 已完成 |

> 逻辑递进、物理独立：每个阶段是独立 Spring Boot 子项目，可单独部署、彼此零依赖。

> 注：MCP 仅列于上方「知识点地图」的 Agent 编排概念中，是需*了解*的概念而非任一里程碑的强制交付；M4 仅在 `Tool` 抽象层为其预留解耦接口（真实接入作为 M7 作品集 / 企业对齐的可选扩展），故 M4 交付不含 MCP。

## 五、企业级对齐原则
每步对照企业落地：托管向量服务 / 自建 Milvus·Qdrant / pgvector；K8s、合规、服务费 TCO、高可用。学习期近乎零成本，但须能讲清企业 TCO。

## 六、学习自测清单
- [x] 能徒手画出 RAG / Agent 端到端架构（M7 门户即架构总览：检索→改写→融合→重排→生成；Agent ReAct→工具→护栏）
- [x] M2 能用 Java 语料端到端问答（`/ask` 增强问答页，含引用溯源）
- [x] M3 能解释混合检索 / Rerank 为何提升召回（稠密语义 + BM25 稀疏互补 → RRF 融合 → 重排精排，`/ask` 阶段可视化）
- [x] M4 能描述 Tool Calling 与 ReAct 流程（含"模型幻觉式谎称已调用工具"的识别，以及"自主 ReAct + 结构性护栏"对策：模型不调真实工具时强制一次真实检索兜底）
- [x] M5 能量化评估（RAGAS）并解释成本/延迟指标（双 RAGAS：Java 侧 LLMJudgeService 即时代理分 + Python 侧 ragas_eval.py 真 RAGAS 离线权威评估；成本看板按 token×单价累计；Micrometer 计时管道各阶段 / 计数工具·缓存·步数，经 /actuator/prometheus 暴露）
- [x] M5 能讲清 LLM Wiki 与普通 RAG 的区别，并落地一套 Wiki：入库时 LLM 抽取实体/关系构建知识图谱（KnowledgeGraph + 本地 wiki/kg.json 持久化），顶层分类复用 corpus/ 子目录，重入库走 merge 做增量修订（关系合并/来源追加/版本自增），提供 /wiki 浏览式页面（树+WikiPage+搜索）；能解释"结构化·互链·可演进"为何是 Wiki 区别于扁平 RAG 的关键
- [x] M6 能讲清「自研 Spring AI 方案 vs 百炼/Dify/RAGFlow/Milvus」的取舍：百炼 RAG 不支持替换底层检索/生成组件、托管智能体声明式编排、支持 MCP 与本地化 Hybrid RAG；自研差异化价值在语义缓存、双 RAGAS 评估、LLM Wiki 知识演进与全链路可控；并能复述 TCO 五项（模型/存储/运维/合规/扩容）与"build vs buy"决策框架
- [x] M7 能向面试官讲清"架构与语料解耦、可平移企业"（四层解耦：语料源/向量库/模型层/Tool 抽象；TCO 五项 + build-vs-buy；MCP 扩展路径；统一门户 `stages/M7-portfolio/` + `docs/enterprise-migration.md`）
