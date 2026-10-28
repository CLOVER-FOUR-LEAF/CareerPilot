<template>
  <div>
    <a-spin :spinning="loading">
      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :sm="12" :lg="6">
          <a-card :bordered="false">
            <a-statistic title="用户总数" :value="stats.totalUsers">
              <template #suffix><TeamOutlined class="stat-icon" /></template>
            </a-statistic>
            <div class="sub">今日新增 {{ stats.todayNewUsers }}</div>
          </a-card>
        </a-col>
        <a-col :xs="24" :sm="12" :lg="6">
          <a-card :bordered="false">
            <a-statistic title="招聘方账号" :value="stats.recruiterCount" />
            <div class="sub">管理员 {{ stats.adminCount }}</div>
          </a-card>
        </a-col>
        <a-col :xs="24" :sm="12" :lg="6">
          <a-card :bordered="false">
            <a-statistic title="AI 任务总数" :value="stats.totalAiTasks">
              <template #suffix><ThunderboltOutlined class="stat-icon" /></template>
            </a-statistic>
            <div class="sub">今日 {{ stats.todayAiTasks }} · 运行中 {{ stats.runningAiTasks }} · 失败 {{ stats.failedAiTasks }}</div>
          </a-card>
        </a-col>
        <a-col :xs="24" :sm="12" :lg="6">
          <a-card :bordered="false">
            <a-statistic title="服务健康" :value="healthyCount" :suffix="`/ ${services.length}`" />
            <div class="sub">
              <a-tag :color="healthyCount === services.length ? 'green' : 'orange'">
                {{ healthyCount === services.length ? '全部正常' : '有服务异常' }}
              </a-tag>
              <a @click="$router.push('/services')">查看详情</a>
            </div>
          </a-card>
        </a-col>
      </a-row>

      <a-row :gutter="[16, 16]" style="margin-top: 16px">
        <a-col :xs="24" :lg="16">
          <a-card :bordered="false" title="近 14 天活动趋势">
            <div class="legend">
              <span><i class="dot dot-task" />AI 任务</span>
              <span><i class="dot dot-resume" />简历</span>
              <span><i class="dot dot-jd" />JD</span>
              <span class="legend-rate">
                任务成功率
                <strong>{{ trends?.successRate == null ? '-' : (trends.successRate * 100).toFixed(1) + '%' }}</strong>
              </span>
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
          </a-card>
        </a-col>

        <a-col :xs="24" :lg="8">
          <a-card :bordered="false" title="AI 任务类型分布">
            <div v-for="item in trends?.taskTypes || []" :key="item.name" class="dist-row">
              <div class="dist-head">
                <span>{{ taskTypeLabel(item.name) }}</span>
                <span class="dist-count">{{ item.count }}</span>
              </div>
              <a-progress
                :percent="percentOf(item.count, taskTypeTotal)"
                :show-info="false"
                size="small"
                stroke-color="#722ed1"
              />
            </div>
            <a-empty v-if="!trends?.taskTypes?.length" description="暂无任务" :image="false" />
            <a-divider style="margin: 12px 0" />
            <div class="role-row">
              <span class="role-title">账号角色</span>
              <a-tag v-for="item in trends?.roles || []" :key="item.name" :color="roleColor(item.name)">
                {{ roleLabel(item.name) }} {{ item.count }}
              </a-tag>
            </div>
          </a-card>
        </a-col>
      </a-row>

      <a-row :gutter="[16, 16]" style="margin-top: 16px">
        <a-col :xs="24" :lg="16">
          <a-card :bordered="false" title="内容与业务">
            <a-row :gutter="[16, 16]">
              <a-col :span="8"><a-statistic title="简历" :value="stats.totalResumes" /></a-col>
              <a-col :span="8"><a-statistic title="JD" :value="stats.totalJds" /></a-col>
              <a-col :span="8"><a-statistic title="HR 洞察报告" :value="stats.totalInsights" /></a-col>
              <a-col :span="8"><a-statistic title="已支付订单" :value="stats.paidOrders" /></a-col>
              <a-col :span="8">
                <a-statistic title="收入（元）" :value="stats.revenueCents / 100" :precision="2" />
              </a-col>
              <a-col :span="8"><a-statistic title="失败任务" :value="stats.failedAiTasks" /></a-col>
            </a-row>
          </a-card>
        </a-col>

        <a-col :xs="24" :lg="8">
          <a-card :bordered="false" title="快捷入口">
            <a-space direction="vertical" style="width: 100%">
              <a-button block @click="$router.push('/users')">用户与角色管理</a-button>
              <a-button block @click="$router.push('/activity')">用户使用与行为</a-button>
              <a-button block @click="$router.push('/agents')">Agent / 模型配置</a-button>
              <a-button block @click="$router.push('/tasks')">AI 任务排查</a-button>
              <a-button block @click="$router.push('/audits')">操作审计</a-button>
            </a-space>
          </a-card>
        </a-col>
      </a-row>

      <a-card :bordered="false" title="平台边界" style="margin-top: 16px">
        <ul class="boundary">
          <li>用户端共用一份前端：<code>/seeker/**</code> 给求职者，<code>/hr/**</code> 给招聘方，首页 <code>/</code> 是产品介绍与双入口。</li>
          <li>招聘方只分析自己主动导入的候选人简历；平台不提供投递、站内沟通、全站人才库检索。</li>
          <li>管理端独立前端 + 独立服务，只接受 <code>ADMIN</code> 角色；配置与操作全部留痕。</li>
          <li>模型 / Agent 配置是可编辑的事实来源；API Key 仍走部署环境变量，避免被页面或数据库导出带出。</li>
        </ul>
      </a-card>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { TeamOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
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
.sub {
  margin-top: 8px;
  color: #999;
  font-size: 12px;
}
.stat-icon {
  color: #bfbfbf;
  font-size: 16px;
}
.legend {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  color: #8c8c8c;
  font-size: 12px;
}
.legend-rate {
  margin-left: auto;
  color: #595959;
}
.legend-rate strong {
  color: #722ed1;
  margin-left: 4px;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
  margin-right: 5px;
}
.dot-task { background: #722ed1; }
.dot-resume { background: #1677ff; }
.dot-jd { background: #13c2c2; }
.chart {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 150px;
  padding-top: 8px;
  border-bottom: 1px solid #f0f0f0;
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
  height: 112px;
  width: 100%;
}
.bar {
  width: 6px;
  min-height: 0;
  border-radius: 2px 2px 0 0;
  transition: height 0.3s ease;
}
.bar-task { background: #722ed1; }
.bar-resume { background: #1677ff; }
.bar-jd { background: #13c2c2; }
.chart-label {
  margin-top: 6px;
  font-size: 10px;
  color: #bfbfbf;
  white-space: nowrap;
  transform: scale(0.9);
}
.dist-row {
  margin-bottom: 8px;
}
.dist-head {
  display: flex;
  justify-content: space-between;
  color: #595959;
  font-size: 12px;
  margin-bottom: 2px;
}
.dist-count {
  color: #8c8c8c;
}
.role-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.role-title {
  color: #8c8c8c;
  font-size: 12px;
}
.boundary {
  margin: 0;
  padding-left: 20px;
  color: #595959;
  line-height: 1.9;
}
code {
  background: #f5f5f5;
  padding: 1px 5px;
  border-radius: 3px;
}
</style>
