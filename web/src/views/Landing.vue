<template>
  <div class="landing">
    <!-- ── 顶部导航 ─────────────────────────────────────── -->
    <header class="nav">
      <div class="nav-inner">
        <div class="brand" @click="router.push('/')">
          <span class="brand-mark">CP</span>
          <span class="brand-name">CareerPilot</span>
          <span class="brand-sub">CAREER INTELLIGENCE</span>
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
        <div class="hero-stamp">EVIDENCE<br />FIRST</div>
        <div class="hero-index">01 / THE PREMISE</div>
        <div class="hero-inner">
          <div class="hero-copy">
            <span class="eyebrow">多 Agent 协同 · LangGraph 编排</span>
            <h1>
              <span class="hero-line">让每一次求职与招聘，</span>
              <span class="hero-line">都有<span class="accent">证据</span>可依。</span>
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
                <div class="score-ring">
                  <strong>82</strong>
                  <span>/100</span>
                </div>
                <div class="preview-score-text">
                  <span>综合匹配分</span>
                  <em>SEMANTIC · KEYWORD · BUSINESS</em>
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
            <div class="feature-icon">
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
            <div class="feature-icon">
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
  { label: '语义匹配', value: 86, color: '#e4572e' },
  { label: '关键词覆盖', value: 78, color: '#1f3d2b' },
  { label: '业务规则', value: 81, color: '#b9ae99' },
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
/* ── Shell ───────────────────────────────────────────────────────────── */

.landing {
  --max: 1240px;
  background: var(--paper);
  color: var(--ink);
  overflow-x: hidden;
}

.landing ::selection {
  background: var(--accent);
  color: var(--white);
}

/* ── Navigation ──────────────────────────────────────────────────────── */

.nav {
  position: sticky;
  top: 0;
  z-index: 40;
  background: rgba(243, 239, 230, 0.9);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--line);
}

.nav-inner {
  max-width: var(--max);
  margin: 0 auto;
  height: 74px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 34px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  white-space: nowrap;
}

.brand-mark {
  width: 32px;
  height: 32px;
  display: inline-grid;
  place-items: center;
  background: var(--ink);
  color: var(--sand);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.08em;
  border: 1px solid var(--ink);
}

.brand-name {
  font-family: var(--font-display);
  font-size: 22px;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--ink);
}

.brand-sub {
  margin-left: 4px;
  padding-left: 10px;
  border-left: 1px solid var(--line-strong);
  font-family: var(--font-mono);
  font-size: 7px;
  letter-spacing: 0.16em;
  color: var(--muted);
}

.nav-links {
  display: flex;
  gap: 22px;
  margin-left: 8px;
}

.nav-links a {
  position: relative;
  color: var(--ink-soft);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  text-decoration: none;
  padding: 6px 0;
}

.nav-links a::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 1px;
  background: var(--accent);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.2s ease;
}

.nav-links a:hover {
  color: var(--accent-deep);
}

.nav-links a:hover::after {
  transform: scaleX(1);
}

.nav-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.login-btn {
  height: 34px !important;
  padding: 0 14px !important;
  font-size: 12px !important;
}

.role-tag {
  margin: 0;
}

/* ── Hero ────────────────────────────────────────────────────────────── */

.hero {
  position: relative;
  padding: 94px 24px 104px;
  border-bottom: 1px solid var(--line);
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--line) 1px, transparent 1px),
    linear-gradient(90deg, var(--line) 1px, transparent 1px);
  background-size: 86px 86px;
  opacity: 0.42;
  mask-image: linear-gradient(to bottom, black 0%, transparent 82%);
  -webkit-mask-image: linear-gradient(to bottom, black 0%, transparent 82%);
  pointer-events: none;
}

.hero-stamp {
  position: absolute;
  top: 92px;
  right: max(24px, calc((100vw - var(--max)) / 2 - 16px));
  z-index: 2;
  padding: 10px 14px;
  border: 2px solid var(--accent);
  color: var(--accent-deep);
  font-family: var(--font-mono);
  font-size: 9px;
  line-height: 1.6;
  letter-spacing: 0.22em;
  text-align: center;
  transform: rotate(6deg);
  opacity: 0.75;
  pointer-events: none;
}

.hero-index {
  position: absolute;
  top: 34px;
  left: max(24px, calc((100vw - var(--max)) / 2));
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.18em;
  color: var(--muted);
  z-index: 2;
}

.hero-inner {
  position: relative;
  z-index: 3;
  max-width: var(--max);
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1.06fr) minmax(0, 0.94fr);
  gap: 70px;
  align-items: center;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--accent-deep);
}

.eyebrow::before {
  content: '';
  width: 22px;
  height: 1px;
  background: currentColor;
}

.hero h1 {
  margin: 22px 0 24px;
  font-family: var(--font-display);
  font-size: clamp(40px, 4vw, 58px);
  font-weight: 600;
  line-height: 1.02;
  letter-spacing: -0.045em;
  color: var(--ink);
}

.hero-line {
  display: block;
  white-space: nowrap;
}

.accent {
  position: relative;
  color: var(--accent-deep);
  font-style: italic;
  padding: 0 0.04em;
  background: linear-gradient(transparent 68%, var(--accent-soft) 68%);
}

.hero-desc {
  max-width: 560px;
  margin: 0 0 30px;
  padding-left: 18px;
  border-left: 1px solid var(--line-strong);
  color: var(--ink-soft);
  font-size: 15px;
  line-height: 1.95;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.ghost-btn {
  border-color: var(--line-strong) !important;
}

.hero-trust {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  margin-top: 30px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-trust span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.hero-trust .anticon {
  color: var(--accent);
}

/* ── Report preview ──────────────────────────────────────────────────── */

.hero-preview {
  position: relative;
  min-height: 440px;
}

.preview-card {
  position: relative;
  z-index: 2;
  background: var(--white);
  border: 1px solid var(--line-strong);
  box-shadow: 10px 10px 0 rgba(23, 23, 20, 0.08);
}

.preview-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 11px 16px;
  background: var(--ink);
  color: var(--white);
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgba(255, 253, 248, 0.25);
}

.dot.red { background: var(--accent); }
.dot.yellow { background: var(--sand); }
.dot.green { background: #7aa88a; }

.preview-title {
  margin-left: 8px;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(255, 253, 248, 0.62);
}

.preview-score {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 22px 20px;
  border-bottom: 1px solid var(--line);
}

.score-ring {
  position: relative;
  width: 106px;
  height: 106px;
  flex: none;
  border-radius: 50%;
  background: conic-gradient(var(--accent) 0 82%, var(--paper-2) 82% 100%);
  display: grid;
  place-items: center;
}

.score-ring::before {
  content: '';
  position: absolute;
  inset: 7px;
  border-radius: 50%;
  background: var(--white);
}

.score-ring strong,
.score-ring span {
  position: relative;
  z-index: 1;
}

.score-ring strong {
  font-family: var(--font-display);
  font-size: 42px;
  line-height: 1;
  letter-spacing: -0.04em;
  color: var(--ink);
}

.score-ring span {
  position: absolute;
  bottom: 26px;
  font-family: var(--font-mono);
  font-size: 8px;
  color: var(--muted);
  letter-spacing: 0.06em;
}

.preview-score-text {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preview-score-text span {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--ink);
}

.preview-score-text em {
  font-style: normal;
  font-family: var(--font-mono);
  font-size: 7px;
  letter-spacing: 0.08em;
  color: var(--muted);
  line-height: 1.6;
}

.preview-bars {
  display: grid;
  gap: 12px;
  padding: 18px 20px;
}

.preview-bar-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 5px;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--muted);
}

.preview-bar-track {
  height: 3px;
  background: var(--paper-2);
}

.preview-bar-fill {
  height: 100%;
}

.preview-gap {
  margin: 0 20px 20px;
  padding: 12px 14px;
  background: var(--accent-soft);
  border-left: 3px solid var(--accent);
}

.gap-title {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--accent-deep);
  font-weight: 500;
}

.preview-gap p {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-soft);
}

.floating-card {
  position: absolute;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: var(--white);
  border: 1px solid var(--line-strong);
  box-shadow: 5px 5px 0 rgba(23, 23, 20, 0.08);
  font-size: 10px;
  color: var(--muted);
}

.floating-card .anticon {
  font-size: 18px;
  color: var(--accent);
}

.floating-card strong {
  display: block;
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--ink);
}

.floating-card span {
  display: block;
  margin-top: 2px;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.04em;
}

.floating-1 {
  top: 20px;
  left: -26px;
}

.floating-2 {
  bottom: 10px;
  right: -14px;
}

.floating-2 .anticon {
  color: var(--forest);
}

/* ── Sections ────────────────────────────────────────────────────────── */

.section {
  position: relative;
  max-width: var(--max);
  margin: 0 auto;
  padding: 96px 24px;
}

.section-tinted {
  max-width: none;
  background: var(--paper-2);
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.section-tinted > * {
  max-width: var(--max);
  margin-left: auto;
  margin-right: auto;
}

.section-dark {
  max-width: none;
  background: var(--ink);
  color: var(--paper);
  border-top: 1px solid var(--ink);
  border-bottom: 1px solid var(--ink);
  background-image:
    radial-gradient(circle at 12% 0%, rgba(228, 87, 46, 0.16), transparent 32%),
    url("data:image/svg+xml,%3Csvg viewBox='0 0 220 220' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.04'/%3E%3C/svg%3E");
}

.section-dark > * {
  max-width: var(--max);
  margin-left: auto;
  margin-right: auto;
}

.section-head {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr);
  gap: 32px;
  align-items: start;
  padding-top: 20px;
  margin-bottom: 48px;
  border-top: 1px solid var(--line-strong);
}

.section-head.light {
  border-top-color: rgba(255, 253, 248, 0.2);
}

.section-kicker {
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--accent-deep);
  padding-top: 8px;
}

.section-dark .section-kicker {
  color: var(--sand);
}

.section-head h2 {
  grid-column: 2;
  margin: 0 0 12px;
  font-family: var(--font-display);
  font-size: clamp(34px, 4vw, 52px);
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.035em;
  color: var(--ink);
}

.section-head.light h2 {
  color: var(--paper);
}

.section-head p {
  grid-column: 2;
  max-width: 640px;
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.85;
}

.section-head.light p {
  color: rgba(255, 253, 248, 0.55);
}

/* ── Problem columns ─────────────────────────────────────────────────── */

.problem-grid {
  counter-reset: problem;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--line);
  border-left: 1px solid var(--line);
}

.problem-card {
  counter-increment: problem;
  position: relative;
  padding: 34px 32px;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: transparent;
}

.problem-card::before {
  content: '0' counter(problem);
  display: block;
  margin-bottom: 22px;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
  color: var(--accent-deep);
}

.problem-card::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 28px;
  height: 28px;
  border-left: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: var(--paper);
}

.seeker-card,
.hr-card {
  background: transparent;
}

.problem-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line-strong);
  background: var(--white);
  font-size: 18px;
  color: var(--accent);
}

.hr-card .problem-icon {
  color: var(--forest);
}

.problem-card h3 {
  margin: 20px 0 16px;
  font-family: var(--font-display);
  font-size: 28px;
  line-height: 1.15;
  letter-spacing: -0.02em;
}

.problem-card ul {
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.9;
}

.problem-card li {
  position: relative;
  padding-left: 16px;
  margin-bottom: 6px;
}

.problem-card li::before {
  content: '—';
  position: absolute;
  left: 0;
  color: var(--line-strong);
}

.problem-card b {
  color: var(--ink);
  font-weight: 600;
}

/* ── Feature grid ────────────────────────────────────────────────────── */

.feature-grid {
  counter-reset: feature;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-top: 1px solid var(--line);
  border-left: 1px solid var(--line);
}

.hr-features {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.feature-card {
  counter-increment: feature;
  position: relative;
  padding: 28px;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  background: transparent;
  transition: background 0.18s ease;
}

.feature-card::before {
  content: '0' counter(feature);
  display: block;
  margin-bottom: 22px;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
  color: var(--muted);
}

.feature-card:hover {
  background: var(--white);
}

.feature-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line-strong);
  background: var(--white) !important;
  color: var(--accent);
  font-size: 18px;
  margin-bottom: 18px;
}

.feature-card h3 {
  margin: 0 0 8px;
  font-family: var(--font-display);
  font-size: 24px;
  line-height: 1.15;
  letter-spacing: -0.01em;
}

.feature-card p {
  margin: 0;
  color: var(--ink-soft);
  font-size: 12.5px;
  line-height: 1.8;
}

.feature-tag {
  display: inline-block;
  margin-top: 18px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--muted);
}

.section-cta {
  margin-top: 34px;
}

.boundary {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-top: 24px;
  padding: 18px 20px;
  background: var(--accent-soft);
  border: 1px solid var(--accent);
  color: var(--accent-deep);
}

.boundary .anticon {
  font-size: 20px;
  margin-top: 2px;
}

.boundary strong {
  display: block;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  margin-bottom: 5px;
}

.boundary span {
  font-size: 12.5px;
  line-height: 1.75;
}

/* ── How it works ────────────────────────────────────────────────────── */

.steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-top: 1px solid rgba(255, 253, 248, 0.2);
  border-left: 1px solid rgba(255, 253, 248, 0.2);
}

.step {
  padding: 26px 24px;
  border-right: 1px solid rgba(255, 253, 248, 0.2);
  border-bottom: 1px solid rgba(255, 253, 248, 0.2);
}

.step-no {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-bottom: 18px;
  border: 1px solid var(--accent);
  color: var(--sand);
  font-family: var(--font-mono);
  font-size: 10px;
}

.step h3 {
  margin: 0 0 8px;
  font-family: var(--font-display);
  font-size: 24px;
  color: var(--paper);
  letter-spacing: -0.01em;
}

.step p {
  margin: 0;
  color: rgba(255, 253, 248, 0.55);
  font-size: 12px;
  line-height: 1.8;
}

.agent-strip {
  display: flex;
  flex-wrap: wrap;
  margin-top: 30px;
  border-top: 1px solid rgba(255, 253, 248, 0.2);
  border-left: 1px solid rgba(255, 253, 248, 0.2);
}

.agent-strip span {
  padding: 10px 14px;
  border-right: 1px solid rgba(255, 253, 248, 0.2);
  border-bottom: 1px solid rgba(255, 253, 248, 0.2);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(255, 253, 248, 0.66);
}

/* ── CTA ─────────────────────────────────────────────────────────────── */

.cta-band {
  position: relative;
  max-width: var(--max);
  margin: 0 auto 90px;
  padding: 48px 52px;
  background: var(--ink);
  color: var(--paper);
  border: 1px solid var(--ink);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 26px;
  flex-wrap: wrap;
  overflow: hidden;
}

.cta-band::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: 6px;
  background: var(--accent);
}

.cta-band h2 {
  margin: 0 0 8px;
  font-family: var(--font-display);
  font-size: clamp(30px, 3.4vw, 44px);
  font-weight: 600;
  letter-spacing: -0.03em;
  color: var(--paper);
}

.cta-band p {
  margin: 0;
  color: rgba(255, 253, 248, 0.55);
  font-size: 13px;
}

.cta-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.light-btn {
  background: transparent !important;
  color: var(--paper) !important;
  border-color: rgba(255, 253, 248, 0.36) !important;
}

.light-btn:hover {
  color: var(--ink) !important;
  background: var(--paper) !important;
  border-color: var(--paper) !important;
}

/* ── Footer ──────────────────────────────────────────────────────────── */

.footer {
  border-top: 1px solid var(--line);
  padding: 28px 24px;
  background: var(--paper);
}

.footer-inner {
  max-width: var(--max);
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 26px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--muted);
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 9px;
  font-family: var(--font-body);
  font-size: 12px;
  font-weight: 600;
  color: var(--ink);
}

.footer-brand .brand-mark {
  width: 26px;
  height: 26px;
  font-size: 8px;
}

.footer-links {
  display: flex;
  gap: 18px;
}

.footer-links a {
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--muted);
  text-decoration: none;
}

.footer-links a:hover {
  color: var(--accent-deep);
}

.footer-note {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.08em;
  color: var(--muted);
}

/* ── Responsive ──────────────────────────────────────────────────────── */

@media (max-width: 1080px) {
  .hero-inner {
    grid-template-columns: 1fr;
    gap: 48px;
  }
  .hero-preview {
    max-width: 560px;
  }
  .hero-stamp {
    display: none;
  }
  .feature-grid,
  .hr-features {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .steps {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .section-head {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .section-head h2,
  .section-head p {
    grid-column: 1;
  }
  .section-kicker {
    padding-top: 0;
  }
}

@media (max-width: 760px) {
  .nav-links {
    display: none;
  }
  .brand-sub {
    display: none;
  }
  .hero {
    padding: 64px 18px 72px;
  }
  .hero h1 {
    font-size: clamp(32px, 8vw, 46px);
  }
  .hero-index {
    display: none;
  }
  .hero-line {
    white-space: normal;
  }
  .floating-1 {
    left: 0;
  }
  .floating-2 {
    right: 0;
  }
  .section {
    padding: 64px 18px;
  }
  .problem-grid,
  .feature-grid,
  .hr-features,
  .steps {
    grid-template-columns: 1fr;
  }
  .cta-band {
    margin: 0 18px 56px;
    padding: 32px 24px;
  }
  .footer-note {
    margin-left: 0;
  }
}

@media (max-width: 520px) {
  .nav-inner {
    padding: 0 14px;
    gap: 10px;
  }
  .brand-name {
    font-size: 18px;
  }
  .nav-actions .login-btn {
    height: 32px !important;
    padding: 0 10px !important;
    font-size: 11px !important;
  }
  .hero-actions .ant-btn {
    width: 100%;
  }
  .preview-score {
    gap: 16px;
  }
  .score-ring {
    width: 88px;
    height: 88px;
  }
  .score-ring strong {
    font-size: 34px;
  }
  .score-ring span {
    bottom: 19px;
  }
  .floating-card {
    display: none;
  }
}

</style>
