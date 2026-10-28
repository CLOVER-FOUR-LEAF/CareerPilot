<template>
  <a-card :bordered="false" title="AI 任务">
    <template #extra>
      <a-space>
        <a-select
          v-model:value="statusFilter"
          placeholder="全部状态"
          allow-clear
          style="width: 150px"
          :options="statusOptions"
          @change="reload"
        />
        <a-select
          v-model:value="typeFilter"
          placeholder="全部类型"
          allow-clear
          style="width: 190px"
          :options="typeOptions"
          @change="reload"
        />
        <a-button @click="reload">刷新</a-button>
      </a-space>
    </template>

    <a-table
      :loading="loading"
      :data-source="rows"
      :columns="columns"
      row-key="taskId"
      size="middle"
      :pagination="pagination"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
        </template>
        <template v-else-if="column.key === 'taskId'">
          <a-typography-text copyable :content="record.taskId">{{ record.taskId }}</a-typography-text>
        </template>
        <template v-else-if="column.key === 'createdAt'">
          {{ formatTime(record.createdAt) }}
        </template>
        <template v-else-if="column.key === 'durationMs'">
          {{ record.durationMs == null ? '-' : `${record.durationMs} ms` }}
        </template>
        <template v-else-if="column.key === 'errorMessage'">
          <a-typography-text v-if="record.errorMessage" type="danger" :content="record.errorMessage" :ellipsis="{ tooltip: record.errorMessage }" />
          <span v-else>-</span>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { adminApi } from '@/api'
import type { AiTask } from '@/types'

const loading = ref(false)
const rows = ref<AiTask[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const statusFilter = ref<string | undefined>(undefined)
const typeFilter = ref<string | undefined>(undefined)

const statusOptions = ['PENDING', 'RUNNING', 'SUCCESS', 'FAILED'].map((s) => ({ label: s, value: s }))
const typeOptions = [
  'jd_parse', 'resume_analyze', 'match_score', 'resume_optimize',
  'interview', 'cover_letter', 'career_plan', 'hr_insight', 'jd_calibrate', 'hr_verify',
].map((t) => ({ label: t, value: t }))

const columns = [
  { title: '任务 ID', key: 'taskId', width: 210 },
  { title: '类型', dataIndex: 'taskType', key: 'taskType', width: 150 },
  { title: '状态', key: 'status', width: 110 },
  { title: '进度', dataIndex: 'progress', key: 'progress', width: 90 },
  { title: 'Agent', dataIndex: 'currentAgent', key: 'currentAgent', width: 180 },
  { title: '耗时', key: 'durationMs', width: 100 },
  { title: '错误', key: 'errorMessage' },
  { title: '创建时间', key: 'createdAt', width: 170 },
]

const pagination = computed(() => ({
  current: page.value,
  pageSize: size.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`,
}))

function formatTime(value: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

function statusColor(status: string) {
  switch (status) {
    case 'SUCCESS': return 'green'
    case 'FAILED': return 'red'
    case 'RUNNING': return 'blue'
    default: return 'default'
  }
}

async function load() {
  loading.value = true
  try {
    const res = await adminApi.tasks({
      page: page.value,
      size: size.value,
      status: statusFilter.value || undefined,
      taskType: typeFilter.value || undefined,
    })
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

function handleTableChange(p: { current?: number; pageSize?: number }) {
  page.value = p.current || 1
  size.value = p.pageSize || 20
  load()
}

onMounted(load)
</script>
