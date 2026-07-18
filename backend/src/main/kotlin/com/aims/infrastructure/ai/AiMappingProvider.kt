package com.aims.infrastructure.ai

import com.aims.domain.mapping.MappingHistory
import com.aims.domain.mapping.MappingType
import com.aims.domain.schema.SchemaTree

/**
 * Abstraction for AI providers (OpenAI / Claude / Gemini / OpenAI-compatible).
 * All recommendations must return structured JSON — never free-form prose.
 */
interface AiMappingProvider {
    fun recommend(
        sourceSchema: SchemaTree,
        targetSchema: SchemaTree,
        history: List<MappingHistory> = emptyList()
    ): MappingRecommendationResult
}

data class MappingRecommendationResult(
    val mappings: List<MappingRecommendationItem>
)

data class MappingRecommendationItem(
    val targetField: String,
    val sourceField: String? = null,
    val mappingType: MappingType = MappingType.DIRECT,
    val fixedValue: String? = null,
    val defaultValue: String? = null,
    val confidence: Double? = null,
    val reason: String? = null,
    val needConfirm: Boolean = false
)
