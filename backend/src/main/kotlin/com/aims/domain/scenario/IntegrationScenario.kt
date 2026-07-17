package com.aims.domain.scenario

import jakarta.persistence.*

@Entity
@Table(name = "integration_scenario")
data class IntegrationScenario(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 64)
    val code: String = "",

    @Column(nullable = false, length = 128)
    val name: String = "",

    @Column(name = "source_api", length = 256)
    val sourceApi: String? = null,

    @Column(name = "target_form_id", length = 128)
    val targetFormId: String? = null
)
