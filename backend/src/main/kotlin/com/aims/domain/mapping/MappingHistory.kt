package com.aims.domain.mapping

import jakarta.persistence.*

@Entity
@Table(
    name = "mapping_history",
    indexes = [
        Index(name = "idx_history_source_target", columnList = "source_field,target_form_id,target_field")
    ]
)
data class MappingHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "source_field", nullable = false, length = 256)
    val sourceField: String = "",

    @Column(name = "source_field_name", length = 128)
    val sourceFieldName: String? = null,

    @Column(name = "target_form_id", nullable = false, length = 128)
    val targetFormId: String = "",

    @Column(name = "target_field", nullable = false, length = 256)
    val targetField: String = "",

    @Column(name = "target_field_name", length = 128)
    val targetFieldName: String? = null,

    @Column(name = "usage_count", nullable = false)
    var usageCount: Int = 1
)
