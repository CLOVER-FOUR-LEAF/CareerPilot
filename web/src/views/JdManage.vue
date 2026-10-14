<template>
  <div class="jd-page">
    <a-page-header title="JD 解析" sub-title="粘贴职位描述，智能提取结构化信息">
      <template #extra>
        <a-button type="primary" @click="openCreate">
          <PlusOutlined /> 新建 JD
        </a-button>
      </template>
    </a-page-header>

    <!-- JD 列表：§3.2 分页包络，读 data.items -->
    <a-spin :spinning="loading">
      <a-list :data-source="jdList">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-card style="width: 100%">
              <template #title>
                <div class="jd-head">
                  <span class="jd-title">
                    {{ item.title || '未命名 JD' }}
                    <span v-if="item.company" class="jd-company">{{ item.company }}</span>
                  </span>
                  <a-tag :color="statusColor(item.parseStatus)">{{ statusText(item.parseStatus) }}</a-tag>
                </div>
              </template>
              <template #extra>
                <a-space>
                  <a-button size="small" :loading="viewingId === item.jdId" @click="handleViewResult(item.jdId)">
                    查看结果
                  </a-button>
                  <a-tooltip title="重新解析">
                    <a-button size="small" :loading="reparsingId === item.jdId" @click="handleReparse(item.jdId)">
                      <RedoOutlined />
                    </a-button>
                  </a-tooltip>
                  <a-popconfirm title="确定删除？" @confirm="handleDelete(item.jdId)">
                    <a-button type="text" danger size="small">
                      <DeleteOutlined />
                    </a-button>
                  </a-popconfirm>
                </a-space>
              </template>

              <a-typography-paragraph
                :ellipsis="{ rows: 2 }"
                :content="item.rawTextPreview || '暂无内容'"
              />
              <p class="jd-meta">
                来源：{{ item.sourceUrl || '手动输入' }} · 创建于 {{ item.createdAt }}
              </p>
            </a-card>
          </a-list-item>
        </template>
      </a-list>
    </a-spin>

    <a-empty v-if="!loading && jdList.length === 0" description="暂无 JD，点击上方按钮新建" />

    <!-- 新建 JD：§3.1 -->
    <a-modal
      v-model:open="showCreateModal"
      title="新建 JD"
      @ok="handleCreateJd"
      :confirm-loading="creating"
      width="720px"
    >
      <a-form :model="jdForm" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="职位名称" required>
              <a-input v-model:value="jdForm.jobTitle" placeholder="如：高级后端工程师" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="公司名称">
              <a-input v-model:value="jdForm.companyName" placeholder="如：某厂" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="薪资范围">
              <a-input v-model:value="jdForm.salaryRange" placeholder="如：25-40k" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="来源类型">
              <a-select v-model:value="jdForm.sourceType" :options="sourceTypeOptions" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="来源链接">
          <a-input v-model:value="jdForm.sourceUrl" placeholder="https://..." />
        </a-form-item>
        <a-form-item label="职位描述" required>
          <a-textarea
            v-model:value="jdForm.rawText"
            placeholder="请粘贴完整的职位描述内容..."
            :rows="12"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 解析结果：§3.3 JdChunk 原结构 -->
    <a-modal v-model:open="showResultModal" title="JD 解析结果" :footer="null" width="860px">
      <div v-if="parseResult">
        <a-descriptions bordered :column="2" size="small">
          <a-descriptions-item label="职位名称">{{ parseResult.job_title || '-' }}</a-descriptions-item>
          <a-descriptions-item label="公司名称">{{ parseResult.company_name || '-' }}</a-descriptions-item>
          <a-descriptions-item label="工作地点">{{ parseResult.location || '-' }}</a-descriptions-item>
          <a-descriptions-item label="工作类型">{{ parseResult.employment_type || '-' }}</a-descriptions-item>
          <a-descriptions-item label="最低学历">{{ parseResult.education_required || '-' }}</a-descriptions-item>
          <a-descriptions-item label="经验要求">
            {{ parseResult.experience_min_years ?? '-' }} ~ {{ parseResult.experience_max_years ?? '-' }} 年
          </a-descriptions-item>
          <a-descriptions-item label="行业 / 职级">
            {{ parseResult.industry || '-' }} / {{ parseResult.seniority || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="薪资范围">
            <template v-if="parseResult.salary_min">
              {{ parseResult.salary_min }}k ~ {{ parseResult.salary_max }}k {{ parseResult.salary_currency }}
            </template>
            <template v-else>-</template>
          </a-descriptions-item>
          <a-descriptions-item label="解析得分" :span="2">
            <a-progress
              :percent="Math.round((parseResult.parse_score ?? 0) * 100)"
              :stroke-color="(parseResult.parse_score ?? 0) >= 0.7 ? '#52c41a' : '#faad14'"
              size="small"
              style="width: 240px"
            />
            <span class="parse-method">{{ parseResult.parse_method }}</span>
          </a-descriptions-item>
        </a-descriptions>

        <a-divider />

        <h4>技能要求 ({{ parseResult.skills?.length || 0 }})</h4>
        <div class="tag-row">
          <a-tag
            v-for="skill in parseResult.skills || []"
            :key="skill.name"
            :color="importanceColor(skill.importance)"
          >
            {{ skill.name }} ({{ skill.category }})
          </a-tag>
          <a-tag v-if="!parseResult.skills?.length">暂无提取到的技能</a-tag>
        </div>

        <template v-if="parseResult.responsibilities?.length">
          <h4>岗位职责</h4>
          <ul>
            <li v-for="(item, index) in parseResult.responsibilities" :key="`r-${index}`">{{ item }}</li>
          </ul>
        </template>

        <template v-if="parseResult.requirements?.length">
          <h4>任职要求</h4>
          <ul>
            <li v-for="(item, index) in parseResult.requirements" :key="`q-${index}`">{{ item }}</li>
          </ul>
        </template>

        <template v-if="parseResult.benefits?.length">
          <h4>公司福利</h4>
          <ul>
            <li v-for="(item, index) in parseResult.benefits" :key="`b-${index}`">{{ item }}</li>
          </ul>
        </template>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined, RedoOutlined } from '@ant-design/icons-vue'
import { jdApi } from '@/api'
import type { JdListItem, JdParseResult, JdSourceType } from '@/types'

const loading = ref(false)
const creating = ref(false)
const viewingId = ref<number | null>(null)
const reparsingId = ref<number | null>(null)
const jdList = ref<JdListItem[]>([])
const showCreateModal = ref(false)
const showResultModal = ref(false)
const parseResult = ref<JdParseResult | null>(null)

const sourceTypeOptions = [
  { label: '手动输入', value: 'MANUAL' },
  { label: '链接抓取', value: 'URL' },
  { label: '文件解析', value: 'FILE' },
]

const jdForm = reactive({
  jobTitle: '',
  companyName: '',
  salaryRange: '',
  rawText: '',
  sourceType: 'MANUAL' as JdSourceType,
  sourceUrl: '',
})

function statusText(status: string) {
  switch (status) {
    case 'SUCCESS':
      return '已解析'
    case 'PROCESSING':
      return '解析中'
    case 'FAILED':
      return '解析失败'
    default:
      return '待解析'
  }
}

function statusColor(status: string) {
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

function importanceColor(importance: string) {
  return importance === 'required' ? 'red' : importance === 'preferred' ? 'orange' : 'default'
}

function openCreate() {
  jdForm.jobTitle = ''
  jdForm.companyName = ''
  jdForm.salaryRange = ''
  jdForm.rawText = ''
  jdForm.sourceType = 'MANUAL'
  jdForm.sourceUrl = ''
  showCreateModal.value = true
}

async function fetchJdList() {
  loading.value = true
  try {
    const res = await jdApi.list({ page: 1, size: 50 })
    jdList.value = res.data?.items ?? []
  } catch {
    jdList.value = []
  } finally {
    loading.value = false
  }
}

async function handleCreateJd() {
  if (!jdForm.rawText.trim()) {
    message.warning('请输入职位描述')
    return
  }
  creating.value = true
  try {
    // §3.1 请求体字段
    await jdApi.upload({
      jobTitle: jdForm.jobTitle || jdForm.rawText.slice(0, 20),
      companyName: jdForm.companyName || null,
      rawText: jdForm.rawText,
      sourceType: jdForm.sourceType,
      sourceUrl: jdForm.sourceUrl || null,
      salaryRange: jdForm.salaryRange || null,
    })
    message.success('创建成功，正在后台解析')
    showCreateModal.value = false
    await fetchJdList()
  } catch {
    message.error('创建失败')
  } finally {
    creating.value = false
  }
}

async function handleDelete(jdId: number) {
  try {
    await jdApi.delete(jdId)
    message.success('已删除')
    await fetchJdList()
  } catch {
    message.error('删除失败')
  }
}

async function handleReparse(jdId: number) {
  reparsingId.value = jdId
  try {
    await jdApi.reparse(jdId)
    message.success('已重新提交解析')
    await fetchJdList()
  } catch {
    message.error('重新解析失败')
  } finally {
    reparsingId.value = null
  }
}

async function handleViewResult(jdId: number) {
  viewingId.value = jdId
  try {
    const res = await jdApi.getParseResult(jdId)
    parseResult.value = res.data
    showResultModal.value = true
  } catch (err) {
    const msg = err instanceof Error ? err.message : ''
    message.error(msg || '获取解析结果失败')
  } finally {
    viewingId.value = null
  }
}

onMounted(fetchJdList)
</script>

<style scoped>
.jd-page {
  max-width: 1200px;
  margin: 0 auto;
}
.jd-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.jd-company {
  color: #999;
  font-size: 13px;
  margin-left: 8px;
}
.jd-meta {
  color: #999;
  font-size: 12px;
}
.parse-method {
  margin-left: 12px;
  color: #999;
  font-size: 12px;
}
.tag-row {
  margin-bottom: 12px;
}
</style>
