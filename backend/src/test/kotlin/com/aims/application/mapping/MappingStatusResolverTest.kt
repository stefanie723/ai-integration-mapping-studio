package com.aims.application.mapping

import com.aims.application.dto.FieldMappingDto
import com.aims.domain.mapping.MappingStatus
import com.aims.domain.mapping.MappingType
import com.aims.infrastructure.mcp.kingdee.RuleBasedKingdeeSystemFieldClassifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MappingStatusResolverTest {

    private val resolver = MappingStatusResolver(RuleBasedKingdeeSystemFieldClassifier())

    @Test
    fun `required without effective mapping is REQUIRED_UNMAPPED`() {
        val status = resolver.resolve(
            FieldMappingDto(
                targetField = "FBillTypeID.FNumber",
                mappingType = MappingType.IGNORE,
                targetRequired = true
            )
        )
        assertEquals(MappingStatus.REQUIRED_UNMAPPED, status)
    }

    @Test
    fun `required system field without config is SYSTEM_FIELD not REQUIRED_UNMAPPED`() {
        val status = resolver.resolve(
            FieldMappingDto(
                targetField = "FCreatorId.FNumber",
                mappingType = MappingType.IGNORE,
                targetRequired = true,
                confidence = null,
                confirmed = false
            )
        )
        assertEquals(MappingStatus.SYSTEM_FIELD, status)
    }

    @Test
    fun `low confidence AI suggestion is NEED_CONFIRM`() {
        val status = resolver.resolve(
            FieldMappingDto(
                targetField = "FPurchaseOrgId.FNumber",
                mappingType = MappingType.CONSTANT,
                fixedValue = "100",
                confidence = 0.55,
                confirmed = false,
                targetRequired = true
            )
        )
        assertEquals(MappingStatus.NEED_CONFIRM, status)
    }

    @Test
    fun `high confidence unconfirmed AI suggestion is AI_RECOMMENDED`() {
        val status = resolver.resolve(
            FieldMappingDto(
                targetField = "FBillNo",
                mappingType = MappingType.DIRECT,
                sourceField = "orderNo",
                confidence = 0.98,
                confirmed = false
            )
        )
        assertEquals(MappingStatus.AI_RECOMMENDED, status)
    }

    @Test
    fun `confirmed mapping is CONFIRMED`() {
        val status = resolver.resolve(
            FieldMappingDto(
                targetField = "FBillNo",
                mappingType = MappingType.DIRECT,
                sourceField = "orderNo",
                confidence = 0.98,
                confirmed = true
            )
        )
        assertEquals(MappingStatus.CONFIRMED, status)
    }

    @Test
    fun `manual effective override on system field is not forced SYSTEM_FIELD`() {
        val status = resolver.resolve(
            FieldMappingDto(
                targetField = "FCreatorId.FNumber",
                mappingType = MappingType.DIRECT,
                sourceField = "creatorCode",
                confirmed = true,
                targetRequired = true
            )
        )
        assertEquals(MappingStatus.CONFIRMED, status)
    }

    @Test
    fun `ignored non-system optional field is IGNORED`() {
        val status = resolver.resolve(
            FieldMappingDto(
                targetField = "FRemark",
                mappingType = MappingType.IGNORE,
                confidence = null,
                confirmed = false,
                targetRequired = false
            )
        )
        assertEquals(MappingStatus.IGNORED, status)
    }

    @Test
    fun `summary counts pending and required unmapped`() {
        val mappings = resolver.enrichAll(
            listOf(
                FieldMappingDto(
                    targetField = "FBillTypeID.FNumber",
                    mappingType = MappingType.IGNORE,
                    targetRequired = true
                ),
                FieldMappingDto(
                    targetField = "FBillNo",
                    mappingType = MappingType.DIRECT,
                    sourceField = "orderNo",
                    confidence = 0.98,
                    confirmed = false
                ),
                FieldMappingDto(
                    targetField = "FCreatorId.FNumber",
                    mappingType = MappingType.IGNORE,
                    targetRequired = true,
                    confidence = null
                )
            )
        )
        val summary = resolver.summarize(mappings)
        assertEquals(3, summary.totalFields)
        assertEquals(2, summary.requiredFields)
        assertEquals(1, summary.requiredUnmappedFields)
        assertEquals(2, summary.pendingFields)
        assertTrue(summary.configuredFields >= 1)
        assertEquals(MappingStatus.SYSTEM_FIELD, mappings[2].status)
    }
}
