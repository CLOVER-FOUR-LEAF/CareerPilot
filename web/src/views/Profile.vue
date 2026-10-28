<template>
  <div class="profile-page">
    <a-page-header
      title="个人中心"
      :sub-title="userStore.canAccessHr ? '维护招聘方账号资料' : '完善求职档案，让 AI 建议更贴合你的目标'"
    />

    <a-tabs v-model:active-key="activeTab">
      <!-- 基本资料：§1.5 -->
      <a-tab-pane key="profile" tab="基本资料">
        <a-card :loading="loading">
          <a-form ref="formRef" :model="form" layout="vertical">
            <a-row :gutter="16">
              <a-col :xs="24" :md="12">
                <a-form-item label="昵称">
                  <a-input v-model:value="form.nickname" placeholder="怎么称呼你" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="邮箱">
                  <a-input :value="form.email" disabled />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="手机号">
                  <a-input v-model:value="form.phone" placeholder="用于面试提醒" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="性别">
                  <a-select v-model:value="form.gender" :options="genderOptions" allow-clear />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="出生年份">
                  <a-input-number v-model:value="form.birthYear" :min="1950" :max="2015" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="所在城市">
                  <a-input v-model:value="form.city" placeholder="如：杭州" />
                </a-form-item>
              </a-col>

              <a-col :span="24"><a-divider orientation="left" style="margin: 8px 0 16px">当前情况</a-divider></a-col>

              <a-col :xs="24" :md="12">
                <a-form-item label="当前职位">
                  <a-input v-model:value="form.currentTitle" placeholder="如：后端工程师" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="工作年限">
                  <a-input-number v-model:value="form.yearsOfExperience" :min="0" :max="40" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="最高学历">
                  <a-select v-model:value="form.educationLevel" :options="educationOptions" allow-clear />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="毕业年份">
                  <a-input-number v-model:value="form.graduationYear" :min="1980" :max="2035" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="学校">
                  <a-input v-model:value="form.school" placeholder="如：XX大学" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="专业">
                  <a-input v-model:value="form.major" placeholder="如：软件工程" />
                </a-form-item>
              </a-col>

              <a-col :span="24"><a-divider orientation="left" style="margin: 8px 0 16px">求职目标</a-divider></a-col>

              <a-col :xs="24" :md="12">
                <a-form-item label="目标行业">
                  <a-input v-model:value="form.targetIndustry" placeholder="如：互联网" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="目标岗位">
                  <a-input v-model:value="form.targetPosition" placeholder="如：高级后端工程师" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="目标职级">
                  <a-select v-model:value="form.targetSeniority" :options="seniorityOptions" allow-clear />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="意向城市">
                  <a-select
                    v-model:value="form.targetCity"
                    mode="tags"
                    :options="cityOptions"
                    placeholder="输入城市后回车，可多选"
                  />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="期望薪资下限（K）">
                  <a-input-number v-model:value="form.targetSalaryMin" :min="0" :max="999" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-form-item label="期望薪资上限（K）">
                  <a-input-number v-model:value="form.targetSalaryMax" :min="0" :max="999" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="24">
                <a-form-item label="技能标签">
                  <a-select
                    v-model:value="form.skills"
                    mode="tags"
                    placeholder="如：Java、Kafka，输入后回车"
                  />
                </a-form-item>
              </a-col>
            </a-row>

            <a-space>
              <a-button type="primary" :loading="saving" @click="handleSave">
                <SaveOutlined /> 保存修改
              </a-button>
              <a-button @click="loadProfile">重置</a-button>
            </a-space>
          </a-form>
        </a-card>
      </a-tab-pane>

      <!-- 会员权益：§1.6（仅求职者展示） -->
      <a-tab-pane v-if="userStore.isSeeker" key="membership" tab="会员权益">
        <a-card :loading="membershipLoading">
          <template v-if="membership">
            <a-row :gutter="16">
              <a-col :xs="24" :md="12">
                <a-card size="small" class="member-card">
                  <div class="member-head">
                    <CrownOutlined class="member-icon" />
                    <div>
                      <p class="member-level">{{ membership.levelName }}</p>
                      <p class="member-code">{{ membership.level }}</p>
                    </div>
                  </div>
                  <a-descriptions :column="1" size="small" bordered>
                    <a-descriptions-item label="到期时间">
                      {{ membership.expireAt || '永久有效' }}
                    </a-descriptions-item>
                    <a-descriptions-item label="自动续费">
                      {{ membership.autoRenew ? '已开启' : '未开启' }}
                    </a-descriptions-item>
                    <a-descriptions-item label="会员等级">
                      <a-tag :color="membership.level === 'FREE' ? 'default' : 'gold'">
                        {{ membership.levelName }}
                      </a-tag>
                    </a-descriptions-item>
                  </a-descriptions>
                </a-card>
              </a-col>
              <a-col :xs="24" :md="12">
                <a-card size="small" title="AI 额度">
                  <a-progress
                    type="dashboard"
                    :percent="quotaPercent"
                    :format="() => quotaText"
                  />
                  <p class="quota-hint">
                    剩余 {{ membership.aiQuotaRemaining }} 次 AI 调用，共 {{ membership.aiQuotaTotal }} 次。
                    每次简历分析、匹配、面试与求职信生成都会消耗额度。
                  </p>
                </a-card>
              </a-col>
            </a-row>
          </template>
          <a-empty v-else-if="!membershipLoading" description="暂无会员信息" />
        </a-card>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { CrownOutlined, SaveOutlined } from '@ant-design/icons-vue'
import { userApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { Membership, UpdateProfileParams, UserProfile } from '@/types'

interface ProfileForm {
  email: string
  nickname: string
  phone: string
  gender: string | null
  birthYear: number | null
  city: string
  currentTitle: string
  yearsOfExperience: number | null
  educationLevel: string | null
  school: string
  major: string
  graduationYear: number | null
  targetIndustry: string
  targetPosition: string
  targetSeniority: string | null
  targetCity: string[]
  targetSalaryMin: number | null
  targetSalaryMax: number | null
  skills: string[]
}

const route = useRoute()
const userStore = useUserStore()

const activeTab = ref<string>(
  route.query.tab === 'membership' && userStore.isSeeker ? 'membership' : 'profile',
)
const loading = ref(false)
const saving = ref(false)
const membershipLoading = ref(false)
const membership = ref<Membership | null>(null)

const formRef = ref()

const genderOptions = [
  { label: '男', value: '男' },
  { label: '女', value: '女' },
]

const educationOptions = ['大专', '本科', '硕士', '博士', '博士后'].map((item) => ({ label: item, value: item }))

const seniorityOptions = [
  { label: '实习 / 应届', value: 'junior' },
  { label: '初中级', value: 'mid' },
  { label: '中高级', value: 'senior' },
  { label: '专家 / 架构', value: 'expert' },
]

const cityOptions = ['北京', '上海', '广州', '深圳', '杭州', '成都', '南京', '武汉'].map((item) => ({
  label: item,
  value: item,
}))

const form = reactive<ProfileForm>({
  email: '',
  nickname: '',
  phone: '',
  gender: null,
  birthYear: null,
  city: '',
  currentTitle: '',
  yearsOfExperience: null,
  educationLevel: null,
  school: '',
  major: '',
  graduationYear: null,
  targetIndustry: '',
  targetPosition: '',
  targetSeniority: null,
  targetCity: [],
  targetSalaryMin: null,
  targetSalaryMax: null,
  skills: [],
})

const quotaPercent = computed(() => {
  if (!membership.value || !membership.value.aiQuotaTotal) return 0
  return Math.round((membership.value.aiQuotaRemaining / membership.value.aiQuotaTotal) * 100)
})

const quotaText = computed(
  () => `${membership.value?.aiQuotaRemaining ?? 0} / ${membership.value?.aiQuotaTotal ?? 0}`
)

watch(
  () => route.query.tab,
  (tab) => {
    if (tab === 'membership') activeTab.value = 'membership'
  }
)

function applyProfile(data: UserProfile) {
  form.email = data.email || ''
  form.nickname = data.nickname || ''
  form.phone = data.phone || ''
  form.gender = data.gender
  form.birthYear = data.birthYear
  form.city = data.city || ''
  form.currentTitle = data.currentTitle || ''
  form.yearsOfExperience = data.yearsOfExperience
  form.educationLevel = data.educationLevel
  form.school = data.school || ''
  form.major = data.major || ''
  form.graduationYear = data.graduationYear
  form.targetIndustry = data.targetIndustry || ''
  form.targetPosition = data.targetPosition || ''
  form.targetSeniority = data.targetSeniority
  form.targetCity = [...(data.targetCity || [])]
  form.targetSalaryMin = data.targetSalaryMin
  form.targetSalaryMax = data.targetSalaryMax
  form.skills = [...(data.skills || [])]
}

async function loadProfile() {
  loading.value = true
  try {
    const res = await userApi.getProfile()
    applyProfile(res.data)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function loadMembership() {
  membershipLoading.value = true
  try {
    const res = await userApi.getMembership()
    membership.value = res.data
  } catch {
    membership.value = null
  } finally {
    membershipLoading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const payload: UpdateProfileParams = {
      nickname: form.nickname,
      phone: form.phone || null,
      gender: form.gender,
      birthYear: form.birthYear,
      city: form.city || null,
      currentTitle: form.currentTitle || null,
      yearsOfExperience: form.yearsOfExperience,
      educationLevel: form.educationLevel,
      school: form.school || null,
      major: form.major || null,
      graduationYear: form.graduationYear,
      targetIndustry: form.targetIndustry || null,
      targetPosition: form.targetPosition || null,
      targetSeniority: form.targetSeniority,
      targetCity: form.targetCity,
      targetSalaryMin: form.targetSalaryMin,
      targetSalaryMax: form.targetSalaryMax,
      skills: form.skills,
    }
    const res = await userApi.updateProfile(payload)
    applyProfile(res.data)
    message.success('保存成功')
  } catch {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadProfile()
  if (activeTab.value === 'membership') {
    loadMembership()
  }
})

watch(activeTab, (tab) => {
  if (tab === 'membership' && !membership.value) {
    loadMembership()
  }
})
</script>

<style scoped>
.profile-page {
  max-width: 1000px;
  margin: 0 auto;
}
.member-card {
  height: 100%;
}
.member-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.member-icon {
  font-size: 32px;
  color: #faad14;
}
.member-level {
  margin: 0;
  font-size: 18px;
  font-weight: bold;
}
.member-code {
  margin: 0;
  color: #999;
  font-size: 12px;
}
.quota-hint {
  margin-top: 12px;
  color: #999;
  font-size: 12px;
  line-height: 1.8;
}
</style>
