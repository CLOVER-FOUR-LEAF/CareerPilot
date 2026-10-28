<template>
  <a-config-provider :theme="theme">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { theme } from './theme'
</script>

<style>
#app {
  min-height: 100vh;
}
</style>
