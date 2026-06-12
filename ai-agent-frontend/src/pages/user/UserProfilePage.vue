<template>
  <div class="profile-page" :class="themeClass">
    <section class="profile-shell">
      <aside class="profile-summary">
        <a-avatar class="summary-avatar" :src="formState.userAvatar" :size="96">
          {{ userInitial }}
        </a-avatar>
        <h1>{{ formState.userName || '普通用户' }}</h1>
        <p>{{ loginUserStore.loginUser.userAccount }}</p>
        <a-tag :color="loginUserStore.loginUser.userRole === 'admin' ? 'green' : 'blue'">
          {{ loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '普通用户' }}
        </a-tag>
      </aside>

      <main class="profile-panel">
        <div class="panel-heading">
          <div>
            <h2>用户资料</h2>
          </div>
          <a-button @click="router.push('/')">返回首页</a-button>
        </div>

        <a-form
          class="profile-form"
          layout="vertical"
          :model="formState"
          autocomplete="off"
          @finish="handleSubmit"
        >
          <a-divider orientation="left">基础信息</a-divider>
          <a-form-item
            label="昵称"
            name="userName"
            :rules="[{ max: 80, message: '昵称不能超过 80 个字符' }]"
          >
            <a-input v-model:value="formState.userName" size="large" placeholder="请输入昵称" />
          </a-form-item>
          <a-form-item
            label="头像地址"
            name="userAvatar"
            :rules="[{ max: 1024, message: '头像地址不能超过 1024 个字符' }]"
          >
            <div class="avatar-upload-row">
              <a-input v-model:value="formState.userAvatar" size="large" placeholder="请输入图片 URL" />
              <a-upload
                accept="image/*"
                :before-upload="handleAvatarUpload"
                :show-upload-list="false"
              >
                <a-button size="large" :loading="uploadingAvatar">
                  <template #icon><UploadOutlined /></template>
                  上传头像
                </a-button>
              </a-upload>
            </div>
          </a-form-item>
          <a-form-item
            label="简介"
            name="userProfile"
            :rules="[{ max: 512, message: '简介不能超过 512 个字符' }]"
          >
            <a-textarea
              v-model:value="formState.userProfile"
              :rows="4"
              show-count
              :maxlength="512"
              placeholder="写一点关于你的介绍"
            />
          </a-form-item>

          <a-divider orientation="left">修改密码</a-divider>
          <a-form-item label="当前密码" name="oldPassword">
            <a-input-password
              v-model:value="formState.oldPassword"
              size="large"
              placeholder="不修改密码可留空"
            />
          </a-form-item>
          <a-form-item label="新密码" name="newPassword" :rules="passwordRules">
            <a-input-password
              v-model:value="formState.newPassword"
              size="large"
              placeholder="至少 6 位"
            />
            <div v-if="formState.newPassword" class="password-strength">
              <div class="strength-meta">
                <span>密码强度：{{ passwordStrength.text }}</span>
                <span>{{ passwordStrength.tip }}</span>
              </div>
              <div class="strength-track">
                <div
                  class="strength-fill"
                  :style="{ width: `${passwordStrength.percent}%`, background: passwordStrength.color }"
                />
              </div>
            </div>
          </a-form-item>
          <a-form-item label="确认新密码" name="checkPassword" :rules="checkPasswordRules">
            <a-input-password
              v-model:value="formState.checkPassword"
              size="large"
              placeholder="再次输入新密码"
            />
          </a-form-item>

          <a-divider orientation="left">偏好设置</a-divider>
          <a-form-item label="界面风格">
            <a-radio-group v-model:value="preferenceState.theme" button-style="solid">
              <a-radio-button value="cyber">赛博风格</a-radio-button>
              <a-radio-button value="light">浅色风格</a-radio-button>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="默认 AI 应用">
            <a-select
              v-model:value="preferenceState.defaultAiApp"
              size="large"
              :options="defaultAiAppOptions"
            />
          </a-form-item>
          <a-form-item label="默认风险偏好">
            <a-select
              v-model:value="preferenceState.defaultRiskPreference"
              size="large"
              :options="riskPreferenceOptions"
            />
          </a-form-item>
          <a-form-item label="对话展示密度">
            <a-radio-group v-model:value="preferenceState.conversationDensity" button-style="solid">
              <a-radio-button value="comfortable">舒适</a-radio-button>
              <a-radio-button value="compact">紧凑</a-radio-button>
            </a-radio-group>
          </a-form-item>

          <div class="form-actions">
            <a-button @click="resetForm">重置</a-button>
            <a-button type="primary" html-type="submit" :loading="submitting">保存资料与偏好</a-button>
          </div>
        </a-form>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { UploadOutlined } from '@ant-design/icons-vue'
import { updateMyProfile, uploadMyAvatar } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import { setInterfaceTheme, useInterfaceTheme } from '@/composables/useInterfaceTheme'
import { getPasswordStrength } from '@/utils/passwordStrength'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const submitting = ref(false)
const uploadingAvatar = ref(false)
const { themeClass } = useInterfaceTheme()

const formState = reactive<API.UserProfileUpdateRequest>({
  userName: '',
  userAvatar: '',
  userProfile: '',
  oldPassword: '',
  newPassword: '',
  checkPassword: '',
})

const preferenceState = reactive<API.UserPreferenceUpdateRequest>({
  theme: 'cyber',
  defaultAiApp: 'home',
  defaultRiskPreference: 'balanced',
  conversationDensity: 'comfortable',
})

const defaultAiAppOptions = [
  { label: '首页', value: 'home' },
  { label: 'AI 股票大师', value: 'stock-master' },
  { label: 'AI 超级智能体', value: 'super-agent' },
]

const riskPreferenceOptions = [
  { label: '稳健', value: 'conservative' },
  { label: '平衡', value: 'balanced' },
  { label: '激进', value: 'aggressive' },
]

const userInitial = computed(() => {
  const userName = formState.userName || loginUserStore.loginUser.userName
  return userName ? userName.charAt(0).toUpperCase() : 'U'
})

const passwordStrength = computed(() => getPasswordStrength(formState.newPassword))

const fillForm = () => {
  formState.userName = loginUserStore.loginUser.userName || ''
  formState.userAvatar = loginUserStore.loginUser.userAvatar || ''
  formState.userProfile = loginUserStore.loginUser.userProfile || ''
  formState.oldPassword = ''
  formState.newPassword = ''
  formState.checkPassword = ''
}

const fillPreference = () => {
  preferenceState.theme = loginUserStore.userPreference.theme || 'cyber'
  preferenceState.defaultAiApp = loginUserStore.userPreference.defaultAiApp || 'home'
  preferenceState.defaultRiskPreference = loginUserStore.userPreference.defaultRiskPreference || 'balanced'
  preferenceState.conversationDensity = loginUserStore.userPreference.conversationDensity || 'comfortable'
}

const hasPasswordInput = () => {
  return Boolean(formState.oldPassword || formState.newPassword || formState.checkPassword)
}

const passwordRules: Rule[] = [
  {
    validator: async () => {
      if (!hasPasswordInput()) {
        return Promise.resolve()
      }
      if (!formState.oldPassword || !formState.newPassword || !formState.checkPassword) {
        return Promise.reject('请完整填写密码信息')
      }
      if ((formState.newPassword || '').length < 6) {
        return Promise.reject('新密码长度不能小于 6 位')
      }
      return Promise.resolve()
    },
    trigger: 'change',
  },
]

const checkPasswordRules: Rule[] = [
  {
    validator: async () => {
      if (!hasPasswordInput()) {
        return Promise.resolve()
      }
      if (formState.newPassword !== formState.checkPassword) {
        return Promise.reject('两次输入的新密码不一致')
      }
      return Promise.resolve()
    },
    trigger: 'change',
  },
]

const resetForm = () => {
  fillForm()
  fillPreference()
}

const handleAvatarUpload = async (file: File) => {
  if (uploadingAvatar.value) {
    return false
  }
  uploadingAvatar.value = true
  try {
    const res = await uploadMyAvatar(file)
    if (res.data.code === 0 && res.data.data) {
      formState.userAvatar = res.data.data
      message.success('头像已上传，保存后生效')
      return false
    }
    message.error(res.data.message || '头像上传失败')
  } catch (error) {
    message.error('头像上传失败，请稍后重试')
  } finally {
    uploadingAvatar.value = false
  }
  return false
}

const handleSubmit = async () => {
  if (submitting.value) {
    return
  }
  submitting.value = true
  try {
    const res = await updateMyProfile({
      userName: formState.userName,
      userAvatar: formState.userAvatar,
      userProfile: formState.userProfile,
      oldPassword: formState.oldPassword,
      newPassword: formState.newPassword,
      checkPassword: formState.checkPassword,
    })
    if (res.data.code !== 0 || !res.data.data) {
      message.error(`保存失败，${res.data.message || '请稍后重试'}`)
      return
    }
    loginUserStore.setLoginUser(res.data.data)

    const preferenceRes = await loginUserStore.saveUserPreference({
      theme: preferenceState.theme,
      defaultAiApp: preferenceState.defaultAiApp,
      defaultRiskPreference: preferenceState.defaultRiskPreference,
      conversationDensity: preferenceState.conversationDensity,
    })
    if (preferenceRes.data.code !== 0 || !preferenceRes.data.data) {
      message.error(`偏好保存失败，${preferenceRes.data.message || '请稍后重试'}`)
      return
    }
    setInterfaceTheme(preferenceRes.data.data.theme)
    fillForm()
    fillPreference()
    message.success('资料与偏好已更新')
  } catch (error) {
    message.error('保存失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loginUserStore.fetchLoginUser()
  await loginUserStore.fetchUserPreference()
  fillForm()
  fillPreference()
  setInterfaceTheme(preferenceState.theme)
})
</script>

<style scoped>
.profile-page {
  --profile-accent: #2563eb;
  --profile-accent-dark: #1d4ed8;
  --profile-accent-soft: #eff6ff;
  --profile-success: #0f766e;
  --profile-bg: #f4f8fc;
  --profile-panel: rgba(255, 255, 255, 0.94);
  --profile-border: #dbe5f2;
  --profile-text: #172033;
  --profile-muted: #617089;
  min-height: 100vh;
  padding: 96px 28px 48px;
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.1), transparent 42%),
    linear-gradient(90deg, rgba(15, 23, 42, 0.05) 1px, transparent 1px),
    linear-gradient(rgba(15, 23, 42, 0.05) 1px, transparent 1px),
    var(--profile-bg);
  background-size: auto, 44px 44px, 44px 44px, auto;
  color: var(--profile-text);
}

.profile-shell {
  width: min(100%, 1120px);
  margin: 0 auto;
  display: grid;
  grid-template-columns: 312px minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.profile-summary,
.profile-panel {
  border: 1px solid var(--profile-border);
  border-radius: 8px;
  background: var(--profile-panel);
  box-shadow: 0 24px 72px rgba(15, 23, 42, 0.1);
  backdrop-filter: blur(12px);
}

.profile-summary {
  padding: 34px 24px 28px;
  text-align: center;
  position: sticky;
  top: 96px;
  overflow: hidden;
}

.profile-summary::before {
  content: '';
  display: block;
  height: 72px;
  margin: -34px -24px 26px;
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.92), rgba(15, 118, 110, 0.86)),
    var(--profile-accent);
}

.summary-avatar {
  margin-top: -74px;
  margin-bottom: 18px;
  border: 4px solid #ffffff;
  background: linear-gradient(135deg, var(--profile-accent), var(--profile-success));
  font-size: 36px;
  font-weight: 700;
  box-shadow: 0 18px 38px rgba(37, 99, 235, 0.22);
}

.profile-summary h1 {
  margin: 0;
  color: var(--profile-text);
  font-size: 24px;
  line-height: 1.25;
  overflow-wrap: anywhere;
}

.profile-summary p {
  margin: 8px 0 16px;
  color: var(--profile-muted);
  overflow-wrap: anywhere;
}

.profile-summary :deep(.ant-tag) {
  margin-inline-end: 0;
  border-radius: 8px;
  padding: 2px 10px;
  font-weight: 600;
}

.profile-panel {
  padding: 30px 34px 34px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--profile-border);
}

.panel-heading h2 {
  margin: 0 0 8px;
  color: var(--profile-text);
  font-size: 28px;
  line-height: 1.2;
}

.panel-heading p {
  margin: 0;
  color: var(--profile-muted);
}

.panel-heading :deep(.ant-btn) {
  height: 38px;
  border-color: #cfd9e8;
  border-radius: 8px;
  color: #334155;
  font-weight: 600;
}

.panel-heading :deep(.ant-btn:hover) {
  border-color: rgba(37, 99, 235, 0.5);
  color: var(--profile-accent);
}

.profile-form {
  max-width: 720px;
}

.avatar-upload-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.avatar-upload-row :deep(.ant-upload) {
  width: 100%;
}

.avatar-upload-row :deep(.ant-btn) {
  height: 42px;
  border-radius: 8px;
  font-weight: 700;
}

.profile-form :deep(.ant-divider) {
  margin: 22px 0 18px;
  color: var(--profile-text);
  font-weight: 700;
}

.profile-form :deep(.ant-divider::before),
.profile-form :deep(.ant-divider::after) {
  border-color: var(--profile-border);
}

.profile-form :deep(.ant-form-item-label > label) {
  color: #334155;
  font-weight: 600;
}

.profile-form :deep(.ant-input),
.profile-form :deep(.ant-input-affix-wrapper),
.profile-form :deep(.ant-input-textarea textarea),
.profile-form :deep(.ant-select-selector) {
  border-color: #d6e0ee;
  border-radius: 8px;
  background: #f8fafc;
  color: var(--profile-text);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}

.profile-form :deep(.ant-input),
.profile-form :deep(.ant-input-affix-wrapper),
.profile-form :deep(.ant-select-selector) {
  min-height: 42px;
}

.profile-form :deep(.ant-select-selection-item) {
  display: flex;
  align-items: center;
  color: var(--profile-text);
}

.profile-form :deep(.ant-input-affix-wrapper > input.ant-input) {
  min-height: 40px;
  background: transparent;
}

.profile-form :deep(.ant-input:hover),
.profile-form :deep(.ant-input-affix-wrapper:hover),
.profile-form :deep(.ant-select:hover .ant-select-selector),
.profile-form :deep(.ant-select-focused .ant-select-selector),
.profile-form :deep(.ant-input:focus),
.profile-form :deep(.ant-input-affix-wrapper-focused),
.profile-form :deep(.ant-input-textarea textarea:hover),
.profile-form :deep(.ant-input-textarea textarea:focus) {
  border-color: rgba(37, 99, 235, 0.58);
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.profile-form :deep(.ant-input::placeholder),
.profile-form :deep(.ant-input-password-icon),
.profile-form :deep(.ant-input-textarea textarea::placeholder) {
  color: #8a97aa;
}

.profile-form :deep(.ant-input-data-count) {
  color: var(--profile-muted);
}

.password-strength {
  margin-top: 10px;
}

.strength-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 7px;
  color: var(--profile-muted);
  font-size: 12px;
  line-height: 1.4;
}

.strength-meta span:last-child {
  text-align: right;
}

.strength-track {
  height: 6px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.2);
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.2s ease, background-color 0.2s ease;
}

.profile-form :deep(.ant-radio-button-wrapper) {
  min-width: 88px;
  border-color: #d6e0ee;
  color: #334155;
  font-weight: 600;
  text-align: center;
}

.profile-form :deep(.ant-radio-button-wrapper-checked) {
  border-color: var(--profile-accent);
  background: var(--profile-accent);
  color: #ffffff;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 14px;
}

.form-actions :deep(.ant-btn) {
  height: 40px;
  border-radius: 8px;
  font-weight: 700;
}

.form-actions :deep(.ant-btn-primary) {
  border: none;
  background: linear-gradient(90deg, var(--profile-accent), var(--profile-success));
  box-shadow: 0 14px 30px rgba(37, 99, 235, 0.18);
}

.form-actions :deep(.ant-btn-primary:hover) {
  background: linear-gradient(90deg, var(--profile-accent-dark), #115e59);
}

.profile-page.theme-cyber {
  --profile-accent: #22d3ee;
  --profile-accent-dark: #0891b2;
  --profile-accent-soft: rgba(34, 211, 238, 0.12);
  --profile-success: #6366f1;
  --profile-bg: #08111f;
  --profile-panel: rgba(10, 18, 34, 0.88);
  --profile-border: rgba(103, 232, 249, 0.22);
  --profile-text: #e0f2fe;
  --profile-muted: #94a3b8;
  background:
    linear-gradient(135deg, rgba(8, 13, 29, 0.96), rgba(15, 23, 42, 0.94)),
    linear-gradient(rgba(34, 211, 238, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(129, 140, 248, 0.05) 1px, transparent 1px);
  background-size: auto, 48px 48px, 48px 48px;
}

.profile-page.theme-cyber .profile-summary,
.profile-page.theme-cyber .profile-panel {
  box-shadow: 0 24px 72px rgba(0, 0, 0, 0.26);
}

.profile-page.theme-cyber .profile-summary::before {
  background:
    linear-gradient(135deg, rgba(8, 145, 178, 0.92), rgba(79, 70, 229, 0.86)),
    #07111f;
}

.profile-page.theme-cyber .summary-avatar {
  border-color: rgba(224, 242, 254, 0.9);
}

.profile-page.theme-cyber .panel-heading :deep(.ant-btn),
.profile-page.theme-cyber .form-actions :deep(.ant-btn:not(.ant-btn-primary)) {
  border-color: rgba(103, 232, 249, 0.22);
  background: rgba(15, 23, 42, 0.72);
  color: var(--profile-text);
}

.profile-page.theme-cyber .profile-form :deep(.ant-input),
.profile-page.theme-cyber .profile-form :deep(.ant-input-affix-wrapper),
.profile-page.theme-cyber .profile-form :deep(.ant-input-textarea textarea),
.profile-page.theme-cyber .profile-form :deep(.ant-select-selector) {
  border-color: rgba(103, 232, 249, 0.18);
  background: rgba(15, 23, 42, 0.72);
  color: var(--profile-text);
}

.profile-page.theme-cyber .profile-form :deep(.ant-input:hover),
.profile-page.theme-cyber .profile-form :deep(.ant-input-affix-wrapper:hover),
.profile-page.theme-cyber .profile-form :deep(.ant-select:hover .ant-select-selector),
.profile-page.theme-cyber .profile-form :deep(.ant-select-focused .ant-select-selector),
.profile-page.theme-cyber .profile-form :deep(.ant-input:focus),
.profile-page.theme-cyber .profile-form :deep(.ant-input-affix-wrapper-focused),
.profile-page.theme-cyber .profile-form :deep(.ant-input-textarea textarea:hover),
.profile-page.theme-cyber .profile-form :deep(.ant-input-textarea textarea:focus) {
  border-color: rgba(103, 232, 249, 0.62);
  background: rgba(15, 23, 42, 0.9);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.11);
}

.profile-page.theme-cyber .profile-form :deep(.ant-input-affix-wrapper .ant-input) {
  background: transparent;
}

.profile-page.theme-cyber .profile-form :deep(.ant-radio-button-wrapper) {
  border-color: rgba(103, 232, 249, 0.22);
  background: rgba(15, 23, 42, 0.72);
  color: var(--profile-text);
}

.profile-page.theme-cyber .profile-form :deep(.ant-radio-button-wrapper-checked) {
  border-color: rgba(103, 232, 249, 0.72);
  background: linear-gradient(135deg, var(--profile-accent), var(--profile-success));
  color: #ffffff;
}

@media (max-width: 820px) {
  .profile-page {
    padding: 84px 16px 32px;
  }

  .profile-shell {
    grid-template-columns: 1fr;
  }

  .profile-summary {
    position: static;
  }

  .profile-panel {
    padding: 24px 18px;
  }

  .panel-heading {
    flex-direction: column;
  }

  .profile-form {
    max-width: none;
  }
}

@media (max-width: 520px) {
  .profile-page {
    padding: 76px 12px 24px;
  }

  .profile-summary {
    padding: 30px 18px 24px;
  }

  .profile-summary::before {
    margin: -30px -18px 26px;
  }

  .panel-heading h2 {
    font-size: 24px;
  }

  .form-actions {
    flex-direction: column-reverse;
  }

  .avatar-upload-row {
    grid-template-columns: 1fr;
  }

  .form-actions :deep(.ant-btn) {
    width: 100%;
  }
}
</style>
