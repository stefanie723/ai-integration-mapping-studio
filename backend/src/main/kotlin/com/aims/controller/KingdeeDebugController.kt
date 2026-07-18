package com.aims.controller

import com.aims.application.dto.ApiResponse
import com.aims.infrastructure.mcp.kingdee.McpKingdeeMcpClient
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Dev-only raw MCP response endpoint for comparing KingdeeMCP output vs SchemaTree.
 */
@RestController
@RequestMapping("/api/debug/kingdee")
@Profile("dev")
@CrossOrigin(origins = ["*"])
class KingdeeDebugController(
    private val realClientProvider: ObjectProvider<McpKingdeeMcpClient>
) {

    @GetMapping("/raw-schema")
    fun rawSchema(@RequestParam formId: String): ApiResponse<JsonNode?> {
        val client = realClientProvider.ifAvailable
            ?: throw IllegalStateException("当前不是 real 模式，无法获取 KingdeeMCP 原始结果")
        // Force refresh to populate lastRawJson
        client.getFormSchema(customerId = 0L, formId = formId, refresh = true)
        return ApiResponse(data = client.getLastRawJson())
    }
}
