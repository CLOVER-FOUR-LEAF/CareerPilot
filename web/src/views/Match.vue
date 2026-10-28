<template>
  <div class="match-page">
    <a-page-header title="匹配打分" sub-title="简历与 JD 的可解释匹配评估：语义 + 关键词 + 业务三维度">
      <template #extra>
        <a-button @click="fetchAll">
          <ReloadOutlined /> 刷新
        </a-button>
      </template>
    </a-page-header>

    <!-- 选择评估对象 -->
    <a-card title="选择评估对象" class="panel">
      <a-row :gutter="16" align="middle">
        <a-col :xs="24" :md="9">
          <a-form-item label="简历" class="inline-form-item">
            <a-select
              v-model:value="resumeId"
              :options="resumeOptions"
              placeholder="选择一份已分析的简历"
              show-search
              option-filter-prop="label"
              :not-found-content="noResumeContent"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :md="9">
          <a-form-item label="职位 JD" class="inline-form-item">
            <a-select
              v-model:value="jdId"
              :options="jdOptions"
              placeholder="选择一个已解析的 JD"
              show-search
              option-filter-prop="label"
              :not-found-content="noJdContent"
            />
          </a-form-item>
        </a-col>
        <a-col :xs="24" :md="6">
          <a-button type="primary" block :loading="evaluating" @click="handleEvaluate">
            <ThunderboltOutlined /> 开始评估
          </a-button>
        </a-col>
      </a-row>
      <a-alert
        v-if="!loadingOptions && resumes.length === 0"
        type="info"
        show-icon
        message="还没有可用简历"
        description="请先到「简历管理」上传简历并等待分析完成（ATS 分析完成后才能参与匹配）。"
      />
      <a-alert
        v-else-if="!loadingOptions && jds.length === 0"
        type="info"
        show-icon
        message="还没有可用 JD"
        description="请先到「JD 解析」新建职位描述。"
      />
    </a-card>

    <!-- 评估进行中 -->
    <a-card v-if="evaluating || (record && record.status !== 'SUCCESS')" class="panel">
      <a-progress :percent="pollProgress" :status="evaluating ? 'active' : 'exception'" />
      <p class="poll-hint">
        {{
          evaluating
            ? '正在创建评估任务…'
            : `等待 AI 返回结果（已轮询 ${Math.round(pollElapsed / 10) / 100}s / 60s）`
        }}
      </p>
    </a-card>

    <!-- 评估结果 -->
    <a-card v-if="record && record.status === 'SUCCESS'" title="评估结果" class="panel">
      <a-row :gutter="24">
        <a-col :xs="24" :md="6">
          <div class="final-score">
            <a-progress
              type="circle"
              :percent="record.finalScore ?? 0"
              :size="150"
              :stroke-color="getScoreColor(record.finalScore)"
              :format="() => `${record?.finalScore ?? 0} 分`"
            />
            <p class="score-caption">综合匹配分</p>
            <p class="score-tip">
              综合分 = 语义 40% + 关键词 35% + 业务 25%
            </p>
          </div>
        </a-col>
        <a-col :xs="24" :md="12">
          <h4>三项子分数</h4>
          <div class="sub-score">
            <span class="sub-score-label">语义匹配</span>
            <a-progress
              :percent="record.semanticScore ?? 0"
              :stroke-color="getScoreColor(record.semanticScore)"
              style="flex: 1"
            />
          </div>
          <div class="sub-score">
            <span class="sub-score-label">关键词覆盖</span>
            <a-progress
              :percent="record.keywordScore ?? 0"
              :stroke-color="getScoreColor(record.keywordScore)"
              style="flex: 1"
            />
          </div>
          <div class="sub-score">
            <span class="sub-score-label">业务对齐</span>
            <a-progress
              :percent="record.businessScore ?? 0"
              :stroke-color="getScoreColor(record.businessScore)"
              style="flex: 1"
            />
          </div>

          <a-divider style="margin: 16px 0" />

          <h4>已命中技能（{{ record.matchedSkills?.length || 0 }}）</h4>
          <div class="tag-row">
            <a-tag v-for="skill in record.matchedSkills || []" :key="skill" color="green">{{ skill }}</a-tag>
            <span v-if="!record.matchedSkills?.length" class="empty-text">未命中任何技能</span>
          </div>
        </a-col>
      </a-row>

      <a-divider />

      <h4>能力差距（按严重程度排序）</h4>
      <a-table
        :data-source="sortedGaps"
        :columns="gapColumns"
        :pagination="false"
        size="small"
        row-key="gapContent"
        :locale="{ emptyText: '没有发现能力差距，恭喜！' }"
      >
        <template #bodyCell="{ column, record: row }">
          <template v-if="column.key === 'gapType'">
            <a-tag>{{ gapTypeLabel(row.gapType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'severity'">
            <a-tag :color="severityColor(row.severity)">{{ severityLabel(row.severity) }}</a-tag>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 历史记录 -->
    <a-card title="历史评估" class="panel">
      <a-table
        :data-source="history"
        :columns="historyColumns"
        :loading="historyLoading"
        :pagination="false"
        size="small"
        row-key="recordId"
        :locale="{ emptyText: '暂无评估记录' }"
      >
        <template #bodyCell="{ column, record: row }">
          <template v-if="column.key === 'finalScore'">
            <a-tag :color="getScoreColor(row.finalScore)">{{ row.finalScore ?? '—' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="row.status === 'SUCCESS' ? 'green' : 'blue'">{{ row.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button type="link" size="small" @click="handleViewHistory(row)">查看</a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { jdApi, matchApi, resumeApi } from '@/api'
import type { JdListItem, MatchGap, MatchListItem, MatchRecord, ResumeListItem } from '@/types'

const resumes = ref<ResumeListItem[]>([])
const jds = ref<JdListItem[]>([])
const loadingOptions = ref(false)

const resumeId = ref<number | null>(null)
const jdId = ref<number | null>(null)
const evaluating = ref(false)
const record = ref<MatchRecord | null>(null)
const pollProgress = ref(0)
const pollElapsed = ref(0)

const history = ref<MatchListItem[]>([])
const historyLoading = ref(false)

const noResumeContent = '没有简历，请先上传'
const noJdContent = '没有 JD，请先新建'

const resumeOptions = computed(() =>
  resumes.value.map((item) => ({
    label: `${item.title}${item.atsScore !== null ? `（ATS ${item.atsScore}）` : ''}`,
    value: item.resumeId,
  }))
)

const jdOptions = computed(() =>
  jds.value.map((item) => ({
    label: `${item.title}${item.company ? ` · ${item.company}` : ''}`,
    value: item.jdId,
  }))
)

const sortedGaps = computed<MatchGap[]>(() =>
  [...(record.value?.gapList ?? [])].sort((a, b) => b.severity - a.severity)
)

const gapColumns = [
  { title: '类型', dataIndex: 'gapType', key: 'gapType', width: 120 },
  { title: '差距内容', dataIndex: 'gapContent', key: 'gapContent' },
  { title: '严重度', dataIndex: 'severity', key: 'severity', width: 110 },
  { title: '改进建议', dataIndex: 'suggestion', key: 'suggestion' },
]

const historyColumns = [
  { title: '记录 ID', dataIndex: 'recordId', key: 'recordId' },
  { title: '简历 ID', dataIndex: 'resumeId', key: 'resumeId', width: 100 },
  { title: 'JD ID', dataIndex: 'jdId', key: 'jdId', width: 90 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '综合分', dataIndex: 'finalScore', key: 'finalScore', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 90 },
]

function getScoreColor(score: number | null | undefined) {
  const value = score ?? 0
  if (value >= 80) return '#52c41a'
  if (value >= 60) return '#faad14'
  return '#ff4d4f'
}

function gapTypeLabel(type: string) {
  switch (type) {
    case 'missing_skill':
      return '缺少技能'
    case 'weak_experience':
      return '经验不足'
    case 'mismatch':
      return '方向不符'
    default:
      return type
  }
}

function severityColor(severity: number) {
  if (severity >= 5) return 'red'
  if (severity >= 4) return 'orange'
  if (severity >= 3) return 'gold'
  return 'blue'
}

function severityLabel(severity: number) {
  if (severity >= 5) return `${severity} · 硬性`
  if (severity >= 4) return `${severity} · 高`
  if (severity >= 3) return `${severity} · 中`
  return `${severity} · 低`
}

async function fetchOptions() {
  loadingOptions.value = true
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
  } finally {
    loadingOptions.value = false
  }
}

async function fetchHistory() {
  historyLoading.value = true
  try {
    const res = await matchApi.list({ page: 1, size: 10 })
    history.value = res.data?.items ?? []
  } catch {
    history.value = []
  } finally {
    historyLoading.value = false
  }
}

async function fetchAll() {
  await Promise.all([fetchOptions(), fetchHistory()])
}

/** §4.2 轮询评估记录，1.5s 一次，最多 60s */
async function pollRecord(recordId: string): Promise<MatchRecord | null> {
  const deadline = Date.now() + 60000
  pollElapsed.value = 0
  pollProgress.value = 10
  for (;;) {
    try {
      const res = await matchApi.get(recordId)
      const data = res.data
      record.value = data
      if (data.status === 'SUCCESS' || data.status === 'FAILED' || data.status === 'CANCELLED') {
        pollProgress.value = 100
        return data
      }
    } catch {
      // 单次失败继续等
    }
    if (Date.now() > deadline) {
      message.warning('评估超时，请稍后到历史记录中查看结果')
      return record.value
    }
    pollProgress.value = Math.min(95, pollProgress.value + 5)
    pollElapsed.value = Date.now() - (deadline - 60000)
    await new Promise((resolve) => setTimeout(resolve, 1500))
  }
}

async function handleEvaluate() {
  if (!resumeId.value || !jdId.value) {
    message.warning('请先选择简历与 JD')
    return
  }
  evaluating.value = true
  record.value = null
  pollProgress.value = 0
  try {
    const res = await matchApi.evaluate({ resumeId: resumeId.value, jdId: jdId.value })
    const result = await pollRecord(res.data.recordId)
    if (result?.status === 'SUCCESS') {
      message.success('评估完成')
      await fetchHistory()
    } else if (result && result.status !== 'PENDING') {
      message.error('评估失败')
    }
  } catch {
    message.error('评估发起失败')
  } finally {
    evaluating.value = false
  }
}

async function handleViewHistory(row: MatchListItem) {
  resumeId.value = row.resumeId
  jdId.value = row.jdId
  try {
    const res = await matchApi.get(row.recordId)
    record.value = res.data
    if (res.data.status !== 'SUCCESS') {
      message.warning('该评估尚未完成')
    }
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch {
    message.error('获取评估详情失败')
  }
}

onMounted(fetchAll)
</script>

<style scoped>
.match-page {
  max-width: 1200px;
  margin: 0 auto;
}
.panel {
  margin-bottom: 16px;
}
.inline-form-item {
  margin-bottom: 0;
}
.poll-hint {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}
.final-score {
  text-align: center;
}
.score-caption {
  margin: 8px 0 0;
  color: var(--ink-soft);
}
.score-tip {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}
.sub-score {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}
.sub-score-label {
  width: 84px;
  color: var(--ink-soft);
}
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.empty-text {
  color: var(--muted);
  font-size: 12px;
}
</style>
