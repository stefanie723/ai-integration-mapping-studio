package com.aims.infrastructure.mcp.kingdee

import com.aims.domain.schema.SchemaField
import org.springframework.stereotype.Component

/**
 * Marks Kingdee-managed system fields that should not enter the pending workbench list.
 */
interface KingdeeSystemFieldClassifier {
    fun isSystemField(field: SchemaField): Boolean

    fun isSystemField(targetField: String): Boolean
}

@Component
class RuleBasedKingdeeSystemFieldClassifier : KingdeeSystemFieldClassifier {

    private val systemCodes: Set<String> = setOf(
        "FCreatorId",
        "FCreateDate",
        "FModifierId",
        "FModifyDate",
        "FApproverId",
        "FApproveDate",
        "FCancellerId",
        "FCancelDate",
        "FDocumentStatus"
    )

    override fun isSystemField(field: SchemaField): Boolean =
        isSystemField(field.path) || isSystemField(field.code)

    override fun isSystemField(targetField: String): Boolean {
        if (targetField.isBlank()) return false
        val leaf = targetField.substringAfterLast('.').substringAfterLast(']').trimStart('.')
        val code = leaf.ifBlank { targetField }
        return code in systemCodes || targetField in systemCodes
    }
}
