<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1>CareerPilot</h1>
        <p>平台管理后台 · 仅限管理员</p>
      </div>
      <a-form :model="formState" layout="vertical" @finish="handleLogin">
        <a-form-item label="管理员邮箱" name="email">
          <a-input v-model:value="formState.email" placeholder="请输入管理员邮箱" size="large">
            <template #prefix><MailOutlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item label="密码" name="password">
          <a-input-password v-model:value="formState.password" placeholder="请输入密码" size="large">
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" block :loading="loading">
            登录管理后台
          </a-button>
        </a-form-item>
      </a-form>
      <div class="login-footer">
        普通用户 / 招聘方请前往
        <a :href="userWebUrl">用户端首页</a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { LockOutlined, MailOutlined } from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)

const userWebUrl = `${window.location.protocol}//${window.location.hostname}:3000`

const formState = reactive({ email: '', password: '' })

async function handleLogin() {
  loading.value = true
  try {
    await auth.login(formState.email, formState.password)
    message.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect || '/dashboard')
  } catch (err) {
    message.error(err instanceof Error ? err.message : '登录失败')
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
  padding: 40px 20px;
  background:
    radial-gradient(circle at 15% 10%, rgba(228, 87, 46, 0.16), transparent 34%),
    radial-gradient(circle at 85% 90%, rgba(31, 61, 43, 0.1), transparent 34%),
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
  opacity: 0.4;
  mask-image: radial-gradient(circle at center, black 0%, transparent 78%);
  -webkit-mask-image: radial-gradient(circle at center, black 0%, transparent 78%);
  pointer-events: none;
}

.login-card {
  position: relative;
  z-index: 1;
  width: 440px;
  max-width: 100%;
  padding: 44px 40px 34px;
  background: var(--white);
  border: 1px solid var(--line-strong);
  box-shadow: 10px 10px 0 rgba(23, 23, 20, 0.1);
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
  margin-bottom: 30px;
}

.login-header h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 40px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -0.04em;
  color: var(--ink);
}

.login-header p {
  margin: 9px 0 0;
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
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

@media (max-width: 520px) {
  .login-container {
    padding: 22px 14px;
  }
  .login-card {
    padding: 34px 24px 28px;
  }
  .login-header h1 {
    font-size: 34px;
  }
}

</style>
