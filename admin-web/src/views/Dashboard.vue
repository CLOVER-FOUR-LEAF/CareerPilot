<template>
  <div class="admin-dash">
    <header class="dash-head">
      <div>
        <span class="eyebrow">Control room / {{ today }}</span>
        <h1 class="dash-title">所有正在发生的，<em>都在这</em>。</h1>
        <p class="dash-sub">用户、内容、AI 任务与服务健康，一屏掌握平台运行状态。</p>
      </div>
      <div class="head-status">
        <span class="status-label">Service health</span>
        <strong :class="{ bad: healthyCount !== services.length }">
          {{ healthyCount }}<em>/{{ services.length }}</em>
        </strong>
        <a-tag :color="healthyCount === services.length ? 'green' : 'orange'">
          {{ healthyCount === services.length ? '全部正常' : '有服务异常' }}
        </a-tag>
      </div>
    </header>

    <a-spin :spinning="loading">
      <section class="ledger">
        <button type="button" class="ledger-item" @click="$router.push('/users')">
          <span class="ledger-label">Total users</span>
          <strong>{{ stats.totalUsers }}</strong>
          <span class="ledger-note">今日新增 {{ stats.todayNewUsers }}</span>
        </button>
        <button type="button" class="ledger-item" @click="$router.push('/users')">
          <span class="ledger-label">Recruiters</span>
          <strong>{{ stats.recruiterCount }}</strong>
          <span class="ledger-note">管理员 {{ stats.adminCount }}</span>
        </button>
        <button type="button" class="ledger-item" @click="$router.push('/tasks')">
          <span class="ledger-label">AI tasks</span>
          <strong>{{ stats.totalAiTasks }}</strong>
          <span class="ledger-note">
            今日 {{ stats.todayAiTasks }} · 运行中 {{ stats.runningAiTasks }} · 失败 {{ stats.failedAiTasks }}
          </span>
        </button>
        <button type="button" class="ledger-item" @click="$router.push('/activity')">
          <span class="ledger-label">Content</span>
          <strong>{{ stats.totalResumes + stats.totalJds }}</strong>
          <span class="ledger-note">简历 {{ stats.totalResumes }} · JD {{ stats.totalJds }}</span>
        </button>
      </section>

      <section class="dash-grid">
        <div class="panel trend-panel">
          <div class="panel-head">
            <div>
              <span class="eyebrow">Last 14 days</span>
              <h2>活动趋势</h2>
            </div>
            <div class="legend">
              <span><i class="dot dot-task" />AI 任务</span>
              <span><i class="dot dot-resume" />简历</span>
              <span><i class="dot dot-jd" />JD</span>
              <span class="legend-rate">
                成功率
                <strong>{{ trends?.successRate == null ? '-' : (trends.successRate * 100).toFixed(1) + '%' }}</strong>
              </span>
            </div>
          </div>
          <div class="chart">
            <div v-for="point in trends?.daily || []" :key="point.date" class="chart-col">
              <div class="bars">
                <div class="bar bar-task" :style="{ height: barHeight(point.aiTasks) }" :title="`AI 任务 ${point.aiTasks}`" />
                <div class="bar bar-resume" :style="{ height: barHeight(point.resumes) }" :title="`简历 ${point.resumes}`" />
                <div class="bar bar-jd" :style="{ height: barHeight(point.jds) }" :title="`JD ${point.jds}`" />
              </div>
              <div class="chart-label">{{ point.date.slice(5) }}</div>
            </div>
            <a-empty v-if="!trends?.daily?.length" description="暂无趋势数据" :image="false" />
          </div>
        </div>

        <div class="panel dist-panel">
          <div class="panel-head">
            <div>
              <span class="eyebrow">Task mix</span>
              <h2>任务类型分布</h2>
            </div>
          </div>
          <div class="dist-list">
            <div v-for="item in trends?.taskTypes || []" :key="item.name" class="dist-row">
              <div class="dist-head">
                <span>{{ taskTypeLabel(item.name) }}</span>
                <span class="dist-count">{{ item.count }}</span>
              </div>
              <div class="dist-track">
                <span :style="{ width: percentOf(item.count, taskTypeTotal) + '%' }" />
              </div>
            </div>
            <a-empty v-if="!trends?.taskTypes?.length" description="暂无任务" :image="false" />
          </div>
          <div class="role-row">
            <span class="role-title">账号角色</span>
            <a-tag v-for="item in trends?.roles || []" :key="item.name" :color="roleColor(item.name)">
              {{ roleLabel(item.name) }} {{ item.count }}
            </a-tag>
          </div>
        </div>
      </section>

      <section class="dash-grid lower">
        <div class="panel content-panel">
          <div class="panel-head">
            <div>
              <span class="eyebrow">Content & revenue</span>
              <h2>内容与业务</h2>
            </div>
          </div>
          <div class="content-grid">
            <div><span>简历</span><strong>{{ stats.totalResumes }}</strong></div>
            <div><span>JD</span><strong>{{ stats.totalJds }}</strong></div>
            <div><span>HR 洞察</span><strong>{{ stats.totalInsights }}</strong></div>
            <div><span>已支付订单</span><strong>{{ stats.paidOrders }}</strong></div>
            <div><span>收入（元）</span><strong>{{ (stats.revenueCents / 100).toFixed(2) }}</strong></div>
            <div><span>失败任务</span><strong>{{ stats.failedAiTasks }}</strong></div>
          </div>
        </div>

        <div class="panel quick-panel">
          <div class="panel-head">
            <div>
              <span class="eyebrow">Shortcuts</span>
              <h2>快捷入口</h2>
            </div>
          </div>
          <button
            v-for="(item, index) in shortcuts"
            :key="item.path"
            type="button"
            class="quick-row"
            @click="$router.push(item.path)"
          >
            <span class="quick-index">0{{ index + 1 }}</span>
            <span class="quick-label">{{ item.label }}</span>
            <ArrowRightOutlined class="quick-arrow" />
          </button>
        </div>
      </section>

      <section class="panel boundary-panel">
        <div class="panel-head">
          <div>
            <span class="eyebrow">Boundaries</span>
            <h2>平台边界</h2>
          </div>
        </div>
        <ul class="boundary">
          <li>用户端共用一份前端：<code>/seeker/**</code> 给求职者，<code>/hr/**</code> 给招聘方，首页 <code>/</code> 是产品介绍与双入口。</li>
          <li>招聘方只分析自己主动导入的候选人简历；平台不提供投递、站内沟通、全站人才库检索。</li>
          <li>管理端独立前端 + 独立服务，只接受 <code>ADMIN</code> 角色；配置与操作全部留痕。</li>
          <li>模型 / Agent 配置是可编辑的事实来源；API Key 仍走部署环境变量，避免被页面或数据库导出带出。</li>
        </ul>
      </section>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ArrowRightOutlined } from '@ant-design/icons-vue'
import { adminApi } from '@/api'
import type { Overview, OverviewTrends, ServiceHealth } from '@/types'

const loading = ref(false)
const services = ref<ServiceHealth[]>([])
const trends = ref<OverviewTrends | null>(null)
const stats = ref<Overview>({
  totalUsers: 0,
  recruiterCount: 0,
  adminCount: 0,
  todayNewUsers: 0,
  totalResumes: 0,
  totalJds: 0,
  totalAiTasks: 0,
  runningAiTasks: 0,
  failedAiTasks: 0,
  todayAiTasks: 0,
  totalInsights: 0,
  paidOrders: 0,
  revenueCents: 0,
})

const shortcuts = [
  { label: '用户与角色管理', path: '/users' },
  { label: '用户使用与行为', path: '/activity' },
  { label: 'Agent / 模型配置', path: '/agents' },
  { label: 'AI 任务排查', path: '/tasks' },
  { label: '操作审计', path: '/audits' },
]

const today = computed(() => {
  const date = new Date()
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
})

const healthyCount = computed(() => services.value.filter((s) => s.up).length)

const taskTypeTotal = computed(() =>
  (trends.value?.taskTypes || []).reduce((sum, item) => sum + item.count, 0),
)

const maxDaily = computed(() => {
  const max = Math.max(
    1,
    ...(trends.value?.daily || []).map((p) => Math.max(p.aiTasks, p.resumes, p.jds)),
  )
  return max
})

function barHeight(value: number) {
  return `${Math.max(value > 0 ? 4 : 0, Math.round((value / maxDaily.value) * 100))}%`
}

function percentOf(value: number, total: number) {
  return total > 0 ? Math.round((value / total) * 100) : 0
}

const TASK_TYPE_LABELS: Record<string, string> = {
  jd_parse: 'JD 解析',
  resume_analyze: '简历分析',
  match_score: '匹配打分',
  resume_optimize: '简历优化',
  interview: '模拟面试',
  cover_letter: '求职信',
  career_plan: '职业规划',
  hr_insight: 'HR 洞察',
  hr_verify: '验证判定',
  jd_calibrate: 'JD 校准',
}

function taskTypeLabel(type: string) {
  return TASK_TYPE_LABELS[type] || type
}

function roleLabel(role: string) {
  return role === 'RECRUITER' ? '招聘方' : role === 'ADMIN' ? '管理员' : '求职者'
}

function roleColor(role: string) {
  return role === 'RECRUITER' ? 'gold' : role === 'ADMIN' ? 'red' : 'blue'
}

async function load() {
  loading.value = true
  try {
    const [overview, health, trendData] = await Promise.all([
      adminApi.overview(),
      adminApi.services(),
      adminApi.trends(),
    ])
    stats.value = overview
    services.value = health
    trends.value = trendData
  } catch (e) {
    message.error('概览加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.admin-dash {
  max-width: 1500px;
  margin: 0 auto;
}

.dash-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 30px;
  padding-bottom: 30px;
  border-bottom: 1px solid var(--line-strong);
}

.dash-title {
  margin: 16px 0 0;
  font-family: var(--font-display);
  font-size: clamp(38px, 4.2vw, 58px);
  font-weight: 600;
  line-height: 0.98;
  letter-spacing: -0.045em;
}

.dash-title em {
  font-style: italic;
  color: var(--accent-deep);
}

.dash-sub {
  max-width: 560px;
  margin: 16px 0 0;
  padding-left: 16px;
  border-left: 1px solid var(--line-strong);
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.8;
}

.head-status {
  display: grid;
  grid-template-columns: auto auto;
  align-items: center;
  gap: 2px 12px;
  padding-left: 24px;
  border-left: 1px solid var(--line);
}

.status-label {
  grid-column: 1 / -1;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--muted);
}

.head-status strong {
  font-family: var(--font-display);
  font-size: 44px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.04em;
}

.head-status strong em {
  font-style: normal;
  font-size: 18px;
  color: var(--muted);
}

.head-status strong.bad {
  color: var(--accent-deep);
}

.ledger {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 26px;
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
  font-size: 48px;
  font-weight: 600;
  line-height: 0.9;
  letter-spacing: -0.04em;
}

.ledger-note {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.04em;
  color: var(--muted);
}

.dash-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  gap: 22px;
  margin-top: 26px;
}

.dash-grid.lower {
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.75fr);
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

.legend {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.legend-rate {
  padding-left: 14px;
  border-left: 1px solid var(--line);
}

.legend-rate strong {
  color: var(--accent-deep);
  margin-left: 4px;
}

.dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 5px;
}

.dot-task { background: var(--accent); }
.dot-resume { background: var(--forest); }
.dot-jd { background: var(--line-strong); }

.chart {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 230px;
  padding: 22px 22px 0;
  border-bottom: 1px solid var(--line);
  background-image: linear-gradient(var(--line) 1px, transparent 1px);
  background-size: 100% 46px;
}

.chart-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  justify-content: flex-end;
}

.bars {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 2px;
  height: 184px;
  width: 100%;
}

.bar {
  width: 6px;
  min-height: 0;
  transition: height 0.3s ease;
}

.bar-task { background: var(--accent); }
.bar-resume { background: var(--forest); }
.bar-jd { background: var(--line-strong); }

.chart-label {
  margin-top: 8px;
  font-family: var(--font-mono);
  font-size: 7px;
  color: var(--muted);
  white-space: nowrap;
}

.dist-list {
  padding: 16px 22px 6px;
}

.dist-row {
  margin-bottom: 12px;
}

.dist-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 4px;
  color: var(--ink-soft);
  font-size: 11px;
}

.dist-count {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--muted);
}

.dist-track {
  height: 3px;
  background: var(--paper-2);
}

.dist-track span {
  display: block;
  height: 100%;
  background: var(--accent);
}

.role-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 8px 22px 18px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.role-title {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--muted);
}

.content-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-left: 1px solid var(--line);
  margin: 0 22px 22px;
  margin-top: 16px;
}

.content-grid > div {
  padding: 16px 18px;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.content-grid span {
  display: block;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--muted);
}

.content-grid strong {
  display: block;
  margin-top: 6px;
  font-family: var(--font-display);
  font-size: 34px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.03em;
}

.quick-panel {
  display: flex;
  flex-direction: column;
}

.quick-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px 22px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.16s ease, padding-left 0.16s ease;
}

.quick-row:last-child {
  border-bottom: 0;
}

.quick-row:hover {
  background: var(--white);
  padding-left: 28px;
}

.quick-index {
  font-family: var(--font-mono);
  font-size: 8px;
  color: var(--muted);
}

.quick-label {
  font-size: 13px;
  font-weight: 500;
}

.quick-arrow {
  margin-left: auto;
  color: var(--accent);
  font-size: 10px;
}

.boundary-panel {
  margin-top: 22px;
}

.boundary {
  margin: 0;
  padding: 18px 22px 20px 38px;
  color: var(--ink-soft);
  font-size: 12.5px;
  line-height: 1.9;
}

.boundary code {
  padding: 1px 5px;
  background: var(--paper-2);
  font-family: var(--font-mono);
  font-size: 11px;
}

@media (max-width: 1100px) {
  .ledger {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .dash-grid,
  .dash-grid.lower {
    grid-template-columns: 1fr;
  }
  .dash-head {
    flex-direction: column;
    align-items: flex-start;
  }
  .head-status {
    padding-left: 0;
    border-left: 0;
  }
}

@media (max-width: 640px) {
  .ledger {
    grid-template-columns: 1fr;
  }
  .content-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .chart {
    height: 180px;
  }
}
</style>
