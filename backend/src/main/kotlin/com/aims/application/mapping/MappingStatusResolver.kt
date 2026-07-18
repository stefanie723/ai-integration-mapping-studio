package com.aims.application.mapping

import com.aims.application.dto.FieldMappingDto
import com.aims.application.dto.MappingSummary
import com.aims.domain.mapping.MappingStatus
import com.aims.domain.mapping.MappingType
import com.aims.infrastructure.mcp.kingdee.KingdeeSystemFieldClassifier
import org.springframework.stereotype.Component

@Component
class MappingStatusResolver(
    private val systemFieldClassifier: KingdeeSystemFieldClassifier
) {

    fun resolve(mapping: FieldMappingDto): MappingStatus {
        val required = mapping.targetRequired
        val effective = isEffectiveMapping(mapping)
        val aiSuggestion = isAiSuggestion(mapping)
        val systemField = systemFieldClassifier.isSystemField(mapping.targetField)

        return when {
            required && !effective -> MappingStatus.REQUIRED_UNMAPPED
            aiSuggestion && !mapping.confirmed && (mapping.confidence ?: 0.0) < 0.9 ->
                MappingStatus.NEED_CONFIRM
            aiSuggestion && !mapping.confirmed -> MappingStatus.AI_RECOMMENDED
            mapping.confirmed -> MappingStatus.CONFIRMED
            systemField -> MappingStatus.SYSTEM_FIELD
            mapping.mappingType == MappingType.IGNORE -> MappingStatus.IGNORED
            else -> MappingStatus.UNMAPPED
        }
    }

    fun withStatus(mapping: FieldMappingDto): FieldMappingDto =
        mapping.copy(
            status = resolve(mapping),
            needConfirm = resolve(mapping) == MappingStatus.NEED_CONFIRM ||
                resolve(mapping) == MappingStatus.AI_RECOMMENDED
        )

    fun enrichAll(mappings: List<FieldMappingDto>): List<FieldMappingDto> =
        mappings.map { m ->
            val status = resolve(m)
            m.copy(
                status = status,
                needConfirm = status == MappingStatus.NEED_CONFIRM || status == MappingStatus.AI_RECOMMENDED
            )
        }

    fun summarize(mappings: List<FieldMappingDto>): MappingSummary {
        val enriched = if (mappings.any { it.status == null }) enrichAll(mappings) else mappings
        val pending = enriched.count { it.status in PENDING_STATUSES }
        val required = enriched.count { it.targetRequired }
        val configured = enriched.count { isEffectiveMapping(it) }
        val confirmed = enriched.count { it.status == MappingStatus.CONFIRMED }
        val requiredUnmapped = enriched.count { it.status == MappingStatus.REQUIRED_UNMAPPED }
        return MappingSummary(
            totalFields = enriched.size,
            requiredFields = required,
            configuredFields = configured,
            confirmedFields = confirmed,
            pendingFields = pending,
            requiredUnmappedFields = requiredUnmapped
        )
    }

    companion object {
        val PENDING_STATUSES = setOf(
            MappingStatus.REQUIRED_UNMAPPED,
            MappingStatus.NEED_CONFIRM,
            MappingStatus.AI_RECOMMENDED
        )

        fun isEffectiveMapping(mapping: FieldMappingDto): Boolean {
            if (mapping.mappingType == MappingType.IGNORE) return false
            return when (mapping.mappingType) {
                MappingType.CONSTANT -> !mapping.fixedValue.isNullOrBlank()
                MappingType.DIRECT, MappingType.DICTIONARY ->
                    !mapping.sourceField.isNullOrBlank() &&
                        (mapping.mappingType != MappingType.DICTIONARY || !mapping.dictionary.isNullOrEmpty())
                MappingType.DEFAULT ->
                    !mapping.sourceField.isNullOrBlank() || !mapping.defaultValue.isNullOrBlank()
                else ->
                    !mapping.sourceField.isNullOrBlank() ||
                        !mapping.fixedValue.isNullOrBlank() ||
                        !mapping.defaultValue.isNullOrBlank()
            }
        }

        /**
         * AI suggestion exists when confidence is present and type is not IGNORE,
         * with at least one suggested value (source / constant / default).
         */
        fun isAiSuggestion(mapping: FieldMappingDto): Boolean {
            if (mapping.confidence == null) return false
            if (mapping.mappingType == MappingType.IGNORE) return false
            return !mapping.sourceField.isNullOrBlank() ||
                !mapping.fixedValue.isNullOrBlank() ||
                !mapping.defaultValue.isNullOrBlank()
        }
    }
}
