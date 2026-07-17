package com.aims.infrastructure.persistence

import com.aims.domain.customer.Customer
import org.springframework.data.jpa.repository.JpaRepository

interface CustomerRepository : JpaRepository<Customer, Long> {
    fun findByCode(code: String): Customer?
}
