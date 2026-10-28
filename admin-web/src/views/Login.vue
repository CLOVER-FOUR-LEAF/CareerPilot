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
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2a44 0%, #3b1d3f 100%);
}
.login-card {
  width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
}
.login-header {
  text-align: center;
  margin-bottom: 28px;
}
.login-header h1 {
  font-size: 26px;
  color: #ff4d4f;
  margin: 0;
}
.login-header p {
  color: #999;
  margin-top: 8px;
}
.login-footer {
  text-align: center;
  color: #666;
  font-size: 13px;
}
</style>
