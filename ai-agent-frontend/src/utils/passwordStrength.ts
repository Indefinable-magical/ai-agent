export type PasswordStrengthLevel = 'empty' | 'weak' | 'medium' | 'strong'

export type PasswordStrength = {
  level: PasswordStrengthLevel
  text: string
  percent: number
  color: string
  tip: string
}

/**
 * 根据长度、大小写、数字和符号综合计算密码强度，仅用于前端友好提示。
 */
export const getPasswordStrength = (password?: string): PasswordStrength => {
  const value = password || ''
  if (!value) {
    return {
      level: 'empty',
      text: '未输入',
      percent: 0,
      color: '#cbd5e1',
      tip: '建议使用字母、数字和符号组合',
    }
  }

  let score = 0
  if (value.length >= 8) {
    score += 1
  }
  if (value.length >= 12) {
    score += 1
  }
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) {
    score += 1
  }
  if (/\d/.test(value)) {
    score += 1
  }
  if (/[^A-Za-z0-9]/.test(value)) {
    score += 1
  }

  if (score <= 2) {
    return {
      level: 'weak',
      text: '偏弱',
      percent: 34,
      color: '#f97316',
      tip: '建议增加长度，并混合大小写字母、数字或符号',
    }
  }
  if (score <= 4) {
    return {
      level: 'medium',
      text: '中等',
      percent: 68,
      color: '#eab308',
      tip: '安全性尚可，继续增加长度或符号会更稳',
    }
  }
  return {
    level: 'strong',
    text: '较强',
    percent: 100,
    color: '#10b981',
    tip: '密码强度较高',
  }
}
