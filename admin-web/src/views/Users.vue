<template>
  <a-card :bordered="false" title="用户与角色">
    <template #extra>
      <a-space>
        <a-input-search
          v-model:value="keyword"
          placeholder="搜索邮箱 / 昵称 / userId"
          allow-clear
          style="width: 260px"
          @search="reload"
        />
        <a-select
          v-model:value="roleFilter"
          placeholder="全部角色"
          allow-clear
          style="width: 140px"
          :options="roleOptions"
          @change="reload"
        />
        <a-button @click="reload">刷新</a-button>
      </a-space>
    </template>

    <a-alert
      type="info"
      show-icon
      message="角色是授权维度：把求职者提升为 RECRUITER 后，原账号的求职数据仍然保留，同时获得 HR 工作台权限。"
      style="margin-bottom: 16px"
    />

    <a-table
      :loading="loading"
      :data-source="rows"
      :columns="columns"
      row-key="id"
      size="middle"
      :pagination="pagination"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'role'">
          <a-select
            :value="record.role"
            :options="roleOptions"
            size="small"
            style="width: 170px"
            :loading="updatingId === record.id"
            @change="(value: unknown) => handleRoleChange(record, value as UserRole)"
          />
        </template>
        <template v-else-if="column.key === 'status'">
          <a-switch
            :checked="record.status === 1"
            checked-children="正常"
            un-checked-children="封禁"
            :loading="updatingId === record.id"
            @change="(checked: unknown) => handleStatusChange(record, checked as boolean)"
          />
        </template>
        <template v-else-if="column.key === 'createdAt'">
          {{ formatTime(record.createdAt) }}
        </template>
        <template v-else-if="column.key === 'lastLoginAt'">
          {{ formatTime(record.lastLoginAt) }}
        </template>
        <template v-else-if="column.key === 'membershipLevel'">
          <a-tag v-if="record.membershipLevel === null" color="default">FREE</a-tag>
          <a-tag v-else :color="record.membershipLevel > 0 ? 'gold' : 'default'">
            {{ record.membershipLevel === 0 ? 'FREE' : record.membershipLevel === 1 ? 'PRO' : 'ENTERPRISE' }}
          </a-tag>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import dayjs from 'dayjs'
import { adminApi } from '@/api'
import type { AdminUser, UserRole } from '@/types'

const loading = ref(false)
const updatingId = ref<number | null>(null)
const rows = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const roleFilter = ref<string | undefined>(undefined)

const roleOptions = [
  { label: '求职者 USER', value: 'USER' },
  { label: '招聘方 RECRUITER', value: 'RECRUITER' },
  { label: '管理员 ADMIN', value: 'ADMIN' },
]

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: 'userId', dataIndex: 'userId', key: 'userId', ellipsis: true },
  { title: '邮箱', dataIndex: 'email', key: 'email', ellipsis: true },
  { title: '昵称', dataIndex: 'nickname', key: 'nickname' },
  { title: '角色', key: 'role', width: 190 },
  { title: '状态', key: 'status', width: 110 },
  { title: '会员', key: 'membershipLevel', width: 110 },
  { title: '注册时间', key: 'createdAt', width: 170 },
  { title: '最后登录', key: 'lastLoginAt', width: 170 },
]

const pagination = computed(() => ({
  current: page.value,
  pageSize: size.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`,
}))

function formatTime(value: string | null) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

async function load() {
  loading.value = true
  try {
    const res = await adminApi.users({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      role: roleFilter.value || undefined,
    })
    rows.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

function handleTableChange(p: { current?: number; pageSize?: number }) {
  page.value = p.current || 1
  size.value = p.pageSize || 20
  load()
}

function handleRoleChange(record: AdminUser, role: UserRole) {
  if (role === record.role) return
  Modal.confirm({
    title: `修改 ${record.email} 的角色`,
    content: `将角色从 ${record.role} 改为 ${role}。角色变更后，该用户需要重新登录才会生效。`,
    okText: '确认修改',
    cancelText: '取消',
    onOk: async () => {
      updatingId.value = record.id
      try {
        await adminApi.updateRole(record.id, role)
        message.success('角色已更新')
        await load()
      } finally {
        updatingId.value = null
      }
    },
  })
}

function handleStatusChange(record: AdminUser, checked: boolean) {
  const status = checked ? 1 : 0
  Modal.confirm({
    title: checked ? '解封账号' : '封禁账号',
    content: `确定要${checked ? '解封' : '封禁'} ${record.email} 吗？封禁后该账号无法登录。`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      updatingId.value = record.id
      try {
        await adminApi.updateStatus(record.id, status)
        message.success('状态已更新')
        await load()
      } finally {
        updatingId.value = null
      }
    },
  })
}

onMounted(load)
</script>
