<template>
  <div class="optimize-page">
    <a-page-header title="简历优化" sub-title="用 STAR 法则重写简历条目，补齐量化结果与关键词" />

    <a-row :gutter="16">
      <a-col :xs="24" :lg="10">
        <a-card title="待优化的经历描述" class="panel">
          <a-form :model="form" layout="vertical">
            <a-form-item label="目标 JD（可选，用于对齐关键词）">
              <a-select
                v-model:value="form.jdId"
                :options="jdOptions"
                placeholder="选择一个 JD"
                allow-clear
                show-search
                option-filter-prop="label"
                :not-found-content="noJdContent"
              />
            </a-form-item>
            <a-form-item label="经历描述" required>
              <a-textarea
                v-model:value="form.original"
                placeholder="如：负责用户增长相关工作"
                :rows="7"
              />
            </a-form-item>
            <a-form-item label="改写风格">
              <a-radio-group v-model:value="form.tone">
                <a-radio value="professional">专业严谨</a-radio>
                <a-radio value="data-driven">量化导向</a-radio>
                <a-radio value="concise">简洁精炼</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-button type="primary" block :loading="polling.running.value" @click="handleSubmit">
              <ThunderboltOutlined /> 开始优化
            </a-button>
          </a-form>

          <a-divider style="margin: 16px 0" />

          <div v-if="polling.running.value" class="task-status">
            <a-progress :percent="polling.progress.value" size="small" />
            <p class="hint">
              状态：{{ polling.status.value }}
              <span v-if="polling.currentAgent.value"> · {{ polling.currentAgent.value }}</span>
            </p>
          </div>
          <a-alert
            v-else-if="polling.errorMessage.value"
            type="error"
            show-icon
            :message="polling.errorMessage.value"
          />
        </a-card>
      </a-col>

      <a-col :xs="24" :lg="14">
        <a-card title="优化结果" class="panel">
          <template v-if="result">
            <a-row :gutter="16">
              <a-col :span="12">
                <div class="compare-head">原文</div>
                <div class="compare-box original">{{ result.original || '—' }}</div>
              </a-col>
              <a-col :span="12">
                <div class="compare-head">
                  改写后
                  <a-tag color="green" class="copy-tag" @click="copyRewrite">
                    <CopyOutlined /> 复制
                  </a-tag>
                </div>
                <div class="compare-box rewritten">{{ result.rewritten || '—' }}</div>
              </a-col>
            </a-row>

            <template v-if="result.injected_skills?.length">
              <a-divider style="margin: 16px 0" />
              <h4>植入关键词</h4>
              <div class="tag-row">
                <a-tag v-for="skill in result.injected_skills" :key="skill" color="blue">{{ skill }}</a-tag>
              </div>
            </template>

            <template v-if="result.change_note">
              <a-divider style="margin: 16px 0" />
              <h4>改写说明</h4>
              <p class="change-note">{{ result.change_note }}</p>
            </template>

            <template v-if="result.star_breakdown">
              <a-divider style="margin: 16px 0" />
              <h4>STAR 拆解</h4>
              <a-list :data-source="starItems" size="small" bordered>
                <template #renderItem="{ item }">
                  <a-list-item>
                    <a-list-item-meta>
                      <template #title>
                        <a-tag :color="item.color">{{ item.label }}</a-tag>
                        {{ item.text || '—' }}
                      </template>
                    </a-list-item-meta>
                  </a-list-item>
                </template>
              </a-list>
            </template>
          </template>
          <a-empty v-else description="填写左侧内容并点击「开始优化」" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { CopyOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { jdApi } from '@/api'
import { useTaskPolling } from '@/composables/useTaskPolling'
import type { JdListItem, ResumeOptimizeResult } from '@/types'

type Tone = 'professional' | 'data-driven' | 'concise'

const form = reactive({
  jdId: null as number | null,
  original: '',
  tone: 'professional' as Tone,
})

const jds = ref<JdListItem[]>([])
const noJdContent = '没有 JD，请先到 JD 解析页新建'

// 该页使用 SSE 实时进度（§5.4），不可用时自动降级为轮询
const polling = useTaskPolling<ResumeOptimizeResult>({ useStream: true })

const result = computed<ResumeOptimizeResult | null>(() => polling.output.value)

const jdOptions = computed(() =>
  jds.value.map((item) => ({
    label: `${item.title}${item.company ? ` · ${item.company}` : ''}`,
    value: item.jdId,
  }))
)

const starItems = computed(() => {
  const breakdown = result.value?.star_breakdown
  if (!breakdown) return []
  return [
    { key: 'situation', label: 'S 情境', color: 'blue', text: breakdown.situation },
    { key: 'task', label: 'T 任务', color: 'cyan', text: breakdown.task },
    { key: 'action', label: 'A 行动', color: 'orange', text: breakdown.action },
    { key: 'result', label: 'R 结果', color: 'green', text: breakdown.result },
  ]
})

async function fetchJdList() {
  try {
    const res = await jdApi.list({ page: 1, size: 50 })
    jds.value = res.data?.items ?? []
  } catch {
    jds.value = []
  }
}

async function handleSubmit() {
  const original = form.original.trim()
  if (!original) {
    message.warning('请先填写需要优化的经历描述')
    return
  }

  let jdId: string | null = null
  let jdText = ''
  if (form.jdId) {
    const selected = jds.value.find((item) => item.jdId === form.jdId)
    jdId = selected?.jdBizId ?? null
    try {
      const detail = await jdApi.getById(form.jdId)
      jdText = detail.data?.rawText || ''
    } catch {
      jdText = selected?.rawTextPreview || ''
    }
  }

  const task = await polling.submit({
    taskType: 'resume_optimize',
    bizId: jdId,
    input: {
      // resume-optimizer-agent 的入参字段就叫 bullet（§7.4），不是 original
      bullet: original,
      tone: form.tone,
      jd_id: jdId,
      jd_text: jdText,
    },
  })

  if (task?.status === 'SUCCESS' && polling.output.value) {
    message.success('优化完成')
  } else if (task && task.status !== 'SUCCESS') {
    message.error(polling.errorMessage.value || '优化失败')
  }
}

async function copyRewrite() {
  const text = result.value?.rewritten || ''
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  } catch {
    message.warning('浏览器不支持自动复制，请手动选择文本')
  }
}

onMounted(fetchJdList)
</script>

<style scoped>
.optimize-page {
  max-width: 1200px;
  margin: 0 auto;
}
.panel {
  height: 100%;
}
.compare-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #666;
  font-weight: 600;
}
.copy-tag {
  cursor: pointer;
  font-weight: 400;
}
.compare-box {
  min-height: 150px;
  padding: 12px;
  border-radius: 6px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
.original {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  color: #666;
}
.rewritten {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
}
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.change-note {
  color: #555;
  background: #fafafa;
  padding: 10px 12px;
  border-radius: 6px;
}
.task-status .hint {
  margin: 6px 0 0;
  color: #999;
  font-size: 12px;
}
</style>
