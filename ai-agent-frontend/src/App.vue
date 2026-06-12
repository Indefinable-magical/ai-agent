<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from '@/components/AppHeader.vue'

const route = useRoute()

const showFloatingUserPanel = computed(() => {
  return !Boolean(route.meta.hideUserPanel) && !Boolean(route.meta.embedUserPanel)
})
</script>

<template>
  <div class="app-shell">
    <router-view />
    <div v-if="showFloatingUserPanel" class="floating-user-panel">
      <AppHeader />
    </div>
  </div>
</template>

<style>
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html,
body {
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif;
  font-size: 16px;
  color: #333;
  background-color: #f0f2f5;
  width: 100%;
  height: 100%;
  overflow-x: hidden;
}

#app {
  width: 100%;
  height: 100%;
}

.app-shell {
  min-height: 100%;
}

.floating-user-panel {
  position: fixed;
  top: 20px;
  right: 24px;
  z-index: 1100;
  color: #fff;
}

a {
  text-decoration: none;
  color: inherit;
}

button {
  cursor: pointer;
}

@media (max-width: 768px) {
  html,
  body {
    font-size: 15px;
  }

  .floating-user-panel {
    top: 14px;
    right: 16px;
  }
}

@media (max-width: 480px) {
  html,
  body {
    font-size: 14px;
  }

  .floating-user-panel {
    top: 12px;
    right: 12px;
  }
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #aaa;
}
</style>
