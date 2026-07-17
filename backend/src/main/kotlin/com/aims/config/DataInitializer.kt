package com.aims.config

import com.aims.domain.customer.Customer
import com.aims.domain.scenario.IntegrationScenario
import com.aims.infrastructure.persistence.CustomerRepository
import com.aims.infrastructure.persistence.IntegrationScenarioRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class DataInitializer(
    private val customerRepository: CustomerRepository,
    private val scenarioRepository: IntegrationScenarioRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (customerRepository.count() == 0L) {
            customerRepository.saveAll(
                listOf(
                    Customer(code = "customer-a", name = "Customer A (\u5BA2\u6237 A)"),
                    Customer(code = "customer-b", name = "Customer B (\u5BA2\u6237 B)")
                )
            )
            log.info("Seeded customers")
        }
        if (scenarioRepository.count() == 0L) {
            scenarioRepository.save(
                IntegrationScenario(
                    code = "PURCHASE_ORDER_TO_KINGDEE",
                    name = "\u91C7\u8D2D\u8BA2\u5355 \u2192 \u91D1\u8776\u91C7\u8D2D\u8BA2\u5355",
                    sourceApi = "/openapi/purchase-orders",
                    targetFormId = "PUR_PurchaseOrder"
                )
            )
            log.info("Seeded integration scenarios")
        }
    }
}
