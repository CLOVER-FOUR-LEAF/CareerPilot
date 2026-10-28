<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1>CareerPilot</h1>
        <p>{{ formState.role === 'RECRUITER' ? '创建招聘方账号' : '创建求职者账号' }}</p>
      </div>
      <a-alert
        type="info"
        show-icon
        message="求职者与招聘方都可以自助注册"
        description="求职者用于简历分析、岗位匹配与模拟面试；招聘方用于导入候选人简历、岗位匹配与深度洞察。管理员账号仍由平台授予。"
        style="margin-bottom: 20px; text-align: left"
      />
      <a-form
        :model="formState"
        @finish="handleRegister"
        layout="vertical"
        :rules="rules"
      >
        <a-form-item label="账号类型" name="role">
          <a-radio-group v-model:value="formState.role" button-style="solid" class="role-group">
            <a-radio-button value="USER">我是求职者</a-radio-button>
            <a-radio-button value="RECRUITER">我是招聘方</a-radio-button>
          </a-radio-group>
          <div class="role-hint">{{ roleHint }}</div>
        </a-form-item>
        <a-form-item label="昵称" name="nickname">
          <a-input v-model:value="formState.nickname" placeholder="请输入昵称" size="large" />
        </a-form-item>
        <a-form-item label="邮箱" name="email">
          <a-input v-model:value="formState.email" placeholder="请输入邮箱" size="large">
            <template #prefix><MailOutlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item label="密码" name="password">
          <a-input-password v-model:value="formState.password" placeholder="8-32位，含大小写字母与数字" size="large">
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item label="确认密码" name="confirmPassword">
          <a-input-password v-model:value="formState.confirmPassword" placeholder="请再次输入密码" size="large">
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" block :loading="loading">
            注册
          </a-button>
        </a-form-item>
        <div class="login-footer">
          已有账号？
          <router-link :to="{ path: '/login', query: { role: formState.role === 'RECRUITER' ? 'recruiter' : 'seeker' } }">
            返回登录
          </router-link>
          <span class="divider">·</span>
          <router-link to="/">返回首页</router-link>
        </div>
      </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { MailOutlined, LockOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

type RegisterRole = 'USER' | 'RECRUITER'

const formState = reactive({
  role: (route.query.role === 'recruiter' ? 'RECRUITER' : 'USER') as RegisterRole,
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
})

const roleHint = computed(() => formState.role === 'RECRUITER'
  ? '导入你已收到的候选人简历，创建岗位 JD，做批量匹配与深度洞察。'
  : '分析简历竞争力、匹配目标岗位、优化表达并练习模拟面试。')

// 契约 §1.1：^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,32}$
const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,32}$/

const rules = {
  nickname: [{ max: 20, message: '昵称最多 20 个字符' }],
  email: [
    { required: true, message: '请输入邮箱' },
    { type: 'email' as const, message: '邮箱格式不正确' },
  ],
  password: [
    { required: true, message: '请输入密码' },
    { validator: (_rule: unknown, value: string) => {
      if (!value || PASSWORD_PATTERN.test(value)) return Promise.resolve()
      return Promise.reject('密码需 8-32 位，且同时包含大小写字母与数字')
    } },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码' },
    ({ getFieldValue }: { getFieldValue: (name: string) => string }) => ({
      validator(_: unknown, value: string) {
        if (value && value !== getFieldValue('password')) {
          return Promise.reject(new Error('两次密码不一致'))
        }
        return Promise.resolve()
      },
    }),
  ],
}

async function handleRegister() {
  loading.value = true
  try {
    // 契约 §1.1：注册即登录，直接进首页
    await userStore.register({
      email: formState.email,
      password: formState.password,
      nickname: formState.nickname || undefined,
      role: formState.role,
    })
    message.success('注册成功')
    const home = formState.role === 'RECRUITER' ? '/hr/dashboard' : '/seeker/dashboard'
    const prefix = formState.role === 'RECRUITER' ? '/hr' : '/seeker'
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const allowed = redirect.startsWith(prefix)
    await router.replace(allowed && redirect ? redirect : home)
  } catch (err) {
    const msg = err instanceof Error ? err.message : ''
    if (!msg) message.error('注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 42px 20px;
  background:
    radial-gradient(circle at 12% 16%, rgba(228, 87, 46, 0.11), transparent 34%),
    var(--paper);
  overflow: hidden;
}

.login-container::before {
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

.login-card {
  position: relative;
  z-index: 1;
  width: 470px;
  max-width: 100%;
  padding: 42px 40px 34px;
  background: var(--white);
  border: 1px solid var(--line-strong);
  box-shadow: 10px 10px 0 rgba(23, 23, 20, 0.09);
}

.login-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--accent);
}

.login-header {
  margin-bottom: 28px;
  text-align: left;
}

.login-header h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 38px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.04em;
  color: var(--ink);
}

.login-header p {
  margin: 8px 0 0;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--muted);
}

.login-footer {
  margin-top: 20px;
  text-align: center;
  color: var(--muted);
  font-size: 12px;
}

.login-footer a {
  color: var(--accent-deep);
  text-decoration: none;
  border-bottom: 1px solid rgba(185, 58, 24, 0.4);
}

.divider {
  margin: 0 8px;
  color: var(--line-strong);
}

@media (max-width: 520px) {
  .login-container {
    padding: 22px 14px;
  }
  .login-card {
    padding: 34px 24px 28px;
  }
  .login-header h1 {
    font-size: 32px;
  }
}


.role-group {
  display: flex;
  width: 100%;
}
.role-group :deep(.ant-radio-button-wrapper) {
  flex: 1;
  text-align: center;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  border-color: var(--line-strong);
}
.role-group :deep(.ant-radio-button-wrapper-checked) {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--white);
}
.role-hint {
  margin-top: 8px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
}
</style>
