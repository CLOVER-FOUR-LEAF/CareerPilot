<template>
  <div class="login-page" :class="portal === 'recruiter' ? 'recruiter-bg' : 'seeker-bg'">
    <div class="login-shell">
      <!-- 左侧品牌与能力说明 -->
      <aside class="brand-panel">
        <router-link class="back-link" to="/">
          <ArrowLeftOutlined /> 返回官网首页
        </router-link>
        <div class="brand-head">
          <span class="brand-mark">CP</span>
          <span class="brand-name">CareerPilot</span>
        </div>
        <h1>{{ isRecruiterPortal ? '招聘方登录' : '求职者登录' }}</h1>
        <p class="brand-desc">
          {{ isRecruiterPortal
            ? '导入你已收到的候选人简历，批量做岗位匹配，并把证据缺口转成面试官可直接使用的验证问题。'
            : '从简历竞争力分析开始，逐步完成岗位匹配、简历优化、模拟面试与职业规划。' }}
        </p>
        <ul class="brand-points">
          <li v-for="point in points" :key="point">
            <CheckCircleOutlined /> {{ point }}
          </li>
        </ul>
        <div class="switch-portal">
          <template v-if="isRecruiterPortal">
            我是求职者？
            <router-link :to="{ path: '/login', query: { role: 'seeker' } }">前往求职者登录</router-link>
          </template>
          <template v-else>
            我是招聘方？
            <router-link :to="{ path: '/login', query: { role: 'recruiter' } }">前往招聘方登录</router-link>
          </template>
        </div>
      </aside>

      <!-- 右侧表单 -->
      <section class="form-panel">
        <div class="form-head">
          <h2>{{ isRecruiterPortal ? '进入招聘工作台' : '进入求职工作台' }}</h2>
          <p>{{ isRecruiterPortal ? '使用招聘方账号登录，支持自助注册' : '登录后继续你的求职链路' }}</p>
        </div>

        <a-form
          :model="formState"
          @finish="handleLogin"
          layout="vertical"
          :rules="rules"
        >
          <a-form-item label="邮箱" name="email">
            <a-input
              v-model:value="formState.email"
              placeholder="请输入邮箱"
              size="large"
              allow-clear
            >
              <template #prefix><MailOutlined /></template>
            </a-input>
          </a-form-item>
          <a-form-item label="密码" name="password">
            <a-input-password
              v-model:value="formState.password"
              placeholder="请输入密码"
              size="large"
            >
              <template #prefix><LockOutlined /></template>
            </a-input-password>
          </a-form-item>
          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              size="large"
              block
              :loading="loading"
            >
              {{ isRecruiterPortal ? '登录招聘方工作台' : '登录求职者工作台' }}
            </a-button>
          </a-form-item>
        </a-form>

        <div v-if="!isRecruiterPortal" class="form-footer">
          还没有账号？
          <router-link :to="{ path: '/register', query: { role: 'seeker' } }">立即注册</router-link>
        </div>
        <template v-else>
          <a-alert
            type="info"
            show-icon
            message="招聘方账号可以自助注册"
            description="注册招聘方账号后即可导入候选人简历、创建岗位 JD 并做匹配与洞察。只有管理员账号需要平台授予。"
            class="recruiter-tip"
          />
          <div class="form-footer">
            还没有招聘方账号？
            <router-link :to="{ path: '/register', query: { role: 'recruiter' } }">立即注册</router-link>
          </div>
        </template>

        <div class="form-footer admin-entry">
          <router-link to="/">返回首页</router-link>
          <span class="divider">·</span>
          <a :href="adminUrl">平台管理端</a>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  LockOutlined,
  MailOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const portal = computed<'seeker' | 'recruiter'>(() =>
  route.query.role === 'recruiter' ? 'recruiter' : 'seeker',
)
const isRecruiterPortal = computed(() => portal.value === 'recruiter')

const adminUrl = import.meta.env.VITE_ADMIN_URL
  || `${window.location.protocol}//${window.location.hostname}:3100`

const points = computed(() => isRecruiterPortal.value
  ? ['只分析你主动导入的候选人简历', '多份简历按同一 JD 批量对比', '证据缺口 → 验证问题', '所有判定留痕，可复盘']
  : ['ATS 多维评分与改进建议', '简历 × JD 三段式匹配', '按差距生成多轮模拟面试', '求职信与职业规划一站式产出'],
)

const formState = reactive({
  email: '',
  password: '',
})

const rules = {
  email: [
    { required: true, message: '请输入邮箱' },
    { type: 'email' as const, message: '邮箱格式不正确' },
  ],
  password: [
    { required: true, message: '请输入密码' },
    { min: 6, message: '密码至少6位' },
  ],
}

watch(portal, () => {
  formState.email = ''
  formState.password = ''
})

async function handleLogin() {
  loading.value = true
  try {
    const res = await userStore.login(formState.email, formState.password)
    const role = res.user?.role || 'USER'

    // 管理员使用独立前端：这里不保留用户端会话，避免两套前端状态互相干扰
    if (role === 'ADMIN') {
      message.info('管理员请使用管理后台')
      await userStore.logout()
      window.location.href = adminUrl
      return
    }

    if (isRecruiterPortal.value && role !== 'RECRUITER') {
      message.error('该账号还不是招聘方，请使用求职者入口登录')
      await userStore.logout()
      return
    }
    if (!isRecruiterPortal.value && role !== 'USER') {
      message.warning('该账号是招聘方账号，请使用招聘者入口登录')
      await userStore.logout()
      return
    }

    message.success('登录成功')

    // 登录前被守卫拦下的页面，通过 ?redirect= 带回来；
    // 跨端的 redirect 直接忽略，落到该端自己的工作台。
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const allowed = isRecruiterPortal.value
      ? redirect.startsWith('/hr')
      : redirect.startsWith('/seeker')
    await router.replace(allowed && redirect ? redirect : userStore.homePath)
  } catch (err) {
    const msg = err instanceof Error ? err.message : ''
    if (!msg) message.error('登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 42px 22px;
  overflow: hidden;
}

.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--line) 1px, transparent 1px),
    linear-gradient(90deg, var(--line) 1px, transparent 1px);
  background-size: 92px 92px;
  opacity: 0.38;
  mask-image: radial-gradient(circle at center, black 0%, transparent 78%);
  -webkit-mask-image: radial-gradient(circle at center, black 0%, transparent 78%);
  pointer-events: none;
}

.seeker-bg {
  background:
    radial-gradient(circle at 12% 16%, rgba(228, 87, 46, 0.11), transparent 34%),
    var(--paper);
}

.recruiter-bg {
  background:
    radial-gradient(circle at 12% 16%, rgba(31, 61, 43, 0.13), transparent 34%),
    var(--paper);
}

.login-shell {
  position: relative;
  z-index: 1;
  width: min(1040px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  background: var(--white);
  border: 1px solid var(--line-strong);
  box-shadow: 12px 12px 0 rgba(23, 23, 20, 0.09);
}

.brand-panel {
  position: relative;
  padding: 46px 42px;
  display: flex;
  flex-direction: column;
  color: var(--paper);
  background-color: var(--ink);
  background-image:
    radial-gradient(circle at 0% 0%, rgba(228, 87, 46, 0.2), transparent 42%),
    linear-gradient(160deg, rgba(255, 253, 248, 0.03), transparent 55%);
  overflow: hidden;
}

.brand-panel::after {
  content: 'INTAKE / VERIFY / DECIDE';
  position: absolute;
  right: -70px;
  bottom: 90px;
  transform: rotate(-90deg);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.28em;
  color: rgba(255, 253, 248, 0.18);
  pointer-events: none;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  width: fit-content;
  color: rgba(255, 253, 248, 0.52);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  text-decoration: none;
}

.back-link:hover {
  color: var(--sand);
}

.brand-head {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-top: 46px;
}

.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 253, 248, 0.3);
  color: var(--sand);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.brand-name {
  font-family: var(--font-display);
  font-size: 23px;
  letter-spacing: -0.01em;
}

.brand-panel h1 {
  margin: 34px 0 12px;
  font-family: var(--font-display);
  font-size: clamp(36px, 4vw, 48px);
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.04em;
  color: var(--paper);
}

.brand-desc {
  margin: 0;
  max-width: 340px;
  color: rgba(255, 253, 248, 0.58);
  font-size: 13px;
  line-height: 1.85;
}

.brand-points {
  list-style: none;
  margin: 34px 0 0;
  padding: 0;
  display: grid;
  gap: 11px;
  border-top: 1px solid rgba(255, 253, 248, 0.14);
  padding-top: 20px;
}

.brand-points li {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  color: rgba(255, 253, 248, 0.68);
  font-size: 12px;
  line-height: 1.6;
}

.brand-points .anticon {
  margin-top: 3px;
  color: var(--sand);
  font-size: 11px;
}

.switch-portal {
  margin-top: auto;
  padding-top: 34px;
  color: rgba(255, 253, 248, 0.45);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.04em;
}

.switch-portal a {
  color: var(--sand);
  text-decoration: none;
  margin-left: 4px;
  border-bottom: 1px solid rgba(232, 201, 160, 0.5);
}

.switch-portal a:hover {
  color: var(--paper);
}

.form-panel {
  position: relative;
  padding: 56px 54px 40px;
  background: var(--white);
}

.form-panel::before {
  content: 'ACCESS';
  position: absolute;
  top: 26px;
  right: 30px;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.22em;
  color: var(--muted);
}

.form-head {
  margin-bottom: 30px;
}

.form-head h2 {
  margin: 0 0 7px;
  font-family: var(--font-display);
  font-size: clamp(30px, 3.2vw, 40px);
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.03em;
}

.form-head p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}

.form-footer {
  margin-top: 20px;
  text-align: center;
  color: var(--muted);
  font-size: 12px;
}

.form-footer a {
  color: var(--accent-deep);
  text-decoration: none;
  border-bottom: 1px solid rgba(185, 58, 24, 0.4);
}

.admin-entry {
  margin-top: 28px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--muted);
}

.divider {
  margin: 0 8px;
  color: var(--line-strong);
}

.recruiter-tip {
  margin-top: 20px;
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 540px;
  }
  .brand-panel {
    padding: 30px 28px 26px;
  }
  .brand-head {
    margin-top: 24px;
  }
  .brand-panel h1 {
    margin-top: 22px;
    font-size: 32px;
  }
  .brand-points {
    display: none;
  }
  .switch-portal {
    margin-top: 22px;
    padding-top: 0;
  }
  .brand-panel::after {
    display: none;
  }
  .form-panel {
    padding: 34px 28px 30px;
  }
}

@media (max-width: 480px) {
  .login-page {
    padding: 20px 14px;
  }
  .form-panel {
    padding: 30px 22px 26px;
  }
}

</style>
