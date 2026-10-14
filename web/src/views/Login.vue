<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1>CareerPilot</h1>
        <p>智能求职领航员</p>
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
            登录
          </a-button>
        </a-form-item>
        <div class="login-footer">
          还没有账号？
          <router-link to="/register">立即注册</router-link>
        </div>
      </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { MailOutlined, LockOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

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

async function handleLogin() {
  loading.value = true
  try {
    await userStore.login(formState.email, formState.password)
    message.success('登录成功')
    // 登录前被守卫拦下的页面，通过 ?redirect= 带回来
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect || '/dashboard')
  } catch (err) {
    const msg = err instanceof Error ? err.message : ''
    if (!msg) message.error('登录失败，请稍后重试')
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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}
.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.login-header h1 {
  font-size: 28px;
  color: #1890ff;
  margin: 0;
}
.login-header p {
  color: #999;
  margin-top: 8px;
}
.login-footer {
  text-align: center;
  color: #666;
}
</style>
