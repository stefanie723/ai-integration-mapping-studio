package com.aims.config

import com.aims.infrastructure.mcp.core.KingdeeMcpProperties
import com.aims.infrastructure.mcp.core.McpGateway
import com.aims.infrastructure.mcp.core.SdkMcpGateway
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableCaching
@EnableConfigurationProperties(KingdeeMcpProperties::class)
class McpConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "aims.mcp.kingdee", name = ["mode"], havingValue = "real")
    fun kingdeeMcpGateway(
        properties: KingdeeMcpProperties,
        objectMapper: ObjectMapper
    ): McpGateway = SdkMcpGateway(properties, objectMapper)
}
