package com.aims.infrastructure.mcp.kingdee

import com.aims.domain.schema.SchemaTree

/**
 * Abstraction for Kingdee MCP Server.
 * Used to fetch real Kingdee form field schemas per customer.
 */
interface KingdeeMcpClient {
    fun listForms(customerId: Long): List<KingdeeFormInfo>
    fun getFormSchema(customerId: Long, formId: String): SchemaTree
}

data class KingdeeFormInfo(
    val formId: String,
    val name: String
)
