<template>
  <div class="hr-jds">
    <a-page-header title="岗位 JD" sub-title="为每个在招岗位维护结构化 JD，作为匹配与洞察的统一基准">
      <template #extra>
        <a-button type="primary" @click="openCreate">
          <PlusOutlined /> 新建岗位 JD
        </a-button>
      </template>
    </a-page-header>

    <a-alert
      type="info"
      show-icon
      message="JD 是匹配的基准，不是对外发布的职位"
      description="这里不发布职位、不接收投递。JD 仅用于计算候选人匹配分、能力覆盖与验证问题。"
      style="margin-bottom: 16px"
    />

    <a-card :bordered="false">
      <div class="toolbar">
        <a-input-search
          v-model:value="keyword"
          placeholder="搜索岗位 / 公司"
          allow-clear
          style="width: 280px"
          @search="reload"
        />
        <a-button @click="load">刷新</a-button>
        <span v-if="hasPending" class="hint"><LoadingOutlined /> 有 JD 正在解析，列表会自动刷新</span>
      </div>

      <a-table
        :loading="loading"
        :data-source="jds"
        :columns="columns"
        row-key="jdId"
        size="middle"
        :pagination="pagination"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <div class="jd-title">{{ record.title || '未命名 JD' }}</div>
            <div class="jd-company">{{ record.company || '未填写公司' }}</div>
          </template>
          <template v-else-if="column.key === 'preview'">
            <a-typography-paragraph
              :ellipsis="{ rows: 2 }"
              :content="record.rawTextPreview || '暂无内容'"
              class="preview"
            />
          </template>
          <template v-else-if="column.key === 'parseStatus'">
            <a-tag :color="statusColor(record.parseStatus)">{{ statusText(record.parseStatus) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space :size="4">
              <a-button size="small" :loading="viewingId === record.jdId" @click="openResult(record.jdId)">
                解析结果
              </a-button>
              <a-button size="small" :loading="reparsingId === record.jdId" @click="handleReparse(record.jdId)">
                重新解析
              </a-button>
              <a-popconfirm title="确定删除这个 JD？" @confirm="handleDelete(record.jdId)">
                <a-button type="text" danger size="small">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 新建 -->
    <a-modal v-model:open="showCreate" title="新建岗位 JD" :confirm-loading="creating" width="760px" @ok="handleCreate">
      <a-form :model="form" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="岗位名称" required>
              <a-input v-model:value="form.jobTitle" placeholder="如：高级后端工程师" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="公司 / 团队">
              <a-input v-model:value="form.companyName" placeholder="如：交易平台组" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="薪资范围">
              <a-input v-model:value="form.salaryRange" placeholder="如：25-40k" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="来源类型">
              <a-select v-model:value="form.sourceType" :options="sourceTypeOptions" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="岗位描述" required>
          <a-textarea
            v-model:value="form.rawText"
            :rows="12"
            placeholder="粘贴完整 JD：职责、硬性要求、加分项、工作地点等"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 解析结果 -->
    <a-modal v-model:open="showResult" title="JD 解析结果" :footer="null" width="820px">
      <div v-if="parseResult">
        <a-descriptions bordered :column="2" size="small">
          <a-descriptions-item label="岗位">{{ parseResult.job_title || '-' }}</a-descriptions-item>
          <a-descriptions-item label="公司">{{ parseResult.company_name || '-' }}</a-descriptions-item>
          <a-descriptions-item label="地点">{{ parseResult.location || '-' }}</a-descriptions-item>
          <a-descriptions-item label="学历">{{ parseResult.education_required || '-' }}</a-descriptions-item>
          <a-descriptions-item label="经验">
            {{ parseResult.experience_min_years ?? '-' }} ~ {{ parseResult.experience_max_years ?? '-' }} 年
          </a-descriptions-item>
          <a-descriptions-item label="职级 / 行业">
            {{ parseResult.seniority || '-' }} / {{ parseResult.industry || '-' }}
          </a-descriptions-item>
        </a-descriptions>

        <a-divider />
        <h4>硬技能（{{ parseResult.skills?.length || 0 }}）</h4>
        <div class="tag-row">
          <a-tag
            v-for="skill in parseResult.skills || []"
            :key="skill.name"
            :color="importanceColor(skill.importance)"
          >
            {{ skill.name }} · {{ skill.category }}
          </a-tag>
          <span v-if="!parseResult.skills?.length" class="muted">未提取到技能</span>
        </div>

        <template v-if="parseResult.responsibilities?.length">
          <h4>岗位职责</h4>
          <ul><li v-for="(item, i) in parseResult.responsibilities" :key="`r-${i}`">{{ item }}</li></ul>
        </template>
        <template v-if="parseResult.requirements?.length">
          <h4>任职要求</h4>
          <ul><li v-for="(item, i) in parseResult.requirements" :key="`q-${i}`">{{ item }}</li></ul>
        </template>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { LoadingOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { jdApi } from '@/api'
import type { JdListItem, JdParseResult, JdSourceType } from '@/types'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const jds = ref<JdListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

const showCreate = ref(false)
const creating = ref(false)
const showResult = ref(false)
const viewingId = ref<number | null>(null)
const reparsingId = ref<number | null>(null)
const parseResult = ref<JdParseResult | null>(null)

const form = reactive({
  jobTitle: '',
  companyName: '',
  salaryRange: '',
  rawText: '',
  sourceType: 'MANUAL' as JdSourceType,
  sourceUrl: '',
})

const sourceTypeOptions = [
  { label: '手动输入', value: 'MANUAL' },
  { label: '链接抓取', value: 'URL' },
  { label: '文件解析', value: 'FILE' },
]

const columns = [
  { title: '岗位', key: 'title', width: 200 },
  { title: 'JD 摘要', key: 'preview' },
  { title: '解析状态', key: 'parseStatus', width: 110 },
  { title: '创建时间', key: 'createdAt', width: 160 },
  { title: '操作', key: 'actions', width: 250 },
]

const pagination = computed(() => ({
  current: page.value,
  pageSize: size.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 个 JD`,
}))

const hasPending = computed(() =>
  jds.value.some((j) => j.parseStatus === 'PENDING' || j.parseStatus === 'PROCESSING'),
)

let pollTimer: number | undefined

async function load() {
  loading.value = true
  try {
    const res = await jdApi.list({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    jds.value = res.data?.items ?? []
    total.value = res.data?.total ?? 0
    schedulePoll()
  } finally {
    loading.value = false
  }
}

function schedulePoll() {
  if (pollTimer) {
    window.clearTimeout(pollTimer)
    pollTimer = undefined
  }
  if (!hasPending.value) return
  pollTimer = window.setTimeout(async () => {
    try {
      const res = await jdApi.list({ page: page.value, size: size.value, keyword: keyword.value || undefined })
      jds.value = res.data?.items ?? []
      total.value = res.data?.total ?? 0
      schedulePoll()
    } catch {
      schedulePoll()
    }
  }, 3000)
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

function openCreate() {
  form.jobTitle = ''
  form.companyName = ''
  form.salaryRange = ''
  form.rawText = ''
  form.sourceType = 'MANUAL'
  form.sourceUrl = ''
  showCreate.value = true
}

async function handleCreate() {
  if (!form.jobTitle.trim() || !form.rawText.trim()) {
    message.warning('岗位名称和岗位描述不能为空')
    return
  }
  creating.value = true
  try {
    await jdApi.upload({
      jobTitle: form.jobTitle.trim(),
      companyName: form.companyName.trim() || null,
      rawText: form.rawText,
      sourceType: form.sourceType,
      sourceUrl: form.sourceUrl || null,
      salaryRange: form.salaryRange || null,
    })
    message.success('JD 已创建，解析正在后台进行')
    showCreate.value = false
    await load()
  } finally {
    creating.value = false
  }
}

async function openResult(id: number) {
  viewingId.value = id
  try {
    const res = await jdApi.getParseResult(id)
    parseResult.value = res.data
    showResult.value = true
  } catch {
    message.info('这个 JD 还没完成解析，请稍后再试')
  } finally {
    viewingId.value = null
  }
}

async function handleReparse(id: number) {
  reparsingId.value = id
  try {
    await jdApi.reparse(id)
    message.success('已重新提交解析')
    await load()
  } finally {
    reparsingId.value = null
  }
}

async function handleDelete(id: number) {
  await jdApi.delete(id)
  message.success('已删除')
  await load()
}

function formatTime(value: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

function statusText(status: string) {
  if (status === 'SUCCESS') return '已解析'
  if (status === 'PROCESSING') return '解析中'
  if (status === 'FAILED') return '解析失败'
  return '待解析'
}

function statusColor(status: string) {
  if (status === 'SUCCESS') return 'green'
  if (status === 'PROCESSING') return 'blue'
  if (status === 'FAILED') return 'red'
  return 'default'
}

function importanceColor(importance: string) {
  if (importance === 'must') return 'red'
  if (importance === 'should') return 'orange'
  return 'blue'
}

onMounted(async () => {
  await load()
  if (route.query.create === '1') {
    openCreate()
    router.replace({ path: '/hr/jds' })
  }
})

onBeforeUnmount(() => {
  if (pollTimer) window.clearTimeout(pollTimer)
})
</script>

<style scoped>
.hr-jds {
  max-width: 1280px;
  margin: 0 auto;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.hint {
  color: #1677ff;
  font-size: 12px;
}
.jd-title {
  font-weight: 500;
}
.jd-company {
  color: #999;
  font-size: 12px;
  margin-top: 2px;
}
.preview {
  margin: 0;
  color: #666;
  font-size: 13px;
}
.muted {
  color: #999;
}
.tag-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
