package com.aims.infrastructure.mcp.core

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport
import io.modelcontextprotocol.client.transport.ServerParameters
import io.modelcontextprotocol.client.transport.StdioClientTransport
import io.modelcontextprotocol.spec.McpSchema
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * MCP gateway backed by the official MCP Java SDK.
 * Supports STDIO (spawn KingdeeMCP) and SSE (url) transports.
 */
class SdkMcpGateway(
    private val properties: KingdeeMcpProperties,
    private val objectMapper: ObjectMapper
) : McpGateway, AutoCloseable {

    private val log = LoggerFactory.getLogger(javaClass)
    private val clientRef = AtomicReference<McpSyncClient?>()
    private val connected = AtomicBoolean(false)

    @Synchronized
    fun connect(): Boolean {
        if (connected.get() && clientRef.get() != null) return true
        return try {
            val transport = createTransport()
            val client = McpClient.sync(transport)
                .requestTimeout(Duration.ofMillis(properties.timeout))
                .build()
            client.initialize()
            clientRef.set(client)
            connected.set(true)
            log.info("MCP gateway connected. transport={}", properties.transport)
            true
        } catch (ex: Exception) {
            connected.set(false)
            clientRef.set(null)
            log.warn("MCP gateway connect failed: {}", ex.message)
            false
        }
    }

    private fun createTransport() = when (properties.transport.lowercase()) {
        "sse", "http" -> HttpClientSseClientTransport(properties.url)
        else -> {
            val env = properties.stdio.env.toMutableMap()
            // Ensure child process inherits an augmented PATH (uv / python tools).
            val pathKey = env.keys.find { it.equals("PATH", ignoreCase = true) } ?: "PATH"
            val currentPath = System.getenv("PATH").orEmpty()
            val uvBin = System.getProperty("user.home") + "\\.local\\bin"
            if (!currentPath.contains(uvBin, ignoreCase = true)) {
                env[pathKey] = "$uvBin;$currentPath"
            } else if (!env.containsKey(pathKey)) {
                env[pathKey] = currentPath
            }
            // Force UTF-8 on Windows: MCP SDK 0.8.x reads stdout with Charset.defaultCharset();
            // Python KingdeeMCP emits UTF-8 JSON that otherwise corrupts Chinese and breaks parsing.
            env.putIfAbsent("PYTHONUTF8", "1")
            env.putIfAbsent("PYTHONIOENCODING", "utf-8")
            val blankSecrets = listOf("KINGDEE_SERVER_URL", "KINGDEE_ACCT_ID", "KINGDEE_USERNAME", "KINGDEE_APP_ID", "KINGDEE_APP_SEC")
                .filter { env[it].isNullOrBlank() }
            if (blankSecrets.isNotEmpty()) {
                log.warn("Kingdee MCP stdio env missing: {}", blankSecrets.joinToString())
            }
            val params = ServerParameters.builder(properties.stdio.command)
                .args(properties.stdio.args)
                .env(env)
                .build()
            StdioClientTransport(params, objectMapper)
        }
    }

    private fun clientOrThrow(): McpSyncClient {
        val existing = clientRef.get()
        if (existing != null && connected.get()) return existing
        if (!connect()) {
            throw McpException("无法连接 Kingdee MCP Server。请确认 MCP Server 已启动并检查连接配置。")
        }
        return clientRef.get()
            ?: throw McpException("无法连接 Kingdee MCP Server。请确认 MCP Server 已启动并检查连接配置。")
    }

    override fun listTools(): List<McpToolDefinition> {
        return try {
            val result = clientOrThrow().listTools()
            result.tools().map { McpToolDefinition(name = it.name(), description = it.description()) }
        } catch (ex: McpException) {
            throw ex
        } catch (ex: Exception) {
            connected.set(false)
            throw McpException("无法连接 Kingdee MCP Server。请确认 MCP Server 已启动并检查连接配置。", ex)
        }
    }

    override fun callTool(name: String, arguments: Map<String, Any?>): McpToolResult {
        return try {
            @Suppress("UNCHECKED_CAST")
            val args = arguments.filterValues { it != null } as Map<String, Any>
            val result = clientOrThrow().callTool(McpSchema.CallToolRequest(name, args))
            val text = result.content()
                .filterIsInstance<McpSchema.TextContent>()
                .joinToString("\n") { it.text() }
            McpToolResult(
                contentText = text,
                isError = result.isError() == true
            )
        } catch (ex: McpException) {
            throw ex
        } catch (ex: Exception) {
            connected.set(false)
            throw McpException("无法连接 Kingdee MCP Server。请确认 MCP Server 已启动并检查连接配置。", ex)
        }
    }

    override fun isConnected(): Boolean = connected.get() && clientRef.get() != null

    override fun close() {
        try {
            clientRef.getAndSet(null)?.closeGracefully()
        } catch (_: Exception) {
        } finally {
            connected.set(false)
        }
    }
}
