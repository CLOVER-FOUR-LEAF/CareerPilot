<template>
  <div class="hr-resumes">
    <a-page-header title="候选人简历" sub-title="导入你已收到的候选人简历，系统只做解析、评分与分析">
      <template #extra>
        <a-button type="primary" @click="openUpload">
          <UploadOutlined /> 导入候选人简历
        </a-button>
      </template>
    </a-page-header>

    <a-alert
      type="info"
      show-icon
      message="这里只展示你自己导入的简历"
      description="系统不做候选人投递、站内沟通或全站人才库检索；简历归属按当前账号隔离，其他 HR 看不到你的候选人。"
      style="margin-bottom: 16px"
    />

    <a-card :bordered="false">
      <div class="toolbar">
        <a-input-search
          v-model:value="keyword"
          placeholder="搜索候选人 / 简历标题"
          allow-clear
          style="width: 300px"
          @search="reload"
        />
        <a-button @click="load">刷新</a-button>
        <span class="hint">
          <LoadingOutlined v-if="hasPending" /> {{ hasPending ? '有简历正在解析，列表会自动刷新' : '' }}
        </span>
      </div>

      <a-table
        :loading="loading"
        :data-source="resumes"
        :columns="columns"
        row-key="resumeId"
        size="middle"
        :pagination="pagination"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <div class="title-cell">
              <a-avatar class="candidate-avatar" :style="{ background: avatarColor(record.title) }">
                {{ (record.title || '候').charAt(0) }}
              </a-avatar>
              <div>
                <div class="candidate-name">{{ record.title || '未命名简历' }}</div>
                <div class="candidate-sub">{{ record.resumeBizId }}</div>
              </div>
            </div>
          </template>
          <template v-else-if="column.key === 'parseStatus'">
            <a-tag :color="statusColor(record.parseStatus)">{{ statusText(record.parseStatus) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'atsScore'">
            <a-tag v-if="record.atsScore !== null" color="blue">ATS {{ record.atsScore }}</a-tag>
            <span v-else class="muted">—</span>
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space :size="4">
              <a-button size="small" @click="openDetail(record.resumeId)">全文</a-button>
              <a-button
                size="small"
                :loading="atsLoadingId === record.resumeId"
                @click="openAts(record.resumeId)"
              >
                ATS 报告
              </a-button>
              <a-button size="small" :loading="reparsingId === record.resumeId" @click="handleReparse(record.resumeId)">
                重新分析
              </a-button>
              <a-popconfirm title="确定删除这份候选人简历？" @confirm="handleDelete(record.resumeId)">
                <a-button type="text" danger size="small">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 上传 -->
    <a-modal
      v-model:open="showUpload"
      title="导入候选人简历"
      :confirm-loading="uploading"
      @ok="handleUpload"
    >
      <a-form layout="vertical">
        <a-form-item label="候选人标识">
          <a-input v-model:value="uploadTitle" placeholder="如：张三-后端-5年" />
          <div class="form-hint">建议用「姓名-岗位-年限」，便于后续在匹配结果中辨认。</div>
        </a-form-item>
      </a-form>
      <a-upload-dragger
        :before-upload="() => false"
        :file-list="fileList"
        :max-count="1"
        accept=".pdf,.doc,.docx,.txt,.md"
        @change="handleFileChange"
      >
        <p class="ant-upload-drag-icon"><InboxOutlined /></p>
        <p class="ant-upload-text">点击或拖拽候选人简历到此区域</p>
        <p class="ant-upload-hint">支持 PDF、Word、TXT、Markdown，单个文件不超过 10MB</p>
      </a-upload-dragger>
    </a-modal>

    <!-- 全文 -->
    <a-modal v-model:open="showDetail" :title="detail?.title || '简历全文'" :footer="null" width="820px">
      <a-spin :spinning="detailLoading">
        <div class="detail-body">{{ detail?.textContent || '暂无解析内容' }}</div>
      </a-spin>
    </a-modal>

    <!-- ATS -->
    <a-modal v-model:open="showAts" title="候选人 ATS 报告" :footer="null" width="760px">
      <div v-if="atsResult">
        <a-row :gutter="20" align="middle">
          <a-col :span="8" class="ats-score">
            <a-progress
              type="circle"
              :percent="Math.round(atsResult.overall_score)"
              :size="126"
              :stroke-color="scoreColor(atsResult.overall_score)"
            />
            <p class="ats-label">综合评分</p>
            <p class="muted">{{ atsResult.word_count }} 字 · {{ atsResult.section_count }} 个段落</p>
          </a-col>
          <a-col :span="16">
            <div v-for="dim in atsResult.dimensions" :key="dim.name" class="dimension-row">
              <div class="dimension-head">
                <a-tooltip :title="dim.feedback">
                  <span>{{ dimensionLabel(dim.name) }}</span>
                </a-tooltip>
                <span :style="{ color: scoreColor(dim.score) }">{{ dim.score }}</span>
              </div>
              <a-progress :percent="Math.round(dim.score)" :show-info="false" size="small" :stroke-color="scoreColor(dim.score)" />
            </div>
          </a-col>
        </a-row>

        <a-divider />
        <h4>识别到的技能</h4>
        <div class="tag-row">
          <a-tag v-for="skill in atsResult.skills" :key="skill.name" color="blue">
            {{ skill.name }} · {{ skill.proficiency }}
          </a-tag>
          <span v-if="!atsResult.skills.length" class="muted">未识别到技能</span>
        </div>

        <a-divider />
        <h4>可改进项</h4>
        <a-list :data-source="atsResult.suggestions" size="small">
          <template #renderItem="{ item }">
            <a-list-item>
              <a-list-item-meta>
                <template #title>
                  <a-tag :color="priorityColor(item.priority)">{{ priorityText(item.priority) }}</a-tag>
                  {{ item.title }}
                </template>
                <template #description>{{ item.detail }}</template>
              </a-list-item-meta>
            </a-list-item>
          </template>
        </a-list>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { UploadChangeParam, UploadFile } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  InboxOutlined,
  LoadingOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue'
import { resumeApi } from '@/api'
import type { AtsScoreResult, ResumeDetail, ResumeListItem } from '@/types'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const resumes = ref<ResumeListItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

const showUpload = ref(false)
const uploading = ref(false)
const uploadTitle = ref('')
const fileList = ref<UploadFile[]>([])

const showDetail = ref(false)
const detailLoading = ref(false)
const detail = ref<ResumeDetail | null>(null)

const showAts = ref(false)
const atsLoadingId = ref<number | null>(null)
const atsResult = ref<AtsScoreResult | null>(null)

const reparsingId = ref<number | null>(null)

const columns = [
  { title: '候选人', key: 'title', width: 260 },
  { title: '解析状态', key: 'parseStatus', width: 110 },
  { title: 'ATS', key: 'atsScore', width: 100 },
  { title: '更新时间', key: 'updatedAt', width: 160 },
  { title: '操作', key: 'actions', width: 310 },
]

const pagination = computed(() => ({
  current: page.value,
  pageSize: size.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 份简历`,
}))

const hasPending = computed(() =>
  resumes.value.some((r) => r.parseStatus === 'PENDING' || r.parseStatus === 'PROCESSING'),
)

let pollTimer: number | undefined

async function load() {
  loading.value = true
  try {
    const res = await resumeApi.list({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    resumes.value = res.data?.items ?? []
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
    // 解析中不弹 loading，避免页面一直闪
    try {
      const res = await resumeApi.list({ page: page.value, size: size.value, keyword: keyword.value || undefined })
      resumes.value = res.data?.items ?? []
      total.value = res.data?.total ?? 0
      schedulePoll()
    } catch {
      // 下一轮再试
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

function openUpload() {
  uploadTitle.value = ''
  fileList.value = []
  showUpload.value = true
}

function handleFileChange(info: UploadChangeParam) {
  fileList.value = info.fileList.slice(-1)
}

async function handleUpload() {
  const file = fileList.value[0]?.originFileObj as File | undefined
  if (!file) {
    message.warning('请选择一份简历文件')
    return
  }
  uploading.value = true
  try {
    await resumeApi.upload(file, uploadTitle.value || file.name)
    message.success('已导入，解析与评分正在后台进行')
    showUpload.value = false
    await load()
  } finally {
    uploading.value = false
  }
}

async function openDetail(id: number) {
  showDetail.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await resumeApi.getById(id)
    detail.value = res.data
  } finally {
    detailLoading.value = false
  }
}

async function openAts(id: number) {
  atsLoadingId.value = id
  try {
    const res = await resumeApi.getAtsScore(id)
    atsResult.value = res.data
    showAts.value = true
  } catch {
    message.info('这份简历还没完成 ATS 分析，请稍后再试')
  } finally {
    atsLoadingId.value = null
  }
}

async function handleReparse(id: number) {
  reparsingId.value = id
  try {
    await resumeApi.reparse(id)
    message.success('已重新提交分析')
    await load()
  } finally {
    reparsingId.value = null
  }
}

async function handleDelete(id: number) {
  await resumeApi.delete(id)
  message.success('已删除')
  await load()
}

// ── 展示工具 ──────────────────────────────────────────────────
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

function avatarColor(title: string) {
  const palette = ['#1677ff', '#722ed1', '#13c2c2', '#fa8c16', '#eb2f96']
  let sum = 0
  for (const ch of title || '候') sum += ch.charCodeAt(0)
  return palette[sum % palette.length]
}

function scoreColor(score: number) {
  if (score >= 80) return '#52c41a'
  if (score >= 60) return '#faad14'
  return '#ff4d4f'
}

const DIMENSION_LABELS: Record<string, string> = {
  completeness: '信息完整度',
  keyword_match: '关键词匹配',
  quantified_results: '量化结果',
  format_readability: '格式可读性',
  skill_coverage: '技能覆盖',
  experience_relevance: '经验相关性',
}

function dimensionLabel(name: string) {
  return DIMENSION_LABELS[name] || name
}

function priorityText(priority: string) {
  if (priority === 'high') return '高'
  if (priority === 'medium') return '中'
  return '低'
}

function priorityColor(priority: string) {
  if (priority === 'high') return 'red'
  if (priority === 'medium') return 'orange'
  return 'blue'
}

onMounted(async () => {
  await load()
  if (route.query.upload === '1') {
    openUpload()
    router.replace({ path: '/hr/resumes' })
  }
})

onBeforeUnmount(() => {
  if (pollTimer) window.clearTimeout(pollTimer)
})
</script>

<style scoped>
.hr-resumes {
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
.title-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.candidate-avatar {
  flex: none;
}
.candidate-name {
  font-weight: 500;
}
.candidate-sub {
  color: #999;
  font-size: 11px;
  font-family: 'SFMono-Regular', Consolas, monospace;
}
.muted {
  color: #999;
}
.form-hint {
  margin-top: 4px;
  color: #999;
  font-size: 12px;
}
.detail-body {
  white-space: pre-wrap;
  line-height: 1.9;
  max-height: 60vh;
  overflow: auto;
  background: #fafafa;
  padding: 16px;
  border-radius: 8px;
}
.ats-score {
  text-align: center;
}
.ats-label {
  margin: 10px 0 2px;
  color: #666;
}
.dimension-row {
  margin-bottom: 8px;
}
.dimension-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #595959;
}
.tag-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
