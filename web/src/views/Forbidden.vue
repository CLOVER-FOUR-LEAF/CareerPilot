<template>
  <div class="forbidden">
    <a-result
      status="403"
      title="403"
      sub-title="当前账号没有访问这个页面的权限"
    >
      <template #extra>
        <a-button type="primary" @click="goHome">返回我的工作台</a-button>
      </template>
    </a-result>
    <div class="hint">
      当前角色：{{ userStore.roleLabel }}
      <template v-if="userStore.role === 'USER'">
        · 招聘方功能需要由平台管理员为你开通 RECRUITER 权限
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

function goHome() {
  router.replace(userStore.homePath)
}
</script>

<style scoped>
.forbidden {
  min-height: calc(100vh - 200px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.hint {
  color: var(--muted);
  font-size: 13px;
}
</style>
