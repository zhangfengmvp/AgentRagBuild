# 项目备忘录（可携带版 · 精简）

> 用途：在任何新会话中粘贴本文件，对方即可 1:1 复现我们对「RAG+Agent 专项学习项目」的全部意图与已锁定决策。本文件即计划的自包含快照，与 `plan.md` 等价，但更易携带。

---

## 0. 角色与目标
- **用户画像**：从业 15 年的 Java 后端求职者；既是本项目的**初创者**，也是通过项目完成学习的**学习者**，未来希望把项目**分享给其他人**。
- **核心目标**：通过"实际项目 + 专项训练"掌握 RAG 与 Agent，提升理解/认知/实战经验，转化为**可面试、可分享的作品集**。
- **硬性要求**：学习目标 / 知识点 / 实操经验**全部贴近企业实际生产应用**；说明企业环境复杂度与服务费；标明"学习项目落地到企业需如何调整"。

---

## 1. 业务场景（已锁定 · 勿重新推导）
- **项目定位**：面向 **Java 后端从业者**的智能技术知识助手（自研版 Java 技术 Wiki + RAG 问答 + Agent）。
- **覆盖主题**：① Java 面试八股文；② 分布式（CAP/一致性/分布式锁/事务/ID 生成）；③ 数据库（MySQL 索引·事务·分库分表、Redis 缓存）；④ Spring（IOC/AOP/事务/循环依赖/Bean 生命周期）；⑤ 经典业务场景方案（秒杀、幂等、缓存一致性、限流熔断等）。
- **语料组织**：按主题分目录的 Markdown（`interview/ distributed/ database/ spring/ business-scenarios/`）。来源以自有整理 / CC 内容为主，不内嵌大段受版权保护文本。整理语料即复习 Java 体系。
- **企业平移卖点**：架构与语料解耦——替换为"企业私有 Wiki / 合规库 / 客服 FAQ"架构完全复用。面试叙事：**"本项目即企业知识库通用模板，仅语料不同"**。

---

## 2. 技术栈（已锁定）
- Java 17 + Spring Boot 3（Web 用 Spring MVC + Thymeleaf / 静态 HTML+JS，**无 Node/npm 前端构建**）
- RAG 编排：**Spring AI**（`spring-ai-alibaba` 对接百炼）为主，**LangChain4j** 作对照模块
- 本地向量库：**Qdrant**（Docker 单命令）；pgvector 作 Postgres 路线备选；MySQL 9.0 vector 标注非典型不作主线
- 云端：**阿里百炼**（DashScope embedding / LLM；百炼知识库通过 `bl` 体验）
- 评估：**Python + RAGAS**（仅量化评估，跨阶段脚本 `scripts/eval_ragas.py`）
- 可观测：OpenTelemetry + 日志埋点

---

## 3. 硬约束（务必遵守）
1. **不本地部署大模型**：LLM 推理走百炼云端 SaaS。
2. **递进式但物理独立**：`stages/M1..M7` 各自是独立 Spring Boot 子项目，自带 `pom.xml` + `docker-compose.yml`，可单独部署、**彼此零依赖**；每个阶段 README 标注【迭代内容】。
3. **单机部署**：每个阶段 = 一个 Spring Boot 应用 + 本地 Qdrant，通过自带 `docker-compose.yml` 在**一台机器**拉起（应用 jar 与 Qdrant 容器同机）；"单机"指自托管组件同机、模型云端，**不要求离线**（离线需本地模型，违背约束 1）。企业落地可平滑升级 K8s 多副本。
4. **网页可访问**：每个阶段除 REST/SSE API 外，均提供浏览器演示页（默认 `http://localhost:8080`）。M1=状态/配置校验页；M2+=问答页（含引用溯源）；M4+=Agent 工具调用过程可视化；M5+=流式输出；M7=成品级 UI。
5. **四类文档**（见第 5 节）。

---

## 4. 文档体系（四类）
| 文档 | 读者 | 位置 | 内容要点 |
|------|------|------|---------|
| 项目说明 | 别人（通俗） | 顶层 `README.md` | 产品功能 / 解决什么问题 / 对读者价值 / 快速开始 / 单机部署 |
| 学习计划说明 | 自己 | 顶层 `LEARNING-PLAN.md` | 学习目标 / 整体计划 / 周排期 / 自测清单 |
| 阶段学习计划 | 自己 | `stages/Mn/README.md` 内 4 节 | 阶段性目标 / 实战内容 / 当前架构技术栈 / 企业级迭代与替换指引 |
| 阶段说明（准备） | 自己/协作者 | `stages/Mn/README.md` 内 | 前置准备：安装·账号·环境变量 |

> 每个 `stages/Mn/README.md` 统一含 **6 节**：阶段性学习目标 / 实战内容 / 当前架构与技术栈 / 企业级迭代与替换指引 / 前置准备（安装·账号·环境变量）/ **部署与访问（单机启动命令·URL·验证）**，并突出 **【迭代内容】** 区块（M1 写"从零搭建"，其余写"相对上一阶段新增/修改了哪些模块、类、配置"）。

---

## 5. 企业级生产落地对照（贯穿全计划）
- **企业向量格局三层**：① 托管向量服务（百炼知识库 / AWS Bedrock KB / Azure AI Search / Pinecone / Zilliz Cloud）最主流；② 自建专用向量库（Milvus 国内大厂首选 / Qdrant / Weaviate）跑 K8s；③ Postgres+pgvector 务实中型；④ 混合检索（稠密+BM25/ES）生产标配。
- **复杂度**：K8s/容器编排、私有云/VPC 隔离、安全合规（数据不出域/加密/审计）、高可用与扩缩容、统一鉴权与多租户、监控告警。
- **服务费**：托管向量 / 百炼按量或订阅；自建需算力+存储+运维。学习期近乎零成本，须能讲清企业 **TCO（总拥有成本）**。
- **各里程碑企业调整批注**：env-setup→K8s/认证/持久化/多副本；rag-basic→模型网关/向量库加密；rag-pipeline→托管混合/内容安全/权限过滤；agent-layer→内网系统接入+审计；engineering-eval→接 APM/CI 门禁；platform-compare→采购视角(TCO/合规/SLA/锁定)；portfolio→高可用/灰度/监控/回滚。

---

## 6. 七里程碑（含依赖）
| # | 阶段 | 做什么 | 依赖 | 企业对照 |
|---|------|--------|------|---------|
| M1 | env-setup | Docker 起 Qdrant + 配百炼 Key + Spring Boot3 骨架 + 状态页 | — | K8s/认证/持久化/多副本 |
| M2 | basic-rag | 解析/切片/百炼 embedding/Qdrant 检索 + 问答页（引用溯源） | M1 | 模型网关/向量库加密 |
| M3 | rag-pipeline | 混合检索 + Rerank + Query 改写 + 增强问答页 | M2 | 内容安全/权限过滤 |
| M4 | agent | 接百炼 ReAct/Tool Calling/MCP + Agent 过程可视化页 | M3 | 内网系统接入+审计 |
| M5 | engineering | SSE 流式/语义缓存/成本看板/可观测 + 流式问答页 + RAGAS | M3 | 接 APM/CI 门禁 |
| M6 | platform-compare（研究型） | 研读百炼知识库 vs Dify/Milvus，产出对比 + TCO 说明页（非代码演进） | M5 | 采购视角 |
| M7 | portfolio | 整合 M1-M5 为生产级 Java 技术知识助手 + 成品 UI + 企业平移说明 | M4,M5,M6 | 高可用/灰度/监控/回滚 |

> 逻辑递进：M2=M1+RAG 管道；M3=M2+混合检索/Rerank；M4=M3+Agent；M5=M4+工程化；M6 研究对比；M7 整合生产化。

---

## 7. Agent 工具设计（场景化，M4 引入）
- **T1 知识库检索**：RAG 取 Java 文档片段（核心工具）
- **T2 代码片段检索/生成**：Spring/分布式示例片段
- **T3（进阶）**：官方 Javadoc / 外部文档 API，或本地代码沙箱验证
- 编排示例：问"分布式事务如何保证最终一致" → Agent 拆解 → 检索 事务/Saga/TCC + Spring 事务 → 综合方案

---

## 8. 面试叙事（建议）
> "我做了一个面向 Java 后端工程师的智能技术知识助手，用 RAG 解决 Java 八股文/分布式/Spring 知识的精准检索与问答，并用 Agent 编排多工具做复杂问题拆解；工程上做了流式、语义缓存、成本看板、可观测性。该架构与语料解耦，可直接平移为企业私有知识库。项目以递进式独立子项目组织，每个阶段可单机部署、网页演示，便于分享与单阶段讲解。"

---

## 9. 百炼技能（Agent Extensions）
- **bailian-cli**：百炼资源（鉴权/知识库/embedding/模型/用量），跑通云端能力并体验百炼知识库平台；对比自研 Qdrant 与百炼托管差异。
- **bailian-docs-llm-wiki**：查询百炼模型/API参数/错误码/知识库/RAG文档与定价，支撑"企业 TCO/采购"笔记。

---

## 10. 目录结构（最终）
```
AgentRagBuild/
├── README.md                  # 项目说明（给别人）
├── LEARNING-PLAN.md           # 学习计划说明（给自己）
├── PROJECT-MEMO.md            # 本文件（可携带快照）
├── stages/
│   ├── M1-env-setup/          # 独立可部署：Qdrant + 百炼Key + Spring Boot 骨架 + 状态页
│   │   ├── README.md          # 6节 +【迭代：从零】
│   │   ├── docker-compose.yml  # Qdrant（单机）
│   │   ├── pom.xml
│   │   ├── corpus/            # 语料骨架
│   │   └── src/main/{java,resources/{application.yml,templates|static}}
│   ├── M2-basic-rag/ ...      # 独立：RAG 管道 + 问答页（引用溯源）
│   ├── M3-rag-pipeline/ ...   # 独立：混合检索 + Rerank + Query 改写 + 增强问答页
│   ├── M4-agent/ ...          # 独立：ReAct/Tool Calling/MCP + Agent 可视化页
│   ├── M5-engineering/ ...    # 独立：SSE/缓存/成本/可观测 + 流式问答页 + RAGAS
│   ├── M6-platform-compare/ ...# 研究型：对比 + TCO 说明页
│   └── M7-portfolio/ ...      # 独立：整合 + 生产化 UI + 企业平移
└── scripts/eval_ragas.py      # 跨阶段评估脚本
```

---

## 11. 环境信息（执行 M1 前需确认填写）
- 操作系统（OS）：___
- Docker：已装 / 未装
- JDK 版本：___（需 17+）
- Maven：已装 / 未装
- 百炼 API Key（DASHSCOPE_API_KEY）：已有 / 待注册

---

## 12. 执行闸门（协作约定）
- **Phase A（计划/细化）**：只讨论与调整计划，**不写项目代码、不起 Docker、不配 Key**。
- **Phase B（执行）**：用户说 **"BEGIN"** 后，从 **M1-env-setup** 开始落地，按 todo 顺序推进，每完成一阶段更新对应 todo 状态。

---

## 13. 全新会话开场模板（如换工具/换环境，直接粘贴）
```
角色：我是有15年经验的Java后端求职者，要做 RAG+Agent 专项训练，产出可面试、可分享的作品集。
请先阅读本仓库的 PROJECT-MEMO.md（或 plan.md），所有决策已锁定，不要重新推导。
已锁定决策摘要：业务域=面向Java后端从业者的智能技术知识助手（八股文/分布式/DB/Spring/业务场景）；
技术栈=Java17+SB3、Spring AI(spring-ai-alibaba接百炼)+LangChain4j对照、本地向量库Qdrant(Docker)、云端百炼、评估Python+RAGAS；
硬约束=不本地部署大模型 / 递进式但物理独立(stages/M1..M7各自可独立部署、零依赖) / 单机部署(应用+Qdrant同机,模型云端) / 每阶段网页访问(默认localhost:8080)；
文档=顶层README(给别人)+LEARNING-PLAN(给自己)，每阶段README含6节+【迭代内容】；企业对照=每阶段标注企业落地如何调整；用百炼技能 bailian-cli、bailian-docs-llm-wiki。
环境：OS=___；Docker=已装/未装；JDK=___；百炼Key=有/无。
阶段划分：Phase A 只细化计划，我说 "BEGIN" 后才从 M1 执行。第一步：确认决策清单有无要改的，无则等我 BEGIN。
```
