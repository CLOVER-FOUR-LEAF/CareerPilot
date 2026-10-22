<template>
  <div class="interview-page">
    <a-page-header title="模拟面试" sub-title="AI 出题 + STAR 结构化评分，逐轮逼近真实面试" />

    <a-row :gutter="16">
      <a-col :xs="24" :lg="10">
        <a-card title="面试设置" class="panel">
          <a-form :model="form" layout="vertical">
            <a-form-item label="简历（可选）">
              <a-select
                v-model:value="form.resumeId"
                :options="resumeOptions"
                placeholder="选择一份简历"
                allow-clear
                show-search
                option-filter-prop="label"
              />
            </a-form-item>
            <a-form-item label="目标 JD">
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
            <a-form-item label="面试轮数">
              <a-input-number v-model:value="form.totalRounds" :min="1" :max="10" style="width: 100%" />
            </a-form-item>
            <a-button type="primary" block :loading="asking" :disabled="sessionDone" @click="startQuestion">
              <PlayCircleOutlined /> {{ currentQuestion ? '下一题' : '开始面试' }}
            </a-button>
          </a-form>

          <a-divider style="margin: 16px 0" />

          <div class="round-info">
            <a-tag color="blue">当前第 {{ round }} / {{ form.totalRounds }} 题</a-tag>
            <a-tag v-if="sessionDone" color="default">面试已结束</a-tag>
          </div>

          <div v-if="asking" class="task-status">
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

      <a-col :xs="24" :lg="14">
        <a-card title="面试现场" class="panel">
          <template v-if="currentQuestion">
            <a-card size="small" class="question-card">
              <div class="question-meta">
                <a-tag :color="questionTypeColor(currentQuestion.question_type)">
                  {{ questionTypeLabel(currentQuestion.question_type) }}
                </a-tag>
                <span v-if="currentQuestion.focus">考察点：{{ currentQuestion.focus }}</span>
              </div>
              <p class="question-text">{{ currentQuestion.question }}</p>
            </a-card>

            <a-form layout="vertical" class="answer-form">
              <a-form-item label="你的回答">
                <a-textarea
                  v-model:value="answer"
                  placeholder="按 STAR 结构回答：情境(S) → 任务(T) → 行动(A) → 结果(R)"
                  :rows="8"
                />
              </a-form-item>
              <a-button type="primary" :loading="evaluating" :disabled="!answer.trim()" @click="submitAnswer">
                <SendOutlined /> 提交并评分
              </a-button>
            </a-form>
          </template>
          <a-empty v-else description="选择 JD 后点击「开始面试」" />
        </a-card>

        <a-card v-if="roundRecords.length" title="逐轮记录" class="panel history-panel">
          <a-timeline>
            <a-timeline-item
              v-for="item in roundRecords"
              :key="item.round"
              :color="item.evaluation.score >= 7 ? 'green' : item.evaluation.score >= 5 ? 'orange' : 'red'"
            >
              <div class="record-head">
                <strong>第 {{ item.round }} 题</strong>
                <a-tag :color="item.evaluation.score >= 7 ? 'green' : item.evaluation.score >= 5 ? 'orange' : 'red'">
                  {{ item.evaluation.score }} 分
                </a-tag>
              </div>
              <p class="record-question">问：{{ item.question }}</p>
              <p class="record-answer">答：{{ item.answer }}</p>
              <p class="record-feedback">{{ item.evaluation.feedback }}</p>
              <div class="star-row">
                <a-tag
                  v-for="part in starParts(item.evaluation.star_coverage)"
                  :key="part.key"
                  :color="part.covered ? 'green' : 'default'"
                >
                  {{ part.label }} {{ part.covered ? '✓' : '✗' }}
                </a-tag>
              </div>
              <p v-if="item.evaluation.followup" class="record-followup">
                追问：{{ item.evaluation.followup }}
              </p>
            </a-timeline-item>
          </a-timeline>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlayCircleOutlined, SendOutlined } from '@ant-design/icons-vue'
import { jdApi, resumeApi } from '@/api'
import { useTaskPolling } from '@/composables/useTaskPolling'
import type {
  InterviewEvaluation,
  InterviewQuestion,
  JdListItem,
  ResumeListItem,
  StarCoverage,
} from '@/types'

interface RoundRecord {
  round: number
  question: string
  answer: string
  evaluation: InterviewEvaluation
}

const form = reactive({
  resumeId: null as number | null,
  jdId: null as number | null,
  totalRounds: 3,
})

const resumes = ref<ResumeListItem[]>([])
const jds = ref<JdListItem[]>([])
const noJdContent = '没有 JD，请先到 JD 解析页新建'

// 出题与评价是同一个 taskType，输出结构按出题声明，取评价结果时做一次局部断言
const polling = useTaskPolling<InterviewQuestion>()
const round = ref(1)
const asking = ref(false)
const evaluating = ref(false)
const answer = ref('')
// 新任务会清空 output，所以当前题目单独存一份
const currentQuestion = ref<InterviewQuestion | null>(null)
const roundRecords = ref<RoundRecord[]>([])
// 上一轮的回答，用于让 Agent 生成针对它的追问
const previousAnswer = ref<string>('')

const sessionDone = computed(() => round.value > form.totalRounds)

const resumeOptions = computed(() =>
  resumes.value.map((item) => ({ label: item.title, value: item.resumeId }))
)

const jdOptions = computed(() =>
  jds.value.map((item) => ({
    label: `${item.title}${item.company ? ` · ${item.company}` : ''}`,
    value: item.jdId,
  }))
)

function questionTypeLabel(type: string) {
  switch (type) {
    case 'technical':
      return '技术题'
    case 'behavioral':
      return '行为面试题'
    case 'followup':
      return '追问'
    default:
      return type || '综合题'
  }
}

function questionTypeColor(type: string) {
  return type === 'technical' ? 'geekblue' : type === 'behavioral' ? 'purple' : 'orange'
}

function starParts(coverage: StarCoverage | undefined) {
  const list = [
    { key: 'situation', label: 'S 情境' },
    { key: 'task', label: 'T 任务' },
    { key: 'action', label: 'A 行动' },
    { key: 'result', label: 'R 结果' },
  ]
  return list.map((part) => ({
    ...part,
    covered: !!coverage?.[part.key as keyof StarCoverage],
  }))
}

async function fetchOptions() {
  try {
    const [resumeRes, jdRes] = await Promise.all([
      resumeApi.list({ page: 1, size: 50 }),
      jdApi.list({ page: 1, size: 50 }),
    ])
    resumes.value = resumeRes.data?.items ?? []
    jds.value = jdRes.data?.items ?? []
  } catch {
    resumes.value = []
    jds.value = []
  }
}

/**
 * 取 JD 解析出的技能名。
 *
 * interview-agent 靠 jd_skills 决定出题主题、靠它做 evaluate 的技能命中检查，
 * 前端不传，Agent 侧收到的就恒是空列表，出题只能落到「核心技术方案」的兜底文案（D1）。
 */
async function fetchJdSkills(jdId: number): Promise<string[]> {
  try {
    const res = await jdApi.getParseResult(jdId)
    return (res.data?.skills ?? []).map((skill) => skill.name).filter(Boolean)
  } catch {
    return []
  }
}

/** 组装任务上下文：选了 JD 就带上全文，没选就只带简历 */
async function buildContext(): Promise<Record<string, unknown>> {
  const selectedJd = jds.value.find((item) => item.jdId === form.jdId)
  const context: Record<string, unknown> = {
    resume_id: resumes.value.find((item) => item.resumeId === form.resumeId)?.resumeBizId ?? null,
    jd_id: selectedJd?.jdBizId ?? null,
    // interview-agent 用 job_title 挑题库，用 resume_highlights 做个性化
    job_title: selectedJd?.title || '',
    resume_highlights: resumes.value.find((item) => item.resumeId === form.resumeId)?.title
      ? [resumes.value.find((item) => item.resumeId === form.resumeId)!.title]
      : [],
  }
  if (form.jdId) {
    try {
      const detail = await jdApi.getById(form.jdId)
      context.jd_text = detail.data?.rawText || ''
    } catch {
      context.jd_text = selectedJd?.rawTextPreview || ''
    }
    // JD 技能名：出题主题与技能命中检查都依赖它，不传则两者恒为空
    context.jd_skills = await fetchJdSkills(form.jdId)
  }
  if (form.resumeId) {
    context.resume_text = resumes.value.find((item) => item.resumeId === form.resumeId)?.title || ''
  }
  return context
}

async function startQuestion() {
  if (sessionDone.value) {
    message.info('本场面试已结束')
    return
  }
  asking.value = true
  answer.value = ''
  currentQuestion.value = null
  try {
    const context = await buildContext()
    const task = await polling.submit({
      taskType: 'interview',
      bizId: (context.jd_id as string) ?? null,
      input: {
        ...context,
        phase: 'question',
        round_no: round.value,
        // 必须显式带上 total_rounds：Agent 侧默认只有 3，
        // 不传的话用户选 5 轮时第 4 轮会被判成 round_no > total_rounds 而失败
        total_rounds: form.totalRounds,
        // 带上上一轮回答，Agent 才会据此生成真正的追问而不是又出一道新题
        ...(previousAnswer.value ? { previous_answer: previousAnswer.value } : {}),
      },
    })
    if (task?.status === 'SUCCESS' && polling.output.value) {
      currentQuestion.value = polling.output.value
    } else if (task && task.status !== 'SUCCESS') {
      message.error(polling.errorMessage.value || '出题失败')
    }
  } finally {
    asking.value = false
  }
}

async function submitAnswer() {
  if (!currentQuestion.value) return
  const text = answer.value.trim()
  if (!text) {
    message.warning('请先填写回答')
    return
  }
  evaluating.value = true
  const question = currentQuestion.value.question
  const currentRound = round.value
  try {
    const context = await buildContext()
    const task = await polling.submit({
      taskType: 'interview',
      bizId: (context.jd_id as string) ?? null,
      input: {
        ...context,
        phase: 'evaluate',
        round_no: currentRound,
        question,
        answer: text,
      },
    })
    // 同一 taskType 下 evaluate 阶段的 output 是 InterviewEvaluation
    const evaluation =
      task?.status === 'SUCCESS' ? (polling.output.value as InterviewEvaluation | null) : null
    if (evaluation) {
      roundRecords.value.unshift({ round: currentRound, question, answer: text, evaluation })
      round.value += 1
      currentQuestion.value = null
      // 留住本轮回答，下一轮出题时作为 previous_answer 传给 Agent 生成追问
      previousAnswer.value = text
      answer.value = ''
      if (round.value > form.totalRounds) {
        message.success(`面试结束，平均得分 ${averageScore()} 分`)
      } else {
        message.success('已评分，点击「下一题」继续')
      }
    } else if (task) {
      message.error(polling.errorMessage.value || '评分失败')
    }
  } finally {
    evaluating.value = false
  }
}

function averageScore(): string {
  if (!roundRecords.value.length) return '0'
  const total = roundRecords.value.reduce((sum, item) => sum + (item.evaluation?.score ?? 0), 0)
  return (total / roundRecords.value.length).toFixed(1)
}

onMounted(fetchOptions)
</script>

<style scoped>
.interview-page {
  max-width: 1200px;
  margin: 0 auto;
}
.panel {
  margin-bottom: 16px;
}
.question-card {
  background: #f0f7ff;
  border: 1px solid #91caff;
}
.question-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #888;
  font-size: 12px;
}
.question-text {
  margin: 10px 0 0;
  font-size: 16px;
  line-height: 1.7;
}
.answer-form {
  margin-top: 16px;
}
.round-info {
  margin-bottom: 12px;
}
.task-status .hint {
  margin: 6px 0 0;
  color: #999;
  font-size: 12px;
}
.history-panel {
  height: auto;
}
.record-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.record-question,
.record-answer,
.record-feedback,
.record-followup {
  margin: 6px 0 0;
  color: #555;
  font-size: 13px;
}
.record-question {
  color: #333;
}
.record-answer {
  color: #888;
}
.record-followup {
  color: #fa8c16;
}
.star-row {
  margin-top: 6px;
}
</style>
