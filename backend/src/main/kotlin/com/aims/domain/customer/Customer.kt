package com.aims.domain.customer

import jakarta.persistence.*

@Entity
@Table(name = "customer")
data class Customer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 64)
    val code: String = "",

    @Column(nullable = false, length = 128)
    val name: String = ""
)
