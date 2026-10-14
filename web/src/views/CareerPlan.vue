<template>
  <div class="career-plan-page">
    <a-page-header title="职业规划" sub-title="基于当前岗位与目标行业，生成带里程碑的成长路线图" />

    <a-row :gutter="16">
      <a-col :xs="24" :lg="9">
        <a-card title="规划输入" class="panel">
          <a-form :model="form" layout="vertical">
            <a-form-item label="当前职位" required>
              <a-input v-model:value="form.currentTitle" placeholder="如：后端工程师" />
            </a-form-item>
            <a-form-item label="工作年限" required>
              <a-input-number v-model:value="form.yearsOfExperience" :min="0" :max="40" style="width: 100%" />
            </a-form-item>
            <a-form-item label="目标行业" required>
              <a-select
                v-model:value="form.targetIndustry"
                :options="industryOptions"
                placeholder="选择目标行业"
                show-search
                option-filter-prop="label"
              />
            </a-form-item>
            <a-form-item label="目标岗位" required>
              <a-input v-model:value="form.targetPosition" placeholder="如：高级后端工程师" />
            </a-form-item>
            <a-button type="primary" block :loading="polling.running.value" @click="handleSubmit">
              <ThunderboltOutlined /> 生成规划
            </a-button>
          </a-form>

          <div v-if="polling.running.value" class="task-status">
            <a-progress :percent="polling.progress.value" size="small" />
            <p class="hint">状态：{{ polling.status.value }}</p>
          </div>
          <a-alert
            v-else-if="polling.errorMessage.value"
            type="error"
            show-icon
            :message="polling.errorMessage.value"
          />
        </a-card>
      </a-col>

      <a-col :xs="24" :lg="15">
        <a-card title="规划结果" class="panel">
          <template v-if="result">
            <h4>总体建议</h4>
            <div class="summary">{{ result.summary || '—' }}</div>

            <a-divider style="margin: 16px 0" />

            <h4>成长路线图</h4>
            <a-timeline class="roadmap">
              <a-timeline-item
                v-for="(phase, index) in result.roadmap || []"
                :key="index"
                :color="index === 0 ? 'blue' : 'gray'"
              >
                <div class="phase-head">
                  <strong>{{ phase.phase }}</strong>
                  <span class="phase-goal">{{ phase.goal }}</span>
                </div>
                <ul class="action-list">
                  <li v-for="(action, actionIndex) in phase.actions || []" :key="actionIndex">
                    {{ action }}
                  </li>
                </ul>
                <div v-if="phase.milestone" class="milestone">
                  <FlagOutlined /> 里程碑：{{ phase.milestone }}
                </div>
                <div v-if="phase.doc_ids?.length" class="doc-row">
                  <a-tag
                    v-for="docId in phase.doc_ids"
                    :key="docId"
                    color="geekblue"
                    class="doc-chip"
                    @click="scrollToCitation(docId)"
                  >
                    <FileTextOutlined /> {{ docId }}
                  </a-tag>
                </div>
              </a-timeline-item>
            </a-timeline>

            <template v-if="result.skill_gap?.length">
              <a-divider style="margin: 16px 0" />
              <h4>待补齐技能</h4>
              <div class="tag-row">
                <a-tag v-for="gap in result.skill_gap" :key="gap" color="orange">{{ gap }}</a-tag>
              </div>
            </template>

            <template v-if="result.citations?.length">
              <a-divider style="margin: 16px 0" />
              <h4>引用知识库</h4>
              <a-list :data-source="result.citations" size="small" bordered>
                <template #renderItem="{ item }">
                  <a-list-item :id="citationId(item.doc_id)">
                    <a-list-item-meta>
                      <template #title>
                        <a-tag color="geekblue" class="doc-chip">{{ item.doc_id }}</a-tag>
                        {{ item.source }}
                      </template>
                      <template #description>版本：{{ item.version }}</template>
                    </a-list-item-meta>
                  </a-list-item>
                </template>
              </a-list>
            </template>
          </template>
          <a-empty v-else description="填写左侧信息后点击「生成规划」" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue'
import { message } from 'ant-design-vue'
import { FileTextOutlined, FlagOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { useTaskPolling } from '@/composables/useTaskPolling'
import type { CareerPlanResult } from '@/types'

const form = reactive({
  currentTitle: '',
  yearsOfExperience: 3,
  targetIndustry: '',
  targetPosition: '',
})

const industryOptions = [
  { label: '互联网', value: '互联网' },
  { label: '金融科技', value: '金融科技' },
  { label: '人工智能', value: '人工智能' },
  { label: '企业服务', value: '企业服务' },
  { label: '电商零售', value: '电商零售' },
  { label: '智能制造', value: '智能制造' },
]

const polling = useTaskPolling<CareerPlanResult>()
const result = computed<CareerPlanResult | null>(() => polling.output.value)

function citationId(docId: string) {
  return `citation-${docId}`
}

function scrollToCitation(docId: string) {
  const el = document.getElementById(citationId(docId))
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    message.info(`引用来源：${docId}`)
  } else {
    message.info(`引用来源：${docId}`)
  }
}

async function handleSubmit() {
  if (!form.currentTitle.trim() || !form.targetPosition.trim() || !form.targetIndustry) {
    message.warning('请填写当前职位、目标行业与目标岗位')
    return
  }
  const task = await polling.submit({
    taskType: 'career_plan',
    input: {
      current_title: form.currentTitle,
      years_of_experience: form.yearsOfExperience,
      target_industry: form.targetIndustry,
      target_position: form.targetPosition,
    },
  })

  if (task?.status === 'SUCCESS' && polling.output.value) {
    message.success('职业规划已生成')
  } else if (task && task.status !== 'SUCCESS') {
    message.error(polling.errorMessage.value || '生成失败')
  }
}

onMounted(() => {
  form.targetIndustry = '互联网'
})
</script>

<style scoped>
.career-plan-page {
  max-width: 1200px;
  margin: 0 auto;
}
.panel {
  height: 100%;
}
.summary {
  padding: 16px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 6px;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-word;
}
.roadmap {
  margin-top: 12px;
}
.phase-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px;
}
.phase-goal {
  color: #666;
}
.action-list {
  margin: 6px 0 0;
  padding-left: 20px;
  color: #555;
}
.milestone {
  margin-top: 6px;
  color: #fa8c16;
}
.doc-row {
  margin-top: 8px;
}
.doc-chip {
  cursor: pointer;
}
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.task-status .hint {
  margin: 6px 0 0;
  color: #999;
  font-size: 12px;
}
</style>
