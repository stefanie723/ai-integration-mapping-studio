package com.aims.application

import com.aims.application.dto.*
import com.aims.domain.mapping.FieldMapping
import com.aims.domain.mapping.MappingConfiguration
import com.aims.domain.mapping.MappingHistory
import com.aims.domain.mapping.MappingType
import com.aims.domain.schema.SchemaField
import com.aims.infrastructure.ai.AiMappingProvider
import com.aims.infrastructure.mcp.company.CompanyMcpClient
import com.aims.infrastructure.mcp.kingdee.KingdeeMcpClient
import com.aims.infrastructure.persistence.CustomerRepository
import com.aims.infrastructure.persistence.IntegrationScenarioRepository
import com.aims.infrastructure.persistence.MappingConfigurationRepository
import com.aims.infrastructure.persistence.MappingHistoryRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MappingApplicationService(
    private val customerRepository: CustomerRepository,
    private val scenarioRepository: IntegrationScenarioRepository,
    private val mappingConfigurationRepository: MappingConfigurationRepository,
    private val mappingHistoryRepository: MappingHistoryRepository,
    private val companyMcpClient: CompanyMcpClient,
    private val kingdeeMcpClient: KingdeeMcpClient,
    private val aiMappingProvider: AiMappingProvider,
    private val objectMapper: ObjectMapper
) {

    fun listCustomers(): List<CustomerDto> =
        customerRepository.findAll().map { CustomerDto(it.id!!, it.code, it.name) }

    fun listScenarios(): List<ScenarioDto> =
        scenarioRepository.findAll().map {
            ScenarioDto(it.id!!, it.code, it.name, it.sourceApi, it.targetFormId)
        }

    fun getSourceSchema(scenarioCode: String): com.aims.domain.schema.SchemaTree {
        val scenario = scenarioRepository.findByCode(scenarioCode)
            ?: throw IllegalArgumentException("场景不存在: $scenarioCode")
        val api = scenario.sourceApi
            ?: throw IllegalArgumentException("场景未配置 sourceApi: $scenarioCode")
        return companyMcpClient.getApiSchema(api)
    }

    fun getKingdeeSchema(customerId: Long, formId: String): com.aims.domain.schema.SchemaTree {
        customerRepository.findById(customerId)
            .orElseThrow { IllegalArgumentException("客户不存在: $customerId") }
        return kingdeeMcpClient.getFormSchema(customerId, formId)
    }

    fun recommend(request: RecommendRequest): RecommendResponse {
        val scenario = scenarioRepository.findByCode(request.scenarioCode)
            ?: throw IllegalArgumentException("场景不存在: ${request.scenarioCode}")
        customerRepository.findById(request.customerId)
            .orElseThrow { IllegalArgumentException("客户不存在: ${request.customerId}") }

        val sourceApi = scenario.sourceApi
            ?: throw IllegalArgumentException("场景未配置 sourceApi")
        val targetFormId = scenario.targetFormId
            ?: throw IllegalArgumentException("场景未配置 targetFormId")

        val sourceSchema = companyMcpClient.getApiSchema(sourceApi)
        val targetSchema = kingdeeMcpClient.getFormSchema(request.customerId, targetFormId)
        val history = mappingHistoryRepository.findByTargetFormId(targetFormId)

        val recommendation = aiMappingProvider.recommend(sourceSchema, targetSchema, history)
        val targetLeaves = flatten(targetSchema.fields).associateBy { it.path }

        val mappings = recommendation.mappings.map { item ->
            val target = targetLeaves[item.targetField]
            FieldMappingDto(
                targetField = item.targetField,
                targetFieldName = target?.name,
                mappingType = item.mappingType,
                sourceField = item.sourceField,
                fixedValue = item.fixedValue,
                defaultValue = item.defaultValue,
                confidence = item.confidence,
                aiReason = item.reason,
                confirmed = false,
                targetRequired = target?.required == true,
                needConfirm = item.needConfirm || item.confidence < 0.6
            )
        }

        return RecommendResponse(
            customerId = request.customerId,
            scenarioCode = request.scenarioCode,
            sourceApi = sourceApi,
            targetFormId = targetFormId,
            sourceSchema = sourceSchema,
            targetSchema = targetSchema,
            mappings = mappings
        )
    }

    fun getMapping(customerId: Long, scenarioCode: String): MappingConfigurationDto? {
        val config = mappingConfigurationRepository.findByCustomerIdAndScenarioCode(customerId, scenarioCode)
            ?: return null
        return toDto(config)
    }

    @Transactional
    fun saveMapping(dto: MappingConfigurationDto): MappingConfigurationDto {
        customerRepository.findById(dto.customerId)
            .orElseThrow { IllegalArgumentException("客户不存在: ${dto.customerId}") }
        scenarioRepository.findByCode(dto.scenarioCode)
            ?: throw IllegalArgumentException("场景不存在: ${dto.scenarioCode}")

        val existing = mappingConfigurationRepository.findByCustomerIdAndScenarioCode(
            dto.customerId, dto.scenarioCode
        )

        val toSave = existing ?: MappingConfiguration(
            customerId = dto.customerId,
            scenarioCode = dto.scenarioCode,
            sourceApi = dto.sourceApi,
            targetFormId = dto.targetFormId
        )
        toSave.sourceApi = dto.sourceApi
        toSave.targetFormId = dto.targetFormId

        val fieldMappings = dto.mappings.map { m ->
            FieldMapping(
                targetField = m.targetField,
                targetFieldName = m.targetFieldName,
                mappingType = m.mappingType,
                sourceField = m.sourceField,
                fixedValue = m.fixedValue,
                defaultValue = m.defaultValue,
                expression = m.expression,
                dictionary = m.dictionary?.let { objectMapper.writeValueAsString(it) },
                confidence = m.confidence,
                aiReason = m.aiReason,
                confirmed = m.confirmed,
                targetRequired = m.targetRequired
            )
        }
        toSave.replaceMappings(fieldMappings)

        val saved = mappingConfigurationRepository.save(toSave)

        // Persist history for confirmed DIRECT mappings
        saved.mappings.filter { it.confirmed && it.mappingType != MappingType.IGNORE && !it.sourceField.isNullOrBlank() }
            .forEach { recordHistory(it, saved.targetFormId) }

        return toDto(saved)
    }

    fun checkRequired(dto: MappingConfigurationDto): RequiredCheckResult {
        val targetSchema = kingdeeMcpClient.getFormSchema(dto.customerId, dto.targetFormId)
        val requiredPaths = flatten(targetSchema.fields)
            .filter { it.required && it.children.isEmpty() }
            .map { it.path }
            .toSet()

        val configured = dto.mappings
            .filter { it.mappingType != MappingType.IGNORE }
            .filter {
                when (it.mappingType) {
                    MappingType.CONSTANT -> !it.fixedValue.isNullOrBlank()
                    MappingType.DIRECT, MappingType.DICTIONARY -> !it.sourceField.isNullOrBlank()
                    MappingType.DEFAULT -> !it.sourceField.isNullOrBlank() || !it.defaultValue.isNullOrBlank()
                    else -> !it.sourceField.isNullOrBlank() || !it.fixedValue.isNullOrBlank() || !it.defaultValue.isNullOrBlank()
                }
            }
            .map { it.targetField }
            .toSet()

        val missing = requiredPaths.filter { it !in configured }
        return RequiredCheckResult(passed = missing.isEmpty(), missingRequiredFields = missing)
    }

    fun generateCode(request: CodeGenerationRequest): CodeGenerationResponse {
        val config = mappingConfigurationRepository.findById(request.mappingConfigurationId)
            .orElseThrow { IllegalArgumentException("Mapping 配置不存在: ${request.mappingConfigurationId}") }

        val check = checkRequired(toDto(config))
        if (!check.passed) {
            throw IllegalStateException(
                "还有 ${check.missingRequiredFields.size} 个必填字段未配置: ${check.missingRequiredFields.joinToString()}"
            )
        }

        val language = request.language.uppercase()
        require(language == "KOTLIN" || language == "JAVA") { "暂只支持 KOTLIN / JAVA" }

        val dtoFile = generateDto(config)
        val mapperFile = generateMapper(config)
        return CodeGenerationResponse(files = listOf(dtoFile, mapperFile))
    }

    private fun recordHistory(mapping: FieldMapping, targetFormId: String) {
        val source = mapping.sourceField ?: return
        val existing = mappingHistoryRepository.findBySourceFieldAndTargetFormIdAndTargetField(
            source, targetFormId, mapping.targetField
        )
        if (existing != null) {
            existing.usageCount += 1
            mappingHistoryRepository.save(existing)
        } else {
            mappingHistoryRepository.save(
                MappingHistory(
                    sourceField = source,
                    sourceFieldName = null,
                    targetFormId = targetFormId,
                    targetField = mapping.targetField,
                    targetFieldName = mapping.targetFieldName,
                    usageCount = 1
                )
            )
        }
    }

    private fun toDto(config: MappingConfiguration): MappingConfigurationDto =
        MappingConfigurationDto(
            id = config.id,
            customerId = config.customerId,
            scenarioCode = config.scenarioCode,
            sourceApi = config.sourceApi,
            targetFormId = config.targetFormId,
            mappings = config.mappings.map { m ->
                FieldMappingDto(
                    id = m.id,
                    targetField = m.targetField,
                    targetFieldName = m.targetFieldName,
                    mappingType = m.mappingType,
                    sourceField = m.sourceField,
                    fixedValue = m.fixedValue,
                    defaultValue = m.defaultValue,
                    expression = m.expression,
                    dictionary = m.dictionary?.let { objectMapper.readValue(it) },
                    confidence = m.confidence,
                    aiReason = m.aiReason,
                    confirmed = m.confirmed,
                    targetRequired = m.targetRequired,
                    needConfirm = !m.confirmed && (m.confidence ?: 0.0) < 0.9
                )
            }
        )

    private fun flatten(fields: List<SchemaField>): List<SchemaField> {
        val result = mutableListOf<SchemaField>()
        fun walk(list: List<SchemaField>) {
            list.forEach { f ->
                result.add(f)
                if (f.children.isNotEmpty()) walk(f.children)
            }
        }
        walk(fields)
        return result
    }

    private fun generateDto(config: MappingConfiguration): GeneratedFileDto {
        val content = buildString {
            appendLine("package com.example.integration.kingdee")
            appendLine()
            appendLine("/**")
            appendLine(" * Generated from MappingConfiguration #${config.id}")
            appendLine(" * customerId=${config.customerId}, scenario=${config.scenarioCode}")
            appendLine(" * DO NOT hand-edit mapping logic — change Mapping Configuration instead.")
            appendLine(" */")
            appendLine("data class KingdeePurchaseOrderDTO(")
            appendLine("    val billNo: String? = null,")
            appendLine("    val date: String? = null,")
            appendLine("    val supplierNumber: String? = null,")
            appendLine("    val purchaseOrgNumber: String? = null,")
            appendLine("    val currencyNumber: String? = null,")
            appendLine("    val entries: List<KingdeePurchaseOrderEntryDTO> = emptyList()")
            appendLine(")")
            appendLine()
            appendLine("data class KingdeePurchaseOrderEntryDTO(")
            appendLine("    val materialNumber: String? = null,")
            appendLine("    val qty: java.math.BigDecimal? = null,")
            appendLine("    val price: java.math.BigDecimal? = null")
            appendLine(")")
            appendLine()
            appendLine("data class PurchaseOrder(")
            appendLine("    val orderNo: String? = null,")
            appendLine("    val orderDate: String? = null,")
            appendLine("    val supplierCode: String? = null,")
            appendLine("    val purchaseOrgCode: String? = null,")
            appendLine("    val currencyCode: String? = null,")
            appendLine("    val items: List<PurchaseOrderItem> = emptyList()")
            appendLine(")")
            appendLine()
            appendLine("data class PurchaseOrderItem(")
            appendLine("    val materialCode: String? = null,")
            appendLine("    val qty: java.math.BigDecimal? = null,")
            appendLine("    val price: java.math.BigDecimal? = null")
            appendLine(")")
        }
        return GeneratedFileDto("KingdeePurchaseOrderDTO.kt", content)
    }

    private fun generateMapper(config: MappingConfiguration): GeneratedFileDto {
        fun expr(m: FieldMapping): String = when (m.mappingType) {
            MappingType.DIRECT -> sourceExpr(m.sourceField)
            MappingType.CONSTANT -> "\"${m.fixedValue.orEmpty()}\""
            MappingType.DEFAULT -> {
                val src = sourceExpr(m.sourceField)
                "$src ?: \"${m.defaultValue.orEmpty()}\""
            }
            MappingType.DICTIONARY -> {
                val dict = m.dictionary?.let {
                    try {
                        objectMapper.readValue<Map<String, String>>(it)
                    } catch (_: Exception) {
                        emptyMap()
                    }
                } ?: emptyMap()
                val mapLiteral = dict.entries.joinToString(", ") { "(${'"'}${it.key}${'"'} to ${'"'}${it.value}${'"'})" }
                "mapOf($mapLiteral)[${sourceExpr(m.sourceField)}] ?: ${sourceExpr(m.sourceField)}"
            }
            MappingType.IGNORE -> "null"
            else -> sourceExpr(m.sourceField)
        }

        val byTarget = config.mappings.associateBy { it.targetField }

        fun line(target: String, prop: String, indent: String = "            "): String {
            val m = byTarget[target] ?: return "$indent$prop = null,"
            if (m.mappingType == MappingType.IGNORE) return "$indent$prop = null, // IGNORE"
            return "$indent$prop = ${expr(m)}, // ${m.mappingType} ${m.targetField}"
        }

        val content = buildString {
            appendLine("package com.example.integration.kingdee")
            appendLine()
            appendLine("/**")
            appendLine(" * Generated Mapper from MappingConfiguration #${config.id}")
            appendLine(" * Mapping values come ONLY from saved configuration — AI must not alter them.")
            appendLine(" */")
            appendLine("class PurchaseOrderMapper {")
            appendLine()
            appendLine("    fun convert(source: PurchaseOrder): KingdeePurchaseOrderDTO {")
            appendLine("        return KingdeePurchaseOrderDTO(")
            appendLine(line("FBillNo", "billNo"))
            appendLine(line("FDate", "date"))
            appendLine(line("FSupplierId.FNumber", "supplierNumber"))
            appendLine(line("FPurchaseOrgId.FNumber", "purchaseOrgNumber"))
            appendLine(line("FSettleCurrId.FNumber", "currencyNumber"))
            appendLine("            entries = source.items.map { item ->")
            appendLine("                KingdeePurchaseOrderEntryDTO(")
            appendLine(entryLine(byTarget, "FPOOrderEntry[].FMaterialId.FNumber", "materialNumber"))
            appendLine(entryLine(byTarget, "FPOOrderEntry[].FQty", "qty"))
            appendLine(entryLine(byTarget, "FPOOrderEntry[].FPrice", "price"))
            appendLine("                )")
            appendLine("            }")
            appendLine("        )")
            appendLine("    }")
            appendLine("}")
        }
        return GeneratedFileDto("PurchaseOrderMapper.kt", content)
    }

    private fun entryLine(byTarget: Map<String, FieldMapping>, target: String, prop: String): String {
        val m = byTarget[target] ?: return "                    $prop = null,"
        if (m.mappingType == MappingType.IGNORE) return "                    $prop = null, // IGNORE"
        val value = when (m.mappingType) {
            MappingType.DIRECT -> itemExpr(m.sourceField)
            MappingType.CONSTANT -> "\"${m.fixedValue.orEmpty()}\""
            MappingType.DEFAULT -> {
                val src = itemExpr(m.sourceField)
                "$src ?: \"${m.defaultValue.orEmpty()}\""
            }
            else -> itemExpr(m.sourceField)
        }
        return "                    $prop = $value, // ${m.mappingType}"
    }

    private fun sourceExpr(sourceField: String?): String {
        if (sourceField.isNullOrBlank()) return "null"
        return when {
            sourceField.startsWith("items[].") -> "null /* entry field */"
            else -> "source.$sourceField"
        }
    }

    private fun itemExpr(sourceField: String?): String {
        if (sourceField.isNullOrBlank()) return "null"
        val code = sourceField.removePrefix("items[].")
        return "item.$code"
    }
}
