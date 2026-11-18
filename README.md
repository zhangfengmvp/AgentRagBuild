# Java 技术知识助手 · RAG + Agent 专项学习计划（项目说明）

> 面向 Java 后端从业者的智能技术知识助手：自研版 Java 技术 Wiki + RAG 问答 + Agent。
> 本项目既是学习 RAG/Agent 的实战载体，也是可面试、可分享的作品集。

## 这是什么
一个用 Java（Spring AI）自研的 RAG 管道 + Agent 编排项目，云端走阿里百炼，本地用 Docker 一键部署 Qdrant 向量库。**每个阶段支持单机部署且提供网页访问。**

## 解决什么问题
- Java 知识散落在八股文 / 分布式 / 数据库 / Spring / 业务场景方案里，检索慢、无出处。
- 本项目把知识结构化进向量库，用 RAG 精准检索 + 大模型生成带引用的答案；复杂问题由 Agent 多工具拆解。

## 你能用它做什么 / 学到什么
- **学 Java**：把语料当复习资料，边搭边巩固 15 年功力覆盖的主题。
- **学 RAG/Agent**：从零到生产级，递进式 7 个独立子项目，每阶段可单独运行、网页演示。
- **学工程化**：流式、语义缓存、成本看板、可观测性——后端工程师的差异化护城河。
- **平移企业**：架构与语料解耦，换语料即企业私有知识库。

## 当前进度
- ✅ **M1–M7 全部完成**（7 个递进式独立子项目，各阶段见 `stages/Mn/README.md`）。
- 🎯 **作品集入口：`stages/M7-portfolio/`（端口 8084）**——统一门户 `/` 聚合增强问答 / Agent / 流式 / LLM Wiki / 成本看板 / RAGAS 评估 / 状态自检；企业平移说明见 `docs/enterprise-migration.md`，平台对比见 `docs/platform-compare.md`。

## 快速开始（以 M1 为例）
```bash
cd stages/M1-env-setup
docker compose up -d            # 起 Qdrant
export DASHSCOPE_API_KEY=你的百炼Key
mvn spring-boot:run            # 启动应用（或 mvn package 后 java -jar target/*.jar）
# 浏览器打开 http://localhost:8080
```

### 作品集快速开始（M7）
```bash
cd stages/M7-portfolio
export DASHSCOPE_API_KEY=你的百炼Key
curl -X POST http://localhost:8084/api/ingest   # 首次入库 + 构建 LLM Wiki 图谱（需 Qdrant 已起）
mvn spring-boot:run
# 浏览器打开 http://localhost:8084/  （统一门户，能力总入口）
```
详见各阶段 `stages/Mn/README.md`。

## 技术栈
Java 17 + Spring Boot 3 · Spring AI（spring-ai-alibaba 接百炼）+ LangChain4j（对照） · Qdrant（本地） · 百炼（云端） · Python+RAGAS（评估） · OpenTelemetry。

## 文档导航
- `README.md`：本文件（项目说明 · 给别人）
- `LEARNING-PLAN.md`：学习计划说明（给自己）
- `PROJECT-MEMO.md`：可携带的精简快照（换会话/换人直接看）
- `stages/M1..M7/`：7 个递进式独立子项目，每阶段 README 含 6 节 +【迭代内容】
- `docs/`：`platform-compare.md`（自研 vs 百炼/Dify/RAGFlow/Milvus + TCO）、`enterprise-migration.md`（企业平移说明）

## 企业落地对照
每个阶段 README 均标注"企业级迭代与替换指引"：自托管组件 → K8s / 托管向量 / 模型网关 / 合规审计 / APM，详见各阶段文档。
