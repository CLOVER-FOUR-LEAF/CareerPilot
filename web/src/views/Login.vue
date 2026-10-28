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
          <p>{{ isRecruiterPortal ? '使用平台管理员开通的招聘方账号登录' : '登录后继续你的求职链路' }}</p>
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
          <router-link :to="{ path: '/register' }">立即注册</router-link>
        </div>
        <a-alert
          v-else
          type="info"
          show-icon
          message="招聘方账号由平台管理员开通"
          description="自助注册一律是求职者账号。需要招聘方权限时，请联系平台管理员为你提升角色。"
          class="recruiter-tip"
        />

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
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  position: relative;
  overflow: hidden;
}
.seeker-bg {
  background:
    radial-gradient(800px 500px at 8% 10%, rgba(22, 119, 255, 0.22), transparent 65%),
    radial-gradient(700px 500px at 92% 90%, rgba(114, 46, 209, 0.18), transparent 65%),
    linear-gradient(135deg, #f8fbff 0%, #eef4ff 100%);
}
.recruiter-bg {
  background:
    radial-gradient(800px 500px at 10% 12%, rgba(114, 46, 209, 0.2), transparent 65%),
    radial-gradient(700px 500px at 90% 88%, rgba(19, 194, 194, 0.16), transparent 65%),
    linear-gradient(135deg, #fbf8ff 0%, #f3f0ff 100%);
}
.login-shell {
  width: min(980px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  border-radius: 24px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 40px 90px -50px rgba(15, 23, 42, 0.5);
  border: 1px solid rgba(15, 23, 42, 0.06);
}
.brand-panel {
  position: relative;
  padding: 44px 40px;
  background: linear-gradient(150deg, #0b1220 0%, #13224a 60%, #2b1450 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
}
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: rgba(226, 232, 240, 0.72);
  font-size: 12px;
  text-decoration: none;
  width: fit-content;
}
.back-link:hover {
  color: #fff;
}
.brand-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 34px;
}
.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1677ff, #722ed1);
  font-size: 13px;
  font-weight: 700;
}
.brand-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.01em;
}
.brand-panel h1 {
  margin: 28px 0 10px;
  font-size: 30px;
  letter-spacing: -0.02em;
}
.brand-desc {
  margin: 0;
  color: #94a3b8;
  font-size: 14px;
  line-height: 1.85;
}
.brand-points {
  list-style: none;
  margin: 28px 0 0;
  padding: 0;
  display: grid;
  gap: 12px;
  color: #cbd5e1;
  font-size: 13px;
}
.brand-points li {
  display: flex;
  align-items: center;
  gap: 8px;
}
.brand-points .anticon {
  color: #4ade80;
}
.switch-portal {
  margin-top: auto;
  padding-top: 32px;
  color: #94a3b8;
  font-size: 13px;
}
.switch-portal a {
  color: #69b1ff;
  margin-left: 4px;
}
.form-panel {
  padding: 52px 48px 36px;
}
.form-head h2 {
  margin: 0 0 6px;
  font-size: 24px;
  letter-spacing: -0.02em;
}
.form-head p {
  margin: 0 0 28px;
  color: #64748b;
  font-size: 13px;
}
.form-footer {
  text-align: center;
  color: #64748b;
  font-size: 13px;
  margin-top: 18px;
}
.form-footer a {
  color: #1677ff;
}
.admin-entry {
  margin-top: 26px;
  padding-top: 18px;
  border-top: 1px solid #f1f5f9;
  color: #94a3b8;
  font-size: 12px;
}
.divider {
  margin: 0 8px;
}
.recruiter-tip {
  margin-top: 18px;
}
@media (max-width: 860px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 520px;
  }
  .brand-panel {
    padding: 30px 28px 26px;
  }
  .brand-panel h1 {
    margin-top: 20px;
    font-size: 24px;
  }
  .brand-points {
    display: none;
  }
  .switch-portal {
    margin-top: 20px;
    padding-top: 0;
  }
  .form-panel {
    padding: 32px 28px 28px;
  }
}
</style>
