<template>
  <div class="studio">
    <header class="toolbar">
      <div class="brand">
        <div class="brand-name">AI Integration Mapping Studio</div>
        <div class="brand-sub">AI 接口字段映射工作台 · V0.2.2</div>
      </div>
      <div class="controls">
        <el-select v-model="customerId" placeholder="选择客户" style="width: 160px">
          <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-select v-model="scenarioCode" placeholder="选择场景" style="width: 260px">
          <el-option v-for="s in scenarios" :key="s.code" :label="s.name" :value="s.code" />
        </el-select>
        <el-button type="primary" :loading="loadingRecommend" @click="onRecommend">
          AI 自动推荐
        </el-button>
        <el-button @click="onLoadSaved" :disabled="!customerId || !scenarioCode">加载已保存</el-button>
        <el-button type="success" :loading="loadingSave" @click="onSave" :disabled="!mappings.length">
          保存 Mapping
        </el-button>
        <el-button type="warning" @click="onCheckRequired" :disabled="!mappings.length">
          必填检查
        </el-button>
        <el-button type="danger" plain :loading="loadingCode" @click="onGenerateCode" :disabled="!configId">
          生成代码
        </el-button>
      </div>
    </header>

    <div v-if="mappings.length" class="summary-bar">
      <button type="button" class="stat" @click="activeView = 'required'">
        <span class="stat-label">必填</span>
        <span class="stat-value">{{ summary.requiredFields }}</span>
      </button>
      <button type="button" class="stat" @click="activeView = 'configured'">
        <span class="stat-label">已配置</span>
        <span class="stat-value">{{ summary.configuredFields }}</span>
      </button>
      <button type="button" class="stat" @click="activeView = 'pending'">
        <span class="stat-label">待处理</span>
        <span class="stat-value">{{ summary.pendingFields }}</span>
      </button>
      <button type="button" class="stat" @click="activeView = 'all'">
        <span class="stat-label">全部</span>
        <span class="stat-value">{{ summary.totalFields }}</span>
      </button>
      <div class="summary-msg">
        <span v-if="summary.requiredUnmappedFields > 0" class="warn" @click="activeView = 'required'">
          ⚠ 还有 {{ summary.requiredUnmappedFields }} 个必填字段未配置
        </span>
        <span v-else-if="summary.pendingFields === 0" class="ok">
          ✓ 当前 Mapping 已无待处理字段
        </span>
        <span v-else-if="summary.requiredUnmappedFields === 0" class="ok">
          ✓ 所有必填字段均已配置
        </span>
      </div>
    </div>

    <div v-if="missingRequired.length" class="alert-bar">
      还有 {{ missingRequired.length }} 个必填字段未配置：
      {{ missingRequired.join('、') }}
    </div>

    <div class="workspace">
      <SchemaTreePanel
        title="源系统 Schema"
        :schema="sourceSchema"
        source-label="数据来源: Mock"
        :source-connected="false"
        @field-select="onSourceFieldSelect"
      />

      <div class="mapping-panel">
        <div class="panel-head">
          <span>Mapping 配置</span>
          <span class="hint" v-if="targetFormId">FormId: {{ targetFormId }}</span>
        </div>

        <div class="view-tabs">
          <button
            v-for="tab in viewTabs"
            :key="tab.key"
            type="button"
            class="tab"
            :class="{ active: activeView === tab.key }"
            @click="activeView = tab.key"
          >
            {{ tab.label }} {{ viewCounts[tab.key] }}
          </button>
        </div>

        <el-input
          v-model="mappingKeyword"
          size="small"
          clearable
          placeholder="搜索目标字段 / 中文名称"
          class="mapping-search"
        />

        <el-table
          :data="visibleMappings"
          height="100%"
          border
          size="small"
          empty-text="当前视图无数据；可切换「全部字段」或从右侧 Schema 添加"
          :row-class-name="rowClassName"
          :row-key="(row: FieldMapping) => row.targetField"
        >
          <el-table-column prop="targetField" label="目标字段" min-width="160" show-overflow-tooltip />
          <el-table-column prop="targetFieldName" label="字段名称" width="100" show-overflow-tooltip />
          <el-table-column label="必填" width="56" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.targetRequired" size="small" type="danger">是</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="赋值方式" width="120">
            <template #default="{ row }">
              <el-select
                v-model="row.mappingType"
                size="small"
                :disabled="isSystemFieldLocked(row)"
                @change="onTypeChange(row)"
              >
                <el-option label="直接映射" value="DIRECT" />
                <el-option label="固定值" value="CONSTANT" />
                <el-option label="默认值" value="DEFAULT" />
                <el-option label="字典转换" value="DICTIONARY" />
                <el-option label="忽略" value="IGNORE" :disabled="row.targetRequired" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="来源 / 配置值" min-width="200">
            <template #default="{ row }">
              <div class="config-cell">
                <el-select
                  v-if="row.mappingType === 'DIRECT' || row.mappingType === 'DEFAULT' || row.mappingType === 'DICTIONARY'"
                  v-model="row.sourceField"
                  clearable
                  filterable
                  size="small"
                  placeholder="选择源字段"
                  :disabled="isSystemFieldLocked(row)"
                  @change="refreshLocalStatus"
                >
                  <el-option v-for="p in sourcePaths" :key="p" :label="p" :value="p" />
                </el-select>
                <el-input
                  v-if="row.mappingType === 'CONSTANT'"
                  v-model="row.fixedValue"
                  size="small"
                  placeholder="固定值"
                  :disabled="isSystemFieldLocked(row)"
                  @change="refreshLocalStatus"
                />
                <el-input
                  v-if="row.mappingType === 'DEFAULT'"
                  v-model="row.defaultValue"
                  size="small"
                  placeholder="默认值"
                  :disabled="isSystemFieldLocked(row)"
                  @change="refreshLocalStatus"
                />
                <div v-if="row.mappingType === 'DICTIONARY'" class="dict-box">
                  <div v-for="(pair, idx) in dictRows(row)" :key="idx" class="dict-row">
                    <el-input
                      v-model="pair.from"
                      size="small"
                      placeholder="源值"
                      :disabled="isSystemFieldLocked(row)"
                      @change="syncDict(row)"
                    />
                    <span>→</span>
                    <el-input
                      v-model="pair.to"
                      size="small"
                      placeholder="目标值"
                      :disabled="isSystemFieldLocked(row)"
                      @change="syncDict(row)"
                    />
                    <el-button
                      link
                      type="danger"
                      :disabled="isSystemFieldLocked(row)"
                      @click="removeDictRow(row, idx)"
                    >
                      删
                    </el-button>
                  </div>
                  <el-button size="small" :disabled="isSystemFieldLocked(row)" @click="addDictRow(row)">
                    新增规则
                  </el-button>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="置信度" width="88" align="center">
            <template #default="{ row }">
              <span v-if="showConfidence(row)" :class="confidenceClass(row.confidence)">
                {{ Math.round((row.confidence ?? 0) * 100) }}%
              </span>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small" :effect="row.status === 'AI_RECOMMENDED' ? 'plain' : 'light'">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'AI_RECOMMENDED' || row.status === 'NEED_CONFIRM'">
                <el-button link type="primary" @click="acceptMapping(row)">接受</el-button>
                <el-button link @click="startEdit(row)">修改</el-button>
                <el-button link @click="showReason(row)">为什么</el-button>
              </template>
              <template v-else-if="row.status === 'REQUIRED_UNMAPPED'">
                <el-button link type="danger" @click="startEdit(row)">立即配置</el-button>
              </template>
              <template v-else-if="row.status === 'CONFIRMED'">
                <span class="confirmed-mark">✓ 已确认</span>
                <el-button link @click="startEdit(row)">修改</el-button>
              </template>
              <template v-else-if="row.status === 'SYSTEM_FIELD'">
                <span class="muted">系统字段</span>
                <el-button link type="primary" @click="enableSystemFieldEdit(row)">手工配置</el-button>
              </template>
              <template v-else-if="row.status === 'IGNORED'">
                <span class="muted">已忽略</span>
                <el-button link @click="restoreMapping(row)">恢复配置</el-button>
              </template>
              <template v-else>
                <el-button link @click="startEdit(row)">配置</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="activeView === 'pending' && summary.pendingFields === 0 && mappings.length" class="done-banner">
          ✓ 当前 Mapping 已无待处理字段
        </div>

        <div class="table-actions">
          <el-button size="small" @click="confirmHighConfidence">一键确认高置信度</el-button>
        </div>
      </div>

      <SchemaTreePanel
        title="金蝶 Schema"
        :schema="targetSchema"
        :source-label="kingdeeSourceLabel"
        :source-connected="kingdeeConnected"
        :show-refresh="!!customerId && !!targetFormId"
        :refreshing="loadingKingdeeRefresh"
        :show-add-to-mapping="true"
        @refresh="onRefreshKingdeeSchema"
        @add-to-mapping="onAddToMapping"
      />
    </div>

    <el-dialog v-model="codeVisible" title="生成的 Kotlin 代码" width="780px">
      <div v-for="f in generatedFiles" :key="f.fileName" class="code-block">
        <div class="code-title">{{ f.fileName }}</div>
        <pre>{{ f.content }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SchemaTreePanel from '../../components/SchemaTreePanel.vue'
import { api } from '../../api'
import type {
  Customer,
  FieldMapping,
  GeneratedFile,
  MappingSummary,
  MappingView,
  Scenario,
  SchemaField,
  SchemaTree
} from '../../types'
import {
  buildSummary,
  countByView,
  filterMappings,
  recomputeStatuses,
  showConfidence,
  statusLabel,
  statusTagType
} from '../../utils/mappingFilters'

const customers = ref<Customer[]>([])
const scenarios = ref<Scenario[]>([])
const customerId = ref<number>()
const scenarioCode = ref<string>()
const sourceSchema = ref<SchemaTree | null>(null)
const targetSchema = ref<SchemaTree | null>(null)
const mappings = ref<FieldMapping[]>([])
const summary = ref<MappingSummary>({
  totalFields: 0,
  requiredFields: 0,
  configuredFields: 0,
  confirmedFields: 0,
  pendingFields: 0,
  requiredUnmappedFields: 0
})
const sourceApi = ref('')
const targetFormId = ref('')
const configId = ref<number>()
const missingRequired = ref<string[]>([])

const activeView = ref<MappingView>('pending')
const mappingKeyword = ref('')
const highlightSourceField = ref<string | null>(null)
const focusTargetField = ref<string | null>(null)
/** Target fields the user explicitly chose to override (SYSTEM_FIELD unlock) */
const manuallyEditingSystemFields = ref<Set<string>>(new Set())

const loadingRecommend = ref(false)
const loadingSave = ref(false)
const loadingCode = ref(false)
const loadingKingdeeRefresh = ref(false)
const codeVisible = ref(false)
const generatedFiles = ref<GeneratedFile[]>([])
const kingdeeMode = ref('mock')
const kingdeeConnected = ref(false)
const kingdeeSourceLabel = ref('数据来源: Mock')

const dictCache = reactive<Record<string, { from: string; to: string }[]>>({})

const viewTabs: { key: MappingView; label: string }[] = [
  { key: 'pending', label: '待处理' },
  { key: 'required', label: '必填字段' },
  { key: 'configured', label: '已配置' },
  { key: 'all', label: '全部字段' }
]

const sourcePaths = computed(() => flattenPaths(sourceSchema.value?.fields || []))
const viewCounts = computed(() => countByView(mappings.value))
const visibleMappings = computed(() =>
  filterMappings(mappings.value, activeView.value, mappingKeyword.value)
)

function applyMappings(list: FieldMapping[], serverSummary?: MappingSummary) {
  mappings.value = recomputeStatuses(list)
  summary.value = serverSummary ?? buildSummary(mappings.value)
  activeView.value = 'pending'
  mappingKeyword.value = ''
  highlightSourceField.value = null
}

function refreshLocalStatus() {
  mappings.value = recomputeStatuses(mappings.value)
  summary.value = buildSummary(mappings.value)
}

async function refreshKingdeeStatus() {
  try {
    const status = await api.getKingdeeMcpStatus()
    kingdeeMode.value = status.mode
    kingdeeConnected.value = status.connected && status.mode === 'real'
    if (status.mode === 'real') {
      kingdeeSourceLabel.value = status.connected
        ? '数据来源: Kingdee MCP · 已连接'
        : `数据来源: Kingdee MCP · 未连接`
    } else {
      kingdeeSourceLabel.value = '数据来源: Mock'
      kingdeeConnected.value = false
    }
  } catch {
    kingdeeSourceLabel.value = '数据来源: 未知'
    kingdeeConnected.value = false
  }
}

onMounted(async () => {
  try {
    customers.value = await api.getCustomers()
    scenarios.value = await api.getScenarios()
    if (customers.value.length) customerId.value = customers.value[0].id
    if (scenarios.value.length) scenarioCode.value = scenarios.value[0].code
    await refreshKingdeeStatus()
  } catch (e: any) {
    ElMessage.error(e.message || '初始化失败')
  }
})

async function onRefreshKingdeeSchema() {
  if (!customerId.value || !targetFormId.value) {
    ElMessage.warning('请先选择客户并加载场景 Schema')
    return
  }
  loadingKingdeeRefresh.value = true
  try {
    if (mappings.value.length) {
      const res = await api.reconcileSchema({
        customerId: customerId.value,
        targetFormId: targetFormId.value,
        refresh: true,
        mappings: mappings.value
      })
      targetSchema.value = res.targetSchema
      mappings.value = recomputeStatuses(res.mappings)
      summary.value = res.summary ?? buildSummary(mappings.value)
      ElMessage.success(`Schema 已刷新并同步 Mapping（全部字段 ${summary.value.totalFields}）`)
    } else {
      targetSchema.value = await api.getKingdeeSchema(customerId.value, targetFormId.value, true)
      ElMessage.success('已从 Kingdee MCP 刷新 Schema')
    }
    await refreshKingdeeStatus()
  } catch (e: any) {
    ElMessage.error(e.message || '刷新失败')
    await refreshKingdeeStatus()
  } finally {
    loadingKingdeeRefresh.value = false
  }
}

function isSystemFieldLocked(row: FieldMapping): boolean {
  return row.status === 'SYSTEM_FIELD' && !manuallyEditingSystemFields.value.has(row.targetField)
}

async function enableSystemFieldEdit(row: FieldMapping) {
  try {
    await ElMessageBox.confirm(
      '该字段被识别为金蝶系统管理字段，通常无需在接口中传入。\n确认仍然要手工配置该字段吗？',
      '手工配置系统字段',
      { confirmButtonText: '继续配置', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  const next = new Set(manuallyEditingSystemFields.value)
  next.add(row.targetField)
  manuallyEditingSystemFields.value = next
  if (row.mappingType === 'IGNORE') {
    row.mappingType = 'DIRECT'
  }
  row.confirmed = false
  refreshLocalStatus()
  activeView.value = 'all'
  focusTargetField.value = row.targetField
  ElMessage.info(`已解锁：${row.targetField}`)
}

function flattenPaths(fields: SchemaField[]): string[] {
  const result: string[] = []
  const walk = (list: SchemaField[]) => {
    list.forEach((f) => {
      if (!f.children?.length) result.push(f.path)
      else walk(f.children)
    })
  }
  walk(fields)
  return result
}

function currentConfig() {
  if (!customerId.value || !scenarioCode.value) throw new Error('请先选择客户和场景')
  return {
    id: configId.value,
    customerId: customerId.value,
    scenarioCode: scenarioCode.value,
    sourceApi: sourceApi.value,
    targetFormId: targetFormId.value,
    mappings: mappings.value
  }
}

async function onRecommend() {
  if (!customerId.value || !scenarioCode.value) {
    ElMessage.warning('请先选择客户和场景')
    return
  }
  loadingRecommend.value = true
  try {
    const res = await api.recommend(customerId.value, scenarioCode.value)
    sourceSchema.value = res.sourceSchema
    targetSchema.value = res.targetSchema
    sourceApi.value = res.sourceApi
    targetFormId.value = res.targetFormId
    configId.value = undefined
    missingRequired.value = []
    Object.keys(dictCache).forEach((k) => delete dictCache[k])
    applyMappings(res.mappings, res.summary)
    ElMessage.success(`已加载 Mapping，待处理 ${summary.value.pendingFields} 条`)
  } catch (e: any) {
    ElMessage.error(e.message || '推荐失败')
  } finally {
    loadingRecommend.value = false
  }
}

async function onLoadSaved() {
  if (!customerId.value || !scenarioCode.value) return
  try {
    const saved = await api.getMapping(customerId.value, scenarioCode.value)
    if (!saved) {
      ElMessage.info('尚未保存过 Mapping')
      return
    }
    sourceSchema.value = await api.getSourceSchema(scenarioCode.value)
    targetSchema.value = await api.getKingdeeSchema(customerId.value, saved.targetFormId)
    sourceApi.value = saved.sourceApi
    targetFormId.value = saved.targetFormId
    configId.value = saved.id
    applyMappings(saved.mappings, saved.summary)
    ElMessage.success('已加载保存的 Mapping')
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  }
}

async function onSave() {
  loadingSave.value = true
  try {
    const saved = await api.saveMapping(currentConfig())
    configId.value = saved.id
    applyMappings(saved.mappings, saved.summary)
    ElMessage.success('Mapping 已保存')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    loadingSave.value = false
  }
}

async function onCheckRequired() {
  try {
    const result = await api.checkRequired(currentConfig())
    missingRequired.value = result.missingRequiredFields
    if (result.passed) ElMessage.success('必填字段检查通过')
    else {
      activeView.value = 'required'
      ElMessage.warning(`还有 ${result.missingRequiredFields.length} 个必填字段未配置`)
    }
  } catch (e: any) {
    ElMessage.error(e.message || '检查失败')
  }
}

async function onGenerateCode() {
  if (!configId.value) {
    ElMessage.warning('请先保存 Mapping')
    return
  }
  loadingCode.value = true
  try {
    const check = await api.checkRequired(currentConfig())
    missingRequired.value = check.missingRequiredFields
    if (!check.passed) {
      activeView.value = 'required'
      ElMessage.error(`还有 ${check.missingRequiredFields.length} 个必填字段未配置，禁止生成代码`)
      return
    }
    const res = await api.generateCode(configId.value)
    generatedFiles.value = res.files
    codeVisible.value = true
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '生成失败')
  } finally {
    loadingCode.value = false
  }
}

function onTypeChange(row: FieldMapping) {
  if (row.targetRequired && row.mappingType === 'IGNORE') {
    ElMessage.warning('必填字段不允许忽略')
    row.mappingType = 'DIRECT'
    return
  }
  if (row.mappingType === 'IGNORE') {
    row.sourceField = undefined
    row.fixedValue = undefined
    row.defaultValue = undefined
    row.dictionary = undefined
    row.confirmed = false
  }
  refreshLocalStatus()
}

function confidenceClass(c?: number) {
  if (c == null) return ''
  if (c >= 0.9) return 'c-high'
  if (c >= 0.6) return 'c-mid'
  return 'c-low'
}

async function confirmHighConfidence() {
  const candidates = mappings.value.filter(
    (m) =>
      m.status === 'AI_RECOMMENDED' &&
      (m.confidence ?? 0) >= 0.9 &&
      !!m.sourceField?.trim()
  )
  if (!candidates.length) {
    ElMessage.info('没有可一键确认的高置信度 Mapping')
    return
  }
  try {
    await ElMessageBox.confirm(
      `即将确认 ${candidates.length} 条高置信度 Mapping\n确认后仍然可以手工修改。`,
      '一键确认高置信度',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }
  candidates.forEach((m) => {
    m.confirmed = true
  })
  refreshLocalStatus()
  ElMessage.success(`已确认 ${candidates.length} 条 Mapping`)
}

function acceptMapping(row: FieldMapping) {
  row.confirmed = true
  refreshLocalStatus()
}

function startEdit(row: FieldMapping) {
  focusTargetField.value = row.targetField
  if (row.mappingType === 'IGNORE') {
    row.mappingType = 'DIRECT'
  }
  row.confirmed = false
  refreshLocalStatus()
  if (activeView.value === 'pending' && row.status === 'IGNORED') {
    activeView.value = 'all'
  }
  ElMessage.info(`请配置：${row.targetField}`)
}

function restoreMapping(row: FieldMapping) {
  row.mappingType = 'DIRECT'
  row.confirmed = false
  refreshLocalStatus()
  activeView.value = 'all'
  focusTargetField.value = row.targetField
}

function showReason(row: FieldMapping) {
  ElMessageBox.alert(row.aiReason || '无 AI 说明', `为什么 · ${row.targetField}`)
}

function onSourceFieldSelect(field: SchemaField) {
  highlightSourceField.value = field.path
  const related = mappings.value.filter((m) => m.sourceField === field.path)
  if (related.length) {
    activeView.value = related.some((m) =>
      ['REQUIRED_UNMAPPED', 'NEED_CONFIRM', 'AI_RECOMMENDED'].includes(m.status || '')
    )
      ? 'pending'
      : 'configured'
    ElMessage.info(`已高亮 ${related.length} 条使用 ${field.path} 的 Mapping`)
  } else {
    ElMessage.info(`暂无使用 ${field.path} 的 Mapping`)
  }
}

function onAddToMapping(field: SchemaField) {
  if (field.children?.length) {
    ElMessage.warning('请选择叶子字段')
    return
  }
  const existing = mappings.value.find((m) => m.targetField === field.path)
  if (existing) {
    if (existing.mappingType === 'IGNORE') {
      existing.mappingType = 'DIRECT'
      existing.confirmed = false
    }
    focusTargetField.value = field.path
    activeView.value = 'all'
    mappingKeyword.value = field.code || field.path
    refreshLocalStatus()
    ElMessage.success(`已定位到 ${field.path}`)
    return
  }
  mappings.value = recomputeStatuses([
    ...mappings.value,
    {
      targetField: field.path,
      targetFieldName: field.name,
      mappingType: 'DIRECT',
      confirmed: false,
      targetRequired: field.required
    }
  ])
  summary.value = buildSummary(mappings.value)
  activeView.value = 'all'
  mappingKeyword.value = field.code || field.path
  focusTargetField.value = field.path
  ElMessage.success(`已添加 ${field.path} 到 Mapping`)
}

function rowClassName({ row }: { row: FieldMapping }) {
  const classes: string[] = []
  if (highlightSourceField.value && row.sourceField === highlightSourceField.value) {
    classes.push('row-highlight-source')
  }
  if (focusTargetField.value && row.targetField === focusTargetField.value) {
    classes.push('row-focus-target')
  }
  return classes.join(' ')
}

function dictRows(row: FieldMapping) {
  if (!dictCache[row.targetField]) {
    const entries = Object.entries(row.dictionary || {})
    dictCache[row.targetField] = entries.length
      ? entries.map(([from, to]) => ({ from, to }))
      : [{ from: '', to: '' }]
  }
  return dictCache[row.targetField]
}

function syncDict(row: FieldMapping) {
  const pairs = dictCache[row.targetField] || []
  const dict: Record<string, string> = {}
  pairs.forEach((p) => {
    if (p.from) dict[p.from] = p.to
  })
  row.dictionary = dict
  refreshLocalStatus()
}

function addDictRow(row: FieldMapping) {
  dictRows(row).push({ from: '', to: '' })
}

function removeDictRow(row: FieldMapping, idx: number) {
  dictRows(row).splice(idx, 1)
  syncDict(row)
}
</script>

<style scoped>
.studio {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #e8eef5 0%, #f0f3f7 140px, #f0f3f7 100%);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.9);
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  color: #123a5c;
}

.brand-sub {
  font-size: 12px;
  color: var(--muted);
  margin-top: 2px;
}

.controls {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}

.summary-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 8px 18px 0;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 8px;
}

.stat {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  border: 1px solid var(--border);
  background: #f7fafc;
  border-radius: 6px;
  padding: 4px 10px;
  cursor: pointer;
  font: inherit;
}

.stat:hover {
  border-color: #9bb6d1;
}

.stat-label {
  font-size: 12px;
  color: var(--muted);
}

.stat-value {
  font-size: 16px;
  font-weight: 700;
  color: #123a5c;
}

.summary-msg {
  margin-left: auto;
  font-size: 13px;
}

.summary-msg .warn {
  color: #8a5a00;
  cursor: pointer;
}

.summary-msg .ok {
  color: var(--success);
}

.alert-bar {
  margin: 8px 18px 0;
  padding: 8px 12px;
  background: #fff4e5;
  color: #8a5a00;
  border: 1px solid #f0d9a8;
  border-radius: 6px;
  font-size: 13px;
}

.workspace {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px 1fr 280px;
  gap: 12px;
  padding: 12px 18px 18px;
}

.mapping-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin-bottom: 8px;
}

.hint {
  font-weight: 400;
  color: var(--muted);
  font-size: 12px;
}

.view-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.tab {
  border: 1px solid var(--border);
  background: #fff;
  border-radius: 16px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  color: #334155;
}

.tab.active {
  background: #123a5c;
  border-color: #123a5c;
  color: #fff;
}

.mapping-search {
  margin-bottom: 8px;
}

.config-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dict-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dict-row {
  display: grid;
  grid-template-columns: 1fr auto 1fr auto;
  gap: 4px;
  align-items: center;
}

.table-actions {
  margin-top: 8px;
}

.done-banner {
  margin-top: 8px;
  padding: 10px;
  text-align: center;
  color: var(--success);
  background: #edf8f1;
  border-radius: 6px;
  font-size: 13px;
}

.confirmed-mark {
  color: var(--success);
  font-size: 12px;
  margin-right: 4px;
}

.muted {
  color: var(--muted);
  font-size: 12px;
}

.c-high { color: var(--success); font-weight: 600; }
.c-mid { color: var(--warn); font-weight: 600; }
.c-low { color: var(--danger); font-weight: 600; }

.code-block {
  margin-bottom: 16px;
}

.code-title {
  font-weight: 600;
  margin-bottom: 6px;
}

pre {
  background: #0f172a;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 8px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.5;
}

:deep(.row-highlight-source) {
  --el-table-tr-bg-color: #eef6ff;
}

:deep(.row-focus-target) {
  --el-table-tr-bg-color: #fff8e8;
}

@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 1fr;
    overflow: auto;
  }
}
</style>
