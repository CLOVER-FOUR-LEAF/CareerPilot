<template>
  <div class="dashboard">
    <header class="dash-head">
      <div class="dash-copy">
        <span class="eyebrow">Candidate desk / {{ today }}</span>
        <h1 class="dash-title">
          你好，{{ userStore.nickname || '求职者' }}。<br />
          <em>从证据开始。</em>
        </h1>
        <p class="dash-sub">
          先看清简历与岗位的差距，再决定优化、面试与投递的优先级。
          每一步都留下可复用的产出，而不是从零重来。
        </p>
      </div>
      <div class="dash-actions">
        <a-button type="primary" size="large" @click="go('/seeker/resume')">
          上传 / 分析简历 <ArrowRightOutlined />
        </a-button>
        <a-button size="large" @click="go('/seeker/interview')">
          开始模拟面试
        </a-button>
      </div>
    </header>

    <!-- 数字台账 -->
    <section class="ledger">
      <div class="ledger-item">
        <span class="ledger-label">Resumes</span>
        <strong>{{ stats.resumeCount }}</strong>
        <span class="ledger-note">已上传的简历</span>
      </div>
      <div class="ledger-item">
        <span class="ledger-label">Job descriptions</span>
        <strong>{{ stats.jdCount }}</strong>
        <span class="ledger-note">已解析的目标岗位</span>
      </div>
      <div class="ledger-item">
        <span class="ledger-label">Match records</span>
        <strong>{{ stats.matchCount }}</strong>
        <span class="ledger-note">已完成的匹配评估</span>
      </div>
    </section>

    <!-- 主链路 -->
    <section class="dash-section">
      <div class="dash-section-head">
        <span class="eyebrow">Core loop</span>
        <h2>三步建立你的求职证据链</h2>
      </div>
      <div class="core-grid">
        <button
          v-for="(item, index) in core"
          :key="item.path"
          type="button"
          class="core-card"
          @click="go(item.path)"
        >
          <span class="core-index">0{{ index + 1 }}</span>
          <component :is="item.icon" class="core-icon" />
          <h3>{{ item.title }}</h3>
          <p>{{ item.desc }}</p>
          <span class="core-link">进入 <ArrowRightOutlined /></span>
        </button>
      </div>
    </section>

    <!-- 工具箱 -->
    <section class="dash-section">
      <div class="dash-section-head">
        <span class="eyebrow">Toolkit</span>
        <h2>把分析结果用起来</h2>
      </div>
      <div class="tool-list">
        <button
          v-for="(item, index) in tools"
          :key="item.path"
          type="button"
          class="tool-row"
          @click="go(item.path)"
        >
          <span class="tool-index">0{{ index + 1 }}</span>
          <component :is="item.icon" class="tool-icon" />
          <span class="tool-title">{{ item.title }}</span>
          <span class="tool-desc">{{ item.desc }}</span>
          <ArrowRightOutlined class="tool-arrow" />
        </button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import {
  AimOutlined,
  ArrowRightOutlined,
  EditOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  MailOutlined,
  RocketOutlined,
  SolutionOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { jdApi, matchApi, resumeApi } from '@/api'
import type { Component } from 'vue'

const router = useRouter()
const userStore = useUserStore()

const stats = reactive({
  resumeCount: 0,
  jdCount: 0,
  matchCount: 0,
})

const today = computed(() => {
  const date = new Date()
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
})

interface Entry {
  title: string
  desc: string
  path: string
  icon: Component
}

const core: Entry[] = [
  {
    title: '简历分析',
    desc: 'ATS 多维度评分：结构、关键词、量化结果与可读性，逐项给出改进建议。',
    path: '/seeker/resume',
    icon: FileTextOutlined,
  },
  {
    title: 'JD 解析',
    desc: '把目标岗位拆成硬技能、经验年限与职责，形成可对比的岗位画像。',
    path: '/seeker/jd',
    icon: FileSearchOutlined,
  },
  {
    title: '匹配打分',
    desc: '语义 40% + 关键词 35% + 业务规则 25%，输出总分与按严重度排序的差距清单。',
    path: '/seeker/match',
    icon: AimOutlined,
  },
]

const tools: Entry[] = [
  {
    title: '简历优化',
    desc: '按目标 JD 重写经历，补足 STAR 结构，标出新增技能与改动理由。',
    path: '/seeker/optimize',
    icon: EditOutlined,
  },
  {
    title: '模拟面试',
    desc: '按差距生成多轮追问，逐轮评分并检查 STAR 覆盖。',
    path: '/seeker/interview',
    icon: SolutionOutlined,
  },
  {
    title: '求职信',
    desc: '按岗位与语气生成求职信，突出最相关的技能与经历。',
    path: '/seeker/cover-letter',
    icon: MailOutlined,
  },
  {
    title: '职业规划',
    desc: '结合目标岗位与现有能力，给出分阶段路线图与技能缺口。',
    path: '/seeker/career-plan',
    icon: RocketOutlined,
  },
]

function go(path: string) {
  router.push(path)
}

onMounted(async () => {
  try {
    const [resumeRes, jdRes, matchRes] = await Promise.all([
      resumeApi.list({ page: 1, size: 1 }),
      jdApi.list({ page: 1, size: 1 }),
      matchApi.list({ page: 1, size: 1 }),
    ])
    stats.resumeCount = resumeRes.data?.total ?? 0
    stats.jdCount = jdRes.data?.total ?? 0
    stats.matchCount = matchRes.data?.total ?? 0
  } catch {
    // 统计失败不影响页面主体
  }
})
</script>

<style scoped>
.dashboard {
  max-width: 1320px;
  margin: 0 auto;
}

.dash-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 32px;
  align-items: end;
  padding-bottom: 34px;
  border-bottom: 1px solid var(--line-strong);
}

.dash-title {
  margin: 18px 0 0;
  font-family: var(--font-display);
  font-size: clamp(40px, 4.6vw, 64px);
  font-weight: 600;
  line-height: 0.98;
  letter-spacing: -0.045em;
  color: var(--ink);
}

.dash-title em {
  font-style: italic;
  color: var(--accent-deep);
}

.dash-sub {
  max-width: 580px;
  margin: 20px 0 0;
  padding-left: 16px;
  border-left: 1px solid var(--line-strong);
  color: var(--ink-soft);
  font-size: 13.5px;
  line-height: 1.85;
}

.dash-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 190px;
}

.ledger {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-left: 1px solid var(--line);
}

.ledger-item {
  padding: 26px 28px 24px;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.ledger-label {
  display: block;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--muted);
}

.ledger-item strong {
  display: block;
  margin: 10px 0 4px;
  font-family: var(--font-display);
  font-size: 58px;
  font-weight: 600;
  line-height: 0.9;
  letter-spacing: -0.04em;
  color: var(--ink);
}

.ledger-note {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.06em;
  color: var(--muted);
}

.dash-section {
  margin-top: 58px;
}

.dash-section-head {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 18px;
  align-items: end;
  padding-top: 18px;
  margin-bottom: 22px;
  border-top: 1px solid var(--line-strong);
}

.dash-section-head h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(28px, 3vw, 38px);
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.03em;
}

.core-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-top: 1px solid var(--line);
  border-left: 1px solid var(--line);
}

.core-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 28px;
  border: 0;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.18s ease;
}

.core-card:hover {
  background: var(--white);
}

.core-index {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.14em;
  color: var(--muted);
}

.core-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  margin-top: 22px;
  border: 1px solid var(--line-strong);
  background: var(--white);
  color: var(--accent);
  font-size: 18px;
}

.core-card h3 {
  margin: 18px 0 8px;
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.core-card p {
  margin: 0;
  color: var(--ink-soft);
  font-size: 12.5px;
  line-height: 1.8;
}

.core-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 22px;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--accent-deep);
}

.tool-list {
  border-top: 1px solid var(--line);
}

.tool-row {
  display: grid;
  grid-template-columns: 34px 28px minmax(110px, 170px) minmax(0, 1fr) 24px;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 18px 14px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 0.16s ease, padding-left 0.16s ease;
}

.tool-row:hover {
  background: var(--white);
  padding-left: 20px;
}

.tool-index {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--muted);
}

.tool-icon {
  color: var(--accent);
  font-size: 16px;
}

.tool-title {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--ink);
}

.tool-desc {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.tool-arrow {
  color: var(--accent);
  font-size: 11px;
}

@media (max-width: 900px) {
  .dash-head {
    grid-template-columns: 1fr;
    align-items: start;
  }
  .dash-actions {
    flex-direction: row;
    flex-wrap: wrap;
  }
  .ledger {
    grid-template-columns: 1fr;
  }
  .core-grid {
    grid-template-columns: 1fr;
  }
  .dash-section-head {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  .tool-row {
    grid-template-columns: 28px 24px 1fr 20px;
  }
  .tool-desc {
    display: none;
  }
}
</style>
