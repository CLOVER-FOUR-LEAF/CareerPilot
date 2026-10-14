<template>
  <div class="dashboard">
    <a-page-header title="欢迎回来" :sub-title="userStore.nickname || '用户'" />

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :sm="12" :lg="8">
        <a-card hoverable class="feature-card" @click="$router.push('/resume')">
          <template #cover>
            <div class="card-icon" style="background: linear-gradient(135deg, #667eea, #764ba2)">
              <FileTextOutlined style="font-size: 48px; color: #fff" />
            </div>
          </template>
          <a-card-meta title="简历分析" description="上传简历，获取 ATS 多维度评分与优化建议" />
        </a-card>
      </a-col>

      <a-col :xs="24" :sm="12" :lg="8">
        <a-card hoverable class="feature-card" @click="$router.push('/jd')">
          <template #cover>
            <div class="card-icon" style="background: linear-gradient(135deg, #f093fb, #f5576c)">
              <FileSearchOutlined style="font-size: 48px; color: #fff" />
            </div>
          </template>
          <a-card-meta title="JD 解析" description="粘贴职位描述，智能提取关键信息与技能要求" />
        </a-card>
      </a-col>

      <a-col :xs="24" :sm="12" :lg="8">
        <a-card hoverable class="feature-card" @click="$router.push('/match')">
          <template #cover>
            <div class="card-icon" style="background: linear-gradient(135deg, #4facfe, #00f2fe)">
              <AimOutlined style="font-size: 48px; color: #fff" />
            </div>
          </template>
          <a-card-meta title="匹配打分" description="简历与 JD 智能匹配，发现能力差距" />
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" style="margin-top: 24px">
      <a-col :span="24">
        <a-card title="快速统计">
          <a-row :gutter="16">
            <a-col :xs="12" :md="8">
              <a-statistic title="简历数量" :value="stats.resumeCount" style="text-align: center">
                <template #prefix><FileTextOutlined /></template>
              </a-statistic>
            </a-col>
            <a-col :xs="12" :md="8">
              <a-statistic title="JD 数量" :value="stats.jdCount" style="text-align: center">
                <template #prefix><FileSearchOutlined /></template>
              </a-statistic>
            </a-col>
            <a-col :xs="12" :md="8">
              <a-statistic title="匹配记录" :value="stats.matchCount" style="text-align: center">
                <template #prefix><ThunderboltOutlined /></template>
              </a-statistic>
            </a-col>
          </a-row>
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" style="margin-top: 24px">
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card hoverable size="small" @click="$router.push('/optimize')">
          <div class="tool-entry">
            <EditOutlined />
            <span>简历优化</span>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card hoverable size="small" @click="$router.push('/interview')">
          <div class="tool-entry">
            <SolutionOutlined />
            <span>模拟面试</span>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card hoverable size="small" @click="$router.push('/cover-letter')">
          <div class="tool-entry">
            <MailOutlined />
            <span>生成求职信</span>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card hoverable size="small" @click="$router.push('/career-plan')">
          <div class="tool-entry">
            <RocketOutlined />
            <span>职业规划</span>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import {
  AimOutlined,
  EditOutlined,
  FileTextOutlined,
  FileSearchOutlined,
  MailOutlined,
  RocketOutlined,
  SolutionOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { resumeApi, jdApi, matchApi } from '@/api'

const userStore = useUserStore()

const stats = reactive({
  resumeCount: 0,
  jdCount: 0,
  matchCount: 0,
})

onMounted(async () => {
  try {
    const [resumeRes, jdRes, matchRes] = await Promise.all([
      resumeApi.list({ page: 1, size: 1 }),
      jdApi.list({ page: 1, size: 1 }),
      matchApi.list({ page: 1, size: 1 }),
    ])
    // §0.5 分页包络：业务数据在 data.items / data.total
    stats.resumeCount = resumeRes.data?.total ?? resumeRes.data?.items?.length ?? 0
    stats.jdCount = jdRes.data?.total ?? jdRes.data?.items?.length ?? 0
    stats.matchCount = matchRes.data?.total ?? matchRes.data?.items?.length ?? 0
  } catch {
    // 统计失败不影响页面主体
  }
})
</script>

<style scoped>
.dashboard {
  max-width: 1200px;
  margin: 0 auto;
}
.feature-card {
  text-align: center;
}
.card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 160px;
}
.tool-entry {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 15px;
}
</style>
