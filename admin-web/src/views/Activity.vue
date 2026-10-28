<template>
  <a-card :bordered="false">
    <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
      <!-- ── 用户使用概览 ─────────────────────────────────── -->
      <a-tab-pane key="users" tab="用户使用概览">
        <div class="toolbar">
          <a-input-search
            v-model:value="userKeyword"
            placeholder="搜索邮箱 / 昵称 / userId"
            allow-clear
            style="width: 260px"
            @search="reloadUsers"
          />
          <a-select
            v-model:value="userRole"
            placeholder="全部角色"
            allow-clear
            style="width: 150px"
            :options="roleOptions"
            @change="reloadUsers"
          />
          <a-button @click="loadUsers">刷新</a-button>
        </div>

        <a-alert
          type="info"
          show-icon
          message="这些数字来自业务表实时聚合，不额外维护行为日志"
          description="简历、JD、匹配记录、AI 任务与洞察报告分别按 user_id 统计；最近活跃时间取最近一次 AI 任务。"
          style="margin-bottom: 16px"
        />

        <a-table
          :loading="userLoading"
          :data-source="users"
          :columns="userColumns"
          row-key="id"
          size="middle"
          :pagination="userPagination"
          @change="handleUserTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'role'">
              <a-tag :color="roleColor(record.role)">{{ roleLabel(record.role) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="record.status === 1 ? 'green' : 'red'">
                {{ record.status === 1 ? '正常' : '封禁' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'usage'">
              <a-space :size="4" wrap>
                <a-tag>简历 {{ record.resumeCount }}</a-tag>
                <a-tag>JD {{ record.jdCount }}</a-tag>
                <a-tag>匹配 {{ record.matchCount }}</a-tag>
                <a-tag color="purple">AI {{ record.aiTaskCount }}</a-tag>
                <a-tag v-if="record.insightCount" color="orange">洞察 {{ record.insightCount }}</a-tag>
              </a-space>
            </template>
            <template v-else-if="column.key === 'lastLoginAt'">
              {{ formatTime(record.lastLoginAt) }}
            </template>
            <template v-else-if="column.key === 'lastActiveAt'">
              {{ formatTime(record.lastActiveAt) }}
            </template>
            <template v-else-if="column.key === 'createdAt'">
              {{ formatTime(record.createdAt) }}
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <!-- ── 行为流水 ─────────────────────────────────────── -->
      <a-tab-pane key="events" tab="行为流水">
        <div class="toolbar">
          <a-input-search
            v-model:value="eventKeyword"
            placeholder="搜索邮箱 / userId"
            allow-clear
            style="width: 260px"
            @search="reloadEvents"
          />
          <a-select
            v-model:value="eventAction"
            placeholder="全部行为"
            allow-clear
            style="width: 190px"
            :options="actionOptions"
            @change="reloadEvents"
          />
          <a-button @click="loadEvents">刷新</a-button>
        </div>

        <a-table
          :loading="eventLoading"
          :data-source="events"
          :columns="eventColumns"
          row-key="eventKey"
          size="middle"
          :pagination="eventPagination"
          @change="handleEventTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-tag :color="actionColor(record.action)">{{ actionLabel(record.action) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'createdAt'">
              {{ formatTime(record.createdAt) }}
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import { adminApi } from '@/api'
import type { ActivityEvent, ActivityUser, UserRole } from '@/types'

const activeTab = ref<'users' | 'events'>('users')

// ── 用户使用概览 ──────────────────────────────────────────────
const userLoading = ref(false)
const users = ref<ActivityUser[]>([])
const userTotal = ref(0)
const userPage = ref(1)
const userSize = ref(20)
const userKeyword = ref('')
const userRole = ref<string | undefined>(undefined)

const roleOptions = [
  { label: '求职者 USER', value: 'USER' },
  { label: '招聘方 RECRUITER', value: 'RECRUITER' },
  { label: '管理员 ADMIN', value: 'ADMIN' },
]

const userColumns = [
  { title: '邮箱', dataIndex: 'email', key: 'email', width: 220, ellipsis: true },
  { title: '昵称', dataIndex: 'nickname', key: 'nickname', width: 110 },
  { title: '角色', key: 'role', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '累计使用', key: 'usage', width: 320 },
  { title: '最后登录', key: 'lastLoginAt', width: 150 },
  { title: '最近活跃', key: 'lastActiveAt', width: 150 },
  { title: '注册时间', key: 'createdAt', width: 150 },
]

const userPagination = computed(() => ({
  current: userPage.value,
  pageSize: userSize.value,
  total: userTotal.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 个账号`,
}))

async function loadUsers() {
  userLoading.value = true
  try {
    const res = await adminApi.activityUsers({
      page: userPage.value,
      size: userSize.value,
      keyword: userKeyword.value || undefined,
      role: userRole.value || undefined,
    })
    users.value = res.records
    userTotal.value = res.total
  } finally {
    userLoading.value = false
  }
}

function reloadUsers() {
  userPage.value = 1
  loadUsers()
}

function handleUserTableChange(p: { current?: number; pageSize?: number }) {
  userPage.value = p.current || 1
  userSize.value = p.pageSize || 20
  loadUsers()
}

// ── 行为流水 ──────────────────────────────────────────────────
const eventLoading = ref(false)
const events = ref<ActivityEvent[]>([])
const eventTotal = ref(0)
const eventPage = ref(1)
const eventSize = ref(20)
const eventKeyword = ref('')
const eventAction = ref<string | undefined>(undefined)

const ACTION_LABELS: Record<string, string> = {
  'user.register': '注册账号',
  'user.login': '最近登录',
  'resume.upload': '上传简历',
  'jd.create': '创建 JD',
  'match.evaluate': '发起匹配',
  'hr.insight': '生成深度洞察',
  'ai.resume_optimize': '简历优化',
  'ai.interview': '模拟面试',
  'ai.cover_letter': '生成求职信',
  'ai.career_plan': '职业规划',
  'ai.hr_verify': '提交验证回答',
  'ai.jd_calibrate': 'JD 校准',
}

const actionOptions = Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label }))

const eventColumns = [
  { title: '时间', key: 'createdAt', width: 170 },
  { title: '账号', dataIndex: 'email', key: 'email', width: 220, ellipsis: true },
  { title: '行为', key: 'action', width: 130 },
  { title: '详情', dataIndex: 'summary', key: 'summary', ellipsis: true },
]

const eventPagination = computed(() => ({
  current: eventPage.value,
  pageSize: eventSize.value,
  total: eventTotal.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条行为`,
}))

async function loadEvents() {
  eventLoading.value = true
  try {
    const res = await adminApi.activityEvents({
      page: eventPage.value,
      size: eventSize.value,
      action: eventAction.value || undefined,
      keyword: eventKeyword.value || undefined,
    })
    // 生成稳定的行 key：同一秒的多条行为不能互相覆盖
    events.value = res.records.map((item, index) => ({
      ...item,
      eventKey: `${item.createdAt}-${item.userId || 'anon'}-${item.action}-${eventPage.value}-${index}`,
    }) as ActivityEvent & { eventKey: string })
    eventTotal.value = res.total
  } finally {
    eventLoading.value = false
  }
}

function reloadEvents() {
  eventPage.value = 1
  loadEvents()
}

function handleEventTableChange(p: { current?: number; pageSize?: number }) {
  eventPage.value = p.current || 1
  eventSize.value = p.pageSize || 20
  loadEvents()
}

function handleTabChange(key: string) {
  if (key === 'events' && events.value.length === 0) loadEvents()
  if (key === 'users' && users.value.length === 0) loadUsers()
}

// ── 展示工具 ──────────────────────────────────────────────────
function formatTime(value: string | null) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
}

function roleLabel(role: UserRole) {
  return role === 'RECRUITER' ? '招聘方' : role === 'ADMIN' ? '管理员' : '求职者'
}

function roleColor(role: UserRole) {
  return role === 'RECRUITER' ? 'gold' : role === 'ADMIN' ? 'red' : 'blue'
}

function actionLabel(action: string) {
  return ACTION_LABELS[action] || action
}

function actionColor(action: string) {
  if (action === 'user.register') return 'green'
  if (action === 'user.login') return 'default'
  if (action === 'hr.insight') return 'orange'
  if (action.startsWith('ai.')) return 'purple'
  if (action === 'match.evaluate') return 'cyan'
  return 'blue'
}

onMounted(loadUsers)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
</style>
