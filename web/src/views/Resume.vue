<template>
  <div class="resume-page">
    <a-page-header title="简历管理" sub-title="上传简历，获取 ATS 智能评分">
      <template #extra>
        <a-button type="primary" @click="openUpload">
          <UploadOutlined /> 上传简历
        </a-button>
      </template>
    </a-page-header>

    <!-- 简历列表：§2.2 分页包络，读 data.items -->
    <a-spin :spinning="loading">
      <a-list
        :grid="{ gutter: 16, xs: 1, sm: 1, md: 2, lg: 2, xl: 3 }"
        :data-source="resumes"
      >
        <template #renderItem="{ item }">
          <a-list-item>
            <a-card>
              <template #title>
                <div class="card-title">
                  <FileTextOutlined />
                  <a-tooltip :title="item.title">
                    <span class="card-title-text">{{ item.title || '未命名简历' }}</span>
                  </a-tooltip>
                </div>
              </template>
              <template #extra>
                <a-popconfirm title="确定删除这份简历？" @confirm="handleDelete(item.resumeId)">
                  <a-button type="text" danger size="small">
                    <DeleteOutlined />
                  </a-button>
                </a-popconfirm>
              </template>

              <div class="card-meta">
                <a-tag :color="statusColor(item.parseStatus)">{{ statusText(item.parseStatus) }}</a-tag>
                <a-tag v-if="item.atsScore !== null && item.atsScore !== undefined" color="blue">
                  ATS {{ item.atsScore }}
                </a-tag>
                <a-tag v-if="item.isPrimary" color="gold">主版本</a-tag>
                <span class="card-time">{{ item.updatedAt }}</span>
              </div>

              <a-space class="card-actions">
                <a-button
                  type="primary"
                  block
                  ghost
                  :loading="scoringId === item.resumeId"
                  @click="handleAtsScore(item.resumeId)"
                >
                  <DashboardOutlined /> ATS 评分
                </a-button>
                <a-tooltip title="查看解析全文">
                  <a-button :loading="detailId === item.resumeId" @click="handleDetail(item.resumeId)">
                    <EyeOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip title="查看版本记录">
                  <a-button @click="handleVersions(item.resumeId)">
                    <HistoryOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip title="重新分析">
                  <a-button :loading="reparsingId === item.resumeId" @click="handleReparse(item.resumeId)">
                    <RedoOutlined />
                  </a-button>
                </a-tooltip>
              </a-space>
            </a-card>
          </a-list-item>
        </template>
      </a-list>
    </a-spin>

    <a-empty v-if="!loading && resumes.length === 0" description="暂无简历，点击上方按钮上传" />

    <!-- 上传弹窗：§2.1 multipart -->
    <a-modal
      v-model:open="showUploadModal"
      title="上传简历"
      @ok="handleUpload"
      :confirm-loading="uploading"
    >
      <a-form :model="uploadForm" layout="vertical">
        <a-form-item label="简历标题（可选，默认取文件名）">
          <a-input v-model:value="uploadForm.title" placeholder="如：张三-后端-2026" />
        </a-form-item>
      </a-form>
      <a-upload-dragger
        :before-upload="() => false"
        :file-list="fileList"
        :max-count="1"
        accept=".pdf,.doc,.docx,.txt,.md"
        @change="handleFileChange"
      >
        <p class="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p class="ant-upload-text">点击或拖拽简历文件到此区域</p>
        <p class="ant-upload-hint">支持 PDF、Word、TXT、Markdown，单个文件不超过 10MB</p>
      </a-upload-dragger>
    </a-modal>

    <!-- ATS 评分：§2.4 AnalysisResult 原结构 -->
    <a-modal v-model:open="showScoreModal" title="ATS 评分报告" :footer="null" width="720px">
      <div v-if="atsResult">
        <a-row :gutter="16">
          <a-col :span="8">
            <div class="score-circle">
              <a-progress
                type="circle"
                :percent="roundScore(atsResult.overall_score)"
                :size="120"
                :stroke-color="getScoreColor(atsResult.overall_score)"
              />
              <p style="margin-top: 8px; color: #666">综合评分</p>
              <p class="score-meta">共 {{ atsResult.dimensions.length }} 个维度 / {{ atsResult.word_count }} 字</p>
            </div>
          </a-col>
          <a-col :span="16">
            <h4>维度评分</h4>
            <div v-for="dim in atsResult.dimensions" :key="dim.name" class="dimension-row">
              <div class="dimension-head">
                <a-tooltip :title="dim.feedback">
                  <span>{{ getDimensionLabel(dim.name) }}</span>
                </a-tooltip>
                <span :style="{ color: getScoreColor(dim.score) }">{{ dim.score }}</span>
              </div>
              <a-progress
                :percent="roundScore(dim.score)"
                :show-info="false"
                :stroke-color="getScoreColor(dim.score)"
                size="small"
              />
            </div>
          </a-col>
        </a-row>

        <a-divider />

        <template v-if="atsResult.skills && atsResult.skills.length">
          <h4>识别到的技能（{{ atsResult.skills.length }}）</h4>
          <div class="tag-row">
            <a-tag v-for="skill in atsResult.skills" :key="skill.name" color="blue">
              {{ skill.name }} · {{ skill.proficiency }}
            </a-tag>
          </div>
          <a-divider />
        </template>

        <h4>改进建议</h4>
        <a-list :data-source="atsResult.suggestions || []" size="small">
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

    <!-- 解析全文：§2.3 -->
    <a-modal
      v-model:open="showDetailModal"
      :title="detail?.title || '简历全文'"
      :footer="null"
      width="800px"
    >
      <a-spin :spinning="detailLoading">
        <div class="detail-body">{{ detail?.textContent || '暂无解析内容' }}</div>
      </a-spin>
    </a-modal>

    <!-- 版本记录：§2.5 -->
    <a-modal v-model:open="showVersionModal" title="版本记录" :footer="null" width="600px">
      <a-list :data-source="versions" size="small" :loading="versionLoading">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta>
              <template #title>
                v{{ item.versionNo }} · {{ item.versionId }}
                <a-tag v-if="item.atsScore !== null && item.atsScore !== undefined" color="blue">
                  ATS {{ item.atsScore }}
                </a-tag>
              </template>
              <template #description>
                {{ item.changeNote || '无备注' }} · {{ item.createdAt }}
              </template>
            </a-list-item-meta>
          </a-list-item>
        </template>
      </a-list>
      <a-empty v-if="!versionLoading && versions.length === 0" description="暂无版本记录" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { UploadChangeParam, UploadFile } from 'ant-design-vue'
import {
  DashboardOutlined,
  DeleteOutlined,
  EyeOutlined,
  FileTextOutlined,
  HistoryOutlined,
  InboxOutlined,
  RedoOutlined,
  UploadOutlined,
} from '@ant-design/icons-vue'
import { resumeApi } from '@/api'
import type { AtsScoreResult, ParseStatus, ResumeDetail, ResumeListItem, ResumeVersion } from '@/types'

const loading = ref(false)
const resumes = ref<ResumeListItem[]>([])
const showUploadModal = ref(false)
const showScoreModal = ref(false)
const showVersionModal = ref(false)
const uploading = ref(false)
const fileList = ref<UploadFile[]>([])
const selectedFile = ref<File | null>(null)
const scoringId = ref<number | null>(null)
const reparsingId = ref<number | null>(null)
const atsResult = ref<AtsScoreResult | null>(null)
const versions = ref<ResumeVersion[]>([])
const versionLoading = ref(false)
const showDetailModal = ref(false)
const detail = ref<ResumeDetail | null>(null)
const detailId = ref<number | null>(null)
const detailLoading = ref(false)

const uploadForm = reactive({ title: '' })

const dimensionLabels: Record<string, string> = {
  structure: '结构完整度',
  keywords: '关键词密度',
  quantification: '数据量化',
  duration: '时间线',
  typos: '格式规范',
  formatting: '排版质量',
  expression: '表达专业性',
}

function getDimensionLabel(name: string) {
  return dimensionLabels[name] || name
}

function statusText(status: ParseStatus | string) {
  switch (status) {
    case 'SUCCESS':
      return '已分析'
    case 'PROCESSING':
      return '分析中'
    case 'FAILED':
      return '分析失败'
    default:
      return '待分析'
  }
}

function statusColor(status: ParseStatus | string) {
  switch (status) {
    case 'SUCCESS':
      return 'green'
    case 'PROCESSING':
      return 'blue'
    case 'FAILED':
      return 'red'
    default:
      return 'default'
  }
}

function priorityColor(priority: string) {
  return priority === 'high' ? 'red' : priority === 'medium' ? 'orange' : 'blue'
}

function priorityText(priority: string) {
  return priority === 'high' ? '高' : priority === 'medium' ? '中' : '低'
}

/** 进度条只接受整数，综合分可能带小数（78.2） */
function roundScore(score: number | null | undefined) {
  return Math.round(score ?? 0)
}

function getScoreColor(score: number | null | undefined) {
  const value = score ?? 0
  if (value >= 80) return '#52c41a'
  if (value >= 60) return '#faad14'
  return '#ff4d4f'
}

function openUpload() {
  uploadForm.title = ''
  fileList.value = []
  selectedFile.value = null
  showUploadModal.value = true
}

function handleFileChange(param: UploadChangeParam<UploadFile>) {
  const file = param.file
  fileList.value = [file]
  selectedFile.value = (file.originFileObj ?? file) as unknown as File
  if (!uploadForm.title && file.name) {
    uploadForm.title = file.name
  }
}

async function handleUpload() {
  if (!selectedFile.value) {
    message.warning('请先选择文件')
    return
  }
  uploading.value = true
  try {
    await resumeApi.upload(selectedFile.value, uploadForm.title || undefined)
    message.success('上传成功，正在后台分析')
    showUploadModal.value = false
    fileList.value = []
    selectedFile.value = null
    await fetchResumes()
  } catch {
    message.error('上传失败')
  } finally {
    uploading.value = false
  }
}

async function fetchResumes() {
  loading.value = true
  try {
    const res = await resumeApi.list({ page: 1, size: 50 })
    resumes.value = res.data?.items ?? []
  } catch {
    resumes.value = []
  } finally {
    loading.value = false
  }
}

async function handleDelete(resumeId: number) {
  try {
    await resumeApi.delete(resumeId)
    message.success('已删除')
    await fetchResumes()
  } catch {
    message.error('删除失败')
  }
}

async function handleReparse(resumeId: number) {
  reparsingId.value = resumeId
  try {
    await resumeApi.reparse(resumeId)
    message.success('已重新提交分析')
    await fetchResumes()
  } catch {
    message.error('重新分析失败')
  } finally {
    reparsingId.value = null
  }
}

async function handleAtsScore(resumeId: number) {
  scoringId.value = resumeId
  try {
    const res = await resumeApi.getAtsScore(resumeId)
    atsResult.value = res.data
    showScoreModal.value = true
  } catch (err) {
    const msg = err instanceof Error ? err.message : ''
    message.error(msg || '评分失败')
  } finally {
    scoringId.value = null
  }
}

async function handleDetail(resumeId: number) {
  detailId.value = resumeId
  detailLoading.value = true
  showDetailModal.value = true
  try {
    const res = await resumeApi.getById(resumeId)
    detail.value = res.data
  } catch {
    detail.value = null
    message.error('获取简历详情失败')
  } finally {
    detailLoading.value = false
    detailId.value = null
  }
}

async function handleVersions(resumeId: number) {
  showVersionModal.value = true
  versionLoading.value = true
  versions.value = []
  try {
    const res = await resumeApi.getVersions(resumeId)
    versions.value = res.data ?? []
  } catch {
    versions.value = []
  } finally {
    versionLoading.value = false
  }
}

onMounted(fetchResumes)
</script>

<style scoped>
.resume-page {
  max-width: 1200px;
  margin: 0 auto;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.card-title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}
.card-time {
  color: #999;
  font-size: 12px;
}
.card-actions {
  width: 100%;
}
.dimension-row {
  margin-bottom: 10px;
}
.dimension-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 2px;
}
.score-circle {
  text-align: center;
}
.score-meta {
  color: #999;
  font-size: 12px;
}
.tag-row {
  margin-bottom: 8px;
}
.detail-body {
  max-height: 60vh;
  overflow: auto;
  padding: 16px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
