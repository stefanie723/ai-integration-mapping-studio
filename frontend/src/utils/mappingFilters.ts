import type { FieldMapping, MappingStatus, MappingSummary, MappingView } from '../types'

/** Keep in sync with backend RuleBasedKingdeeSystemFieldClassifier */
const SYSTEM_FIELD_CODES = new Set([
  'FCreatorId',
  'FCreateDate',
  'FModifierId',
  'FModifyDate',
  'FApproverId',
  'FApproveDate',
  'FCancellerId',
  'FCancelDate',
  'FDocumentStatus'
])

const PENDING_STATUSES: MappingStatus[] = ['REQUIRED_UNMAPPED', 'NEED_CONFIRM', 'AI_RECOMMENDED']

const PENDING_ORDER: Record<string, number> = {
  REQUIRED_UNMAPPED: 0,
  NEED_CONFIRM: 1,
  AI_RECOMMENDED: 2
}

/** FEntity[].FCreatorId.FNumber → [FEntity, FCreatorId, FNumber] */
export function normalizePathSegments(path: string): string[] {
  return path
    .split('.')
    .map((s) => s.replace(/\[\]/g, '').trim())
    .filter(Boolean)
}

export function isSystemField(targetField: string): boolean {
  if (!targetField) return false
  return normalizePathSegments(targetField).some((seg) => SYSTEM_FIELD_CODES.has(seg))
}

export function isEffectiveMapping(m: FieldMapping): boolean {
  if (m.mappingType === 'IGNORE') return false
  switch (m.mappingType) {
    case 'CONSTANT':
      return !!m.fixedValue?.trim()
    case 'DIRECT':
      return !!m.sourceField?.trim()
    case 'DICTIONARY':
      return !!m.sourceField?.trim() && !!m.dictionary && Object.keys(m.dictionary).length > 0
    case 'DEFAULT':
      return !!m.sourceField?.trim() || !!m.defaultValue?.trim()
    default:
      return !!m.sourceField?.trim() || !!m.fixedValue?.trim() || !!m.defaultValue?.trim()
  }
}

export function isAiSuggestion(m: FieldMapping): boolean {
  if (m.confidence == null) return false
  if (m.mappingType === 'IGNORE') return false
  return !!m.sourceField?.trim() || !!m.fixedValue?.trim() || !!m.defaultValue?.trim()
}

/** Keep in sync with backend MappingStatusResolver */
export function resolveStatus(m: FieldMapping): MappingStatus {
  const effective = isEffectiveMapping(m)
  const ai = isAiSuggestion(m)
  const system = isSystemField(m.targetField)
  const confidence = m.confidence ?? 0

  if (m.confirmed && effective) return 'CONFIRMED'
  if (effective && !m.confirmed && ai && confidence < 0.9) return 'NEED_CONFIRM'
  if (effective && !m.confirmed && ai) return 'AI_RECOMMENDED'
  if (effective && !m.confirmed) return 'UNMAPPED'
  if (!effective && system) return 'SYSTEM_FIELD'
  if (m.targetRequired && !effective) return 'REQUIRED_UNMAPPED'
  if (ai && !m.confirmed && confidence < 0.9) return 'NEED_CONFIRM'
  if (ai && !m.confirmed) return 'AI_RECOMMENDED'
  if (m.mappingType === 'IGNORE') return 'IGNORED'
  return 'UNMAPPED'
}

export function recomputeStatuses(mappings: FieldMapping[]): FieldMapping[] {
  return mappings.map((m) => {
    const status = resolveStatus(m)
    return {
      ...m,
      status,
      needConfirm: status === 'NEED_CONFIRM' || status === 'AI_RECOMMENDED'
    }
  })
}

export function buildSummary(mappings: FieldMapping[]): MappingSummary {
  const withStatus = mappings.map((m) => ({ ...m, status: m.status ?? resolveStatus(m) }))
  return {
    totalFields: withStatus.length,
    requiredFields: withStatus.filter((m) => m.targetRequired).length,
    configuredFields: withStatus.filter((m) => isEffectiveMapping(m)).length,
    confirmedFields: withStatus.filter((m) => m.status === 'CONFIRMED').length,
    pendingFields: withStatus.filter((m) => PENDING_STATUSES.includes(m.status!)).length,
    requiredUnmappedFields: withStatus.filter((m) => m.status === 'REQUIRED_UNMAPPED').length
  }
}

export function filterMappings(
  mappings: FieldMapping[],
  selectedView: MappingView,
  keyword: string
): FieldMapping[] {
  const q = keyword.trim().toLowerCase()
  let list = mappings.filter((m) => {
    const status = m.status ?? resolveStatus(m)
    switch (selectedView) {
      case 'pending':
        return PENDING_STATUSES.includes(status)
      case 'required':
        return m.targetRequired
      case 'configured':
        return isEffectiveMapping(m)
      case 'all':
      default:
        return true
    }
  })

  if (q) {
    list = list.filter(
      (m) =>
        m.targetField.toLowerCase().includes(q) ||
        (m.targetFieldName || '').toLowerCase().includes(q)
    )
  }

  if (selectedView === 'pending') {
    list = [...list].sort((a, b) => {
      const sa = a.status ?? resolveStatus(a)
      const sb = b.status ?? resolveStatus(b)
      const oa = PENDING_ORDER[sa] ?? 9
      const ob = PENDING_ORDER[sb] ?? 9
      if (oa !== ob) return oa - ob
      if (a.targetRequired !== b.targetRequired) return a.targetRequired ? -1 : 1
      return a.targetField.localeCompare(b.targetField)
    })
  }

  return list
}

export function statusLabel(status?: MappingStatus): string {
  switch (status) {
    case 'AI_RECOMMENDED':
      return 'AI 推荐'
    case 'NEED_CONFIRM':
      return '需要确认'
    case 'REQUIRED_UNMAPPED':
      return '必填未配置'
    case 'CONFIRMED':
      return '已确认'
    case 'IGNORED':
      return '已忽略'
    case 'SYSTEM_FIELD':
      return '系统字段'
    case 'UNMAPPED':
      return '未配置'
    default:
      return '未配置'
  }
}

export function statusTagType(status?: MappingStatus): '' | 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'CONFIRMED':
    case 'AI_RECOMMENDED':
      return 'success'
    case 'NEED_CONFIRM':
    case 'REQUIRED_UNMAPPED':
      return 'warning'
    case 'SYSTEM_FIELD':
    case 'IGNORED':
      return 'info'
    default:
      return ''
  }
}

export function showConfidence(m: FieldMapping): boolean {
  return m.mappingType !== 'IGNORE' && !!m.sourceField?.trim() && m.confidence != null
}

export function countByView(mappings: FieldMapping[]): Record<MappingView, number> {
  return {
    pending: filterMappings(mappings, 'pending', '').length,
    required: filterMappings(mappings, 'required', '').length,
    configured: filterMappings(mappings, 'configured', '').length,
    all: mappings.length
  }
}
