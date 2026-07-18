package com.aims.application

import com.aims.application.dto.KingdeeMcpStatusDto
import com.aims.infrastructure.mcp.core.KingdeeMcpProperties
import com.aims.infrastructure.mcp.core.McpGateway
import com.aims.infrastructure.mcp.kingdee.McpKingdeeMcpClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

@Service
class KingdeeMcpStatusService(
    private val properties: KingdeeMcpProperties,
    private val mcpGatewayProvider: ObjectProvider<McpGateway>,
    private val realClientProvider: ObjectProvider<McpKingdeeMcpClient>
) {
    fun status(): KingdeeMcpStatusDto {
        val mode = properties.mode.lowercase()
        if (mode != "real") {
            return KingdeeMcpStatusDto(
                mode = "mock",
                connected = false,
                transport = null,
                metadataToolAvailable = false,
                message = "当前为 Mock 模式"
            )
        }
        val gateway = mcpGatewayProvider.ifAvailable
        if (gateway == null) {
            return KingdeeMcpStatusDto(
                mode = "real",
                connected = false,
                transport = properties.transport,
                metadataToolAvailable = false,
                message = "MCP Gateway 未初始化"
            )
        }
        return try {
            if (gateway is com.aims.infrastructure.mcp.core.SdkMcpGateway && !gateway.isConnected()) {
                gateway.connect()
            }
            val tools = gateway.listTools().map { it.name }
            KingdeeMcpStatusDto(
                mode = "real",
                connected = gateway.isConnected(),
                transport = properties.transport,
                metadataToolAvailable = properties.metadataTool in tools,
                availableTools = tools,
                message = if (gateway.isConnected()) "已连接 Kingdee MCP" else "Kingdee MCP 未连接"
            )
        } catch (ex: Exception) {
            KingdeeMcpStatusDto(
                mode = "real",
                connected = false,
                transport = properties.transport,
                metadataToolAvailable = false,
                availableTools = realClientProvider.ifAvailable?.availableTools().orEmpty(),
                message = ex.message ?: "Kingdee MCP 未连接"
            )
        }
    }
}
