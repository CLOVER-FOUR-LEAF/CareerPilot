<template>
  <div class="hr-match">
    <a-page-header title="岗位匹配" sub-title="多份候选人简历对同一个 JD 批量打分，按综合分排序">
      <template #extra>
        <a-tag color="orange">分数仅作辅助 · 不输出录用结论</a-tag>
      </template>
    </a-page-header>

    <a-alert
      type="info"
      show-icon
      message="匹配范围：你导入的候选人简历 × 你的岗位 JD"
      description="综合分 = 语义 40% + 关键词 35% + 业务规则 25%。差距清单按严重度排序，供面试追问参考。"
      style="margin-bottom: 16px"
    />

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="8">
        <a-card :bordered="false" title="选择匹配对象" class="panel">
          <a-form layout="vertical">
            <a-form-item label="岗位 JD">
              <a-select
                v-model:value="selectedJd"
                :options="jdOptions"
                show-search
                allow-clear
                option-filter-prop="label"
                placeholder="选择要匹配的岗位"
                :not-found-content="jdNotFound"
              />
            </a-form-item>
            <a-form-item :label="`候选人简历（已选 ${selectedResumes.length} / 10）`">
              <a-select
                v-model:value="selectedResumes"
                mode="multiple"
                :options="resumeOptions"
                show-search
                allow-clear
                option-filter-prop="label"
                placeholder="选择一份或多份候选人简历"
                :max-tag-count="3"
                :not-found-content="resumeNotFound"
              />
            </a-form-item>
            <a-button
              type="primary"
              block
              :loading="running"
              :disabled="!selectedJd || selectedResumes.length === 0"
              @click="startMatch"
            >
              <AimOutlined /> 开始匹配（{{ selectedResumes.length }} 份）
            </a-button>
            <div class="form-hint">
              单次最多 10 份；评分在后台异步执行，页面会自动刷新结果。
            </div>
          </a-form>
        </a-card>

        <a-card :bordered="false" title="历史匹配记录" class="panel" style="margin-top: 16px">
          <a-list :data-source="history" size="small">
            <template #renderItem="{ item }">
              <a-list-item>
                <a-list-item-meta>
                  <template #title>
                    <span class="history-score" :style="{ color: scoreColor(item.finalScore) }">
                      {{ item.finalScore ?? '—' }}
                    </span>
                    <span class="history-title">{{ jdTitleOf(item.jdId) }}</span>
                  </template>
                  <template #description>
                    {{ formatTime(item.createdAt) }} · {{ statusText(item.status) }}
                  </template>
                </a-list-item-meta>
              </a-list-item>
            </template>
          </a-list>
          <a-empty v-if="history.length === 0" description="还没有匹配记录" :image="false" />
        </a-card>
      </a-col>

      <a-col :xs="24" :lg="16">
        <a-card :bordered="false" title="匹配结果" class="panel">
          <template #extra>
            <a-space v-if="results.length">
              <span class="muted">共 {{ results.length }} 份</span>
              <a-button size="small" :loading="polling" @click="refreshInFlight">刷新结果</a-button>
            </a-space>
          </template>

          <div v-if="results.length === 0" class="empty-box">
            <a-empty description="选择岗位与候选人后开始匹配">
              <template #description>
                <div>选择岗位与候选人后开始匹配</div>
                <div class="empty-sub">结果会按综合分从高到低排列，并展示每份简历的差距清单。</div>
              </template>
            </a-empty>
          </div>

          <div v-else class="result-list">
            <div
              v-for="(row, index) in sortedResults"
              :key="row.recordId"
              class="result-card"
            >
              <div class="result-head">
                <div class="rank" :class="`rank-${index + 1}`">{{ index + 1 }}</div>
                <div class="result-title">
                  <strong>{{ row.resumeTitle }}</strong>
                  <span class="muted">{{ row.recordId }}</span>
                </div>
                <a-tag :color="statusColor(row.status)">{{ statusText(row.status) }}</a-tag>
                <div class="final-score" :style="{ color: scoreColor(row.finalScore) }">
                  {{ row.finalScore ?? '—' }}
                  <span>综合分</span>
                </div>
              </div>

              <div v-if="row.status === 'SUCCESS'" class="score-grid">
                <div class="score-item">
                  <span>语义</span>
                  <a-progress :percent="row.semanticScore ?? 0" size="small" stroke-color="#1677ff" />
                </div>
                <div class="score-item">
                  <span>关键词</span>
                  <a-progress :percent="row.keywordScore ?? 0" size="small" stroke-color="#52c41a" />
                </div>
                <div class="score-item">
                  <span>业务规则</span>
                  <a-progress :percent="row.businessScore ?? 0" size="small" stroke-color="#722ed1" />
                </div>
              </div>

              <div v-else-if="row.status === 'FAILED'" class="error-box">
                {{ row.errorMessage || '匹配失败，请重试' }}
              </div>
              <div v-else class="pending-box">
                <a-progress :percent="row.progress" size="small" status="active" />
                <span class="muted">评分中，页面会自动刷新</span>
              </div>

              <template v-if="row.status === 'SUCCESS'">
                <div v-if="row.matchedSkills.length" class="tag-row">
                  <a-tag v-for="skill in row.matchedSkills" :key="skill" color="blue">{{ skill }}</a-tag>
                </div>
                <a-collapse v-if="row.gapList.length" ghost class="gap-collapse">
                  <a-collapse-panel :key="row.recordId" :header="`差距清单（${row.gapList.length}）`">
                    <a-list :data-source="row.gapList" size="small">
                      <template #renderItem="{ item }">
                        <a-list-item>
                          <a-list-item-meta>
                            <template #title>
                              <a-tag :color="severityColor(item.severity)">严重度 {{ item.severity }}</a-tag>
                              <span class="gap-type">{{ item.gapType }}</span>
                              {{ item.gapContent }}
                            </template>
                            <template #description>
                              <span class="gap-suggestion">建议：{{ item.suggestion }}</span>
                            </template>
                          </a-list-item-meta>
                        </a-list-item>
                      </template>
                    </a-list>
                  </a-collapse-panel>
                </a-collapse>
                <div v-else class="muted">未发现明显差距</div>
              </template>
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { AimOutlined } from '@ant-design/icons-vue'
import { jdApi, matchApi, resumeApi } from '@/api'
import type { JdListItem, MatchGap, MatchListItem, MatchRecord, ResumeListItem } from '@/types'

interface MatchRow {
  recordId: string
  resumeId: number
  resumeTitle: string
  status: string
  progress: number
  finalScore: number | null
  semanticScore: number | null
  keywordScore: number | null
  businessScore: number | null
  matchedSkills: string[]
  gapList: MatchGap[]
  errorMessage: string | null
}

const resumes = ref<ResumeListItem[]>([])
const jds = ref<JdListItem[]>([])
const selectedJd = ref<number | null>(null)
const selectedResumes = ref<number[]>([])

const results = ref<MatchRow[]>([])
const history = ref<MatchListItem[]>([])
const running = ref(false)
const polling = ref(false)

const jdOptions = computed(() =>
  jds.value.map((jd) => ({
    label: `${jd.title}${jd.company ? ' · ' + jd.company : ''}`,
    value: jd.jdId,
  })),
)

const resumeOptions = computed(() =>
  resumes.value.map((r) => ({
    label: `${r.title || '未命名简历'}${r.atsScore !== null ? ` · ATS ${r.atsScore}` : ''}`,
    value: r.resumeId,
  })),
)

const sortedResults = computed(() =>
  [...results.value].sort((a, b) => (b.finalScore ?? -1) - (a.finalScore ?? -1)),
)

const jdNotFound = '暂无 JD，请先在「岗位 JD」中创建'
const resumeNotFound = '暂无简历，请先在「候选人简历」中导入'

let pollTimer: number | undefined
let pollAttempts = 0

async function loadOptions() {
  const [resumeRes, jdRes, matchRes] = await Promise.all([
    resumeApi.list({ page: 1, size: 100 }),
    jdApi.list({ page: 1, size: 100 }),
    matchApi.list({ page: 1, size: 10 }),
  ])
  resumes.value = resumeRes.data?.items ?? []
  jds.value = jdRes.data?.items ?? []
  history.value = matchRes.data?.items ?? []
}

function titleOf(resumeId: number) {
  return resumes.value.find((r) => r.resumeId === resumeId)?.title || `简历 ${resumeId}`
}

function jdTitleOf(bizId: string | null) {
  if (!bizId) return '未命名岗位'
  return jds.value.find((j) => j.jdBizId === bizId)?.title || bizId
}

async function startMatch() {
  if (!selectedJd.value || selectedResumes.value.length === 0) return
  if (selectedResumes.value.length > 10) {
    message.warning('单次最多匹配 10 份简历')
    return
  }

  running.value = true
  results.value = []
  pollAttempts = 0
  try {
    const settled = await Promise.allSettled(
      selectedResumes.value.map((resumeId) =>
        matchApi.evaluate({ resumeId, jdId: selectedJd.value as number }),
      ),
    )

    let failed = 0
    settled.forEach((item, index) => {
      const resumeId = selectedResumes.value[index]
      if (item.status === 'fulfilled' && item.value.code === 0) {
        const data = item.value.data
        results.value.push({
          recordId: data.recordId,
          resumeId,
          resumeTitle: titleOf(resumeId),
          status: data.status || 'PENDING',
          progress: 10,
          finalScore: null,
          semanticScore: null,
          keywordScore: null,
          businessScore: null,
          matchedSkills: [],
          gapList: [],
          errorMessage: null,
        })
      } else {
        failed += 1
        results.value.push({
          recordId: `failed-${resumeId}`,
          resumeId,
          resumeTitle: titleOf(resumeId),
          status: 'FAILED',
          progress: 0,
          finalScore: null,
          semanticScore: null,
          keywordScore: null,
          businessScore: null,
          matchedSkills: [],
          gapList: [],
          errorMessage: item.status === 'rejected' ? String(item.reason?.message || '提交失败') : '提交失败',
        })
      }
    })

    if (failed) message.warning(`${failed} 份简历提交失败，请查看结果卡片`)
    if (results.value.some((r) => r.status !== 'FAILED')) {
      schedulePoll()
    }
  } finally {
    running.value = false
  }
}

async function refreshInFlight() {
  await pollOnce()
}

function schedulePoll() {
  if (pollTimer) window.clearTimeout(pollTimer)
  pollTimer = window.setTimeout(pollOnce, 2000)
}

async function pollOnce() {
  const inFlight = results.value.filter((r) => r.status !== 'SUCCESS' && r.status !== 'FAILED')
  if (inFlight.length === 0) {
    polling.value = false
    await loadOptions()
    return
  }

  polling.value = true
  pollAttempts += 1
  if (pollAttempts > 60) {
    polling.value = false
    message.warning('评分耗时较长，请稍后点击「刷新结果」')
    return
  }

  await Promise.all(
    inFlight.map(async (row) => {
      try {
        const res = await matchApi.get(row.recordId)
        applyRecord(row, res.data)
      } catch {
        // 单条失败不阻塞其他条目的轮询
      }
    }),
  )

  const stillInFlight = results.value.some((r) => r.status !== 'SUCCESS' && r.status !== 'FAILED')
  polling.value = false
  if (stillInFlight) schedulePoll()
  else await loadOptions()
}

function applyRecord(row: MatchRow, data: MatchRecord) {
  row.status = data.status
  row.progress = data.status === 'SUCCESS' ? 100 : Math.min(90, row.progress + 10)
  row.finalScore = data.finalScore
  row.semanticScore = data.semanticScore
  row.keywordScore = data.keywordScore
  row.businessScore = data.businessScore
  row.matchedSkills = data.matchedSkills || []
  row.gapList = data.gapList || []
  row.errorMessage = data.errorMessage ?? null
}

// ── 展示工具 ──────────────────────────────────────────────────
function formatTime(value: string) {
  return value ? dayjs(value).format('MM-DD HH:mm') : '-'
}

function statusText(status: string) {
  if (status === 'SUCCESS') return '已完成'
  if (status === 'FAILED') return '失败'
  if (status === 'PROCESSING') return '评分中'
  return '排队中'
}

function statusColor(status: string) {
  if (status === 'SUCCESS') return 'green'
  if (status === 'FAILED') return 'red'
  if (status === 'PROCESSING') return 'blue'
  return 'default'
}

function scoreColor(score: number | null) {
  if (score === null) return '#bfbfbf'
  if (score >= 80) return '#52c41a'
  if (score >= 60) return '#faad14'
  return '#ff4d4f'
}

function severityColor(severity: number) {
  if (severity >= 5) return 'red'
  if (severity >= 3) return 'orange'
  return 'blue'
}

onMounted(loadOptions)

onBeforeUnmount(() => {
  if (pollTimer) window.clearTimeout(pollTimer)
})
</script>

<style scoped>
.hr-match {
  max-width: 1320px;
  margin: 0 auto;
}
.panel {
  height: 100%;
}
.form-hint {
  margin-top: 10px;
  color: #999;
  font-size: 12px;
}
.muted {
  color: #999;
  font-size: 12px;
}
.empty-box {
  padding: 40px 0;
}
.empty-sub {
  color: #999;
  font-size: 12px;
  margin-top: 4px;
}
.result-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.result-card {
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  padding: 16px 18px;
  background: #fff;
}
.result-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.rank {
  width: 28px;
  height: 28px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  background: #f5f5f5;
  color: #8c8c8c;
  flex: none;
}
.rank-1 {
  background: linear-gradient(135deg, #faad14, #fa8c16);
  color: #fff;
}
.rank-2 {
  background: linear-gradient(135deg, #bfbfbf, #8c8c8c);
  color: #fff;
}
.rank-3 {
  background: linear-gradient(135deg, #d4a373, #b07d4b);
  color: #fff;
}
.result-title {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.result-title strong {
  font-size: 15px;
}
.final-score {
  margin-left: auto;
  text-align: right;
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
}
.final-score span {
  display: block;
  font-size: 10px;
  font-weight: 400;
  color: #999;
  margin-top: 4px;
}
.score-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 14px;
}
.score-item span {
  font-size: 12px;
  color: #8c8c8c;
}
.tag-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.gap-collapse {
  margin-top: 12px;
  background: #fafafa;
  border-radius: 8px;
}
.gap-type {
  color: #8c8c8c;
  font-size: 12px;
  margin-right: 4px;
}
.gap-suggestion {
  color: #595959;
}
.pending-box {
  margin-top: 12px;
}
.error-box {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff1f0;
  color: #cf1322;
  font-size: 13px;
}
.history-score {
  font-weight: 700;
  font-size: 16px;
  margin-right: 6px;
}
.history-title {
  font-size: 13px;
}
</style>
