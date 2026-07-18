package com.aims.infrastructure.mcp.kingdee

import com.aims.domain.schema.SchemaField
import com.aims.domain.schema.SchemaTree
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "aims.mcp.kingdee", name = ["mode"], havingValue = "mock", matchIfMissing = true)
class MockKingdeeMcpClient : KingdeeMcpClient {

    override fun listForms(customerId: Long): List<KingdeeFormInfo> = listOf(
        KingdeeFormInfo(formId = "PUR_PurchaseOrder", name = "Purchase Order")
    )

    override fun getFormSchema(customerId: Long, formId: String, refresh: Boolean): SchemaTree =
        getFormSchema(customerId, formId)

    override fun getFormSchema(customerId: Long, formId: String): SchemaTree {
        require(formId == "PUR_PurchaseOrder") {
            "Mock only supports PUR_PurchaseOrder, got: $formId"
        }
        val orgHint = when (customerId) {
            1L -> "Customer A fixed org 100"
            2L -> "Customer B fixed org 200"
            else -> "Purchase org"
        }
        return SchemaTree(
            rootName = "PUR_PurchaseOrder",
            rootId = formId,
            fields = listOf(
                SchemaField(path = "FBillNo", code = "FBillNo", name = "Bill No", description = "Kingdee PO number", dataType = "string", required = true, group = "Header"),
                SchemaField(path = "FDate", code = "FDate", name = "PO Date", description = "Bill date", dataType = "date", required = true, group = "Header"),
                SchemaField(
                    path = "FSupplierId", code = "FSupplierId", name = "Supplier", description = "Supplier master",
                    dataType = "basiedata", required = true, lookUpObject = "BD_Supplier", group = "Header",
                    children = listOf(
                        SchemaField(path = "FSupplierId.FNumber", code = "FNumber", name = "Supplier No", description = "Supplier number", dataType = "string", required = true, group = "Header")
                    )
                ),
                SchemaField(
                    path = "FPurchaseOrgId", code = "FPurchaseOrgId", name = "Purchase Org", description = orgHint,
                    dataType = "basiedata", required = true, lookUpObject = "ORG_Organizations", group = "Header",
                    children = listOf(
                        SchemaField(path = "FPurchaseOrgId.FNumber", code = "FNumber", name = "Org No", description = orgHint, dataType = "string", required = true, group = "Header")
                    )
                ),
                SchemaField(
                    path = "FSettleCurrId", code = "FSettleCurrId", name = "Currency", description = "Settle currency",
                    dataType = "basiedata", required = true, lookUpObject = "BD_Currency", group = "Header",
                    children = listOf(
                        SchemaField(path = "FSettleCurrId.FNumber", code = "FNumber", name = "Currency No", description = "Currency number", dataType = "string", required = true, group = "Header")
                    )
                ),
                SchemaField(
                    path = "FPOOrderEntry", code = "FPOOrderEntry", name = "Entries", description = "PO entries",
                    dataType = "array", required = true, group = "Entry",
                    children = listOf(
                        SchemaField(
                            path = "FPOOrderEntry[].FMaterialId", code = "FMaterialId", name = "Material",
                            description = "Material master", dataType = "basiedata", required = true,
                            lookUpObject = "BD_MATERIAL", group = "Entry",
                            children = listOf(
                                SchemaField(path = "FPOOrderEntry[].FMaterialId.FNumber", code = "FNumber", name = "Material No", description = "Material number", dataType = "string", required = true, group = "Entry")
                            )
                        ),
                        SchemaField(path = "FPOOrderEntry[].FQty", code = "FQty", name = "Qty", description = "Quantity", dataType = "number", required = true, group = "Entry"),
                        SchemaField(path = "FPOOrderEntry[].FPrice", code = "FPrice", name = "Price", description = "Unit price", dataType = "number", required = false, group = "Entry")
                    )
                )
            )
        )
    }
}
