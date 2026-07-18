package com.aims.infrastructure.mcp.kingdee

import com.aims.infrastructure.mcp.core.McpException
import com.aims.infrastructure.mcp.core.McpGateway
import com.aims.infrastructure.mcp.core.McpToolDefinition
import com.aims.infrastructure.mcp.core.McpToolResult
import com.aims.infrastructure.mcp.core.KingdeeMcpProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpKingdeeMcpClientExceptionTest {

    @Test
    fun `unavailable mcp returns friendly error`() {
        val gateway = object : McpGateway {
            override fun listTools(): List<McpToolDefinition> {
                throw McpException("无法连接 Kingdee MCP Server。请确认 MCP Server 已启动并检查连接配置。")
            }

            override fun callTool(name: String, arguments: Map<String, Any?>): McpToolResult {
                throw McpException("无法连接 Kingdee MCP Server。请确认 MCP Server 已启动并检查连接配置。")
            }

            override fun isConnected(): Boolean = false
            override fun close() {}
        }

        val client = McpKingdeeMcpClient(
            mcpGateway = gateway,
            converter = KingdeeMcpSchemaConverter(),
            properties = KingdeeMcpProperties(mode = "real"),
            objectMapper = jacksonObjectMapper()
        )

        val ex = assertThrows(McpException::class.java) {
            client.getFormSchema(1L, "PUR_PurchaseOrder", refresh = true)
        }
        assertTrue(ex.message!!.contains("无法连接 Kingdee MCP Server"))
    }

    @Test
    fun `missing metadata tool returns friendly error`() {
        val gateway = object : McpGateway {
            override fun listTools(): List<McpToolDefinition> =
                listOf(McpToolDefinition("kingdee_query_bills"))

            override fun callTool(name: String, arguments: Map<String, Any?>): McpToolResult =
                McpToolResult("{}")

            override fun isConnected(): Boolean = true
            override fun close() {}
        }

        val client = McpKingdeeMcpClient(
            mcpGateway = gateway,
            converter = KingdeeMcpSchemaConverter(),
            properties = KingdeeMcpProperties(mode = "real"),
            objectMapper = jacksonObjectMapper()
        )

        val ex = assertThrows(McpException::class.java) {
            client.getFormSchema(1L, "PUR_PurchaseOrder", refresh = true)
        }
        assertTrue(ex.message!!.contains("未提供获取表单元数据所需的 Tool"))
    }
}
