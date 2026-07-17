package com.aims.domain.mapping

import jakarta.persistence.*

@Entity
@Table(name = "field_mapping")
class FieldMapping(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "target_field", nullable = false, length = 256)
    var targetField: String = "",

    @Column(name = "target_field_name", length = 128)
    var targetFieldName: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_type", nullable = false, length = 32)
    var mappingType: MappingType = MappingType.DIRECT,

    @Column(name = "source_field", length = 256)
    var sourceField: String? = null,

    @Column(name = "fixed_value", length = 512)
    var fixedValue: String? = null,

    @Column(name = "default_value", length = 512)
    var defaultValue: String? = null,

    @Column(length = 1024)
    var expression: String? = null,

    /** JSON string of dictionary map, e.g. {"SUP001":"001"} */
    @Column(columnDefinition = "TEXT")
    var dictionary: String? = null,

    var confidence: Double? = null,

    @Column(name = "ai_reason", length = 1024)
    var aiReason: String? = null,

    var confirmed: Boolean = false,

    @Column(name = "target_required")
    var targetRequired: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_id")
    var configuration: MappingConfiguration? = null
)
