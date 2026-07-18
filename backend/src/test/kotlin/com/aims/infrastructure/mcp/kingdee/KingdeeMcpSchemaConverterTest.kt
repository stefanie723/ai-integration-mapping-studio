package com.aims.infrastructure.mcp.kingdee

import com.aims.infrastructure.mcp.kingdee.dto.KingdeeRawField
import com.aims.infrastructure.mcp.kingdee.dto.KingdeeRawFormMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KingdeeMcpSchemaConverterTest {

    private val converter = KingdeeMcpSchemaConverter()

    @Test
    fun `converts header base-data entry fields and paths`() {
        val raw = KingdeeRawFormMetadata(
            formId = "PUR_PurchaseOrder",
            name = "采购订单",
            fields = listOf(
                KingdeeRawField("FBillNo", "单据编号", "string", true, null),
                KingdeeRawField("FSupplierId", "供应商", "BaseField", true, "BD_Supplier"),
                KingdeeRawField(
                    key = "FPOOrderEntry",
                    name = "明细信息",
                    type = "entry",
                    required = false,
                    referenceFormId = null,
                    isEntry = true,
                    children = listOf(
                        KingdeeRawField("FMaterialId", "物料", "BaseField", true, "BD_MATERIAL"),
                        KingdeeRawField("FQty", "采购数量", "number", true, null)
                    )
                )
            )
        )

        val tree = converter.toSchemaTree(raw)
        assertEquals("PUR_PurchaseOrder", tree.rootId)

        val billNo = tree.fields.find { it.code == "FBillNo" }
        assertNotNull(billNo)
        assertEquals("FBillNo", billNo!!.path)
        assertTrue(billNo.required)

        val supplier = tree.fields.find { it.code == "FSupplierId" }
        assertNotNull(supplier)
        assertEquals("BD_Supplier", supplier!!.lookUpObject)
        assertTrue(supplier.children.any { it.path == "FSupplierId.FNumber" })

        val entry = tree.fields.find { it.code == "FPOOrderEntry" }
        assertNotNull(entry)
        assertEquals("FPOOrderEntry[]", entry!!.path)
        assertEquals("array", entry.dataType)

        val material = entry.children.find { it.code == "FMaterialId" }
        assertNotNull(material)
        assertEquals("FPOOrderEntry[].FMaterialId", material!!.path)
        assertTrue(material.children.any { it.path == "FPOOrderEntry[].FMaterialId.FNumber" })

        val qty = entry.children.find { it.code == "FQty" }
        assertNotNull(qty)
        assertEquals("FPOOrderEntry[].FQty", qty!!.path)
        assertTrue(qty.required)
    }

    @Test
    fun `preserves base data lookup on supplier`() {
        val raw = KingdeeRawFormMetadata(
            formId = "PUR_PurchaseOrder",
            name = "采购订单",
            fields = listOf(
                KingdeeRawField("FSupplierId", "供应商", "BaseField->BD_Supplier", true, "BD_Supplier")
            )
        )
        val tree = converter.toSchemaTree(raw)
        val supplier = tree.fields.first()
        assertEquals("BD_Supplier", supplier.lookUpObject)
        assertEquals("basiedata", supplier.dataType)
        assertEquals("单据头", supplier.group)
    }
}
