package com.aims.config

import com.aims.infrastructure.mcp.core.KingdeeMcpProperties
import com.aims.infrastructure.mcp.core.McpGateway
import com.aims.infrastructure.mcp.core.SdkMcpGateway
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Real-mode startup health check. Never fails application boot.
 */
@Component
@ConditionalOnProperty(prefix = "aims.mcp.kingdee", name = ["mode"], havingValue = "real")
class KingdeeMcpHealthChecker(
    private val mcpGateway: McpGateway,
    private val properties: KingdeeMcpProperties
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        try {
            if (mcpGateway is SdkMcpGateway) {
                mcpGateway.connect()
            }
            val tools = mcpGateway.listTools()
            val names = tools.map { it.name }
            log.info("Kingdee MCP connected.")
            log.info("Available tools:")
            names.forEach { log.info("- {}", it) }
            val meta = properties.metadataTool
            if (meta in names) {
                log.info("Metadata tool: {}", meta)
            } else {
                log.warn("Metadata tool '{}' NOT found in available tools.", meta)
            }
        } catch (ex: Exception) {
            log.warn(
                "Kingdee MCP health check failed (app continues). reason={}",
                ex.message
            )
        }
    }
}
