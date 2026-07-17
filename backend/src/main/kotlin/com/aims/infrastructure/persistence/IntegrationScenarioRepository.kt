package com.aims.infrastructure.persistence

import com.aims.domain.scenario.IntegrationScenario
import org.springframework.data.jpa.repository.JpaRepository

interface IntegrationScenarioRepository : JpaRepository<IntegrationScenario, Long> {
    fun findByCode(code: String): IntegrationScenario?
}
