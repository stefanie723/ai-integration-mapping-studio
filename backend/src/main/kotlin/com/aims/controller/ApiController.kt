package com.aims.controller

import com.aims.application.KingdeeMcpStatusService
import com.aims.application.MappingApplicationService
import com.aims.application.dto.*
import com.aims.domain.schema.SchemaTree
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = ["*"])
class ApiController(
    private val mappingService: MappingApplicationService,
    private val kingdeeMcpStatusService: KingdeeMcpStatusService
) {

    @GetMapping("/customers")
    fun customers(): ApiResponse<List<CustomerDto>> =
        ApiResponse(data = mappingService.listCustomers())

    @GetMapping("/integration-scenarios")
    fun scenarios(): ApiResponse<List<ScenarioDto>> =
        ApiResponse(data = mappingService.listScenarios())

    @GetMapping("/schemas/source")
    fun sourceSchema(@RequestParam scenarioCode: String): ApiResponse<SchemaTree> =
        ApiResponse(data = mappingService.getSourceSchema(scenarioCode))

    @GetMapping("/schemas/kingdee")
    fun kingdeeSchema(
        @RequestParam customerId: Long,
        @RequestParam formId: String,
        @RequestParam(defaultValue = "false") refresh: Boolean
    ): ApiResponse<SchemaTree> =
        ApiResponse(data = mappingService.getKingdeeSchema(customerId, formId, refresh))

    @GetMapping("/mcp/kingdee/status")
    fun kingdeeMcpStatus(): ApiResponse<KingdeeMcpStatusDto> =
        ApiResponse(data = kingdeeMcpStatusService.status())

    @PostMapping("/mappings/recommend")
    fun recommend(@RequestBody request: RecommendRequest): ApiResponse<RecommendResponse> =
        ApiResponse(data = mappingService.recommend(request))

    @GetMapping("/mappings")
    fun getMapping(
        @RequestParam customerId: Long,
        @RequestParam scenarioCode: String
    ): ApiResponse<MappingConfigurationDto?> =
        ApiResponse(data = mappingService.getMapping(customerId, scenarioCode))

    @PostMapping("/mappings")
    fun saveMapping(@RequestBody dto: MappingConfigurationDto): ApiResponse<MappingConfigurationDto> =
        ApiResponse(data = mappingService.saveMapping(dto))

    @PostMapping("/mappings/check-required")
    fun checkRequired(@RequestBody dto: MappingConfigurationDto): ApiResponse<RequiredCheckResult> =
        ApiResponse(data = mappingService.checkRequired(dto))

    @PostMapping("/code-generation")
    fun generateCode(@RequestBody request: CodeGenerationRequest): ApiResponse<CodeGenerationResponse> =
        ApiResponse(data = mappingService.generateCode(request))
}
