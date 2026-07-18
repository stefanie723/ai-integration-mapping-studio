package com.aims.infrastructure.mcp.kingdee.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

/**
 * Parsed subset of KingdeeMCP `kingdee_get_fields` JSON response.
 * Keep flexible — unknown fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KingdeeGetFieldsResponse(
    val form_id: String? = null,
    val name: String? = null,
    val desc: String? = null,
    val metadata: KingdeeMetadataBlock? = null,
    val entry: KingdeeEntryDetail? = null,
    val error: String? = null,
    val metadata_tip: String? = null,
    /** Published PyPI kingdee-mcp format (older) */
    val field_list: List<String> = emptyList(),
    val recommended_fields: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KingdeeMetadataBlock(
    val source: String? = null,
    val main_fields: List<KingdeeSlimField> = emptyList(),
    val main_required_fields: List<String> = emptyList(),
    val entries: Map<String, KingdeeEntrySummary> = emptyMap(),
    val tip: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KingdeeSlimField(
    val name: String,
    val caption: String? = null,
    val must: Boolean? = null,
    val lookup: String? = null,
    val type: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KingdeeEntrySummary(
    val caption: String? = null,
    val field_count: Int? = null,
    val required: List<String> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KingdeeEntryDetail(
    val key: String,
    val caption: String? = null,
    val field_count: Int? = null,
    val required: List<String> = emptyList(),
    val fields: List<KingdeeSlimField> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KingdeeListFormsResponse(
    val count: Int? = null,
    val forms: List<KingdeeFormItem> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KingdeeFormItem(
    val form_id: String,
    val name: String? = null,
    val desc: String? = null
)

/** Intermediate unified raw model used by SchemaConverter. */
data class KingdeeRawFormMetadata(
    val formId: String,
    val name: String?,
    val fields: List<KingdeeRawField>,
    val sourceJson: JsonNode? = null
)

data class KingdeeRawField(
    val key: String,
    val name: String?,
    val type: String?,
    val required: Boolean,
    val referenceFormId: String?,
    val isEntry: Boolean = false,
    val children: List<KingdeeRawField> = emptyList()
)
