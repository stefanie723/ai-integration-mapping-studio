import axios from 'axios'
import type {
  ApiResponse,
  Customer,
  GeneratedFile,
  MappingConfiguration,
  ReconcileSchemaRequest,
  ReconcileSchemaResponse,
  RecommendResponse,
  RequiredCheckResult,
  Scenario,
  SchemaTree
} from '../types'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000
})

async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  try {
    const { data } = await promise
    if (!data.success) {
      throw new Error(data.message || '请求失败')
    }
    return data.data as T
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '请求失败'
    throw new Error(msg)
  }
}

export interface KingdeeMcpStatus {
  mode: string
  connected: boolean
  server: string
  transport?: string
  metadataToolAvailable: boolean
  availableTools?: string[]
  message?: string
}

export const api = {
  getCustomers: () => unwrap(http.get<ApiResponse<Customer[]>>('/customers')),
  getScenarios: () => unwrap(http.get<ApiResponse<Scenario[]>>('/integration-scenarios')),
  getSourceSchema: (scenarioCode: string) =>
    unwrap(http.get<ApiResponse<SchemaTree>>('/schemas/source', { params: { scenarioCode } })),
  getKingdeeSchema: (customerId: number, formId: string, refresh = false) =>
    unwrap(
      http.get<ApiResponse<SchemaTree>>('/schemas/kingdee', {
        params: { customerId, formId, refresh }
      })
    ),
  getKingdeeMcpStatus: () => unwrap(http.get<ApiResponse<KingdeeMcpStatus>>('/mcp/kingdee/status')),
  recommend: (customerId: number, scenarioCode: string) =>
    unwrap(http.post<ApiResponse<RecommendResponse>>('/mappings/recommend', { customerId, scenarioCode })),
  getMapping: (customerId: number, scenarioCode: string) =>
    unwrap(http.get<ApiResponse<MappingConfiguration | null>>('/mappings', { params: { customerId, scenarioCode } })),
  saveMapping: (config: MappingConfiguration) =>
    unwrap(http.post<ApiResponse<MappingConfiguration>>('/mappings', config)),
  checkRequired: (config: MappingConfiguration) =>
    unwrap(http.post<ApiResponse<RequiredCheckResult>>('/mappings/check-required', config)),
  reconcileSchema: (request: ReconcileSchemaRequest) =>
    unwrap(
      http.post<ApiResponse<ReconcileSchemaResponse>>('/mappings/reconcile-schema', request, {
        timeout: 120000
      })
    ),
  generateCode: (mappingConfigurationId: number, language = 'KOTLIN') =>
    unwrap(
      http.post<ApiResponse<{ files: GeneratedFile[] }>>('/code-generation', {
        mappingConfigurationId,
        language
      })
    )
}
