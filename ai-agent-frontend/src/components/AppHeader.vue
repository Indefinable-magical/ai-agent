<template>
  <div class="user-panel">
    <div v-if="loginUserStore.loginUser.id">
      <a-dropdown placement="bottomRight" :trigger="['hover']">
        <a-space class="user-trigger" size="small">
          <a-avatar :src="loginUserStore.loginUser.userAvatar">
            {{ userInitial }}
          </a-avatar>
          <span class="user-name">{{ loginUserStore.loginUser.userName || '普通用户' }}</span>
        </a-space>
        <template #overlay>
          <a-menu>
            <a-menu-item @click="goToProfile">
              <UserOutlined />
              用户资料
            </a-menu-item>
            <a-menu-item v-if="isAdmin" @click="goToAdmin">
              <TeamOutlined />
              用户管理
            </a-menu-item>
            <a-menu-item v-if="isAdmin" @click="goToStockPoolAdmin">
              <DatabaseOutlined />
              股票池管理
            </a-menu-item>
            <a-menu-divider />
            <a-menu-item @click="toggleTheme">
              <BgColorsOutlined />
              {{ themeButtonText }}
            </a-menu-item>
            <a-menu-divider />
            <a-menu-item @click="doLogout">
              <LogoutOutlined />
              退出登录
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
    <div v-else>
      <a-button type="primary" @click="goToLogin">登录</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { BgColorsOutlined, DatabaseOutlined, LogoutOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons-vue'
import { userLogout } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import { useInterfaceTheme } from '@/composables/useInterfaceTheme'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const { themeButtonText, toggleTheme } = useInterfaceTheme()

const userInitial = computed(() => {
  const userName = loginUserStore.loginUser.userName
  return userName ? userName.charAt(0).toUpperCase() : 'U'
})

const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

const goToLogin = () => {
  router.push({
    path: '/user/login',
    query: {
      redirect: router.currentRoute.value.fullPath,
    },
  })
}

const goToProfile = () => {
  router.push('/user/profile')
}

const goToAdmin = () => {
  router.push('/admin/userManage')
}

const goToStockPoolAdmin = () => {
  router.push('/admin/stockPool')
}

const doLogout = async () => {
  const res = await userLogout()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({})
    message.success('退出登录成功')
    await router.push('/user/login')
    return
  }
  message.error(`退出登录失败，${res.data.message || '请稍后重试'}`)
}
</script>

<style scoped>
.user-panel {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
}

.user-trigger {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.96);
  color: #1f2937;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  transition: background-color 0.2s ease, box-shadow 0.2s ease;
}

.user-trigger:hover {
  background: #ffffff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.16);
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}
</style>
