package com.aims.infrastructure.mcp.company

import com.aims.domain.schema.SchemaField
import com.aims.domain.schema.SchemaTree
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["aims.mcp.company.mode"], havingValue = "mock", matchIfMissing = true)
class MockCompanyMcpClient : CompanyMcpClient {

    override fun listApis(): List<CompanyApiInfo> = listOf(
        CompanyApiInfo(
            path = "/openapi/purchase-orders",
            name = "Purchase Order",
            description = "Company purchase order OpenAPI"
        )
    )

    override fun getApiSchema(apiPath: String): SchemaTree {
        require(apiPath == "/openapi/purchase-orders") {
            "Mock only supports /openapi/purchase-orders, got: $apiPath"
        }
        return SchemaTree(
            rootName = "PurchaseOrder",
            rootId = apiPath,
            fields = listOf(
                SchemaField(path = "orderNo", code = "orderNo", name = "Order No", description = "PO number", dataType = "string", required = true),
                SchemaField(path = "supplierCode", code = "supplierCode", name = "Supplier Code", description = "Supplier code", dataType = "string", required = true),
                SchemaField(path = "currencyCode", code = "currencyCode", name = "Currency", description = "Settle currency", dataType = "string", required = false),
                SchemaField(path = "purchaseOrgCode", code = "purchaseOrgCode", name = "Purchase Org", description = "Purchase org", dataType = "string", required = false),
                SchemaField(path = "orderDate", code = "orderDate", name = "Order Date", description = "PO date", dataType = "date", required = true),
                SchemaField(
                    path = "items",
                    code = "items",
                    name = "Lines",
                    description = "PO lines",
                    dataType = "array",
                    required = true,
                    children = listOf(
                        SchemaField(path = "items[].materialCode", code = "materialCode", name = "Material", description = "Material code", dataType = "string", required = true),
                        SchemaField(path = "items[].qty", code = "qty", name = "Qty", description = "Quantity", dataType = "number", required = true),
                        SchemaField(path = "items[].price", code = "price", name = "Price", description = "Unit price", dataType = "number", required = false)
                    )
                )
            )
        )
    }
}
