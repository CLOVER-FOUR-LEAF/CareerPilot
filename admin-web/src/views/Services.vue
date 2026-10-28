<template>
  <a-card :bordered="false" title="服务健康">
    <template #extra>
      <a-space>
        <span class="summary">
          正常 <b :class="allUp ? 'ok' : 'warn'">{{ upCount }}</b> / {{ rows.length }}
        </span>
        <a-button @click="load" :loading="loading">刷新</a-button>
      </a-space>
    </template>

    <a-alert
      type="info"
      show-icon
      message="当前用配置里的地址直接探测 /actuator/health 与 /health"
      description="等服务发现（Nacos）接入后，这里改成从注册表拉实例，页面与接口不用变。"
      style="margin-bottom: 16px"
    />

    <a-table
      :loading="loading"
      :data-source="rows"
      :columns="columns"
      row-key="name"
      size="middle"
      :pagination="false"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'up'">
          <a-badge :status="record.up ? 'success' : 'error'" :text="record.up ? '正常' : '异常'" />
        </template>
        <template v-else-if="column.key === 'group'">
          <a-tag :color="record.group === 'java' ? 'blue' : 'purple'">
            {{ record.group === 'java' ? 'Java 服务' : 'Python Agent' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'latencyMs'">
          <span :class="record.latencyMs > 1000 ? 'slow' : ''">
            {{ record.latencyMs < 0 ? '-' : `${record.latencyMs} ms` }}
          </span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag v-if="record.status > 0" :color="record.up ? 'green' : 'red'">{{ record.status }}</a-tag>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'error'">
          <a-typography-text v-if="record.error" type="danger">{{ record.error }}</a-typography-text>
          <span v-else>-</span>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/api'
import type { ServiceHealth } from '@/types'

const loading = ref(false)
const rows = ref<ServiceHealth[]>([])

const columns = [
  { title: '服务', dataIndex: 'name', key: 'name', width: 220 },
  { title: '分组', key: 'group', width: 150 },
  { title: '状态', key: 'up', width: 110 },
  { title: 'HTTP', key: 'status', width: 90 },
  { title: '延迟', key: 'latencyMs', width: 110 },
  { title: '地址', dataIndex: 'url', key: 'url', ellipsis: true },
  { title: '错误', key: 'error', width: 160 },
]

const upCount = computed(() => rows.value.filter((r) => r.up).length)
const allUp = computed(() => rows.value.length > 0 && upCount.value === rows.value.length)

async function load() {
  loading.value = true
  try {
    rows.value = await adminApi.services()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.summary {
  color: #595959;
}
.ok { color: #52c41a; }
.warn { color: #faad14; }
.slow { color: #faad14; }
</style>
