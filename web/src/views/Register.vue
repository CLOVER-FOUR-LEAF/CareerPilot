<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1>CareerPilot</h1>
        <p>创建您的账号</p>
      </div>
      <a-form
        :model="formState"
        @finish="handleRegister"
        layout="vertical"
        :rules="rules"
      >
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
          <router-link to="/login">返回登录</router-link>
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
  nickname: '',
  email: '',
  password: '',
  confirmPassword: '',
})

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
    })
    message.success('注册成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.replace(redirect || '/dashboard')
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
