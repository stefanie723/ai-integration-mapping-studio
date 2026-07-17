export type MappingType =
  | 'DIRECT'
  | 'CONSTANT'
  | 'DEFAULT'
  | 'DICTIONARY'
  | 'EXPRESSION'
  | 'CONDITION'
  | 'LOOKUP'
  | 'IGNORE'

export interface Customer {
  id: number
  code: string
  name: string
}

export interface Scenario {
  id: number
  code: string
  name: string
  sourceApi?: string
  targetFormId?: string
}

export interface SchemaField {
  path: string
  code: string
  name?: string
  description?: string
  dataType?: string
  required: boolean
  children?: SchemaField[]
  lookUpObject?: string
  group?: string
}

export interface SchemaTree {
  rootName: string
  rootId: string
  fields: SchemaField[]
}

export interface FieldMapping {
  id?: number
  targetField: string
  targetFieldName?: string
  mappingType: MappingType
  sourceField?: string
  fixedValue?: string
  defaultValue?: string
  expression?: string
  dictionary?: Record<string, string>
  confidence?: number
  aiReason?: string
  confirmed: boolean
  targetRequired: boolean
  needConfirm?: boolean
}

export interface MappingConfiguration {
  id?: number
  customerId: number
  scenarioCode: string
  sourceApi: string
  targetFormId: string
  mappings: FieldMapping[]
}

export interface RecommendResponse {
  customerId: number
  scenarioCode: string
  sourceApi: string
  targetFormId: string
  sourceSchema: SchemaTree
  targetSchema: SchemaTree
  mappings: FieldMapping[]
}

export interface RequiredCheckResult {
  passed: boolean
  missingRequiredFields: string[]
}

export interface GeneratedFile {
  fileName: string
  content: string
}

export interface ApiResponse<T> {
  success: boolean
  data?: T
  message?: string
}
