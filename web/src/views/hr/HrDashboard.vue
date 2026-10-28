<template>
  <div class="hr-dashboard">
    <header class="hr-head">
      <div class="hr-copy">
        <span class="eyebrow">Recruiter desk / {{ today }}</span>
        <h1 class="hr-title">把简历，<em>变成证据</em>。</h1>
        <p class="hr-sub">
          把已收到的简历，变成可比较、可验证的招聘依据。
          只分析你主动导入的候选人简历，不涉及投递、沟通与人才库检索。
        </p>
      </div>
      <div class="hr-actions">
        <a-button type="primary" size="large" @click="router.push({ path: '/hr/resumes', query: { upload: '1' } })">
          <UploadOutlined /> 导入候选人简历
        </a-button>
        <a-tag color="orange">辅助建议 · 最终判断由招聘方作出</a-tag>
      </div>
    </header>

    <a-alert
      type="info"
      show-icon
      message="这是招聘方工作台，不是招聘平台"
      description="系统不提供投递、站内沟通、全站人才库检索，也不会输出自动录用或淘汰结论。"
      class="boundary-note"
    />

    <section class="ledger">
      <button type="button" class="ledger-item" @click="router.push('/hr/resumes')">
        <span class="ledger-label">Candidate resumes</span>
        <strong>{{ stats.resumes }}</strong>
        <span class="ledger-note">已导入的候选人简历</span>
      </button>
      <button type="button" class="ledger-item" @click="router.push('/hr/jds')">
        <span class="ledger-label">Job descriptions</span>
        <strong>{{ stats.jds }}</strong>
        <span class="ledger-note">作为匹配基准的岗位</span>
      </button>
      <button type="button" class="ledger-item" @click="router.push('/hr/match')">
        <span class="ledger-label">Match records</span>
        <strong>{{ stats.matches }}</strong>
        <span class="ledger-note">已完成的匹配评估</span>
      </button>
      <button type="button" class="ledger-item" @click="router.push('/hr/insight')">
        <span class="ledger-label">Deep insights</span>
        <strong>{{ stats.insights }}</strong>
        <span class="ledger-note">证据缺口与验证问题</span>
      </button>
    </section>

    <section class="hr-grid">
      <div class="panel next-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow">Next move</span>
            <h2>下一步</h2>
          </div>
        </div>
        <div class="next-list">
          <button
            v-for="(item, index) in nextSteps"
            :key="item.title"
            type="button"
            class="next-row"
            @click="router.push(item.target)"
          >
            <span class="next-index">0{{ index + 1 }}</span>
            <component :is="item.icon" class="next-icon" />
            <span class="next-title">{{ item.title }}</span>
            <span class="next-desc">{{ item.desc }}</span>
            <ArrowRightOutlined class="next-arrow" />
          </button>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow">Recent matches</span>
            <h2>最近匹配</h2>
          </div>
          <a class="panel-link" @click="router.push('/hr/match')">全部记录</a>
        </div>
        <a-table
          :data-source="recentMatches"
          :columns="matchColumns"
          row-key="recordId"
          size="small"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'jdId'">
              {{ jdTitleOf(record.jdId) }}
            </template>
            <template v-else-if="column.key === 'resumeId'">
              {{ resumeTitleOf(record.resumeId) }}
            </template>
            <template v-else-if="column.key === 'finalScore'">
              <span v-if="record.finalScore !== null" class="score">{{ record.finalScore }}</span>
              <span v-else class="muted">—</span>
            </template>
            <template v-else-if="column.key === 'createdAt'">
              {{ formatTime(record.createdAt) }}
            </template>
          </template>
        </a-table>
        <a-empty v-if="!loading && recentMatches.length === 0" description="还没有匹配记录" :image="false" />
      </div>
    </section>

    <section class="panel insights-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Recent reports</span>
          <h2>最近洞察报告</h2>
        </div>
        <a class="panel-link" @click="router.push('/hr/insight')">全部报告</a>
      </div>
      <div class="insight-list">
        <button
          v-for="item in recentInsights"
          :key="item.insightId"
          type="button"
          class="insight-row"
          @click="router.push({ path: '/hr/insight', query: { insightId: item.insightId } })"
        >
          <span class="insight-id">{{ item.insightId }}</span>
          <span class="insight-route">
            {{ jdTitleOf(item.jdId) }} <span>×</span> {{ resumeTitleOf(item.resumeId) }}
          </span>
          <a-tag :color="item.status === 'SUCCESS' ? 'green' : 'blue'">
            {{ item.status === 'SUCCESS' ? '已完成' : '进行中' }}
          </a-tag>
          <span class="insight-time">{{ formatTime(item.createdAt) }}</span>
          <ArrowRightOutlined class="insight-arrow" />
        </button>
      </div>
      <a-empty v-if="!loading && recentInsights.length === 0" description="还没有洞察报告" :image="false" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  AimOutlined,
  ArrowRightOutlined,
  FileSearchOutlined,
  PlusOutlined,
  ThunderboltOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue'
import { hrApi, jdApi, matchApi, resumeApi } from '@/api'
import type { HrInsightListItem, MatchListItem } from '@/types'
import type { Component } from 'vue'

interface NextStep {
  title: string
  desc: string
  target: string | { path: string; query: Record<string, string> }
  icon: Component
}

const router = useRouter()
const loading = ref(false)
const recentMatches = ref<MatchListItem[]>([])
const recentInsights = ref<HrInsightListItem[]>([])

const stats = reactive({
  resumes: 0,
  jds: 0,
  matches: 0,
  insights: 0,
})

const today = computed(() => {
  const date = new Date()
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
})

const nextSteps: NextStep[] = [
  {
    title: '导入候选人简历',
    desc: '上传你已收到的简历，系统自动解析并做 ATS 评分',
    target: { path: '/hr/resumes', query: { upload: '1' } },
    icon: UploadOutlined,
  },
  {
    title: '创建岗位 JD',
    desc: '为在招岗位建立结构化基准，供匹配与洞察使用',
    target: { path: '/hr/jds', query: { create: '1' } },
    icon: PlusOutlined,
  },
  {
    title: '开始岗位匹配',
    desc: '多份简历对同一 JD 批量打分，按综合分排序',
    target: '/hr/match',
    icon: AimOutlined,
  },
  {
    title: '生成深度洞察',
    desc: '拆解能力覆盖，生成面试官可直接使用的验证问题',
    target: '/hr/insight',
    icon: ThunderboltOutlined,
  },
]

/** 业务 ID -> 展示名；列表接口返回的是 jd_xxx / res_xxx，直接展示可读性差 */
const resumeTitles = ref<Record<string, string>>({})
const jdTitles = ref<Record<string, string>>({})

function jdTitleOf(bizId: string | null | undefined) {
  if (!bizId) return '-'
  return jdTitles.value[bizId] || bizId
}

function resumeTitleOf(bizId: string | null | undefined) {
  if (!bizId) return '-'
  return resumeTitles.value[bizId] || bizId
}

const matchColumns = [
  { title: '岗位', dataIndex: 'jdId', key: 'jdId', ellipsis: true },
  { title: '候选人', dataIndex: 'resumeId', key: 'resumeId', ellipsis: true },
  { title: '状态', key: 'status', width: 100 },
  { title: '综合分', key: 'finalScore', width: 90 },
  { title: '时间', key: 'createdAt', width: 150 },
]

function formatTime(value: string | null) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
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

onMounted(async () => {
  loading.value = true
  try {
    const [resumes, jds, matches, insights] = await Promise.all([
      resumeApi.list({ page: 1, size: 100 }),
      jdApi.list({ page: 1, size: 100 }),
      matchApi.list({ page: 1, size: 5 }),
      hrApi.listInsights({ page: 1, size: 5 }),
    ])
    stats.resumes = resumes.data?.total ?? 0
    stats.jds = jds.data?.total ?? 0
    stats.matches = matches.data?.total ?? 0
    stats.insights = insights.data?.total ?? 0
    resumeTitles.value = Object.fromEntries(
      (resumes.data?.items ?? []).map((r) => [r.resumeBizId, r.title || '未命名简历']),
    )
    jdTitles.value = Object.fromEntries(
      (jds.data?.items ?? []).map((j) => [j.jdBizId, j.title || '未命名 JD']),
    )
    recentMatches.value = matches.data?.items ?? []
    recentInsights.value = insights.data?.items ?? []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.hr-dashboard {
  max-width: 1360px;
  margin: 0 auto;
}

.hr-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 32px;
  align-items: end;
  padding-bottom: 32px;
  border-bottom: 1px solid var(--line-strong);
}

.hr-title {
  margin: 18px 0 0;
  font-family: var(--font-display);
  font-size: clamp(40px, 4.6vw, 62px);
  font-weight: 600;
  line-height: 0.98;
  letter-spacing: -0.045em;
}

.hr-title em {
  font-style: italic;
  color: var(--accent-deep);
}

.hr-sub {
  max-width: 620px;
  margin: 20px 0 0;
  padding-left: 16px;
  border-left: 1px solid var(--line-strong);
  color: var(--ink-soft);
  font-size: 13.5px;
  line-height: 1.85;
}

.hr-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.boundary-note {
  margin: 20px 0 0;
}

.ledger {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 24px;
  border-left: 1px solid var(--line);
}

.ledger-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 24px 26px;
  border: 0;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.16s ease;
}

.ledger-item:hover {
  background: var(--white);
}

.ledger-label {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--muted);
}

.ledger-item strong {
  margin: 8px 0 2px;
  font-family: var(--font-display);
  font-size: 50px;
  font-weight: 600;
  line-height: 0.9;
  letter-spacing: -0.04em;
  color: var(--ink);
}

.ledger-note {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.04em;
  color: var(--muted);
}

.hr-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.82fr) minmax(0, 1.18fr);
  gap: 22px;
  margin-top: 28px;
}

.panel {
  border: 1px solid var(--line);
  background: transparent;
}

.panel-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 22px;
  border-bottom: 1px solid var(--line);
}

.panel-head h2 {
  margin: 6px 0 0;
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.02em;
}

.panel-link {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--accent-deep);
  cursor: pointer;
}

.next-list {
  display: flex;
  flex-direction: column;
}

.next-row {
  display: grid;
  grid-template-columns: 28px 24px 1fr 18px;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.16s ease, padding-left 0.16s ease;
}

.next-row:last-child {
  border-bottom: 0;
}

.next-row:hover {
  background: var(--white);
  padding-left: 26px;
}

.next-index {
  font-family: var(--font-mono);
  font-size: 8px;
  color: var(--muted);
}

.next-icon {
  color: var(--accent);
  font-size: 14px;
}

.next-title {
  display: block;
  font-family: var(--font-body);
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
}

.next-desc {
  display: none;
}

.next-arrow {
  color: var(--accent);
  font-size: 10px;
}

.panel :deep(.ant-table) {
  padding: 0 6px 6px;
}

.score {
  font-family: var(--font-display);
  font-size: 20px;
  color: var(--accent-deep);
}

.muted {
  color: var(--muted);
}

.insights-panel {
  margin-top: 22px;
}

.insight-list {
  display: flex;
  flex-direction: column;
}

.insight-row {
  display: grid;
  grid-template-columns: minmax(140px, 0.8fr) minmax(0, 1.4fr) auto auto 20px;
  align-items: center;
  gap: 16px;
  padding: 16px 22px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.16s ease, padding-left 0.16s ease;
}

.insight-row:last-child {
  border-bottom: 0;
}

.insight-row:hover {
  background: var(--white);
  padding-left: 28px;
}

.insight-id {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.04em;
  color: var(--ink-soft);
  overflow: hidden;
  text-overflow: ellipsis;
}

.insight-route {
  color: var(--ink);
  font-size: 13px;
  font-weight: 500;
}

.insight-route span {
  margin: 0 4px;
  color: var(--muted);
}

.insight-time {
  font-family: var(--font-mono);
  font-size: 8px;
  color: var(--muted);
  white-space: nowrap;
}

.insight-arrow {
  color: var(--accent);
  font-size: 10px;
}

@media (max-width: 1040px) {
  .hr-head {
    grid-template-columns: 1fr;
    align-items: start;
  }
  .hr-actions {
    align-items: flex-start;
  }
  .ledger {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .hr-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .ledger {
    grid-template-columns: 1fr;
  }
  .insight-row {
    grid-template-columns: 1fr auto 18px;
  }
  .insight-id,
  .insight-time {
    display: none;
  }
}
</style>
