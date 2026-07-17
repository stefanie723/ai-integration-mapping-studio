package com.aims.domain.mapping

import jakarta.persistence.*

@Entity
@Table(
    name = "mapping_configuration",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["customer_id", "scenario_code"])
    ]
)
class MappingConfiguration(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "customer_id", nullable = false)
    var customerId: Long = 0,

    @Column(name = "scenario_code", nullable = false, length = 64)
    var scenarioCode: String = "",

    @Column(name = "source_api", nullable = false, length = 256)
    var sourceApi: String = "",

    @Column(name = "target_form_id", nullable = false, length = 128)
    var targetFormId: String = "",

    @OneToMany(
        mappedBy = "configuration",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER
    )
    var mappings: MutableList<FieldMapping> = mutableListOf()
) {
    fun replaceMappings(newMappings: List<FieldMapping>) {
        mappings.clear()
        newMappings.forEach { m ->
            m.configuration = this
            mappings.add(m)
        }
    }
}
