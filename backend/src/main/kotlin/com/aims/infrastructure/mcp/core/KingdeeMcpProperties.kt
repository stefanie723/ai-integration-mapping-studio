package com.aims.infrastructure.mcp.core

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "aims.mcp.kingdee")
data class KingdeeMcpProperties(
    /** mock | real */
    var mode: String = "mock",
    /** stdio | sse */
    var transport: String = "stdio",
    /** SSE base URL when transport=sse */
    var url: String = "http://localhost:8081",
    var timeout: Long = 30000,
    var stdio: StdioProperties = StdioProperties(),
    var metadataTool: String = "kingdee_get_fields",
    var listFormsTool: String = "kingdee_list_forms"
) {
    data class StdioProperties(
        var command: String = "uvx",
        var args: List<String> = listOf("kingdee-mcp"),
        var env: Map<String, String> = emptyMap()
    )
}
