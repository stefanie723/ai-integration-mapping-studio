package com.aims.application.mapping

import com.aims.application.dto.FieldMappingDto
import com.aims.domain.mapping.MappingStatus
import com.aims.domain.mapping.MappingType
import com.aims.domain.schema.SchemaField
import com.aims.domain.schema.SchemaTree
import com.aims.infrastructure.mcp.kingdee.RuleBasedKingdeeSystemFieldClassifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MappingSchemaMergerTest {

    private val merger = MappingSchemaMerger()
    private val resolver = MappingStatusResolver(RuleBasedKingdeeSystemFieldClassifier())

    @Test
    fun `adds new schema fields and preserves existing config`() {
        val old = listOf(
            FieldMappingDto(
                targetField = "FBillNo",
                mappingType = MappingType.DIRECT,
                sourceField = "orderNo",
                confidence = 0.98,
                confirmed = true
            ),
            FieldMappingDto(
                targetField = "FDate",
                mappingType = MappingType.DIRECT,
                sourceField = "orderDate",
                confidence = 0.96
            ),
            FieldMappingDto(
                targetField = "FPurchaseOrgId.FNumber",
                mappingType = MappingType.CONSTANT,
                fixedValue = "100",
                confidence = 0.55
            ),
            FieldMappingDto(
                targetField = "F_OLD_FIELD",
                mappingType = MappingType.DIRECT,
                sourceField = "old"
            )
        )
        val schema = SchemaTree(
            rootName = "PUR_PurchaseOrder",
            rootId = "PUR_PurchaseOrder",
            fields = listOf(
                leaf("FBillNo"),
                leaf("FDate"),
                leaf("FPurchaseOrgId.FNumber"),
                leaf("F_ABC_CUSTOM")
            )
        )

        val merged = merger.mergeMappingsWithTargetSchema(old, schema)
        assertEquals(4, merged.size)
        assertTrue(merged.none { it.targetField == "F_OLD_FIELD" })

        val org = merged.first { it.targetField == "FPurchaseOrgId.FNumber" }
        assertEquals(MappingType.CONSTANT, org.mappingType)
        assertEquals("100", org.fixedValue)

        val bill = merged.first { it.targetField == "FBillNo" }
        assertEquals("orderNo", bill.sourceField)
        assertTrue(bill.confirmed)

        val custom = merged.first { it.targetField == "F_ABC_CUSTOM" }
        assertEquals(MappingType.IGNORE, custom.mappingType)
        assertNull(custom.confidence)
    }

    @Test
    fun `new required field becomes REQUIRED_UNMAPPED after enrich`() {
        val schema = SchemaTree(
            rootName = "Form",
            rootId = "Form",
            fields = listOf(leaf("F_NEW_REQUIRED", required = true))
        )
        val merged = merger.mergeMappingsWithTargetSchema(emptyList(), schema)
        val enriched = resolver.enrichAll(merged)
        assertEquals(MappingStatus.REQUIRED_UNMAPPED, enriched[0].status)
    }

    @Test
    fun `new required system field becomes SYSTEM_FIELD after enrich`() {
        val schema = SchemaTree(
            rootName = "Form",
            rootId = "Form",
            fields = listOf(leaf("FCreatorId.FNumber", required = true))
        )
        val merged = merger.mergeMappingsWithTargetSchema(emptyList(), schema)
        val enriched = resolver.enrichAll(merged)
        assertEquals(MappingStatus.SYSTEM_FIELD, enriched[0].status)
    }

    private fun leaf(path: String, required: Boolean = false) = SchemaField(
        path = path,
        code = path.substringAfterLast('.'),
        name = path,
        required = required
    )
}
