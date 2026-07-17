<template>
  <div class="schema-panel">
    <div class="panel-title">{{ title }}</div>
    <el-input
      v-model="keyword"
      size="small"
      clearable
      placeholder="搜索字段"
      class="search"
    />
    <el-tree
      :data="treeData"
      :props="treeProps"
      node-key="path"
      default-expand-all
      highlight-current
      :filter-node-method="filterNode"
      ref="treeRef"
      @node-click="onNodeClick"
    >
      <template #default="{ data }">
        <div class="tree-node">
          <span class="code">{{ data.code }}</span>
          <span v-if="data.name" class="name">{{ data.name }}</span>
          <el-tag v-if="data.required" size="small" type="danger" effect="plain">必填</el-tag>
        </div>
      </template>
    </el-tree>

    <div v-if="selected" class="detail">
      <div class="detail-title">字段详情</div>
      <div class="row"><label>字段编码</label><span>{{ selected.code }}</span></div>
      <div class="row"><label>字段路径</label><span>{{ selected.path }}</span></div>
      <div class="row"><label>字段名称</label><span>{{ selected.name || '-' }}</span></div>
      <div class="row"><label>字段类型</label><span>{{ selected.dataType || '-' }}</span></div>
      <div class="row"><label>是否必填</label><span>{{ selected.required ? '是' : '否' }}</span></div>
      <div v-if="selected.lookUpObject" class="row">
        <label>引用对象</label><span>{{ selected.lookUpObject }}</span>
      </div>
      <div v-if="selected.description" class="row">
        <label>说明</label><span>{{ selected.description }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ElTree } from 'element-plus'
import type { SchemaField, SchemaTree } from '../../types'

const props = defineProps<{
  title: string
  schema: SchemaTree | null
}>()

const keyword = ref('')
const selected = ref<SchemaField | null>(null)
const treeRef = ref<InstanceType<typeof ElTree>>()

const treeProps = { label: 'code', children: 'children' }

interface TreeNode extends SchemaField {
  children?: TreeNode[]
}

const treeData = computed<TreeNode[]>(() => {
  if (!props.schema) return []
  return [
    {
      path: props.schema.rootId,
      code: props.schema.rootName,
      name: props.schema.rootId,
      required: false,
      children: props.schema.fields
    }
  ]
})

watch(keyword, (val) => {
  treeRef.value?.filter(val)
})

function filterNode(value: string, data: SchemaField) {
  if (!value) return true
  const q = value.toLowerCase()
  return (
    data.code?.toLowerCase().includes(q) ||
    data.name?.toLowerCase().includes(q) ||
    data.path?.toLowerCase().includes(q)
  )
}

function onNodeClick(data: SchemaField) {
  selected.value = data
}
</script>

<style scoped>
.schema-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
  overflow: hidden;
}

.panel-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.search {
  margin-bottom: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.tree-node .code {
  font-family: Consolas, monospace;
}

.tree-node .name {
  color: var(--muted);
}

.detail {
  margin-top: 12px;
  border-top: 1px solid var(--border);
  padding-top: 10px;
  font-size: 13px;
}

.detail-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.row {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px;
  margin-bottom: 6px;
}

.row label {
  color: var(--muted);
}

:deep(.el-tree) {
  flex: 1;
  overflow: auto;
}
</style>
