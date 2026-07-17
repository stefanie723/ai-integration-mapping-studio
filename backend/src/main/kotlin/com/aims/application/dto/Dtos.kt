package com.aims.application.dto

import com.aims.domain.mapping.MappingType

data class CustomerDto(
    val id: Long,
    val code: String,
    val name: String
)

data class ScenarioDto(
    val id: Long,
    val code: String,
    val name: String,
    val sourceApi: String?,
    val targetFormId: String?
)

data class RecommendRequest(
    val customerId: Long,
    val scenarioCode: String
)

data class FieldMappingDto(
    val id: Long? = null,
    val targetField: String,
    val targetFieldName: String? = null,
    val mappingType: MappingType,
    val sourceField: String? = null,
    val fixedValue: String? = null,
    val defaultValue: String? = null,
    val expression: String? = null,
    val dictionary: Map<String, String>? = null,
    val confidence: Double? = null,
    val aiReason: String? = null,
    val confirmed: Boolean = false,
    val targetRequired: Boolean = false,
    val needConfirm: Boolean = false
)

data class MappingConfigurationDto(
    val id: Long? = null,
    val customerId: Long,
    val scenarioCode: String,
    val sourceApi: String,
    val targetFormId: String,
    val mappings: List<FieldMappingDto>
)

data class RecommendResponse(
    val customerId: Long,
    val scenarioCode: String,
    val sourceApi: String,
    val targetFormId: String,
    val sourceSchema: com.aims.domain.schema.SchemaTree,
    val targetSchema: com.aims.domain.schema.SchemaTree,
    val mappings: List<FieldMappingDto>
)

data class RequiredCheckResult(
    val passed: Boolean,
    val missingRequiredFields: List<String>
)

data class CodeGenerationRequest(
    val mappingConfigurationId: Long,
    val language: String = "KOTLIN"
)

data class CodeGenerationResponse(
    val files: List<GeneratedFileDto>
)

data class GeneratedFileDto(
    val fileName: String,
    val content: String
)

data class ApiResponse<T>(
    val success: Boolean = true,
    val data: T? = null,
    val message: String? = null
)
