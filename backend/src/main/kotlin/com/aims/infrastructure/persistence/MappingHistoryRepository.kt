package com.aims.infrastructure.persistence

import com.aims.domain.mapping.MappingHistory
import org.springframework.data.jpa.repository.JpaRepository

interface MappingHistoryRepository : JpaRepository<MappingHistory, Long> {
    fun findByTargetFormId(targetFormId: String): List<MappingHistory>

    fun findBySourceFieldAndTargetFormIdAndTargetField(
        sourceField: String,
        targetFormId: String,
        targetField: String
    ): MappingHistory?
}
