<template>
  <div class="cover-letter-page">
    <a-page-header title="生成求职信" sub-title="结合岗位 JD 自动撰写个性化求职信，突出核心技能" />

    <a-row :gutter="16">
      <a-col :xs="24" :lg="9">
        <a-card title="求职信设置" class="panel">
          <a-form :model="form" layout="vertical">
            <a-form-item label="职位 JD">
              <a-select
                v-model:value="form.jdId"
                :options="jdOptions"
                placeholder="选择一个 JD（可选）"
                allow-clear
                show-search
                option-filter-prop="label"
                :not-found-content="noJdContent"
              />
            </a-form-item>
            <a-form-item label="应聘岗位" required>
              <a-input v-model:value="form.jobTitle" placeholder="如：高级后端工程师" />
            </a-form-item>
            <a-form-item label="目标公司">
              <a-input v-model:value="form.companyName" placeholder="如：某厂" />
            </a-form-item>
            <a-form-item label="写作语气">
              <a-select v-model:value="form.tone" :options="toneOptions" />
            </a-form-item>
            <a-button type="primary" block :loading="polling.running.value" @click="handleSubmit">
              <ThunderboltOutlined /> 生成求职信
            </a-button>
          </a-form>

          <div v-if="polling.running.value" class="task-status">
            <a-progress :percent="polling.progress.value" size="small" />
            <p class="hint">状态：{{ polling.status.value }}</p>
          </div>
          <a-alert
            v-else-if="polling.errorMessage.value"
            type="error"
            show-icon
            :message="polling.errorMessage.value"
          />
        </a-card>
      </a-col>

      <a-col :xs="24" :lg="15">
        <a-card title="求职信正文" class="panel">
          <template v-if="result">
            <div class="letter-meta">
              <a-tag color="blue">{{ toneLabel(result.tone) }}</a-tag>
              <a-tag color="green">{{ result.word_count ?? 0 }} 字</a-tag>
              <a-tag color="default">{{ generatedAt }}</a-tag>
            </div>

            <div class="letter-body">{{ result.content || '—' }}</div>

            <a-divider style="margin: 16px 0" />

            <h4>突出技能</h4>
            <div class="tag-row">
              <a-tag v-for="skill in result.highlighted_skills || []" :key="skill" color="blue">{{ skill }}</a-tag>
              <span v-if="!result.highlighted_skills?.length" class="empty-text">暂无</span>
            </div>

            <a-button class="copy-btn" @click="copyContent">
              <CopyOutlined /> 复制全文
            </a-button>
          </template>
          <a-empty v-else description="填写岗位信息后点击「生成求职信」" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { CopyOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { jdApi } from '@/api'
import { useTaskPolling } from '@/composables/useTaskPolling'
import type { CoverLetterResult, CoverLetterTone, JdListItem } from '@/types'

const form = reactive({
  jdId: null as number | null,
  jobTitle: '',
  companyName: '',
  tone: 'professional' as CoverLetterTone,
})

const toneOptions = [
  { label: '专业稳重', value: 'professional' },
  { label: '热情积极', value: 'passionate' },
  { label: '谦逊真诚', value: 'humble' },
]

const jds = ref<JdListItem[]>([])
const noJdContent = '没有 JD，请先到 JD 解析页新建'
const generatedAt = ref('')

const polling = useTaskPolling<CoverLetterResult>()
const result = computed<CoverLetterResult | null>(() => polling.output.value)

const jdOptions = computed(() =>
  jds.value.map((item) => ({
    label: `${item.title}${item.company ? ` · ${item.company}` : ''}`,
    value: item.jdId,
  }))
)

function toneLabel(tone: string) {
  return toneOptions.find((item) => item.value === tone)?.label || tone
}

async function fetchJdList() {
  try {
    const res = await jdApi.list({ page: 1, size: 50 })
    jds.value = res.data?.items ?? []
  } catch {
    jds.value = []
  }
}

async function handleSubmit() {
  const jobTitle = form.jobTitle.trim()
  if (!jobTitle) {
    message.warning('请填写应聘岗位')
    return
  }

  let jdId: string | null = null
  let jdText = ''
  let jdTitle = ''
  if (form.jdId) {
    const selected = jds.value.find((item) => item.jdId === form.jdId)
    jdId = selected?.jdBizId ?? null
    jdTitle = selected?.title ?? ''
    try {
      const detail = await jdApi.getById(form.jdId)
      jdText = detail.data?.rawText || ''
    } catch {
      jdText = selected?.rawTextPreview || ''
    }
  }

  const task = await polling.submit({
    taskType: 'cover_letter',
    bizId: jdId,
    input: {
      job_title: jobTitle || jdTitle,
      // cover-letter-agent 的入参字段是 company_name（§7.6）
      company_name: form.companyName || '',
      tone: form.tone,
      jd_id: jdId,
      jd_text: jdText,
    },
  })

  if (task?.status === 'SUCCESS' && polling.output.value) {
    generatedAt.value = `生成于 ${dayjs().format('YYYY-MM-DD HH:mm')}`
    message.success('求职信已生成')
  } else if (task && task.status !== 'SUCCESS') {
    message.error(polling.errorMessage.value || '生成失败')
  }
}

async function copyContent() {
  const text = result.value?.content || ''
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
.cover-letter-page {
  max-width: 1200px;
  margin: 0 auto;
}
.panel {
  height: 100%;
}
.letter-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.letter-body {
  padding: 20px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  line-height: 2;
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 260px;
}
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.empty-text {
  color: #999;
  font-size: 12px;
}
.copy-btn {
  margin-top: 16px;
}
.task-status .hint {
  margin: 6px 0 0;
  color: #999;
  font-size: 12px;
}
</style>
