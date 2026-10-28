<template>
  <div class="hr-dashboard">
    <a-page-header title="招聘工作台" sub-title="把已收到的简历，变成可比较、可验证的招聘依据">
      <template #extra>
        <a-tag color="orange">辅助建议 · 最终判断由招聘方作出</a-tag>
      </template>
    </a-page-header>

    <a-alert
      type="info"
      show-icon
      message="这是招聘方工作台，不是招聘平台"
      description="系统只分析你主动导入的候选人简历；不提供投递、站内沟通、全站人才库检索，也不会输出自动录用或淘汰结论。"
      style="margin-bottom: 20px"
    />

    <a-row :gutter="[16, 16]">
      <a-col :xs="12" :md="6">
        <a-card :bordered="false" hoverable @click="router.push('/hr/resumes')">
          <a-statistic title="候选人简历" :value="stats.resumes">
            <template #prefix><FileTextOutlined /></template>
          </a-statistic>
          <div class="sub">已导入并完成解析的简历</div>
        </a-card>
      </a-col>
      <a-col :xs="12" :md="6">
        <a-card :bordered="false" hoverable @click="router.push('/hr/jds')">
          <a-statistic title="岗位 JD" :value="stats.jds">
            <template #prefix><FileSearchOutlined /></template>
          </a-statistic>
          <div class="sub">作为匹配与洞察的基准</div>
        </a-card>
      </a-col>
      <a-col :xs="12" :md="6">
        <a-card :bordered="false" hoverable @click="router.push('/hr/match')">
          <a-statistic title="匹配记录" :value="stats.matches">
            <template #prefix><AimOutlined /></template>
          </a-statistic>
          <div class="sub">多份简历对同一岗位的横向对比</div>
        </a-card>
      </a-col>
      <a-col :xs="12" :md="6">
        <a-card :bordered="false" hoverable @click="router.push('/hr/insight')">
          <a-statistic title="深度洞察" :value="stats.insights">
            <template #prefix><ThunderboltOutlined /></template>
          </a-statistic>
          <div class="sub">能力覆盖、证据缺口与验证问题</div>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" style="margin-top: 16px">
      <a-col :xs="24" :lg="8">
        <a-card :bordered="false" title="下一步" class="panel">
          <a-space direction="vertical" style="width: 100%">
            <a-button type="primary" block @click="router.push({ path: '/hr/resumes', query: { upload: '1' } })">
              <UploadOutlined /> 导入候选人简历
            </a-button>
            <a-button block @click="router.push({ path: '/hr/jds', query: { create: '1' } })">
              <PlusOutlined /> 创建岗位 JD
            </a-button>
            <a-button block @click="router.push('/hr/match')">
              <AimOutlined /> 开始岗位匹配
            </a-button>
            <a-button block @click="router.push('/hr/insight')">
              <ThunderboltOutlined /> 生成深度洞察
            </a-button>
          </a-space>
        </a-card>
      </a-col>

      <a-col :xs="24" :lg="16">
        <a-card :bordered="false" title="最近匹配" class="panel">
          <template #extra>
            <a @click="router.push('/hr/match')">全部记录</a>
          </template>
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
        </a-card>
      </a-col>
    </a-row>

    <a-card :bordered="false" title="最近洞察报告" class="panel" style="margin-top: 16px">
      <template #extra>
        <a @click="router.push('/hr/insight')">全部报告</a>
      </template>
      <a-list :data-source="recentInsights" size="small">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta>
              <template #title>
                <a @click="router.push({ path: '/hr/insight', query: { insightId: item.insightId } })">
                  {{ item.insightId }}
                </a>
                <a-tag :color="item.status === 'SUCCESS' ? 'green' : 'blue'" style="margin-left: 8px">
                  {{ item.status === 'SUCCESS' ? '已完成' : '进行中' }}
                </a-tag>
              </template>
              <template #description>
                岗位 {{ jdTitleOf(item.jdId) }} · 候选人 {{ resumeTitleOf(item.resumeId) }} · {{ formatTime(item.createdAt) }}
              </template>
            </a-list-item-meta>
          </a-list-item>
        </template>
      </a-list>
      <a-empty v-if="!loading && recentInsights.length === 0" description="还没有洞察报告" :image="false" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  AimOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  PlusOutlined,
  ThunderboltOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue'
import { hrApi, jdApi, matchApi, resumeApi } from '@/api'
import type { HrInsightListItem, MatchListItem } from '@/types'

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
  max-width: 1280px;
  margin: 0 auto;
}
.sub {
  margin-top: 8px;
  color: #999;
  font-size: 12px;
}
.panel {
  height: 100%;
}
.score {
  font-weight: 700;
  color: #1677ff;
  font-size: 16px;
}
.muted {
  color: #bfbfbf;
}
</style>
