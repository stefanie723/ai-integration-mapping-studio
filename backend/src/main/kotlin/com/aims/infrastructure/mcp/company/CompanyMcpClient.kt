package com.aims.infrastructure.mcp.company

import com.aims.domain.schema.SchemaTree

/**
 * Abstraction for Company MCP Server.
 * Used to fetch our system's OpenAPI schemas.
 */
interface CompanyMcpClient {
    fun listApis(): List<CompanyApiInfo>
    fun getApiSchema(apiPath: String): SchemaTree
}

data class CompanyApiInfo(
    val path: String,
    val name: String,
    val description: String? = null
)
