# AI Integration Mapping Studio

AI 接口字段映射工作台 — 第一阶段 MVP。

采用 **AI 自动推荐 + 人工确认 + 配置化保存 + 代码生成**，聚焦场景：

`我们系统采购订单 → 金蝶云星空采购订单`

## 第一阶段已完成

- 完整项目骨架（Kotlin / Spring Boot + Vue 3 / TypeScript / Element Plus）
- 数据库表结构（H2 可直接启动，schema 兼容 MySQL）
- 核心 Domain：`Customer` / `IntegrationScenario` / `MappingConfiguration` / `FieldMapping` / `MappingHistory`
- 后端 REST API（客户、场景、Schema、推荐、保存、必填检查、代码生成）
- Mock Company MCP / Mock Kingdee MCP（可后续替换为真实 MCP）
- Mock AI Mapping Recommendation（可切换真实大模型）
- Mapping Studio 三栏页面（源 Schema / Mapping 表 / 金蝶 Schema）

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Kotlin, Spring Boot 3.2, Spring Data JPA |
| 前端 | Vue 3, TypeScript, Element Plus, Vite |
| 数据 | H2（默认）/ MySQL（可切换） |
| MCP | 接口抽象 + Mock 实现 |
| AI | Provider 抽象 + Mock 实现 |

## 环境要求

- JDK 17+
- Node.js 18+
- Maven 3.8+

## 快速启动

### 1. 启动后端

```bash
cd backend
# Windows PowerShell 示例（若已安装 JDK 17）
$env:JAVA_HOME="D:\develop\jdk-17"
mvn spring-boot:run
```

后端默认：http://localhost:8080  
H2 Console：http://localhost:8080/h2-console  
（JDBC URL：`jdbc:h2:file:./data/aims`，用户 `sa`，密码空）

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认：http://localhost:5173（已代理 `/api` → `8080`）

### 3. Mock 完整流程

1. 选择 **客户 A**
2. 选择 **采购订单 → 金蝶采购订单**
3. 点击 **AI 自动推荐**
4. 在中间表格确认/修改 Mapping（直接映射 / 固定值 / 默认值 / 字典转换 / 忽略）
5. 点击 **必填检查**
6. 点击 **保存 Mapping**
7. 点击 **生成代码** → 得到 `KingdeePurchaseOrderDTO.kt` / `PurchaseOrderMapper.kt`

## 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/customers` | 客户列表 |
| GET | `/api/integration-scenarios` | 场景列表 |
| GET | `/api/schemas/source?scenarioCode=` | 源系统 Schema |
| GET | `/api/schemas/kingdee?customerId=&formId=` | 金蝶 Schema |
| POST | `/api/mappings/recommend` | AI 推荐 Mapping |
| GET | `/api/mappings?customerId=&scenarioCode=` | 获取已保存 Mapping |
| POST | `/api/mappings` | 保存 Mapping |
| POST | `/api/mappings/check-required` | 必填字段检查 |
| POST | `/api/code-generation` | 生成 Kotlin 代码 |

## 配置

`backend/src/main/resources/application.yml`：

```yaml
aims:
  mcp:
    company:
      mode: mock   # 后续可改为 real
    kingdee:
      mode: mock
  ai:
    provider: mock # 后续可改为 openai 等
```

## 项目结构

```
backend/
  src/main/kotlin/com/aims/
    controller/
    application/
    domain/{customer,scenario,schema,mapping}/
    infrastructure/{mcp,ai,persistence}/
    config/
frontend/
  src/
    api/
    components/
    views/MappingStudio/
    types/
```

## 后续阶段（文档 Phase 2–8）

- 接入真实 Company MCP / Kingdee MCP
- 接入真实大模型（结构化 JSON）
- 三层 Mapping（标准模板 / 客户配置 / 实例覆盖）
- 完善 Code Generator 与联调能力
