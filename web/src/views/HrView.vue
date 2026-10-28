<template>
  <div class="hr-page">
    <a-page-header
      title="深度洞察"
      sub-title="把简历里的证据缺口，转成面试官可以直接用的验证问题"
    >
      <template #extra>
        <a-tag color="orange">辅助建议 · 最终判断由招聘方作出</a-tag>
      </template>
    </a-page-header>

    <a-row :gutter="16">
      <!-- ── 左：输入 ─────────────────────────────────────── -->
      <a-col :xs="24" :lg="9">
        <a-card title="分析对象" class="panel">
          <a-form :model="form" layout="vertical">
            <a-form-item label="岗位 JD">
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
            <a-form-item label="候选人简历">
              <a-select
                v-model:value="form.resumeId"
                :options="resumeOptions"
                placeholder="选择一份简历"
                allow-clear
                show-search
                option-filter-prop="label"
              />
            </a-form-item>
            <a-form-item label="最多验证问题数">
              <a-input-number v-model:value="form.maxQuestions" :min="1" :max="30" style="width: 100%" />
            </a-form-item>
            <a-button
              type="primary"
              block
              :loading="running"
              :disabled="!form.jdId || !form.resumeId"
              @click="handleGenerate"
            >
              <ThunderboltOutlined /> 生成深度洞察报告
            </a-button>
          </a-form>

          <a-divider style="margin: 16px 0" />

          <div v-if="running" class="task-status">
            <a-progress :percent="progress" size="small" />
            <p class="hint">状态：{{ status }}</p>
          </div>
          <a-alert
            v-else-if="errorMessage"
            type="error"
            show-icon
            :message="errorMessage"
          />

          <a-divider style="margin: 16px 0" />

          <a-button type="link" size="small" @click="loadHistory">历史报告</a-button>
          <a-list v-if="history.length" size="small" :data-source="history" bordered>
            <template #renderItem="{ item }">
              <a-list-item>
                <a @click="openInsight(item.insightId)">
                  {{ jdLabel(item.jdId) }} × {{ resumeLabel(item.resumeId) }}
                  <a-tag :color="item.status === 'SUCCESS' ? 'green' : 'blue'" style="margin-left: 8px">
                    {{ item.status === 'SUCCESS' ? '已完成' : '进行中' }}
                  </a-tag>
                  <div class="history-time">{{ item.createdAt?.slice(0, 16) }}</div>
                </a>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </a-col>

      <!-- ── 右：报告 ─────────────────────────────────────── -->
      <a-col :xs="24" :lg="15">
        <a-card v-if="report" class="panel">
          <!-- ① 匹配总览 -->
          <section class="section">
            <h4 class="section-title">① 匹配总览</h4>
            <a-row :gutter="12">
              <a-col :span="6"><a-statistic title="综合分" :value="report.match_overview.final_score" /></a-col>
              <a-col :span="6"><a-statistic title="语义" :value="report.match_overview.semantic_score" /></a-col>
              <a-col :span="6"><a-statistic title="关键词" :value="report.match_overview.keyword_score" /></a-col>
              <a-col :span="6"><a-statistic title="业务" :value="report.match_overview.business_score" /></a-col>
            </a-row>
            <div class="tag-row">
              <a-tag v-for="skill in report.match_overview.matched_skills" :key="skill" color="blue">
                {{ skill }}
              </a-tag>
            </div>
          </section>

          <a-divider />

          <!-- ② 能力维度覆盖 -->
          <section class="section">
            <h4 class="section-title">② 能力维度覆盖</h4>
            <div v-for="cov in report.capability_coverage" :key="cov.dimension" class="coverage-row">
              <span class="coverage-name">{{ cov.dimension_name }}</span>
              <div class="coverage-bar">
                <div class="bar-require" :style="{ width: requiredWidth(cov) }" />
                <div class="bar-evidence" :style="{ width: evidenceWidth(cov) }" />
              </div>
              <span class="coverage-level">要求 {{ cov.required_level }} / 证据 {{ cov.evidence_level }}</span>
            </div>
            <p class="hint">深色为岗位要求层级，浅色为简历已体现的层级</p>
          </section>

          <a-divider />

          <!-- ③ Gap 清单 -->
          <section class="section">
            <h4 class="section-title">③ 证据缺口与归因</h4>
            <a-list size="small" bordered :data-source="report.attributions">
              <template #renderItem="{ item }">
                <a-list-item>
                  <div class="gap-head">
                    <a-tag :color="severityColor(item.severity)">严重度 {{ item.severity }}</a-tag>
                    <a-tag color="cyan">{{ item.dimension_name }}</a-tag>
                    <a-tag :color="gapKindColor(item.gap_kind)">{{ gapKindLabel(item.gap_kind) }}</a-tag>
                    <span class="gap-level">
                      要求 {{ item.required_level }} / 证据 {{ item.evidence_level }}
                    </span>
                  </div>
                  <p class="gap-content">{{ item.gap_content }}</p>
                  <p class="gap-explanation">{{ item.explanation }}</p>
                  <p v-if="item.evidence_ref" class="gap-evidence">
                    <span class="evidence-label">原文佐证</span>{{ item.evidence_ref }}
                  </p>
                </a-list-item>
              </template>
            </a-list>
          </section>

          <a-divider />

          <!-- ④ 面试地图 -->
          <section class="section">
            <h4 class="section-title">④ 面试地图（验证问题）</h4>
            <a-empty v-if="!report.verification_plan.length" description="本次分析未产出验证问题" />
            <a-collapse v-else accordion>
              <a-collapse-panel
                v-for="q in report.verification_plan"
                :key="q.id"
                :header="questionHeader(q)"
              >
                <p class="q-intent"><span class="label">意图</span>{{ q.intent }}</p>

                <div class="probe-block">
                  <p class="label">追问</p>
                  <ul>
                    <li v-for="(p, i) in q.probes" :key="i">
                      {{ p.question }}
                      <span class="probe-trigger">（{{ p.trigger }}）</span>
                    </li>
                  </ul>
                </div>

                <div class="signal-block">
                  <p class="label">判定信号</p>
                  <ul>
                    <li v-for="s in q.signals" :key="s.verdict">
                      <a-tag :color="verdictColor(s.verdict)">{{ verdictLabel(s.verdict) }}</a-tag>
                      {{ s.evidence }}
                    </li>
                  </ul>
                </div>

                <a-divider style="margin: 12px 0" />

                <a-form layout="vertical">
                  <a-form-item label="录入候选人的回答">
                    <a-textarea
                      v-model:value="answers[q.id]"
                      placeholder="把候选人的回答粘贴到这里"
                      :rows="4"
                    />
                  </a-form-item>
                  <a-button
                    type="primary"
                    size="small"
                    :loading="judgingId === q.id"
                    :disabled="!answers[q.id]?.trim()"
                    @click="submitAnswer(q)"
                  >
                    提交并判定
                  </a-button>
                </a-form>
              </a-collapse-panel>
            </a-collapse>
          </section>

          <a-divider />

          <!-- ⑤ 追问记录 -->
          <section v-if="records.length" class="section">
            <h4 class="section-title">⑤ 追问记录</h4>
            <a-timeline>
              <a-timeline-item
                v-for="r in records"
                :key="r.recordId"
                :color="verdictColor(r.coverage)"
              >
                <div class="record-head">
                  <a-tag :color="verdictColor(r.coverage)">{{ verdictLabel(r.coverage) }}</a-tag>
                  <span class="record-meta">
                    第 {{ r.roundNo }} 轮 · 置信度 {{ r.confidence ?? '—' }} · {{ r.vqId }}
                  </span>
                </div>
                <p class="record-reason">{{ r.reason }}</p>
                <p v-if="r.evidenceRef" class="record-evidence">「{{ r.evidenceRef }}」</p>
              </a-timeline-item>
            </a-timeline>
          </section>

          <a-divider />

          <!-- ⑥ 免责与审计 -->
          <section class="section">
            <h4 class="section-title">⑥ 免责与审计</h4>
            <a-alert type="warning" show-icon :message="report.disclaimer" />
            <p class="hint">{{ report.audit_note }}</p>
          </section>
        </a-card>

        <a-card v-else class="panel">
          <a-empty description="选择 JD 与简历后点击「生成深度洞察报告」" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { ThunderboltOutlined } from '@ant-design/icons-vue'
import { hrApi, jdApi, resumeApi } from '@/api'
import { useTaskPolling } from '@/composables/useTaskPolling'
import type {
  HrInsightListItem,
  HrInsightReport,
  JdListItem,
  ResumeListItem,
  VerificationQuestion,
  VerificationRecord,
  VerificationVerdict,
} from '@/types'

const form = reactive({
  jdId: null as number | null,
  resumeId: null as number | null,
  maxQuestions: 12,
})

const route = useRoute()

const jds = ref<JdListItem[]>([])
const resumes = ref<ResumeListItem[]>([])
const noJdContent = '没有 JD，请先到「岗位 JD」页创建'

const report = ref<HrInsightReport | null>(null)
const insightId = ref('')
const records = ref<VerificationRecord[]>([])
const history = ref<HrInsightListItem[]>([])
const answers = reactive<Record<string, string>>({})
const judgingId = ref('')

// 洞察报告本身是异步任务，用现成的任务轮询跟踪
const polling = useTaskPolling<HrInsightReport>()
const running = computed(() => polling.running.value)
const status = computed(() => polling.status.value)
const progress = computed(() => polling.progress.value)
const errorMessage = computed(() => polling.errorMessage.value)

const jdOptions = computed(() =>
  jds.value.map((item) => ({
    label: `${item.title}${item.company ? ` · ${item.company}` : ''}`,
    value: item.jdId,
  })),
)

const resumeOptions = computed(() =>
  resumes.value.map((item) => ({ label: item.title, value: item.resumeId })),
)

function jdLabel(bizId: string | null) {
  if (!bizId) return '未命名岗位'
  return jds.value.find((item) => item.jdBizId === bizId)?.title || bizId
}

function resumeLabel(bizId: string | null) {
  if (!bizId) return '未命名简历'
  return resumes.value.find((item) => item.resumeBizId === bizId)?.title || bizId
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

async function handleGenerate() {
  if (!form.jdId || !form.resumeId) {
    message.warning('请先选择 JD 与简历')
    return
  }
  report.value = null
  records.value = []
  Object.keys(answers).forEach((k) => delete answers[k])

  try {
    const created = await hrApi.createInsight({
      resumeId: form.resumeId,
      jdId: form.jdId,
      maxQuestions: form.maxQuestions,
    })
    const data = created.data
    if (!data?.insightId) {
      message.error('报告创建失败')
      return
    }
    insightId.value = data.insightId

    if (!data.taskId) {
      message.warning('任务登记失败，请稍后重试')
      return
    }

    // 跟踪任务，拿到报告
    await polling.watch(data.taskId)
    if (polling.succeeded.value && polling.output.value) {
      report.value = polling.output.value
      await loadRecords()
    } else if (polling.errorMessage.value) {
      message.error(polling.errorMessage.value)
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '报告生成失败')
  }
}

async function loadHistory() {
  try {
    const res = await hrApi.listInsights({ page: 1, size: 20 })
    history.value = res.data?.items ?? []
  } catch {
    history.value = []
  }
}

async function openInsight(id: string) {
  try {
    const res = await hrApi.getInsight(id)
    const data = res.data
    if (!data) return
    insightId.value = id
    report.value = data.report
    await loadRecords()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '报告加载失败')
  }
}

async function loadRecords() {
  if (!insightId.value) return
  try {
    const res = await hrApi.getRecords(insightId.value)
    records.value = res.data ?? []
  } catch {
    records.value = []
  }
}

async function submitAnswer(question: VerificationQuestion) {
  const answer = (answers[question.id] || '').trim()
  if (!answer) {
    message.warning('请先录入候选人的回答')
    return
  }
  judgingId.value = question.id
  try {
    const res = await hrApi.submitAnswer({
      insightId: insightId.value,
      vqId: question.id,
      answer,
    })
    const data = res.data
    if (!data?.taskId) {
      message.warning('判定任务登记失败')
      return
    }
    // 判定是异步任务，跟踪到出结果
    const taskPolling = useTaskPolling<import('@/types').HrVerifyResult>()
    await taskPolling.watch(data.taskId)
    if (taskPolling.succeeded.value && taskPolling.output.value) {
      message.success(`判定结果：${verdictLabel(taskPolling.output.value.coverage)}`)
      await loadRecords()
    } else {
      message.error(taskPolling.errorMessage.value || '判定失败')
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '判定失败')
  } finally {
    judgingId.value = ''
  }
}

// ── 展示辅助 ──────────────────────────────────────────────────

function questionHeader(q: VerificationQuestion): string {
  return `${q.id} · ${q.question}`
}

function requiredWidth(cov: { required_level: number }): string {
  return `${(cov.required_level / 3) * 100}%`
}

function evidenceWidth(cov: { required_level: number; evidence_level: number }): string {
  return `${(cov.evidence_level / 3) * 100}%`
}

function severityColor(severity: number): string {
  if (severity >= 5) return 'red'
  if (severity >= 4) return 'orange'
  return 'blue'
}

function gapKindColor(kind: string): string {
  if (kind === 'no_evidence') return 'default'
  if (kind === 'adjacent') return 'gold'
  return 'green'
}

function gapKindLabel(kind: string): string {
  if (kind === 'no_evidence') return '未见证据'
  if (kind === 'adjacent') return '相邻经验'
  return '基本符合'
}

/** 判定值展示 —— 文案与后端常量保持一致，不自行另造。 */
function verdictLabel(verdict: VerificationVerdict | null | undefined): string {
  switch (verdict) {
    case 'demonstrated':
      return '已体现'
    case 'adjacent':
      return '有相邻经验'
    case 'not_evidenced':
      return '本次未获取到证据'
    default:
      return '未判定'
  }
}

function verdictColor(verdict: VerificationVerdict | null | undefined): string {
  switch (verdict) {
    case 'demonstrated':
      return 'green'
    case 'adjacent':
      return 'gold'
    case 'not_evidenced':
      return 'default'
    default:
      return 'default'
  }
}

onMounted(async () => {
  await fetchOptions()
  await loadHistory()
  const queryInsightId = typeof route.query.insightId === 'string' ? route.query.insightId : ''
  if (queryInsightId) await openInsight(queryInsightId)
})
</script>

<style scoped>
.hr-page {
  max-width: 1400px;
  margin: 0 auto;
}
.panel {
  height: 100%;
}
.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: #333;
}
.history-time {
  color: #999;
  font-size: 11px;
  margin-top: 2px;
}
.coverage-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.coverage-name {
  width: 120px;
  flex: none;
  font-size: 13px;
  color: #555;
}
.coverage-bar {
  position: relative;
  flex: 1;
  height: 14px;
  border-radius: 7px;
  background: #f0f0f0;
  overflow: hidden;
}
.bar-require {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: #91caff;
}
.bar-evidence {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: #52c41a;
  opacity: 0.75;
}
.coverage-level {
  width: 130px;
  flex: none;
  font-size: 12px;
  color: #888;
  text-align: right;
}
.gap-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.gap-level {
  font-size: 12px;
  color: #999;
}
.gap-content {
  margin: 6px 0 4px;
  font-weight: 500;
}
.gap-explanation {
  margin: 0 0 4px;
  font-size: 13px;
  color: #666;
  line-height: 1.7;
}
.gap-evidence {
  margin: 0;
  font-size: 12px;
  color: #888;
  background: #fafafa;
  padding: 6px 8px;
  border-radius: 4px;
}
.evidence-label {
  display: inline-block;
  margin-right: 6px;
  color: #999;
}
.q-intent {
  margin: 0 0 10px;
  font-size: 13px;
  color: #555;
}
.label {
  color: #999;
  font-size: 12px;
  margin-right: 6px;
}
.probe-block,
.signal-block {
  margin-bottom: 8px;
  font-size: 13px;
}
.probe-block ul,
.signal-block ul {
  margin: 4px 0 0;
  padding-left: 20px;
  line-height: 1.9;
}
.probe-trigger {
  color: #aaa;
  font-size: 12px;
}
.record-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.record-meta {
  font-size: 12px;
  color: #999;
}
.record-reason {
  margin: 6px 0 2px;
  font-size: 13px;
  color: #555;
}
.record-evidence {
  margin: 0;
  font-size: 12px;
  color: #888;
}
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
}
.hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #999;
}
.task-status .hint {
  margin-top: 6px;
}
</style>
