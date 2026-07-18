<template>
  <div class="studio">
    <header class="toolbar">
      <div class="brand">
        <div class="brand-name">AI Integration Mapping Studio</div>
        <div class="brand-sub">AI 接口字段映射工作台 · V0.2</div>
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
      />

      <div class="mapping-panel">
        <div class="panel-head">
          <span>Mapping 配置</span>
          <span class="hint" v-if="targetFormId">FormId: {{ targetFormId }}</span>
        </div>
        <el-table :data="mappings" height="100%" border size="small" empty-text="请先选择客户与场景，点击 AI 自动推荐">
          <el-table-column prop="targetField" label="目标字段" min-width="180" show-overflow-tooltip />
          <el-table-column prop="targetFieldName" label="目标字段名称" width="110" />
          <el-table-column label="必填" width="60" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.targetRequired" size="small" type="danger">是</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="赋值方式" width="130">
            <template #default="{ row }">
              <el-select v-model="row.mappingType" size="small" @change="onTypeChange(row)">
                <el-option label="直接映射" value="DIRECT" />
                <el-option label="固定值" value="CONSTANT" />
                <el-option label="默认值" value="DEFAULT" />
                <el-option label="字典转换" value="DICTIONARY" />
                <el-option label="忽略" value="IGNORE" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="源字段 / 配置值" min-width="220">
            <template #default="{ row }">
              <div class="config-cell">
                <el-select
                  v-if="row.mappingType === 'DIRECT' || row.mappingType === 'DEFAULT' || row.mappingType === 'DICTIONARY'"
                  v-model="row.sourceField"
                  clearable
                  filterable
                  size="small"
                  placeholder="选择源字段"
                >
                  <el-option v-for="p in sourcePaths" :key="p" :label="p" :value="p" />
                </el-select>
                <el-input
                  v-if="row.mappingType === 'CONSTANT'"
                  v-model="row.fixedValue"
                  size="small"
                  placeholder="固定值"
                />
                <el-input
                  v-if="row.mappingType === 'DEFAULT'"
                  v-model="row.defaultValue"
                  size="small"
                  placeholder="默认值"
                />
                <div v-if="row.mappingType === 'DICTIONARY'" class="dict-box">
                  <div v-for="(pair, idx) in dictRows(row)" :key="idx" class="dict-row">
                    <el-input v-model="pair.from" size="small" placeholder="源值" @change="syncDict(row)" />
                    <span>→</span>
                    <el-input v-model="pair.to" size="small" placeholder="目标值" @change="syncDict(row)" />
                    <el-button link type="danger" @click="removeDictRow(row, idx)">删</el-button>
                  </div>
                  <el-button size="small" @click="addDictRow(row)">新增规则</el-button>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="置信度" width="100" align="center">
            <template #default="{ row }">
              <span :class="confidenceClass(row.confidence)">
                {{ row.confidence != null ? Math.round(row.confidence * 100) + '%' : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.confirmed" type="success" size="small">已确认</el-tag>
              <el-tag v-else-if="(row.confidence ?? 0) >= 0.9" type="success" effect="plain" size="small">高置信度</el-tag>
              <el-tag v-else-if="(row.confidence ?? 0) >= 0.6" type="warning" size="small">需要确认</el-tag>
              <el-tag v-else type="danger" size="small">无法确定</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="row.confirmed = true">确认</el-button>
              <el-button link @click="showReason(row)">原因</el-button>
            </template>
          </el-table-column>
        </el-table>
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
        @refresh="onRefreshKingdeeSchema"
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
  MappingType,
  Scenario,
  SchemaField,
  SchemaTree
} from '../../types'

const customers = ref<Customer[]>([])
const scenarios = ref<Scenario[]>([])
const customerId = ref<number>()
const scenarioCode = ref<string>()
const sourceSchema = ref<SchemaTree | null>(null)
const targetSchema = ref<SchemaTree | null>(null)
const mappings = ref<FieldMapping[]>([])
const sourceApi = ref('')
const targetFormId = ref('')
const configId = ref<number>()
const missingRequired = ref<string[]>([])

const loadingRecommend = ref(false)
const loadingSave = ref(false)
const loadingCode = ref(false)
const loadingKingdeeRefresh = ref(false)
const codeVisible = ref(false)
const generatedFiles = ref<GeneratedFile[]>([])
const kingdeeMode = ref('mock')
const kingdeeConnected = ref(false)
const kingdeeSourceLabel = ref('数据来源: Mock')

/** local editable dictionary rows keyed by targetField */
const dictCache = reactive<Record<string, { from: string; to: string }[]>>({})

const sourcePaths = computed(() => flattenPaths(sourceSchema.value?.fields || []))

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
    targetSchema.value = await api.getKingdeeSchema(customerId.value, targetFormId.value, true)
    await refreshKingdeeStatus()
    ElMessage.success('已从 Kingdee MCP 刷新 Schema')
  } catch (e: any) {
    ElMessage.error(e.message || '刷新失败')
    await refreshKingdeeStatus()
  } finally {
    loadingKingdeeRefresh.value = false
  }
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
    mappings.value = res.mappings
    configId.value = undefined
    missingRequired.value = []
    Object.keys(dictCache).forEach((k) => delete dictCache[k])
    ElMessage.success(`已推荐 ${res.mappings.length} 条 Mapping`)
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
    const scenario = scenarios.value.find((s) => s.code === scenarioCode.value)
    sourceSchema.value = await api.getSourceSchema(scenarioCode.value)
    targetSchema.value = await api.getKingdeeSchema(customerId.value, saved.targetFormId)
    sourceApi.value = saved.sourceApi
    targetFormId.value = saved.targetFormId
    mappings.value = saved.mappings
    configId.value = saved.id
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
    mappings.value = saved.mappings
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
    else ElMessage.warning(`还有 ${result.missingRequiredFields.length} 个必填字段未配置`)
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
  if (row.mappingType === 'IGNORE') {
    row.sourceField = undefined
    row.fixedValue = undefined
    row.defaultValue = undefined
    row.dictionary = undefined
  }
}

function confidenceClass(c?: number) {
  if (c == null) return ''
  if (c >= 0.9) return 'c-high'
  if (c >= 0.6) return 'c-mid'
  return 'c-low'
}

function confirmHighConfidence() {
  mappings.value.forEach((m) => {
    if ((m.confidence ?? 0) >= 0.9) m.confirmed = true
  })
  ElMessage.success('已确认高置信度 Mapping')
}

function showReason(row: FieldMapping) {
  ElMessageBox.alert(row.aiReason || '无 AI 说明', `推荐原因 · ${row.targetField}`)
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

@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 1fr;
    overflow: auto;
  }
}
</style>
