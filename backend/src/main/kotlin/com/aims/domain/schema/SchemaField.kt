package com.aims.domain.schema

/**
 * Unified schema field for both company API and Kingdee form fields.
 * Path must preserve full hierarchy, e.g. items[].materialCode or FPOOrderEntry[].FQty
 */
data class SchemaField(
    val path: String,
    val code: String,
    val name: String? = null,
    val description: String? = null,
    val dataType: String? = null,
    val required: Boolean = false,
    val children: List<SchemaField> = emptyList(),
    /** Kingdee-specific: base data entity, e.g. BD_Supplier */
    val lookUpObject: String? = null,
    /** Group label, e.g. 单据头 / 明细 */
    val group: String? = null
)

data class SchemaTree(
    val rootName: String,
    val rootId: String,
    val fields: List<SchemaField>
)
