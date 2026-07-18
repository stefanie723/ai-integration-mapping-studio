package com.aims.infrastructure.mcp.core

data class McpToolDefinition(
    val name: String,
    val description: String? = null
)

data class McpToolResult(
    val contentText: String,
    val isError: Boolean = false,
    val structuredContent: Any? = null
)

class McpException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Transport-agnostic MCP gateway for listing/calling tools.
 * Reusable by Kingdee / Company MCP clients.
 */
interface McpGateway {
    fun listTools(): List<McpToolDefinition>
    fun callTool(name: String, arguments: Map<String, Any?>): McpToolResult
    fun isConnected(): Boolean
    fun close()
}
