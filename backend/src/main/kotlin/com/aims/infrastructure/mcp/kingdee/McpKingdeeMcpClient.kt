package com.aims.infrastructure.mcp.kingdee

import com.aims.domain.schema.SchemaTree
import com.aims.infrastructure.mcp.core.McpException
import com.aims.infrastructure.mcp.core.McpGateway
import com.aims.infrastructure.mcp.core.KingdeeMcpProperties
import com.aims.infrastructure.mcp.kingdee.dto.*
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/**
 * Real Kingdee schema client via MCP protocol (not plain HTTP pretending to be MCP).
 *
 * TODO V0.5: 支持 customerId -> Kingdee Connection / MCP Server 动态路由
 */
@Component
@ConditionalOnProperty(prefix = "aims.mcp.kingdee", name = ["mode"], havingValue = "real")
class McpKingdeeMcpClient(
    private val mcpGateway: McpGateway,
    private val converter: KingdeeMcpSchemaConverter,
    private val properties: KingdeeMcpProperties,
    private val objectMapper: ObjectMapper
) : KingdeeMcpClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val schemaCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(10))
        .maximumSize(200)
        .build<String, SchemaTree>()

    private val lastRawJson = AtomicReference<JsonNode?>()
    private val toolNames = AtomicReference<List<String>>(emptyList())

    override fun listForms(customerId: Long): List<KingdeeFormInfo> {
        // V0.2: customerId ignored for routing (single test account).
        return try {
            ensureMetadataTool()
            val result = mcpGateway.callTool(
                properties.listFormsTool,
                mapOf("params" to mapOf("keyword" to "采购"))
            )
            if (result.isError) {
                throw McpException("Kingdee MCP 调用失败: ${result.contentText.take(200)}")
            }
            val parsed = objectMapper.readValue<KingdeeListFormsResponse>(result.contentText)
            val forms = parsed.forms.map { KingdeeFormInfo(it.form_id, it.name ?: it.form_id) }
            if (forms.isEmpty()) fallbackForms() else forms
        } catch (ex: Exception) {
            log.warn("listForms via MCP failed, fallback fixed list: {}", ex.message)
            fallbackForms()
        }
    }

    override fun getFormSchema(customerId: Long, formId: String): SchemaTree =
        getFormSchema(customerId, formId, refresh = false)

    override fun getFormSchema(customerId: Long, formId: String, refresh: Boolean): SchemaTree {
        // TODO V0.5: 支持 customerId -> Kingdee Connection / MCP Server 动态路由
        if (!refresh) {
            schemaCache.getIfPresent(formId)?.let { return it }
        } else {
            schemaCache.invalidate(formId)
        }

        ensureMetadataTool()
        val started = System.currentTimeMillis()
        log.info("Calling Kingdee MCP metadata tool. formId={} tool={}", formId, properties.metadataTool)

        // GitHub KingdeeMCP supports verbose=true (full main fields via QueryBusinessInfo).
        // Older PyPI builds reject verbose — fall back automatically.
        val overviewText = callGetFieldsPreferVerbose(formId)
        val overview = parseGetFields(overviewText)
        if (!overview.error.isNullOrBlank()) {
            throw McpException("未找到金蝶表单：$formId (${overview.error})")
        }
        val authFailed = overviewText.contains("认证失败", ignoreCase = true) ||
            (overviewText.contains("Login", ignoreCase = true) && overviewText.contains("fail", ignoreCase = true))
        if (authFailed) {
            throw McpException("Kingdee MCP 已连接，但金蝶云星空认证失败。请检查测试账套认证配置。")
        }

        val raw = when {
            overview.metadata != null -> buildRawFromMetadata(overview, formId)
            overview.field_list.isNotEmpty() -> buildRawFromFieldList(overview, formId)
            else -> {
                log.warn(
                    "kingdee_get_fields returned no usable fields. formId={} tip={} rawPrefix={}",
                    formId,
                    overview.metadata_tip,
                    overviewText.take(240).replace("\n", " ")
                )
                if (!overview.metadata_tip.isNullOrBlank()) {
                    throw McpException(
                        "Kingdee MCP 已连接，但未能获取表单元数据：$formId。${overview.metadata_tip}"
                    )
                }
                throw McpException("未找到金蝶表单：$formId")
            }
        }
        if (raw.fields.isEmpty()) {
            throw McpException("Kingdee MCP 返回结果无法解析。formId=$formId")
        }

        val tree = converter.toSchemaTree(raw)
        schemaCache.put(formId, tree)
        val fieldCount = countLeaves(tree.fields)
        log.info(
            "Kingdee MCP call success. formId={} fieldCount={} elapsed={}ms source={}",
            formId,
            fieldCount,
            System.currentTimeMillis() - started,
            if (overview.metadata != null) "metadata" else "field_list"
        )
        return tree
    }

    private fun buildRawFromMetadata(overview: KingdeeGetFieldsResponse, formId: String): KingdeeRawFormMetadata {
        val rawFields = mutableListOf<KingdeeRawField>()
        overview.metadata?.main_fields?.forEach { f ->
            rawFields += KingdeeRawField(
                key = f.name,
                name = f.caption,
                type = f.type,
                required = f.must == true || overview.metadata.main_required_fields.contains(f.name),
                referenceFormId = f.lookup,
                isEntry = false
            )
        }

        val entryKeys = overview.metadata?.entries?.keys.orEmpty()
        for (entryKey in entryKeys) {
            val entryDetail = try {
                val text = callGetFields(formId, entryKey = entryKey, verbose = null)
                parseGetFields(text).entry
            } catch (ex: Exception) {
                log.warn("Failed to drill entry {} for {}: {}", entryKey, formId, ex.message)
                null
            }
            val summary = overview.metadata?.entries?.get(entryKey)
            val children = entryDetail?.fields?.map { f ->
                KingdeeRawField(
                    key = f.name,
                    name = f.caption,
                    type = f.type,
                    required = f.must == true || entryDetail.required.contains(f.name),
                    referenceFormId = f.lookup,
                    isEntry = false
                )
            }.orEmpty()
            rawFields += KingdeeRawField(
                key = entryKey,
                name = entryDetail?.caption ?: summary?.caption ?: entryKey,
                type = "entry",
                required = false,
                referenceFormId = null,
                isEntry = true,
                children = children
            )
        }
        return KingdeeRawFormMetadata(
            formId = overview.form_id ?: formId,
            name = overview.name,
            fields = rawFields,
            sourceJson = lastRawJson.get()
        )
    }

    /**
     * Adapt published PyPI kingdee-mcp response shape:
     * { form_id, name, recommended_fields, field_list: ["FBillNo", "FSupplierId.FName", ...] }
     */
    private fun buildRawFromFieldList(overview: KingdeeGetFieldsResponse, formId: String): KingdeeRawFormMetadata {
        val names = overview.field_list.filter { it.isNotBlank() }.distinct()
        val entries = linkedMapOf<String, MutableList<KingdeeRawField>>()
        val headers = mutableListOf<KingdeeRawField>()

        for (item in names) {
            val parts = item.split('.')
            if (parts.size >= 2 && parts[0].contains("Entry", ignoreCase = true)) {
                val entryKey = parts[0]
                val childKey = parts.drop(1).joinToString(".")
                entries.getOrPut(entryKey) { mutableListOf() } += KingdeeRawField(
                    key = childKey,
                    name = childKey,
                    type = null,
                    required = false,
                    referenceFormId = null
                )
            } else {
                headers += KingdeeRawField(
                    key = item,
                    name = item,
                    type = null,
                    required = false,
                    referenceFormId = null
                )
            }
        }

        val fields = headers.distinctBy { it.key }.toMutableList()
        entries.forEach { (entryKey, children) ->
            fields += KingdeeRawField(
                key = entryKey,
                name = entryKey,
                type = "entry",
                required = false,
                referenceFormId = null,
                isEntry = true,
                children = children.distinctBy { it.key }
            )
        }

        log.info(
            "Parsed field_list format. formId={} headerCount={} entryCount={} totalNames={}",
            formId,
            headers.size,
            entries.size,
            names.size
        )
        return KingdeeRawFormMetadata(
            formId = overview.form_id ?: formId,
            name = overview.name,
            fields = fields,
            sourceJson = lastRawJson.get()
        )
    }

    fun getLastRawJson(): JsonNode? = lastRawJson.get()

    fun availableTools(): List<String> = toolNames.get()

    private fun callGetFieldsPreferVerbose(formId: String): String {
        return try {
            callGetFields(formId, entryKey = null, verbose = true)
        } catch (ex: McpException) {
            val msg = ex.message.orEmpty()
            if (msg.contains("verbose", ignoreCase = true) || msg.contains("Extra inputs", ignoreCase = true)) {
                log.info("verbose unsupported by current KingdeeMCP, fallback without verbose")
                callGetFields(formId, entryKey = null, verbose = null)
            } else {
                throw ex
            }
        }
    }

    private fun callGetFields(formId: String, entryKey: String?, verbose: Boolean?): String {
        val params = mutableMapOf<String, Any?>(
            "form_id" to formId
        )
        if (verbose != null) {
            params["verbose"] = verbose
        }
        if (!entryKey.isNullOrBlank()) {
            params["entry_key"] = entryKey
        }
        // Prefer nested `params` (KingdeeMCP tool signature); fall back to flat args.
        val result = try {
            mcpGateway.callTool(properties.metadataTool, mapOf("params" to params))
        } catch (ex: McpException) {
            log.info("Nested params call failed, retry flat args: {}", ex.message)
            mcpGateway.callTool(properties.metadataTool, params)
        }
        if (result.isError || result.contentText.contains("Error executing tool", ignoreCase = true)) {
            val msg = result.contentText
            when {
                msg.contains("认证", ignoreCase = true) || msg.contains("login", ignoreCase = true) ->
                    throw McpException("Kingdee MCP 已连接，但金蝶云星空认证失败。请检查测试账套认证配置。")
                msg.contains("not found", ignoreCase = true) || msg.contains("不存在") ->
                    throw McpException("未找到金蝶表单：$formId")
                else -> throw McpException("Kingdee MCP 调用失败: ${msg.take(300)}")
            }
        }
        return result.contentText
    }

    private fun parseGetFields(text: String): KingdeeGetFieldsResponse {
        return try {
            val node = objectMapper.readTree(text)
            lastRawJson.set(node)
            objectMapper.treeToValue(node, KingdeeGetFieldsResponse::class.java)
        } catch (ex: Exception) {
            log.warn("Failed to parse Kingdee MCP response structure: {}", ex.message)
            throw McpException("Kingdee MCP 返回结果无法解析。", ex)
        }
    }

    private fun ensureMetadataTool() {
        val tools = try {
            mcpGateway.listTools().also { toolNames.set(it.map { t -> t.name }) }
        } catch (ex: McpException) {
            throw ex
        }
        val names = tools.map { it.name }
        if (properties.metadataTool !in names) {
            log.error("Metadata tool missing. Available tools: {}", names)
            throw McpException("当前 KingdeeMCP 未提供获取表单元数据所需的 Tool。")
        }
    }

    private fun fallbackForms(): List<KingdeeFormInfo> = listOf(
        KingdeeFormInfo("PUR_PurchaseOrder", "采购订单")
    )

    private fun countLeaves(fields: List<com.aims.domain.schema.SchemaField>): Int {
        var n = 0
        fun walk(list: List<com.aims.domain.schema.SchemaField>) {
            list.forEach {
                if (it.children.isEmpty()) n++ else walk(it.children)
            }
        }
        walk(fields)
        return n
    }
}
