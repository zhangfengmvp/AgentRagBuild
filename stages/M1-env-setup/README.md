# M1 · env-setup（阶段说明 + 学习计划）

> 【迭代内容：从零搭建】本阶段是 7 个递进子项目的第一个，**从零**建立可运行骨架，后续 M2–M7 均在此结构之上演进。

## 一、阶段性学习目标
- 跑通"本地向量库 + 云端模型"的最小基础设施。
- 理解 Qdrant 与百炼 DashScope 在 RAG 中的角色分工。
- 建立可复用、可独立部署的 Spring Boot 工程骨架与状态自检页。

## 二、实战内容
1. 编写 `docker-compose.yml`，一条命令起本地 **Qdrant**（端口 6333）。
2. 配置百炼 **DashScope API Key**（环境变量 `DASHSCOPE_API_KEY`）。
3. 搭建 **Spring Boot 3** 空骨架（Web + Thymeleaf）。
4. 实现 **状态/配置自检页**（`/` 网页 + `/api/status` JSON）：展示 Qdrant 可达性、Key 是否已配置、整体 READY/NOT_READY。
5. 建立 `corpus/` 语料目录骨架（后续阶段喂 Java 技术 Markdown）。

## 三、当前架构与技术栈
- 运行时：Java 17 + Spring Boot 3.3
- Web：Spring MVC + Thymeleaf（纯服务端渲染，**无前端构建**）
- 本地向量库：**Qdrant**（Docker，端口 6333）
- 云端模型：**阿里百炼 DashScope**（embedding / LLM，后续阶段使用）
- 依赖：本期仅 Spring Boot（web / thymeleaf / actuator）。Spring AI 依赖（spring-ai-qdrant-store、spring-ai-alibaba-starter-dashscope）**在 M2 才真正引入**，届时用对齐版本（qdrant-store `1.0.0-M6`、百炼 `com.alibaba.cloud.ai:1.0.0.2`），避免 M1 背负版本冲突。
- 本阶段尚未接入向量读写与 LLM 调用，仅完成"环境就绪"验证。

## 四、企业级迭代与替换指引
- 本地 Qdrant 单机 → 企业：百炼托管向量库 / 自建 Milvus 集群（K8s + 持久化 + 多副本 + 访问控制）。
- `docker compose up` → 企业：K8s(Helm/Operator) + HPA + TLS/认证 + 配置中心。
- 环境变量明文 Key → 企业：密钥管理（KMS / Vault）+ 模型网关统一鉴权。
- 单机裸奔 → 企业：纳入 CI/CD、监控告警、日志聚合。

## 五、前置准备（安装·账号·环境变量）
- 安装 Docker（含 Docker Compose v2）。
- 安装 JDK 17、Maven 3.9+。
- 注册阿里云账号，开通百炼（DashScope），获取 API Key。
- 配置环境变量：`export DASHSCOPE_API_KEY=你的Key`（建议写入 `.env` 或由 shell 管理，勿提交仓库）。

## 六、部署与访问（单机）
```bash
cd stages/M1-env-setup
docker compose up -d            # 起 Qdrant
export DASHSCOPE_API_KEY=你的Key
mvn spring-boot:run            # 启动应用（或 mvn package 后 java -jar target/*.jar）
```
- 网页访问：http://localhost:8080 （状态/配置自检页）
- API 校验：http://localhost:8080/api/status
- 验证标准：Qdrant ✅ 可达 且 DashScope Key ✅ 已配置 → 页面/接口显示 `READY`。

> 构建前请核对 `pom.xml` 中 Spring AI 与 spring-ai-alibaba 的兼容版本（见百炼官方文档），版本号随发布节奏变化。
