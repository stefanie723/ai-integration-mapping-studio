package com.aims.infrastructure.persistence

import com.aims.domain.mapping.MappingConfiguration
import org.springframework.data.jpa.repository.JpaRepository

interface MappingConfigurationRepository : JpaRepository<MappingConfiguration, Long> {
    fun findByCustomerIdAndScenarioCode(customerId: Long, scenarioCode: String): MappingConfiguration?
}
