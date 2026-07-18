package com.aims.infrastructure.mcp.kingdee

import com.aims.domain.schema.SchemaTree

/**
 * Abstraction for Kingdee MCP Server.
 * Used to fetch real Kingdee form field schemas per customer.
 */
interface KingdeeMcpClient {
    fun listForms(customerId: Long): List<KingdeeFormInfo>
    fun getFormSchema(customerId: Long, formId: String): SchemaTree

    /**
     * @param refresh true to bypass schema cache and re-fetch from KingdeeMCP
     */
    fun getFormSchema(customerId: Long, formId: String, refresh: Boolean): SchemaTree =
        getFormSchema(customerId, formId)
}

data class KingdeeFormInfo(
    val formId: String,
    val name: String
)
