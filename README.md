# AI Integration Mapping Studio

AI 接口字段映射工作台。

采用 **AI 自动推荐 + 人工确认 + 配置化保存 + 代码生成**，聚焦场景：

`我们系统采购订单 → 金蝶云星空采购订单`

## 当前版本状态（V0.2）

| 模块 | 状态 |
|------|------|
| Company Schema | Mock |
| Kingdee Schema | **真实 KingdeeMCP**（可切 Mock） |
| AI Mapping | Mock |
| Mapping 保存 / 代码生成 | 可用 |

> V0.2 仅支持 **一个 KingdeeMCP 实例对应一个金蝶测试账套**。  
> `customerId` 参数保留，但不会动态切换账套（TODO V0.5）。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Kotlin, Spring Boot 3.2, Spring Data JPA, Caffeine |
| 前端 | Vue 3, TypeScript, Element Plus, Vite |
| MCP Client | 官方 MCP Java SDK `io.modelcontextprotocol.sdk:mcp:0.8.1`（stdio / SSE） |
| KingdeeMCP | [WaHaiLong/KingdeeMCP](https://github.com/WaHaiLong/KingdeeMCP) |
| 数据 | H2（默认）/ MySQL（可切换） |

## 环境要求

- JDK 17+
- Node.js 18+
- Maven 3.8+
- Real 模式额外需要：`uv` / `uvx`（或本机可执行的 `kingdee-mcp`），以及金蝶测试账套凭证

## 快速启动（Mock，默认）

### 1. 启动后端

```powershell
cd backend
$env:JAVA_HOME="D:\develop\jdk-17"
mvn spring-boot:run
```

### 2. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

- 前端：http://localhost:5173
- 后端：http://localhost:8080

## 使用真实 KingdeeMCP（V0.2 Real 模式）

### 1. 准备金蝶测试账套

在金蝶云星空后台创建集成用户并生成 AppID / AppSecret，准备：

- `KINGDEE_SERVER_URL`（需包含 `/k3cloud/`）
- `KINGDEE_ACCT_ID`
- `KINGDEE_USERNAME`
- `KINGDEE_APP_ID`
- `KINGDEE_APP_SEC`

详见：[KingdeeMCP 配置说明](https://github.com/WaHaiLong/KingdeeMCP)

### 2. 启动 KingdeeMCP

推荐（stdio，由本系统自动拉起）：

```bash
# 使用 GitHub 版（含 QueryBusinessInfo 完整元数据；PyPI 版字段很少）
uvx --from git+https://github.com/WaHaiLong/KingdeeMCP kingdee-mcp --check
```

KingdeeMCP 默认 Transport 为 **stdio**（FastMCP `mcp.run()`）。  
本系统 Real 模式默认通过 MCP Java SDK 的 **STDIO Client** 拉起：

```text
uvx --from git+https://github.com/WaHaiLong/KingdeeMCP kingdee-mcp
```

并注入上述 `KINGDEE_*` 环境变量。首次会从 GitHub 拉取，耗时更长。

Windows 注意：JVM 需以 UTF-8 运行（`mvn spring-boot:run` 已通过 `.mvn/jvm.config` 配置），否则 MCP stdio 中文会乱码导致 Schema 拉取失败。

> 若你自行以 SSE/HTTP 方式暴露 MCP Server，可将 `aims.mcp.kingdee.transport` 设为 `sse`，并配置 `url`。

### 3. 启动 Mapping Studio（Real）

```powershell
cd backend
$env:JAVA_HOME="D:\develop\jdk-17"
$env:AIMS_KINGDEE_MCP_MODE="real"
$env:AIMS_KINGDEE_MCP_TRANSPORT="stdio"
$env:KINGDEE_SERVER_URL="http://your-server/k3cloud/"
$env:KINGDEE_ACCT_ID="your-acct-id"
$env:KINGDEE_USERNAME="your-username"
$env:KINGDEE_APP_ID="your-app-id"
$env:KINGDEE_APP_SEC="your-app-secret"
mvn spring-boot:run
```

对应配置（`application.yml`）：

```yaml
aims:
  mcp:
    kingdee:
      mode: real          # mock | real
      transport: stdio    # stdio | sse
      url: http://localhost:8081
      timeout: 30000
      metadata-tool: kingdee_get_fields
      list-forms-tool: kingdee_list_forms
```

### 4. 验证 MCP 是否连接成功

1. 打开 Mapping Studio，右侧应显示：`数据来源: Kingdee MCP · 已连接`
2. 或调用：

```http
GET /api/mcp/kingdee/status
```

期望：

```json
{
  "mode": "real",
  "connected": true,
  "server": "KingdeeMCP",
  "metadataToolAvailable": true
}
```

### 5. 获取 PUR_PurchaseOrder 真实 Schema

1. 选择客户与「采购订单 → 金蝶采购订单」
2. 点击 **AI 自动推荐**（会拉取真实金蝶 Schema）
3. 或点击右侧 **刷新 Schema**（`refresh=true`，绕过 10 分钟缓存）
4. 在金蝶测试环境新增自定义字段 `F_XXXX_CUSTOM` 后点刷新，应能看到该字段

开发调试（仅 `dev` profile）：

```http
GET /api/debug/kingdee/raw-schema?formId=PUR_PurchaseOrder
```

## 实际 MCP Tool

| 项 | 值 |
|----|----|
| Transport | stdio（默认）/ sse（可选） |
| 元数据 Tool | `kingdee_get_fields` |
| 表单列表 Tool | `kingdee_list_forms` |
| 请求参数 | `{ "params": { "form_id": "PUR_PurchaseOrder", "verbose": true } }` |
| 分录钻取 | `{ "params": { "form_id": "...", "entry_key": "FPOOrderEntry" } }` |
| 底层金蝶接口 | `QueryBusinessInfo`（由 KingdeeMCP 内部调用） |

## 主要 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/customers` | 客户列表 |
| GET | `/api/integration-scenarios` | 场景列表 |
| GET | `/api/schemas/source?scenarioCode=` | 源系统 Schema |
| GET | `/api/schemas/kingdee?customerId=&formId=&refresh=` | 金蝶 Schema |
| GET | `/api/mcp/kingdee/status` | Kingdee MCP 状态 |
| POST | `/api/mappings/recommend` | AI 推荐 Mapping |
| GET | `/api/mappings?customerId=&scenarioCode=` | 获取已保存 Mapping |
| POST | `/api/mappings` | 保存 Mapping |
| POST | `/api/mappings/check-required` | 必填字段检查 |
| POST | `/api/code-generation` | 生成 Kotlin 代码 |

## 项目结构（MCP 相关）

```
backend/src/main/kotlin/com/aims/infrastructure/mcp/
  core/
    McpGateway.kt
    SdkMcpGateway.kt
    KingdeeMcpProperties.kt
  kingdee/
    KingdeeMcpClient.kt
    MockKingdeeMcpClient.kt
    McpKingdeeMcpClient.kt
    KingdeeMcpSchemaConverter.kt
    dto/
```

## 下一阶段建议

- **V0.3**：接入真实 Company MCP
- **V0.4**：真实 AI Mapping Recommendation（基于双边真实 Schema）
- **V0.5**：`customerId` → 多账套 / 多 MCP 动态路由
