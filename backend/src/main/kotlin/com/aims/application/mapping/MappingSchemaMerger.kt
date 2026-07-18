package com.aims.application.mapping

import com.aims.application.dto.FieldMappingDto
import com.aims.domain.mapping.MappingType
import com.aims.domain.schema.SchemaField
import com.aims.domain.schema.SchemaTree
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Merge current Mapping rows with the latest target Schema leaves.
 * Preserves user/AI configuration; does not re-run AI recommend.
 */
@Component
class MappingSchemaMerger {

    private val log = LoggerFactory.getLogger(javaClass)

    fun mergeMappingsWithTargetSchema(
        mappings: List<FieldMappingDto>,
        targetSchema: SchemaTree
    ): List<FieldMappingDto> {
        val leaves = leafFields(targetSchema)
        val leafPaths = leaves.map { it.path }.toSet()
        val byTarget = mappings.associateBy { it.targetField }

        for (path in byTarget.keys) {
            if (path !in leafPaths) {
                log.warn("Target field no longer exists: {}", path)
            }
        }

        return leaves.map { leaf ->
            val existing = byTarget[leaf.path]
            if (existing != null) {
                existing.copy(
                    targetFieldName = existing.targetFieldName ?: leaf.name,
                    targetRequired = leaf.required
                )
            } else {
                FieldMappingDto(
                    targetField = leaf.path,
                    targetFieldName = leaf.name,
                    mappingType = MappingType.IGNORE,
                    confirmed = false,
                    targetRequired = leaf.required,
                    confidence = null
                )
            }
        }
    }

    fun leafFields(schema: SchemaTree): List<SchemaField> =
        flatten(schema.fields).filter { it.children.isEmpty() }

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
}
