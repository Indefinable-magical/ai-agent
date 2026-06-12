import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUser, getUserPreference, updateUserPreference } from '@/api/userController'

const DEFAULT_LOGIN_USER: API.LoginUserVO = {
  userName: '未登录',
}

const DEFAULT_USER_PREFERENCE: API.UserPreferenceVO = {
  theme: 'cyber',
  defaultAiApp: 'home',
  defaultRiskPreference: 'balanced',
  defaultRiskPreferenceName: '平衡',
  conversationDensity: 'comfortable',
}

export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>({ ...DEFAULT_LOGIN_USER })
  const userPreference = ref<API.UserPreferenceVO>({ ...DEFAULT_USER_PREFERENCE })

  async function fetchLoginUser() {
    try {
      const res = await getLoginUser()
      if (res.data.code === 0 && res.data.data) {
        loginUser.value = res.data.data
        return res.data.data
      }
    } catch (error) {
      // Keep the guest state when the current visitor is not logged in.
    }

    loginUser.value = { ...DEFAULT_LOGIN_USER }
    userPreference.value = { ...DEFAULT_USER_PREFERENCE }
    return loginUser.value
  }

  function setLoginUser(newLoginUser: API.LoginUserVO = {}) {
    loginUser.value = {
      ...DEFAULT_LOGIN_USER,
      ...newLoginUser,
    }
  }

  function setUserPreference(newPreference: API.UserPreferenceVO = {}) {
    userPreference.value = {
      ...DEFAULT_USER_PREFERENCE,
      ...newPreference,
    }
  }

  async function fetchUserPreference() {
    if (!loginUser.value?.id) {
      setUserPreference()
      return userPreference.value
    }
    try {
      const res = await getUserPreference()
      if (res.data.code === 0 && res.data.data) {
        setUserPreference(res.data.data)
      }
    } catch (error) {
      setUserPreference()
    }
    return userPreference.value
  }

  async function saveUserPreference(preference: API.UserPreferenceUpdateRequest) {
    const res = await updateUserPreference(preference)
    if (res.data.code === 0 && res.data.data) {
      setUserPreference(res.data.data)
    }
    return res
  }

  return {
    loginUser,
    userPreference,
    setLoginUser,
    setUserPreference,
    fetchLoginUser,
    fetchUserPreference,
    saveUserPreference,
  }
})
