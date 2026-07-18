package com.aims.infrastructure.ai

import com.aims.domain.mapping.MappingHistory
import com.aims.domain.mapping.MappingType
import com.aims.domain.schema.SchemaField
import com.aims.domain.schema.SchemaTree
import com.aims.infrastructure.mcp.kingdee.KingdeeSystemFieldClassifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Rule-based mock AI for Phase 1 / V0.2.1.
 * Only recommends against real target fields from Kingdee schema — never invents fields.
 */
@Component
@ConditionalOnProperty(name = ["aims.ai.provider"], havingValue = "mock", matchIfMissing = true)
class MockAiMappingProvider(
    private val systemFieldClassifier: KingdeeSystemFieldClassifier
) : AiMappingProvider {

    private val knownRules: List<RecommendRule> = listOf(
        RecommendRule("FBillNo", "orderNo", MappingType.DIRECT, 0.98, "源字段为订单编号，目标字段为金蝶单据编号"),
        RecommendRule("FDate", "orderDate", MappingType.DIRECT, 0.96, "源字段为订单日期，目标字段为采购日期"),
        RecommendRule("FSupplierId.FNumber", "supplierCode", MappingType.DIRECT, 0.95, "源字段为供应商编码，目标字段为供应商基础资料编码"),
        RecommendRule("FSettleCurrId.FNumber", "currencyCode", MappingType.DEFAULT, 0.88, "币别可直接映射，空值时建议默认 PRE001", defaultValue = "PRE001"),
        RecommendRule("FPurchaseOrgId.FNumber", "purchaseOrgCode", MappingType.CONSTANT, 0.55, "采购组织常因客户而异，建议确认固定值", fixedValue = "100", needConfirm = true),
        RecommendRule("FPOOrderEntry[].FMaterialId.FNumber", "items[].materialCode", MappingType.DIRECT, 0.97, "明细物料编码直接映射"),
        RecommendRule("FPOOrderEntry[].FQty", "items[].qty", MappingType.DIRECT, 0.97, "明细数量直接映射"),
        RecommendRule("FPOOrderEntry[].FPrice", "items[].price", MappingType.DIRECT, 0.92, "明细单价直接映射")
    )

    override fun recommend(
        sourceSchema: SchemaTree,
        targetSchema: SchemaTree,
        history: List<MappingHistory>
    ): MappingRecommendationResult {
        val sourcePaths = flatten(sourceSchema.fields).map { it.path }.toSet()
        val targetLeaves = flatten(targetSchema.fields).filter { it.children.isEmpty() }
        val historyBoost = history.associate { "${it.targetField}|${it.sourceField}" to it.usageCount }

        val items = targetLeaves.map { target ->
            val rule = knownRules.find { it.targetField == target.path }
            if (rule != null && (rule.sourceField == null || rule.sourceField in sourcePaths || rule.mappingType == MappingType.CONSTANT)) {
                val boostKey = "${rule.targetField}|${rule.sourceField}"
                val usage = historyBoost[boostKey] ?: 0
                val confidence = (rule.confidence + minOf(usage * 0.005, 0.05)).coerceAtMost(0.99)
                MappingRecommendationItem(
                    targetField = target.path,
                    sourceField = rule.sourceField,
                    mappingType = rule.mappingType,
                    fixedValue = rule.fixedValue,
                    defaultValue = rule.defaultValue,
                    confidence = confidence,
                    reason = rule.reason + if (usage > 0) "（历史确认 ${usage} 次）" else "",
                    needConfirm = rule.needConfirm || confidence < 0.6
                )
            } else if (systemFieldClassifier.isSystemField(target)) {
                MappingRecommendationItem(
                    targetField = target.path,
                    sourceField = null,
                    mappingType = MappingType.IGNORE,
                    confidence = null,
                    reason = "系统字段，无需人工 Mapping",
                    needConfirm = false
                )
            } else {
                MappingRecommendationItem(
                    targetField = target.path,
                    sourceField = null,
                    mappingType = MappingType.IGNORE,
                    confidence = null,
                    reason = "未匹配到可用映射，默认忽略",
                    needConfirm = false
                )
            }
        }

        return MappingRecommendationResult(mappings = items)
    }

    private fun flatten(fields: List<SchemaField>): List<SchemaField> {
        val result = mutableListOf<SchemaField>()
        fun walk(list: List<SchemaField>) {
            list.forEach { f ->
                result.add(f)
                if (f.children.isNotEmpty()) walk(f.children)
            }
        }
        walk(fields)
        return result
    }

    private data class RecommendRule(
        val targetField: String,
        val sourceField: String?,
        val mappingType: MappingType,
        val confidence: Double,
        val reason: String,
        val fixedValue: String? = null,
        val defaultValue: String? = null,
        val needConfirm: Boolean = false
    )
}
