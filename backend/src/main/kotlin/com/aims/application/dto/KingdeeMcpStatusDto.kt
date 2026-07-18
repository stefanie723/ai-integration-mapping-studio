package com.aims.application.dto

data class KingdeeMcpStatusDto(
    val mode: String,
    val connected: Boolean,
    val server: String = "KingdeeMCP",
    val transport: String? = null,
    val metadataToolAvailable: Boolean = false,
    val availableTools: List<String> = emptyList(),
    val message: String? = null
)
