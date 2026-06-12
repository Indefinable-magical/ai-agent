import { computed, ref } from 'vue'
import { useLoginUserStore } from '@/stores/loginUser'

type InterfaceTheme = 'cyber' | 'light'

const THEME_STORAGE_KEY = 'ai_agent_interface_theme'

const getInitialTheme = (): InterfaceTheme => {
  if (typeof window === 'undefined') {
    return 'cyber'
  }
  const savedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
  return savedTheme === 'light' ? 'light' : 'cyber'
}

const interfaceTheme = ref<InterfaceTheme>(getInitialTheme())

const normalizeTheme = (theme?: string): InterfaceTheme => {
  return theme === 'light' ? 'light' : 'cyber'
}

export const setInterfaceTheme = (theme?: string) => {
  interfaceTheme.value = normalizeTheme(theme)
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(THEME_STORAGE_KEY, interfaceTheme.value)
  }
}

export const useInterfaceTheme = () => {
  const themeClass = computed(() => `theme-${interfaceTheme.value}`)
  const themeButtonText = computed(() => (interfaceTheme.value === 'cyber' ? '浅色风格' : '赛博风格'))

  const toggleTheme = async () => {
    const nextTheme = interfaceTheme.value === 'cyber' ? 'light' : 'cyber'
    setInterfaceTheme(nextTheme)
    const loginUserStore = useLoginUserStore()
    if (!loginUserStore.loginUser.id) {
      return
    }
    try {
      await loginUserStore.saveUserPreference({ theme: nextTheme })
    } catch (error) {
      // 主题已经在本地生效；后端同步失败时不打断用户操作。
    }
  }

  return {
    interfaceTheme,
    themeClass,
    themeButtonText,
    toggleTheme,
  }
}
