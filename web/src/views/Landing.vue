<template>
  <div class="landing">
    <!-- ── 顶部导航 ─────────────────────────────────────── -->
    <header class="nav">
      <div class="nav-inner">
        <div class="brand" @click="router.push('/')">
          <span class="brand-mark">CP</span>
          <span class="brand-name">CareerPilot</span>
          <span class="brand-sub">智能求职领航员</span>
        </div>

        <nav class="nav-links">
          <a href="#problems">解决什么问题</a>
          <a href="#seeker">求职者</a>
          <a href="#recruiter">招聘方</a>
          <a href="#how">工作原理</a>
        </nav>

        <div class="nav-actions">
          <template v-if="!userStore.isLoggedIn">
            <a-button class="login-btn" @click="goLogin('seeker')">求职者登录</a-button>
            <a-button type="primary" class="login-btn" @click="goLogin('recruiter')">招聘者登录</a-button>
          </template>
          <template v-else>
            <a-tag class="role-tag">{{ userStore.roleLabel }}</a-tag>
            <a-button type="primary" @click="goHome">进入工作台</a-button>
          </template>
        </div>
      </div>
    </header>

    <main>
      <!-- ── Hero ───────────────────────────────────────── -->
      <section class="hero">
        <div class="hero-bg" />
        <div class="hero-inner">
          <div class="hero-copy">
            <span class="eyebrow">多 Agent 协同 · LangGraph 编排</span>
            <h1>
              让每一次求职与招聘，<br />
              都有<span class="accent">证据</span>可依。
            </h1>
            <p class="hero-desc">
              CareerPilot 把 JD 解析、简历诊断、匹配打分、简历优化、模拟面试与招聘洞察，
              拆成 8 个专职 Agent。规则引擎打底，LLM 可选增强 ——
              产出的是可解释的分数、差距与验证问题，而不是一句“感觉不合适”。
            </p>
            <div class="hero-actions">
              <a-button type="primary" size="large" @click="goLogin('seeker')">
                我是求职者 <ArrowRightOutlined />
              </a-button>
              <a-button size="large" class="ghost-btn" @click="goLogin('recruiter')">
                我是招聘方
              </a-button>
            </div>
            <div class="hero-trust">
              <span><CheckCircleOutlined /> 规则引擎优先</span>
              <span><SafetyCertificateOutlined /> 数据归属校验</span>
              <span><FileSearchOutlined /> 全链路可追溯</span>
            </div>
          </div>

          <div class="hero-preview">
            <div class="preview-card">
              <div class="preview-head">
                <span class="dot red" /><span class="dot yellow" /><span class="dot green" />
                <span class="preview-title">匹配分析报告</span>
              </div>
              <div class="preview-score">
                <a-progress
                  type="circle"
                  :percent="82"
                  :size="108"
                  stroke-color="#1890ff"
                  :format="() => ''"
                />
                <div class="preview-score-text">
                  <strong>82</strong>
                  <span>综合匹配分</span>
                </div>
              </div>
              <div class="preview-bars">
                <div v-for="item in previewBars" :key="item.label" class="preview-bar">
                  <div class="preview-bar-head">
                    <span>{{ item.label }}</span>
                    <span>{{ item.value }}</span>
                  </div>
                  <div class="preview-bar-track">
                    <div class="preview-bar-fill" :style="{ width: item.value + '%', background: item.color }" />
                  </div>
                </div>
              </div>
              <div class="preview-gap">
                <span class="gap-title">优先验证</span>
                <p>“高并发系统设计”在简历中缺少量化结果，建议面试时追问。</p>
              </div>
            </div>
            <div class="floating-card floating-1">
              <RobotOutlined />
              <div>
                <strong>8 个专职 Agent</strong>
                <span>各司其职，可单独配置</span>
              </div>
            </div>
            <div class="floating-card floating-2">
              <BulbOutlined />
              <div>
                <strong>差距可解释</strong>
                <span>分数 + 证据 + 建议</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- ── 解决什么问题 ───────────────────────────────── -->
      <section id="problems" class="section">
        <div class="section-head">
          <span class="section-kicker">Why CareerPilot</span>
          <h2>两端各自最痛的事，分别解决</h2>
          <p>不是又一个招聘网站。求职者获得诊断与演练，招聘方获得证据与验证问题。</p>
        </div>

        <div class="problem-grid">
          <div class="problem-card seeker-card">
            <div class="problem-icon"><UserOutlined /></div>
            <h3>求职者：投了简历，却不知道差在哪</h3>
            <ul>
              <li><b>ATS 黑箱：</b>简历关键词、结构、量化结果没有反馈。</li>
              <li><b>岗位错配：</b>不知道自己和这份 JD 到底差什么。</li>
              <li><b>面试没底：</b>缺少针对差距的追问演练与 STAR 反馈。</li>
              <li><b>重复劳动：</b>每投一个岗位都要重写简历与求职信。</li>
            </ul>
          </div>

          <div class="problem-card hr-card">
            <div class="problem-icon"><TeamOutlined /></div>
            <h3>招聘方：简历很多，判断却靠感觉</h3>
            <ul>
              <li><b>筛选低效：</b>多份简历与同一个 JD 难以横向比较。</li>
              <li><b>证据缺失：</b>“写过”不等于“做到过”，面试问题不聚焦。</li>
              <li><b>标准漂移：</b>不同面试官问的不同，判断口径不一致。</li>
              <li><b>合规风险：</b>自动淘汰、跨用户检索简历都可能越界。</li>
            </ul>
          </div>
        </div>
      </section>

      <!-- ── 求职者能力 ─────────────────────────────────── -->
      <section id="seeker" class="section section-tinted">
        <div class="section-head">
          <span class="section-kicker">For Job Seekers</span>
          <h2>从“投出去”到“面得赢”</h2>
          <p>七个能力串成一条求职链路，每一步都有可复用的产出。</p>
        </div>

        <div class="feature-grid">
          <div v-for="item in seekerFeatures" :key="item.title" class="feature-card">
            <div class="feature-icon" :style="{ background: item.bg }">
              <component :is="item.icon" />
            </div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.desc }}</p>
            <span class="feature-tag">{{ item.tag }}</span>
          </div>
        </div>

        <div class="section-cta">
          <a-button type="primary" @click="goLogin('seeker')">登录求职者工作台 <ArrowRightOutlined /></a-button>
        </div>
      </section>

      <!-- ── 招聘方能力 ─────────────────────────────────── -->
      <section id="recruiter" class="section">
        <div class="section-head">
          <span class="section-kicker">For Recruiters</span>
          <h2>把“收到的一摞简历”，变成可比较的证据</h2>
          <p>只分析你主动导入的候选人简历；不做投递、站内沟通与全站人才库检索。</p>
        </div>

        <div class="feature-grid hr-features">
          <div v-for="item in hrFeatures" :key="item.title" class="feature-card">
            <div class="feature-icon" :style="{ background: item.bg }">
              <component :is="item.icon" />
            </div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.desc }}</p>
            <span class="feature-tag">{{ item.tag }}</span>
          </div>
        </div>

        <div class="boundary">
          <SafetyCertificateOutlined />
          <div>
            <strong>刻意不做的三件事</strong>
            <span>不提供职位投递与候选人沟通 · 不允许跨用户检索简历 · 不输出自动录用 / 淘汰结论。</span>
          </div>
        </div>

        <div class="section-cta">
          <a-button type="primary" @click="goLogin('recruiter')">登录招聘方工作台 <ArrowRightOutlined /></a-button>
        </div>
      </section>

      <!-- ── 工作原理 ───────────────────────────────────── -->
      <section id="how" class="section section-dark">
        <div class="section-head light">
          <span class="section-kicker">How it works</span>
          <h2>一条链路由 8 个 Agent 接力完成</h2>
          <p>Orchestrator 按任务类型路由，失败可降级到规则引擎；结果回调业务服务落库。</p>
        </div>

        <div class="steps">
          <div v-for="(step, index) in steps" :key="step.title" class="step">
            <span class="step-no">{{ index + 1 }}</span>
            <h3>{{ step.title }}</h3>
            <p>{{ step.desc }}</p>
          </div>
        </div>

        <div class="agent-strip">
          <span v-for="agent in agentNames" :key="agent">{{ agent }}</span>
        </div>
      </section>

      <!-- ── 底部 CTA ───────────────────────────────────── -->
      <section class="cta-band">
        <div>
          <h2>先看清差距，再做决定。</h2>
          <p>求职者从一份简历开始，招聘方从一份 JD 开始。</p>
        </div>
        <div class="cta-actions">
          <a-button type="primary" size="large" @click="goLogin('seeker')">求职者登录</a-button>
          <a-button size="large" class="ghost-btn light-btn" @click="goLogin('recruiter')">招聘者登录</a-button>
        </div>
      </section>
    </main>

    <footer class="footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <span class="brand-mark">CP</span>
          <span>CareerPilot · 智能求职领航员</span>
        </div>
        <div class="footer-links">
          <a :href="adminUrl">平台管理端</a>
          <a href="#problems">产品能力</a>
          <a href="#how">工作原理</a>
        </div>
        <div class="footer-note">© 2026 CareerPilot · 分析结果仅作辅助参考，最终判断由用户作出</div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  AimOutlined,
  ArrowRightOutlined,
  BarChartOutlined,
  BulbOutlined,
  CheckCircleOutlined,
  EditOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  MailOutlined,
  RobotOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  SolutionOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const adminUrl = import.meta.env.VITE_ADMIN_URL
  || `${window.location.protocol}//${window.location.hostname}:3100`

const previewBars = [
  { label: '语义匹配', value: 86, color: '#1890ff' },
  { label: '关键词覆盖', value: 78, color: '#52c41a' },
  { label: '业务规则', value: 81, color: '#722ed1' },
]

const seekerFeatures = [
  { title: '简历竞争力分析', desc: 'ATS 多维度评分：结构、关键词、量化结果与可读性，逐项给出改进建议。', tag: 'ATS 评分', icon: FileTextOutlined, bg: 'linear-gradient(135deg, #e6f4ff, #bae0ff)' },
  { title: 'JD 智能解析', desc: '粘贴职位描述，自动抽取硬技能、经验年限、学历与职责，形成可对比的岗位画像。', tag: '结构化 JD', icon: FileSearchOutlined, bg: 'linear-gradient(135deg, #f0f5ff, #d6e4ff)' },
  { title: '岗位匹配打分', desc: '语义 40% + 关键词 35% + 业务规则 25%，输出总分与按严重度排序的差距清单。', tag: '三段式打分', icon: AimOutlined, bg: 'linear-gradient(135deg, #e6fffb, #b5f5ec)' },
  { title: '简历优化', desc: '针对目标 JD 重写经历描述，补足 STAR 结构，标出新增技能与改动理由。', tag: 'STAR 重写', icon: EditOutlined, bg: 'linear-gradient(135deg, #f9f0ff, #efdbff)' },
  { title: '模拟面试', desc: '按差距生成多轮追问，逐轮评分并检查 Situation / Task / Action / Result 覆盖。', tag: '多轮追问', icon: SolutionOutlined, bg: 'linear-gradient(135deg, #fff7e6, #ffd8bf)' },
  { title: '求职信生成', desc: '按岗位与语气生成求职信，突出最相关的技能与经历，避免空泛套话。', tag: '一岗一封', icon: MailOutlined, bg: 'linear-gradient(135deg, #fff0f6, #ffd6e7)' },
  { title: '职业规划', desc: '结合目标岗位与现有能力，给出分阶段路线图、技能缺口与可执行动作。', tag: '分阶段路线', icon: RocketOutlined, bg: 'linear-gradient(135deg, #f6ffed, #d9f7be)' },
]

const hrFeatures = [
  { title: '候选人简历集中管理', desc: '上传你已收到的候选人简历，统一查看解析状态、ATS 评分与全文。', tag: '本地导入', icon: FileTextOutlined, bg: 'linear-gradient(135deg, #e6f4ff, #bae0ff)' },
  { title: '岗位 JD 管理', desc: '为每个在招岗位维护结构化 JD，作为后续匹配与洞察的统一基准。', tag: '岗位基准', icon: FileSearchOutlined, bg: 'linear-gradient(135deg, #f0f5ff, #d6e4ff)' },
  { title: '岗位匹配排序', desc: '多份简历对同一 JD 批量打分，按综合分排序，快速缩小面试范围。', tag: '批量对比', icon: BarChartOutlined, bg: 'linear-gradient(135deg, #e6fffb, #b5f5ec)' },
  { title: '深度洞察', desc: '拆解能力维度覆盖，定位证据缺口，生成面试官可直接使用的验证问题。', tag: '证据缺口', icon: ThunderboltOutlined, bg: 'linear-gradient(135deg, #fff7e6, #ffd8bf)' },
  { title: '追问判定与留痕', desc: '录入面试官听到的回答，判定 demonstrated / adjacent / not_evidenced，保留判断依据。', tag: '可追溯', icon: SafetyCertificateOutlined, bg: 'linear-gradient(135deg, #f9f0ff, #efdbff)' },
]

const steps = [
  { title: '导入素材', desc: '求职者上传简历、招聘方导入候选人简历；双方各自维护目标 JD。' },
  { title: '解析与评分', desc: 'JD 解析 Agent 抽结构化要求，简历分析 Agent 给出 ATS 多维评分。' },
  { title: '匹配与洞察', desc: '匹配 Agent 计算三段式分数；洞察 Agent 把 gap 转成验证问题。' },
  { title: '生成与演练', desc: '优化 / 面试 / 求职信 / 职业规划 Agent 接力，结果统一回调落库。' },
]

const agentNames = [
  'JD 解析',
  '简历分析',
  '匹配打分',
  '简历优化',
  '模拟面试',
  '求职信',
  '职业规划',
  'Orchestrator',
]

const currentHome = computed(() => userStore.homePath)

function goLogin(role: 'seeker' | 'recruiter') {
  if (userStore.isLoggedIn) {
    // 已登录时直接进对应工作台，避免登录页守卫把用户又弹回工作台
    const allowed = role === 'recruiter' ? userStore.canAccessHr : userStore.role === 'USER'
    if (allowed) {
      router.push(role === 'recruiter' ? '/hr/dashboard' : '/seeker/dashboard')
      return
    }
  }
  router.push({ path: '/login', query: { role } })
}

function goHome() {
  router.push(currentHome.value)
}
</script>

<style scoped>
.landing {
  --ink: #0f172a;
  --muted: #64748b;
  --line: rgba(15, 23, 42, 0.08);
  --blue: #1677ff;
  --violet: #722ed1;
  --cyan: #13c2c2;
  background: #fff;
  color: var(--ink);
  overflow-x: hidden;
}
.nav {
  position: sticky;
  top: 0;
  z-index: 20;
  backdrop-filter: blur(14px);
  background: rgba(255, 255, 255, 0.82);
  border-bottom: 1px solid var(--line);
}
.nav-inner {
  max-width: 1180px;
  margin: 0 auto;
  height: 68px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 32px;
}
.brand {
  display: flex;
  align-items: baseline;
  gap: 8px;
  cursor: pointer;
  white-space: nowrap;
}
.brand-mark {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(135deg, #1677ff, #722ed1);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 0.5px;
  transform: translateY(4px);
}
.brand-name {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.brand-sub {
  font-size: 12px;
  color: var(--muted);
}
.nav-links {
  display: flex;
  gap: 24px;
  margin-left: 12px;
}
.nav-links a {
  color: #475569;
  font-size: 14px;
  text-decoration: none;
}
.nav-links a:hover {
  color: var(--blue);
}
.nav-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}
.login-btn {
  font-size: 14px;
}
.role-tag {
  margin: 0;
}
.hero {
  position: relative;
  padding: 84px 24px 96px;
  overflow: hidden;
}
.hero-bg {
  position: absolute;
  inset: -20% -10% auto -10%;
  height: 640px;
  background:
    radial-gradient(600px 320px at 12% 20%, rgba(22, 119, 255, 0.14), transparent 70%),
    radial-gradient(520px 300px at 82% 12%, rgba(114, 46, 209, 0.12), transparent 70%),
    radial-gradient(420px 260px at 60% 70%, rgba(19, 194, 194, 0.1), transparent 70%);
  pointer-events: none;
}
.hero-inner {
  position: relative;
  max-width: 1180px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 0.92fr);
  gap: 56px;
  align-items: center;
}
.eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(22, 119, 255, 0.09);
  color: #0958d9;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.hero h1 {
  margin: 20px 0 18px;
  font-size: clamp(38px, 5vw, 58px);
  line-height: 1.12;
  letter-spacing: -0.035em;
  font-weight: 800;
}
.accent {
  background: linear-gradient(120deg, #1677ff 10%, #722ed1 70%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.hero-desc {
  max-width: 560px;
  color: #475569;
  font-size: 16px;
  line-height: 1.85;
  margin: 0 0 28px;
}
.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.ghost-btn {
  border-color: rgba(15, 23, 42, 0.16);
}
.hero-trust {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  margin-top: 28px;
  color: #64748b;
  font-size: 13px;
}
.hero-trust span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.hero-trust .anticon {
  color: #16a34a;
}
.hero-preview {
  position: relative;
  min-height: 440px;
}
.preview-card {
  position: relative;
  z-index: 2;
  border-radius: 20px;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 30px 70px -30px rgba(15, 23, 42, 0.35);
  padding: 18px 20px 20px;
  transform: rotate(-1.2deg);
}
.preview-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.dot.red { background: #ff5f57; }
.dot.yellow { background: #febc2e; }
.dot.green { background: #28c840; }
.preview-title {
  margin-left: 8px;
  font-size: 12px;
  color: #94a3b8;
}
.preview-score {
  position: relative;
  display: flex;
  justify-content: center;
  padding: 18px 0 14px;
}
.preview-score-text {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 8px;
}
.preview-score-text strong {
  font-size: 26px;
  line-height: 1;
}
.preview-score-text span {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
}
.preview-bars {
  display: grid;
  gap: 10px;
}
.preview-bar-head {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}
.preview-bar-track {
  height: 7px;
  border-radius: 999px;
  background: #f1f5f9;
  overflow: hidden;
}
.preview-bar-fill {
  height: 100%;
  border-radius: 999px;
}
.preview-gap {
  margin-top: 16px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #fff7e6;
  border: 1px dashed #ffd591;
}
.gap-title {
  font-size: 11px;
  color: #d46b08;
  font-weight: 600;
}
.preview-gap p {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: #7c4a03;
}
.floating-card {
  position: absolute;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 18px 40px -22px rgba(15, 23, 42, 0.4);
  font-size: 12px;
  color: #64748b;
}
.floating-card .anticon {
  font-size: 20px;
  color: #1677ff;
}
.floating-card strong {
  display: block;
  color: #0f172a;
  font-size: 13px;
}
.floating-card span {
  display: block;
  margin-top: 2px;
}
.floating-1 {
  top: 22px;
  left: -22px;
}
.floating-2 {
  bottom: 12px;
  right: -10px;
}
.floating-2 .anticon {
  color: #d48806;
}
.section {
  padding: 88px 24px;
  max-width: 1180px;
  margin: 0 auto;
}
.section-tinted {
  max-width: none;
  background: linear-gradient(180deg, #f8fafc, #fff);
}
.section-tinted > * {
  max-width: 1180px;
  margin-left: auto;
  margin-right: auto;
}
.section-dark {
  max-width: none;
  background: #0b1220;
  color: #e2e8f0;
}
.section-dark > * {
  max-width: 1180px;
  margin-left: auto;
  margin-right: auto;
}
.section-head {
  max-width: 720px;
  margin-bottom: 40px;
}
.section-head.light p {
  color: #94a3b8;
}
.section-kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #1677ff;
}
.section-head h2 {
  margin: 12px 0 12px;
  font-size: clamp(28px, 3.4vw, 38px);
  letter-spacing: -0.03em;
  line-height: 1.2;
}
.section-head p {
  margin: 0;
  color: #64748b;
  font-size: 15px;
  line-height: 1.8;
}
.section-dark .section-kicker {
  color: #69b1ff;
}
.problem-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
.problem-card {
  border-radius: 20px;
  padding: 28px;
  border: 1px solid var(--line);
  background: #fff;
  box-shadow: 0 18px 40px -32px rgba(15, 23, 42, 0.35);
}
.seeker-card {
  background: linear-gradient(180deg, #f0f7ff, #fff 55%);
}
.hr-card {
  background: linear-gradient(180deg, #f7f2ff, #fff 55%);
}
.problem-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid var(--line);
  font-size: 20px;
  color: #1677ff;
}
.hr-card .problem-icon {
  color: #722ed1;
}
.problem-card h3 {
  margin: 18px 0 14px;
  font-size: 20px;
  letter-spacing: -0.01em;
}
.problem-card ul {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  line-height: 1.95;
  font-size: 14px;
}
.problem-card b {
  color: #0f172a;
}
.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.hr-features {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.feature-card {
  border-radius: 16px;
  border: 1px solid var(--line);
  background: #fff;
  padding: 22px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.feature-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 22px 40px -30px rgba(15, 23, 42, 0.4);
}
.feature-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  color: #0958d9;
  margin-bottom: 16px;
}
.feature-card h3 {
  margin: 0 0 8px;
  font-size: 16px;
}
.feature-card p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.8;
}
.feature-tag {
  display: inline-block;
  margin-top: 14px;
  padding: 3px 9px;
  border-radius: 999px;
  background: #f8fafc;
  border: 1px solid var(--line);
  color: #475569;
  font-size: 11px;
}
.section-cta {
  margin-top: 32px;
}
.boundary {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-top: 22px;
  padding: 18px 20px;
  border-radius: 14px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  color: #7c4a03;
}
.boundary .anticon {
  font-size: 22px;
  color: #d48806;
  margin-top: 2px;
}
.boundary strong {
  display: block;
  font-size: 14px;
  margin-bottom: 4px;
}
.boundary span {
  font-size: 13px;
  line-height: 1.7;
}
.steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.step {
  position: relative;
  padding: 24px 20px;
  border-radius: 16px;
  background: rgba(148, 163, 184, 0.08);
  border: 1px solid rgba(148, 163, 184, 0.16);
}
.step-no {
  display: inline-flex;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1677ff, #722ed1);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 14px;
}
.step h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: #f8fafc;
}
.step p {
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.75;
}
.agent-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 28px;
}
.agent-strip span {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  color: #cbd5e1;
  font-size: 12px;
  background: rgba(148, 163, 184, 0.08);
}
.cta-band {
  max-width: 1180px;
  margin: 0 auto 72px;
  padding: 40px 44px;
  border-radius: 22px;
  background: linear-gradient(120deg, #0b1220 0%, #13224a 55%, #2b1450 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}
.cta-band h2 {
  margin: 0 0 8px;
  font-size: clamp(24px, 3vw, 32px);
  letter-spacing: -0.02em;
}
.cta-band p {
  margin: 0;
  color: #94a3b8;
  font-size: 14px;
}
.cta-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.light-btn {
  background: transparent;
  color: #e2e8f0;
  border-color: rgba(226, 232, 240, 0.35);
}
.light-btn:hover {
  color: #fff;
  border-color: #fff;
}
.footer {
  border-top: 1px solid var(--line);
  padding: 28px 24px;
}
.footer-inner {
  max-width: 1180px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #64748b;
}
.footer-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #0f172a;
  font-weight: 600;
}
.footer-brand .brand-mark {
  transform: none;
}
.footer-links {
  display: flex;
  gap: 18px;
}
.footer-links a {
  color: #64748b;
  text-decoration: none;
}
.footer-links a:hover {
  color: #1677ff;
}
.footer-note {
  margin-left: auto;
  font-size: 12px;
  color: #94a3b8;
}
@media (max-width: 980px) {
  .nav-links { display: none; }
  .hero-inner { grid-template-columns: 1fr; gap: 40px; }
  .hero-preview { max-width: 520px; }
  .floating-1 { left: 0; }
  .floating-2 { right: 0; }
  .feature-grid, .hr-features { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .steps { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .nav-inner { padding: 0 16px; gap: 12px; }
  .brand-sub { display: none; }
  .nav-actions .login-btn { font-size: 12px; padding: 0 10px; }
  .hero { padding: 56px 16px 64px; }
  .section { padding: 56px 16px; }
  .problem-grid, .feature-grid, .hr-features, .steps { grid-template-columns: 1fr; }
  .floating-card { display: none; }
  .cta-band { margin: 0 16px 48px; padding: 28px 22px; }
  .footer-note { margin-left: 0; }
}
</style>
