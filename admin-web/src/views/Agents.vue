<template>
  <div class="agents-page">
    <a-alert
      type="warning"
      show-icon
      message="这里配置的是 Agent 项目的「能力参数」，不保存任何密钥"
      description="LLM / 向量 / TTS / ASR 的 API Key 属于部署密钥，走服务器环境变量。配置保存到平台配置表，作为编排与下发的唯一事实来源；当前 Agent 进程仍从部署环境变量读取，保存后需按部署流程下发或重启生效。"
      style="margin-bottom: 16px"
    />

    <!-- ── 模型能力 ─────────────────────────────────────── -->
    <a-card :bordered="false" title="模型能力" class="section-card">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :xl="12">
          <a-card size="small" title="大语言模型（LLM）" class="cap-card">
            <template #extra>
              <a-switch v-model:checked="llm.enabled" checked-children="启用" un-checked-children="规则引擎" />
            </template>
            <a-form layout="vertical" size="small">
              <a-row :gutter="12">
                <a-col :span="12">
                  <a-form-item label="Provider">
                    <a-select v-model:value="llm.provider" :options="providerOptions.llm" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="模型名称">
                    <a-input v-model:value="llm.model" placeholder="gpt-4o" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-form-item label="API Base URL">
                <a-input v-model:value="llm.baseUrl" placeholder="https://api.openai.com/v1" />
              </a-form-item>
              <a-row :gutter="12">
                <a-col :span="12">
                  <a-form-item label="温度">
                    <a-input-number v-model:value="llm.temperature" :min="0" :max="2" :step="0.1" style="width: 100%" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="最大输出 tokens">
                    <a-input-number v-model:value="llm.maxTokens" :min="256" :max="32768" :step="256" style="width: 100%" />
                  </a-form-item>
                </a-col>
              </a-row>
            </a-form>
            <a-button type="primary" block :loading="savingKey === 'llm.provider'" @click="save('llm.provider', llm)">
              保存 LLM 配置
            </a-button>
          </a-card>
        </a-col>

        <a-col :xs="24" :xl="12">
          <a-card size="small" title="向量模型（Embedding）" class="cap-card">
            <template #extra>
              <a-switch v-model:checked="embedding.enabled" checked-children="启用" un-checked-children="关闭" />
            </template>
            <a-form layout="vertical" size="small">
              <a-row :gutter="12">
                <a-col :span="12">
                  <a-form-item label="Provider">
                    <a-select v-model:value="embedding.provider" :options="providerOptions.embedding" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="向量维度">
                    <a-input-number v-model:value="embedding.dimension" :min="64" :max="8192" :step="128" style="width: 100%" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-form-item label="模型名称">
                <a-input v-model:value="embedding.model" placeholder="text-embedding-3-small" />
              </a-form-item>
              <a-form-item label="API Base URL">
                <a-input v-model:value="embedding.baseUrl" placeholder="留空表示使用本地向量服务" />
              </a-form-item>
              <a-form-item label="说明">
                <a-input v-model:value="embedding.note" />
              </a-form-item>
            </a-form>
            <a-button type="primary" block :loading="savingKey === 'embedding.provider'" @click="save('embedding.provider', embedding)">
              保存向量配置
            </a-button>
          </a-card>
        </a-col>

        <a-col :xs="24" :xl="12">
          <a-card size="small" title="语音合成（TTS）" class="cap-card">
            <template #extra>
              <a-switch v-model:checked="tts.enabled" checked-children="启用" un-checked-children="浏览器合成" />
            </template>
            <a-form layout="vertical" size="small">
              <a-row :gutter="12">
                <a-col :span="12">
                  <a-form-item label="Provider">
                    <a-select v-model:value="tts.provider" :options="providerOptions.tts" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="音色">
                    <a-input v-model:value="tts.voice" placeholder="alloy" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-form-item label="模型名称">
                <a-input v-model:value="tts.model" placeholder="tts-1" />
              </a-form-item>
              <a-row :gutter="12">
                <a-col :span="12">
                  <a-form-item label="语速">
                    <a-input-number v-model:value="tts.speed" :min="0.5" :max="2" :step="0.1" style="width: 100%" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="API Base URL">
                    <a-input v-model:value="tts.baseUrl" placeholder="留空使用默认" />
                  </a-form-item>
                </a-col>
              </a-row>
            </a-form>
            <a-button type="primary" block :loading="savingKey === 'tts.provider'" @click="save('tts.provider', tts)">
              保存 TTS 配置
            </a-button>
          </a-card>
        </a-col>

        <a-col :xs="24" :xl="12">
          <a-card size="small" title="语音识别（ASR）" class="cap-card">
            <template #extra>
              <a-switch v-model:checked="asr.enabled" checked-children="启用" un-checked-children="仅文字" />
            </template>
            <a-form layout="vertical" size="small">
              <a-row :gutter="12">
                <a-col :span="12">
                  <a-form-item label="Provider">
                    <a-select v-model:value="asr.provider" :options="providerOptions.asr" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="识别语言">
                    <a-input v-model:value="asr.language" placeholder="zh" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-form-item label="模型名称">
                <a-input v-model:value="asr.model" placeholder="whisper-1" />
              </a-form-item>
              <a-form-item label="API Base URL">
                <a-input v-model:value="asr.baseUrl" placeholder="留空使用默认" />
              </a-form-item>
              <a-form-item label="说明">
                <a-input v-model:value="asr.note" />
              </a-form-item>
            </a-form>
            <a-button type="primary" block :loading="savingKey === 'asr.provider'" @click="save('asr.provider', asr)">
              保存 ASR 配置
            </a-button>
          </a-card>
        </a-col>
      </a-row>
    </a-card>

    <!-- ── Agent 编排 ───────────────────────────────────── -->
    <a-card :bordered="false" title="Agent 编排" class="section-card">
      <template #extra>
        <a-space>
          <a-button size="small" @click="addAgent">新增 Agent</a-button>
          <a-button type="primary" size="small" :loading="savingKey === 'agent.registry'" @click="save('agent.registry', registry)">
            保存编排配置
          </a-button>
        </a-space>
      </template>

      <a-card size="small" title="Agent Orchestrator" class="orch-card">
        <template #extra>
          <a-space>
            <a-tag :color="orchHealth.up ? 'green' : 'red'">
              {{ orchHealth.up ? 'UP' : 'DOWN' }}
              <template v-if="orchHealth.latencyMs >= 0"> · {{ orchHealth.latencyMs }}ms</template>
            </a-tag>
            <a-switch v-model:checked="registry.orchestrator.enabled" />
          </a-space>
        </template>
        <a-form layout="vertical" size="small">
          <a-row :gutter="12">
            <a-col :xs="24" :md="6">
              <a-form-item label="端口">
                <a-input-number v-model:value="registry.orchestrator.port" :min="1" :max="65535" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="12">
              <a-form-item label="调用地址">
                <a-input v-model:value="registry.orchestrator.endpoint" placeholder="http://localhost:8001" />
              </a-form-item>
            </a-col>
            <a-col :xs="24" :md="6">
              <a-form-item label="超时（秒）">
                <a-input-number v-model:value="registry.orchestrator.timeoutSeconds" :min="10" :max="600" style="width: 100%" />
              </a-form-item>
            </a-col>
          </a-row>
        </a-form>
      </a-card>

      <a-row :gutter="[16, 16]">
        <a-col v-for="agent in registry.agents" :key="agent.key" :xs="24" :md="12" :xl="8">
          <a-card size="small" class="agent-card">
            <template #title>
              <span class="agent-title">{{ agent.name || '未命名 Agent' }}</span>
            </template>
            <template #extra>
              <a-space>
                <a-tag :color="healthOf(agent).up ? 'green' : 'red'">
                  {{ healthOf(agent).up ? 'UP' : 'DOWN' }}
                  <template v-if="healthOf(agent).latencyMs >= 0"> · {{ healthOf(agent).latencyMs }}ms</template>
                </a-tag>
                <a-switch v-model:checked="agent.enabled" size="small" />
                <a-popconfirm title="从编排列表移除这个 Agent？" @confirm="removeAgent(agent.key)">
                  <a-button type="text" danger size="small">删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>

            <a-form layout="vertical" size="small">
              <a-row :gutter="10">
                <a-col :span="12">
                  <a-form-item label="名称">
                    <a-input v-model:value="agent.name" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="健康探测名">
                    <a-input v-model:value="agent.probeName" placeholder="如 jd-parser-agent" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-row :gutter="10">
                <a-col :span="8">
                  <a-form-item label="端口">
                    <a-input-number v-model:value="agent.port" :min="1" :max="65535" style="width: 100%" />
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="模型标识">
                    <a-input v-model:value="agent.model" />
                  </a-form-item>
                </a-col>
                <a-col :span="8">
                  <a-form-item label="超时（秒）">
                    <a-input-number v-model:value="agent.timeoutSeconds" :min="5" :max="600" style="width: 100%" />
                  </a-form-item>
                </a-col>
              </a-row>
              <a-form-item label="职责说明">
                <a-input v-model:value="agent.description" />
              </a-form-item>
            </a-form>
          </a-card>
        </a-col>
      </a-row>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { adminApi } from '@/api'
import type { AgentEntry, AgentRegistry, ConfigItem, ServiceHealth } from '@/types'

interface LlmConfig {
  enabled: boolean
  provider: string
  baseUrl: string
  model: string
  temperature: number
  maxTokens: number
}

interface EmbeddingConfig {
  enabled: boolean
  provider: string
  baseUrl: string
  model: string
  dimension: number
  note: string
}

interface TtsConfig {
  enabled: boolean
  provider: string
  baseUrl: string
  model: string
  voice: string
  speed: number
  note: string
}

interface AsrConfig {
  enabled: boolean
  provider: string
  baseUrl: string
  model: string
  language: string
  note: string
}

const providerOptions = {
  llm: [
    { label: 'OpenAI 兼容', value: 'openai-compatible' },
    { label: 'Anthropic', value: 'anthropic' },
    { label: 'DeepSeek', value: 'deepseek' },
    { label: '自定义', value: 'custom' },
  ],
  embedding: [
    { label: '本地向量服务', value: 'local' },
    { label: 'OpenAI', value: 'openai' },
    { label: 'RAGFlow', value: 'ragflow' },
    { label: '自定义', value: 'custom' },
  ],
  tts: [
    { label: 'OpenAI', value: 'openai' },
    { label: 'Azure', value: 'azure' },
    { label: '火山引擎', value: 'volcengine' },
    { label: '自定义', value: 'custom' },
  ],
  asr: [
    { label: 'OpenAI', value: 'openai' },
    { label: 'Azure', value: 'azure' },
    { label: '火山引擎', value: 'volcengine' },
    { label: '自定义', value: 'custom' },
  ],
}

const llm = reactive<LlmConfig>({
  enabled: false,
  provider: 'openai-compatible',
  baseUrl: 'https://api.openai.com/v1',
  model: 'gpt-4o',
  temperature: 0.3,
  maxTokens: 2048,
})

const embedding = reactive<EmbeddingConfig>({
  enabled: false,
  provider: 'local',
  baseUrl: '',
  model: 'text-embedding-3-small',
  dimension: 1536,
  note: '用于 JD / 简历语义匹配；关闭时使用关键词与规则打分',
})

const tts = reactive<TtsConfig>({
  enabled: false,
  provider: 'openai',
  baseUrl: '',
  model: 'tts-1',
  voice: 'alloy',
  speed: 1.0,
  note: '模拟面试语音播报；关闭时使用浏览器语音合成',
})

const asr = reactive<AsrConfig>({
  enabled: false,
  provider: 'openai',
  baseUrl: '',
  model: 'whisper-1',
  language: 'zh',
  note: '模拟面试语音回答转写；关闭时只接受文字回答',
})

const DEFAULT_AGENTS: AgentEntry[] = [
  { key: 'jd-parser', name: 'JD 解析 Agent', probeName: 'jd-parser-agent', port: 8002, enabled: true, model: 'rule-engine', timeoutSeconds: 60, description: '抽取岗位技能、经验与硬性条件' },
  { key: 'resume-analyzer', name: '简历分析 Agent', probeName: 'resume-analyzer-agent', port: 8003, enabled: true, model: 'rule-engine', timeoutSeconds: 60, description: 'ATS 多维评分与改进建议' },
  { key: 'match', name: '匹配打分 Agent', probeName: 'match-agent', port: 8004, enabled: true, model: 'rule-engine', timeoutSeconds: 60, description: '语义 / 关键词 / 业务三段式匹配' },
  { key: 'resume-optimizer', name: '简历优化 Agent', probeName: 'resume-optimizer-agent', port: 8005, enabled: true, model: 'rule-engine', timeoutSeconds: 90, description: '按 JD 重写经历，补足 STAR 结构' },
  { key: 'interview', name: '模拟面试 Agent', probeName: 'interview-agent', port: 8006, enabled: true, model: 'rule-engine', timeoutSeconds: 90, description: '多轮追问、评分与 STAR 覆盖判定' },
  { key: 'cover-letter', name: '求职信 Agent', probeName: 'cover-letter-agent', port: 8007, enabled: true, model: 'rule-engine', timeoutSeconds: 60, description: '按岗位与语气生成求职信' },
  { key: 'career-advisor', name: '职业规划 Agent', probeName: 'career-advisor-agent', port: 8008, enabled: true, model: 'rule-engine', timeoutSeconds: 90, description: '分阶段路线图与技能缺口' },
]

const registry = reactive<AgentRegistry>({
  orchestrator: {
    enabled: true,
    port: 8001,
    endpoint: 'http://localhost:8001',
    timeoutSeconds: 120,
  },
  agents: DEFAULT_AGENTS.map((a) => ({ ...a })),
})

const loading = ref(false)
const savingKey = ref<string | null>(null)
const health = ref<ServiceHealth[]>([])

const emptyHealth = (): ServiceHealth => ({
  name: '',
  group: 'agent',
  url: '',
  up: false,
  status: -1,
  latencyMs: -1,
})

function healthOf(agent: AgentEntry): ServiceHealth {
  if (!agent.probeName) return emptyHealth()
  return health.value.find((h) => h.name === agent.probeName) || emptyHealth()
}

const orchHealth = computed(() =>
  health.value.find((h) => h.name === 'agent-orchestrator') || emptyHealth(),
)

function applyConfig(configs: ConfigItem[]) {
  const byKey = new Map(configs.map((c) => [c.configKey, c.value]))

  const llmValue = byKey.get('llm.provider') as Partial<LlmConfig> | undefined
  if (llmValue) Object.assign(llm, normalizeObject(llmValue))

  const embeddingValue = byKey.get('embedding.provider') as Partial<EmbeddingConfig> | undefined
  if (embeddingValue) Object.assign(embedding, normalizeObject(embeddingValue))

  const ttsValue = byKey.get('tts.provider') as Partial<TtsConfig> | undefined
  if (ttsValue) Object.assign(tts, normalizeObject(ttsValue))

  const asrValue = byKey.get('asr.provider') as Partial<AsrConfig> | undefined
  if (asrValue) Object.assign(asr, normalizeObject(asrValue))

  const registryValue = byKey.get('agent.registry') as Partial<AgentRegistry> | undefined
  if (registryValue && Array.isArray(registryValue.agents)) {
    const orchestrator = { ...registry.orchestrator, ...(registryValue.orchestrator || {}) }
    registry.orchestrator = orchestrator
    registry.agents = registryValue.agents.map((a) => ({ ...a }))
  }
}

function normalizeObject<T extends object>(value: Partial<T>): Partial<T> {
  return { ...value }
}

async function load() {
  loading.value = true
  try {
    const [configs, services] = await Promise.all([
      adminApi.configs(),
      adminApi.services(),
    ])
    applyConfig(configs)
    health.value = services.filter((s) => s.group === 'agent')
  } finally {
    loading.value = false
  }
}

async function save(key: string, value: unknown) {
  savingKey.value = key
  try {
    // 去掉响应式代理，避免把 Vue 内部字段写进 JSON
    await adminApi.updateConfig(key, JSON.parse(JSON.stringify(value)))
    message.success('配置已保存')
    await load()
  } finally {
    savingKey.value = null
  }
}

function addAgent() {
  const port = 8100 + registry.agents.length
  registry.agents.push({
    key: `agent-${Date.now()}`,
    name: '新 Agent',
    probeName: '',
    port,
    enabled: false,
    model: 'rule-engine',
    timeoutSeconds: 60,
    description: '请填写这个 Agent 的职责',
  })
}

function removeAgent(key: string) {
  const index = registry.agents.findIndex((a) => a.key === key)
  if (index >= 0) registry.agents.splice(index, 1)
}

onMounted(load)
</script>

<style scoped>
.section-card {
  margin-bottom: 16px;
}
.cap-card {
  height: 100%;
}
.orch-card {
  margin-bottom: 16px;
}
.agent-card {
  height: 100%;
}
.agent-title {
  font-weight: 600;
}
</style>
