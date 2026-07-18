package com.aims.infrastructure.mcp.kingdee

import com.aims.domain.schema.SchemaField
import com.aims.domain.schema.SchemaTree
import com.aims.infrastructure.mcp.kingdee.dto.KingdeeRawField
import com.aims.infrastructure.mcp.kingdee.dto.KingdeeRawFormMetadata
import org.springframework.stereotype.Component

/**
 * Converts KingdeeMCP metadata into the project's unified SchemaTree.
 */
@Component
class KingdeeMcpSchemaConverter {

    fun toSchemaTree(raw: KingdeeRawFormMetadata): SchemaTree {
        val fields = raw.fields.map { toSchemaField(it, parentPath = null, isUnderEntry = false) }
        return SchemaTree(
            rootName = raw.name ?: raw.formId,
            rootId = raw.formId,
            fields = fields
        )
    }

    private fun toSchemaField(
        field: KingdeeRawField,
        parentPath: String?,
        isUnderEntry: Boolean
    ): SchemaField {
        val path = when {
            parentPath == null && field.isEntry -> "${field.key}[]"
            parentPath == null -> field.key
            isUnderEntry || parentPath.endsWith("[]") -> "$parentPath.${field.key}"
            else -> "$parentPath.${field.key}"
        }

        val children = field.children.map { child ->
            toSchemaField(
                child,
                parentPath = path,
                isUnderEntry = field.isEntry || isUnderEntry
            )
        }.toMutableList()

        // For base-data fields, expose .FNumber as a leaf mapping target without inventing a second Kingdee field.
        if (!field.isEntry && !field.referenceFormId.isNullOrBlank() && children.none { it.code == "FNumber" }) {
            children += SchemaField(
                path = "$path.FNumber",
                code = "FNumber",
                name = "${field.name ?: field.key}编码",
                description = "基础资料编码赋值属性（LookUp=${field.referenceFormId}）",
                dataType = "string",
                required = field.required,
                lookUpObject = field.referenceFormId,
                group = if (path.contains("[]")) "明细" else "单据头"
            )
        }

        return SchemaField(
            path = path,
            code = field.key,
            name = field.name,
            description = buildDescription(field),
            dataType = resolveDataType(field),
            required = field.required,
            children = children,
            lookUpObject = field.referenceFormId,
            group = when {
                field.isEntry -> "明细"
                path.contains("[]") -> "明细"
                else -> "单据头"
            }
        )
    }

    private fun buildDescription(field: KingdeeRawField): String? {
        val parts = mutableListOf<String>()
        field.type?.takeIf { it.isNotBlank() }?.let { parts += it }
        field.referenceFormId?.let { parts += "LookUp=$it" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun resolveDataType(field: KingdeeRawField): String? = when {
        field.isEntry -> "array"
        !field.referenceFormId.isNullOrBlank() -> "basiedata"
        else -> field.type ?: "string"
    }
}
