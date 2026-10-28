<template>
  <a-card :bordered="false" title="操作审计">
    <template #extra>
      <a-button @click="load" :loading="loading">刷新</a-button>
    </template>

    <a-table
      :loading="loading"
      :data-source="rows"
      :columns="columns"
      row-key="auditId"
      size="middle"
      :pagination="pagination"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-tag color="blue">{{ record.action }}</a-tag>
        </template>
        <template v-else-if="column.key === 'target'">
          {{ record.targetType }} / {{ record.targetId }}
        </template>
        <template v-else-if="column.key === 'detail'">
          <a-typography-text v-if="record.detail" :content="record.detail" :ellipsis="{ tooltip: record.detail }" />
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'createdAt'">
          {{ formatTime(record.createdAt) }}
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { adminApi } from '@/api'
import type { AuditLog } from '@/types'

const loading = ref(false)
const rows = ref<AuditLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const columns = [
  { title: '审计 ID', dataIndex: 'auditId', key: 'auditId', width: 200 },
  { title: '操作人', dataIndex: 'actorId', key: 'actorId', width: 170 },
  { title: '动作', key: 'action', width: 170 },
  { title: '对象', key: 'target', width: 180 },
  { title: '详情', key: 'detail' },
  { title: '时间', key: 'createdAt', width: 170 },
]

const pagination = computed(() => ({
  current: page.value,
  pageSize: size.value,
  total: total.value,
  showTotal: (t: number) => `共 ${t} 条`,
}))

function formatTime(value: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

async function load() {
  loading.value = true
  try {
    const res = await adminApi.audits({ page: page.value, size: size.value })
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleTableChange(p: { current?: number; pageSize?: number }) {
  page.value = p.current || 1
  size.value = p.pageSize || 20
  load()
}

onMounted(load)
</script>
