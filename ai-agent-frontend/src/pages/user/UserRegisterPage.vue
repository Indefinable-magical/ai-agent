<template>
  <div class="auth-page" :class="themeClass">
    <button class="theme-switch" type="button" @click="toggleTheme">{{ themeButtonText }}</button>
    <section class="auth-hero" aria-label="AI 超级智能体应用平台">
      <RouterLink class="home-link" to="/">返回首页</RouterLink>
      <div class="hero-copy">
        <div class="brand-lockup">
          <span class="brand-mark">AI</span>
          <span>超级智能体应用平台</span>
        </div>
        <h1>创建账号</h1>
      </div>
    </section>

    <section class="auth-panel">
      <div class="panel-header">
        <h2>用户注册</h2>
      </div>

      <a-form
        class="auth-form"
        :model="formState"
        name="register"
        layout="vertical"
        autocomplete="off"
        @finish="handleSubmit"
      >
        <a-form-item
          label="账号"
          name="userAccount"
          :rules="[
            { required: true, message: '请输入账号' },
            { min: 4, message: '账号长度不能小于 4 位' },
          ]"
        >
          <a-input v-model:value="formState.userAccount" size="large" placeholder="请输入账号" />
        </a-form-item>
        <a-form-item
          label="密码"
          name="userPassword"
          :rules="[
            { required: true, message: '请输入密码' },
            { min: 6, message: '密码不能小于 6 位' },
          ]"
        >
          <a-input-password v-model:value="formState.userPassword" size="large" placeholder="请输入密码" />
          <div v-if="formState.userPassword" class="password-strength">
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
        <a-form-item
          label="确认密码"
          name="checkPassword"
          :rules="[
            { required: true, message: '请确认密码' },
            { min: 6, message: '密码不能小于 6 位' },
            { validator: validateCheckPassword },
          ]"
        >
          <a-input-password v-model:value="formState.checkPassword" size="large" placeholder="请再次输入密码" />
        </a-form-item>

        <a-button
          class="submit-button"
          type="primary"
          html-type="submit"
          size="large"
          :loading="submitting"
          block
        >
          创建账号
        </a-button>
      </a-form>

      <div class="switch-auth">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { userRegister } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import { computed, reactive, ref } from 'vue'
import { useInterfaceTheme } from '@/composables/useInterfaceTheme'
import { getPasswordStrength } from '@/utils/passwordStrength'

const router = useRouter()
const { themeClass, themeButtonText, toggleTheme } = useInterfaceTheme()

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})
const submitting = ref(false)
const passwordStrength = computed(() => getPasswordStrength(formState.userPassword))

/**
 * 验证确认密码
 * @param rule
 * @param value
 * @param callback
 */
const validateCheckPassword = (rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && value !== formState.userPassword) {
    callback(new Error('两次输入密码不一致'))
  } else {
    callback()
  }
}

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: API.UserRegisterRequest) => {
  if (submitting.value) {
    return
  }
  submitting.value = true
  try {
    const res = await userRegister(values)
    // 注册成功，跳转到登录页面
    if (res.data.code === 0) {
      message.success('注册成功，请登录')
      router.push({
        path: '/user/login',
        replace: true,
      })
    } else {
      message.error('注册失败，' + res.data.message)
    }
  } catch (error) {
    message.error('注册失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(420px, 540px);
  background:
    linear-gradient(118deg, rgba(3, 7, 18, 0.98) 0%, rgba(6, 18, 37, 0.97) 46%, rgba(11, 28, 52, 0.98) 100%),
    linear-gradient(135deg, rgba(34, 211, 238, 0.16), transparent 34%),
    linear-gradient(315deg, rgba(20, 184, 166, 0.1), transparent 42%),
    url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96"><path d="M0 1h96M1 0v96" stroke="%23243b55" stroke-width="1" opacity="0.45"/><path d="M24 0v96M48 0v96M72 0v96M0 24h96M0 48h96M0 72h96" stroke="%23152a44" stroke-width="1" opacity="0.38"/></svg>');
  background-size: auto, auto, auto, 48px 48px;
  color: #f8fafc;
  position: relative;
  overflow: hidden;
}

.auth-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(100deg, transparent 0 28%, rgba(34, 211, 238, 0.1) 40%, transparent 54%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), transparent 32%),
    repeating-linear-gradient(180deg, rgba(255, 255, 255, 0.025) 0 1px, transparent 1px 8px);
  transform: translateX(-100%);
  animation: page-scan 9s linear infinite;
  pointer-events: none;
}

.auth-page::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(34, 211, 238, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 211, 238, 0.05) 1px, transparent 1px),
    linear-gradient(135deg, transparent 0 48%, rgba(103, 232, 249, 0.12) 49%, transparent 50%);
  background-size: 88px 88px, 88px 88px, 220px 220px;
  animation: grid-drift 22s linear infinite;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.75), transparent 82%);
  pointer-events: none;
}

.auth-hero {
  position: relative;
  min-height: 100vh;
  padding: 56px 76px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  z-index: 1;
}

.auth-hero::before {
  content: '';
  position: absolute;
  inset: 86px 58px 86px 58px;
  border: 1px solid rgba(103, 232, 249, 0.13);
  border-radius: 8px;
  background:
    radial-gradient(circle at 18% 24%, rgba(103, 232, 249, 0.16), transparent 32%),
    radial-gradient(circle at 82% 72%, rgba(20, 184, 166, 0.12), transparent 34%),
    linear-gradient(135deg, rgba(103, 232, 249, 0.1), transparent 44%),
    rgba(15, 23, 42, 0.08);
  opacity: 0.72;
  box-shadow: inset 0 0 80px rgba(103, 232, 249, 0.05);
  pointer-events: none;
}

.auth-hero::after {
  content: '';
  position: absolute;
  inset: auto 0 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 240, 255, 0.8), transparent);
}

.home-link {
  position: absolute;
  top: 32px;
  left: 64px;
  color: #a5f3fc;
  font-weight: 600;
  padding: 8px 0;
  transition: color 0.2s;
}

.home-link:hover {
  color: #ffffff;
}

.hero-copy {
  position: relative;
  max-width: 620px;
  z-index: 1;
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 46px;
  color: #e0f2fe;
  font-weight: 700;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(103, 232, 249, 0.64);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(34, 211, 238, 0.22), rgba(59, 130, 246, 0.1)),
    rgba(8, 145, 178, 0.18);
  color: #67e8f9;
  box-shadow:
    0 0 24px rgba(34, 211, 238, 0.22),
    inset 0 0 18px rgba(103, 232, 249, 0.1);
  animation: mark-pulse 3.2s ease-in-out infinite;
}

.hero-copy h1 {
  margin: 0;
  color: #ffffff;
  font-size: 5.4rem;
  line-height: 1;
  text-shadow:
    0 0 12px rgba(0, 240, 255, 0.6),
    0 0 28px rgba(0, 240, 255, 0.28);
  animation: title-glow 4s ease-in-out infinite;
}

.summary {
  max-width: 420px;
  margin: 22px 0 0;
  color: #cbd5e1;
  font-size: 1rem;
  line-height: 1.7;
}

.auth-panel {
  align-self: center;
  justify-self: center;
  width: min(100% - 72px, 448px);
  padding: 36px 38px;
  border: 1px solid rgba(103, 232, 249, 0.18);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.86), rgba(6, 18, 34, 0.9)),
    rgba(15, 23, 42, 0.86);
  box-shadow:
    0 30px 90px rgba(0, 0, 0, 0.42),
    0 0 0 1px rgba(255, 255, 255, 0.03) inset;
  backdrop-filter: blur(18px);
  position: relative;
  z-index: 1;
  overflow: hidden;
}

.auth-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  padding: 1px;
  background: linear-gradient(120deg, rgba(34, 211, 238, 0.08), rgba(34, 211, 238, 0.78), rgba(20, 184, 166, 0.18));
  -webkit-mask:
    linear-gradient(#000 0 0) content-box,
    linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  opacity: 0.65;
  pointer-events: none;
}

.auth-panel::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  width: 120px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.08), transparent);
  transform: translateX(-180px) skewX(-18deg);
  animation: panel-sheen 6s ease-in-out infinite;
  pointer-events: none;
}

.panel-header {
  margin-bottom: 26px;
}

.panel-header h2 {
  margin: 0 0 10px;
  color: #ffffff;
  font-size: 2rem;
  line-height: 1.25;
}

.panel-header p {
  margin: 0;
  color: #94a3b8;
}

.auth-form :deep(.ant-form-item-label > label) {
  color: #dbeafe;
  font-weight: 600;
}

.auth-form :deep(.ant-input),
.auth-form :deep(.ant-input-affix-wrapper) {
  border-color: rgba(103, 232, 249, 0.22);
  border-radius: 8px;
  background: rgba(5, 14, 28, 0.74);
  color: #f8fafc;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}

.auth-form :deep(.ant-input) {
  height: 42px;
}

.auth-form :deep(.ant-input-affix-wrapper) {
  height: 42px;
  padding: 0 12px;
}

.auth-form :deep(.ant-input-affix-wrapper > input.ant-input) {
  height: 40px;
  padding: 0;
  border: 0;
  box-shadow: none;
}

.auth-form :deep(.ant-input:hover),
.auth-form :deep(.ant-input-affix-wrapper:hover),
.auth-form :deep(.ant-input:focus),
.auth-form :deep(.ant-input-affix-wrapper-focused) {
  border-color: rgba(103, 232, 249, 0.76);
  background: rgba(8, 22, 42, 0.92);
  box-shadow:
    0 0 0 3px rgba(6, 182, 212, 0.13),
    0 12px 26px rgba(8, 145, 178, 0.12);
}

.auth-form :deep(.ant-input::placeholder) {
  color: #64748b;
}

.auth-form :deep(.ant-input-affix-wrapper .ant-input) {
  background: transparent;
  color: #f8fafc;
}

.auth-form :deep(.ant-input-password-icon) {
  color: #94a3b8;
}

.auth-form :deep(.ant-input-password-icon:hover) {
  color: #67e8f9;
}

.auth-form :deep(.ant-input:-webkit-autofill),
.auth-form :deep(.ant-input:-webkit-autofill:hover),
.auth-form :deep(.ant-input:-webkit-autofill:focus) {
  -webkit-text-fill-color: #f8fafc;
  box-shadow: 0 0 0 1000px #0f172a inset;
  transition: background-color 9999s ease-in-out 0s;
}

.auth-page.theme-light .auth-form :deep(.ant-form-item-label > label) {
  color: #334155;
}

.auth-page.theme-light .auth-form :deep(.ant-input),
.auth-page.theme-light .auth-form :deep(.ant-input-affix-wrapper) {
  border-color: #d7e2f0;
  background: rgba(248, 250, 252, 0.94);
  color: #172033;
}

.auth-page.theme-light .auth-form :deep(.ant-input:hover),
.auth-page.theme-light .auth-form :deep(.ant-input-affix-wrapper:hover),
.auth-page.theme-light .auth-form :deep(.ant-input:focus),
.auth-page.theme-light .auth-form :deep(.ant-input-affix-wrapper-focused) {
  border-color: rgba(37, 99, 235, 0.58);
  background: #ffffff;
  box-shadow:
    0 0 0 3px rgba(37, 99, 235, 0.1),
    0 12px 24px rgba(37, 99, 235, 0.08);
}

.auth-page.theme-light .auth-form :deep(.ant-input::placeholder),
.auth-page.theme-light .auth-form :deep(.ant-input-password-icon) {
  color: #8a97aa;
}

.auth-page.theme-light .auth-form :deep(.ant-input-affix-wrapper .ant-input) {
  background: transparent;
  color: #172033;
}

.auth-page.theme-light .auth-form :deep(.ant-input:-webkit-autofill),
.auth-page.theme-light .auth-form :deep(.ant-input:-webkit-autofill:hover),
.auth-page.theme-light .auth-form :deep(.ant-input:-webkit-autofill:focus) {
  -webkit-text-fill-color: #172033;
  box-shadow: 0 0 0 1000px #ffffff inset;
}

.password-strength {
  margin-top: 10px;
}

.strength-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 7px;
  color: #94a3b8;
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

.submit-button {
  height: 48px;
  margin-top: 8px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(90deg, #0891b2, #14b8a6);
  font-weight: 700;
  box-shadow:
    0 14px 34px rgba(20, 184, 166, 0.24),
    inset 0 1px 0 rgba(255, 255, 255, 0.18);
  position: relative;
  overflow: hidden;
}

.submit-button:hover {
  background: linear-gradient(90deg, #0e7490, #0f766e);
  transform: translateY(-1px);
}

.submit-button::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  width: 64px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.34), transparent);
  transform: translateX(-120px) skewX(-18deg);
}

.submit-button:hover::after {
  animation: button-sheen 0.9s ease;
}

.switch-auth {
  margin-top: 22px;
  color: #a8b4c7;
  text-align: center;
}

.switch-auth a {
  color: #67e8f9;
  font-weight: 700;
}

@keyframes page-scan {
  0% {
    transform: translateX(-100%);
  }
  55%, 100% {
    transform: translateX(100%);
  }
}

@keyframes grid-drift {
  from {
    background-position: 0 0, 0 0;
  }
  to {
    background-position: 80px 80px, 80px 80px;
  }
}

@keyframes mark-pulse {
  0%, 100% {
    box-shadow: 0 0 20px rgba(34, 211, 238, 0.18);
  }
  50% {
    box-shadow: 0 0 32px rgba(34, 211, 238, 0.38);
  }
}

@keyframes title-glow {
  0%, 100% {
    text-shadow:
      0 0 10px rgba(0, 240, 255, 0.5),
      0 0 24px rgba(0, 240, 255, 0.22);
  }
  50% {
    text-shadow:
      0 0 16px rgba(0, 240, 255, 0.76),
      0 0 34px rgba(0, 240, 255, 0.34);
  }
}

@keyframes panel-sheen {
  0%, 45% {
    transform: translateX(-180px) skewX(-18deg);
  }
  70%, 100% {
    transform: translateX(620px) skewX(-18deg);
  }
}

@keyframes button-sheen {
  from {
    transform: translateX(-120px) skewX(-18deg);
  }
  to {
    transform: translateX(520px) skewX(-18deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .auth-page::before,
  .auth-page::after,
  .brand-mark,
  .hero-copy h1,
  .auth-panel::after,
  .submit-button:hover::after {
    animation: none;
  }
}

@media (max-width: 900px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .auth-hero {
    min-height: auto;
    padding: 96px 24px 28px;
    text-align: center;
    align-items: center;
  }

  .home-link {
    left: 24px;
  }

  .brand-lockup {
    justify-content: center;
    margin-bottom: 30px;
  }

  .hero-copy h1 {
    font-size: 4rem;
  }

  .summary {
    margin-left: auto;
    margin-right: auto;
  }

  .auth-panel {
    width: min(100% - 40px, 460px);
    margin: 0 auto 40px;
  }
}

@media (max-width: 520px) {
  .auth-panel {
    width: calc(100% - 28px);
    padding: 26px 20px;
  }

  .hero-copy h1 {
    font-size: 2.8rem;
  }
}
</style>
