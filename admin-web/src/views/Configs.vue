<template>
  <div>
    <a-alert
      type="warning"
      show-icon
      message="模型密钥不在这里配置"
      description="LLM / 向量 / TTS / ASR 的结构化配置请前往「Agent 配置」页面；本页保留原始 JSON 视图，便于排查与兜底修改。API Key 属于部署密钥，走服务器环境变量或独立 secret 管理。"
      style="margin-bottom: 16px"
    >
      <template #action>
        <a-button size="small" @click="$router.push('/agents')">前往 Agent 配置</a-button>
      </template>
    </a-alert>

    <a-spin :spinning="loading">
      <a-card
        v-for="item in configs"
        :key="item.configKey"
        :bordered="false"
        class="config-card"
      >
        <template #title>
          <span class="config-key">{{ item.configKey }}</span>
          <a-tag v-if="isManaged(item.configKey)" color="purple" style="margin-left: 8px">
            建议在 Agent 配置页修改
          </a-tag>
        </template>
        <template #extra>
          <a-space>
            <a-button size="small" @click="format(item)">格式化</a-button>
            <a-button type="primary" :loading="savingKey === item.configKey" @click="save(item)">
              保存
            </a-button>
          </a-space>
        </template>

        <a-textarea
          v-model:value="text[item.configKey]"
          :rows="item.configKey === 'agent.registry' ? 16 : 7"
          class="json-editor"
        />

        <div class="meta">
          {{ item.description || '无说明' }}
          <span v-if="item.updatedBy"> · 最后修改：{{ item.updatedBy }}</span>
          <span v-if="item.updatedAt"> · {{ formatTime(item.updatedAt) }}</span>
        </div>
      </a-card>

      <a-empty v-if="!loading && configs.length === 0" description="暂无配置项" />
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { adminApi } from '@/api'
import type { ConfigItem } from '@/types'

const loading = ref(false)
const savingKey = ref<string | null>(null)
const configs = ref<ConfigItem[]>([])
const text = reactive<Record<string, string>>({})

/** 这些键有结构化编辑页，原始 JSON 视图只做兜底 */
const MANAGED_KEYS = new Set([
  'llm.provider',
  'embedding.provider',
  'tts.provider',
  'asr.provider',
  'agent.registry',
])

function isManaged(key: string) {
  return MANAGED_KEYS.has(key)
}

function formatTime(value: string) {
  return dayjs(value).format('YYYY-MM-DD HH:mm')
}

async function load() {
  loading.value = true
  try {
    configs.value = await adminApi.configs()
    for (const item of configs.value) {
      text[item.configKey] = JSON.stringify(item.value, null, 2)
    }
  } finally {
    loading.value = false
  }
}

function format(item: ConfigItem) {
  try {
    text[item.configKey] = JSON.stringify(JSON.parse(text[item.configKey]), null, 2)
  } catch {
    message.error('JSON 格式不正确')
  }
}

async function save(item: ConfigItem) {
  let value: unknown
  try {
    value = JSON.parse(text[item.configKey])
  } catch {
    message.error('JSON 格式不正确')
    return
  }

  savingKey.value = item.configKey
  try {
    await adminApi.updateConfig(item.configKey, value)
    message.success('配置已保存')
    await load()
  } finally {
    savingKey.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.config-card {
  margin-bottom: 16px;
}
.config-key {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 14px;
}
.json-editor {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
}
.meta {
  margin-top: 10px;
  color: #999;
  font-size: 12px;
}
</style>
