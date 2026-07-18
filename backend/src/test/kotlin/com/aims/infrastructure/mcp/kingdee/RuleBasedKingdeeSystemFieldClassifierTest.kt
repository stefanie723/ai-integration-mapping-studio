package com.aims.infrastructure.mcp.kingdee

import com.aims.domain.schema.SchemaField
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleBasedKingdeeSystemFieldClassifierTest {

    private val classifier = RuleBasedKingdeeSystemFieldClassifier()

    @Test
    fun `recognizes common system field codes`() {
        assertTrue(classifier.isSystemField("FCreatorId"))
        assertTrue(classifier.isSystemField("FCreateDate"))
        assertTrue(classifier.isSystemField("FModifierId"))
        assertTrue(classifier.isSystemField("FDocumentStatus"))
        assertTrue(classifier.isSystemField("FApproveDate"))
    }

    @Test
    fun `recognizes nested path leaf code`() {
        assertTrue(classifier.isSystemField("Header.FCreatorId"))
        assertTrue(
            classifier.isSystemField(
                SchemaField(path = "FCreateDate", code = "FCreateDate", name = "创建日期")
            )
        )
    }

    @Test
    fun `business fields are not system fields`() {
        assertFalse(classifier.isSystemField("FBillNo"))
        assertFalse(classifier.isSystemField("FSupplierId.FNumber"))
        assertFalse(classifier.isSystemField("FPOOrderEntry[].FQty"))
    }
}
