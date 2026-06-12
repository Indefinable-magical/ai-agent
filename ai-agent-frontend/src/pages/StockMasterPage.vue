<template>
  <div class="stock-master-container" :class="themeClass">
    <div class="header">
      <div class="back-button" @click="goBack">返回</div>
      <h1 class="title">AI股票大师</h1>
      <div class="header-user">
        <button
          class="watchlist-entry-button"
          type="button"
          @click="openWatchlistPanel"
        >
          自选股
          <span>{{ watchlist.length }}</span>
        </button>
        <button
          class="watchlist-entry-button"
          type="button"
          @click="router.push('/stock-workbench')"
        >
          工作台
        </button>
        <AppHeader />
      </div>
    </div>

    <StockWatchlistPanel
      v-if="isWatchlistPanelOpen"
      :watchlist="watchlist"
      :risk-preference="riskPreference"
      :risk-options="riskOptions"
      :disabled="connectionStatus === 'connecting'"
      :reset-key="watchlistFormResetKey"
      @close="closeWatchlistPanel"
      @change-risk="changeRiskPreference"
      @add-stock="addWatchStock"
      @remove-stock="removeWatchStock"
    />

    <div class="workspace">
      <aside class="conversation-sidebar">
        <button class="new-chat-button" :disabled="connectionStatus === 'connecting'" @click="startNewConversation">
          + 新对话
        </button>

        <input
          v-model="conversationKeyword"
          class="conversation-search"
          type="search"
          placeholder="搜索标题或内容"
          :disabled="connectionStatus === 'connecting'"
        />

        <div class="conversation-list">
          <button
            v-for="conversation in conversations"
            :key="conversation.conversationId"
            class="conversation-item"
            :class="{ active: conversation.conversationId === chatId }"
            :disabled="connectionStatus === 'connecting'"
            @click="loadConversation(conversation.conversationId)"
          >
            <span class="conversation-title">{{ conversation.title || '新对话' }}</span>
            <span class="conversation-time">{{ formatConversationTime(conversation.updateTime) }}</span>
            <span class="rename-chat" title="重命名对话" @click.stop="renameConversation(conversation)">✎</span>
            <span class="delete-chat" title="删除对话" @click.stop="removeConversation(conversation.conversationId)">×</span>
          </button>

          <div v-if="!conversations.length" class="empty-conversations">
            暂无历史对话
          </div>
        </div>
      </aside>

      <main class="chat-area">
        <ChatRoom
          :messages="messages"
          :connection-status="connectionStatus"
          :enable-message-export="true"
          :pdf-exporting-index="pdfExportingIndex"
          ai-type="stock"
          @send-message="sendMessage"
          @regenerate-message="regenerateMessage"
          @stop-generation="stopGeneration"
          @export-message-markdown="exportMessageMarkdown"
          @export-message-pdf="exportMessagePdf"
        />
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import { message as antMessage } from 'ant-design-vue'
import AppHeader from '@/components/AppHeader.vue'
import ChatRoom from '../components/ChatRoom.vue'
import StockWatchlistPanel from '@/components/StockWatchlistPanel.vue'
import { useInterfaceTheme } from '@/composables/useInterfaceTheme'
import { useMessageExport } from '@/composables/useMessageExport'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  chatWithStockApp,
  addStockWatchlist,
  deleteStockWatchlist,
  deleteStockConversation,
  getStockUserPreference,
  getStockConversationMessages,
  listStockWatchlist,
  listStockConversations,
  renameStockConversation,
  updateStockRiskPreference,
} from '@/services/chat'

useHead({
  title: 'AI股票大师 - AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: 'AI股票大师是AI超级智能体应用平台的专业股票投研助手，帮你分析股票、行业、财报、估值和风险',
    },
    {
      name: 'keywords',
      content: 'AI股票大师,股票分析,投研助手,财报分析,估值分析,风险提示,AI智能体',
    },
  ],
})

const router = useRouter()
const { themeClass } = useInterfaceTheme()
const loginUserStore = useLoginUserStore()
const messages = ref([])
const conversations = ref([])
const conversationKeyword = ref('')
const watchlist = ref([])
const riskPreference = ref('balanced')
const chatId = ref('')
const connectionStatus = ref('disconnected')
const activeAiMessageIndex = ref(-1)
const isWatchlistPanelOpen = ref(false)
const watchlistFormResetKey = ref(0)
const STOCK_CHAT_ID_KEY = 'stock_master_chat_id'
const PENDING_STOCK_PROMPT_KEY = 'stock_master_pending_prompt'
let eventSource = null
let conversationSearchTimer = null

const riskOptions = [
  { label: '稳健', value: 'conservative' },
  { label: '平衡', value: 'balanced' },
  { label: '激进', value: 'aggressive' },
]

const welcomeMessage =
  '欢迎来到AI股票大师。你可以告诉我关注的股票、行业或投资问题，我会从基本面、估值、趋势和风险等角度帮你分析。内容仅供学习参考，不构成投资建议。'

const getCurrentConversationTitle = () => {
  const currentConversation = conversations.value.find((conversation) => conversation.conversationId === chatId.value)
  if (currentConversation?.title) {
    return currentConversation.title
  }
  const firstUserMessage = messages.value.find((message) => message.isUser && message.content)
  if (firstUserMessage?.content) {
    return firstUserMessage.content.replace(/\s+/g, ' ').trim().slice(0, 24)
  }
  return '股票分析报告'
}

const {
  pdfExportingIndex,
  exportMessageMarkdown,
  exportMessagePdf,
} = useMessageExport({
  messages,
  getConversationTitle: getCurrentConversationTitle,
})

const generateChatId = () => {
  return 'stock_' + Math.random().toString(36).substring(2, 10)
}

const getDisplayMessageForStoredContent = (content) => {
  const value = String(content || '')
  if (value.includes('请基于我的自选股生成“今日关注摘要”')) {
    return '请生成我的自选股今日关注摘要'
  }

  const stockMatch = value.match(/标的：([^\n]+)/)
  const stockName = stockMatch?.[1]?.trim()
  if (stockName && value.includes('请重点展开风险标签')) {
    return `请展开 ${stockName} 的风险分析`
  }
  if (stockName && value.includes('请重点对比同业公司')) {
    return `请对比 ${stockName} 的同业公司`
  }
  if (stockName && value.includes('请输出投资结论')) {
    return `请生成 ${stockName} 的结构化分析档案`
  }

  const followUpDisplayMap = {
    '请继续分析这条回答涉及公司的财报重点，包括营收、净利润、毛利率、现金流和费用率。': '继续分析财报',
    '请基于上一条回答展开风险分析，按风险标签、触发条件、验证指标和应对观察清单输出。': '展开风险',
    '请基于上一条回答对比同业公司，重点比较行业地位、估值、成长性、盈利质量和风险。': '对比同业',
  }
  return followUpDisplayMap[value] || value
}

const addMessage = (content, isUser, extra = {}) => {
  messages.value.push({
    content,
    isUser,
    time: new Date().getTime(),
    ...extra,
  })
}

const normalizeMessageTime = (time) => {
  const timestamp = typeof time === 'string' && /^\d+$/.test(time) ? Number(time) : time
  const date = new Date(timestamp || Date.now())
  return Number.isNaN(date.getTime()) ? Date.now() : date.getTime()
}

const showWelcomeMessage = () => {
  messages.value = []
  addMessage(welcomeMessage, false)
}

const openWatchlistPanel = () => {
  isWatchlistPanelOpen.value = true
}

const closeWatchlistPanel = () => {
  isWatchlistPanelOpen.value = false
}

const rememberChatId = (conversationId) => {
  chatId.value = conversationId
  localStorage.setItem(STOCK_CHAT_ID_KEY, conversationId)
}

const refreshConversations = async () => {
  const res = await listStockConversations(conversationKeyword.value.trim())
  if (res.data.code === 0) {
    conversations.value = res.data.data || []
  }
}

// 加载股票大师个性化配置：自选股和风险偏好。
const loadStockPreferences = async () => {
  const [watchlistRes, preferenceRes] = await Promise.all([
    listStockWatchlist(),
    getStockUserPreference(),
  ])
  if (watchlistRes.data.code === 0) {
    watchlist.value = watchlistRes.data.data || []
  }
  if (preferenceRes.data.code === 0 && preferenceRes.data.data?.riskPreference) {
    riskPreference.value = preferenceRes.data.data.riskPreference
  }
}

// 风险偏好会保存到后端，后续股票大师分析会自动使用。
const changeRiskPreference = async (value) => {
  if (value === riskPreference.value || connectionStatus.value === 'connecting') {
    return
  }
  const res = await updateStockRiskPreference(value)
  if (res.data.code === 0 && res.data.data?.riskPreference) {
    riskPreference.value = res.data.data.riskPreference
    await loginUserStore.fetchUserPreference()
    antMessage.success('风险偏好已更新')
    return
  }
  antMessage.error(res.data.message || '风险偏好更新失败')
}

const addWatchStock = async (stock) => {
  if (connectionStatus.value === 'connecting') {
    return
  }
  const res = await addStockWatchlist(stock)
  if (res.data.code === 0) {
    watchlistFormResetKey.value += 1
    antMessage.success('自选股已添加')
    await loadStockPreferences()
    return
  }
  antMessage.error(res.data.message || '自选股添加失败')
}

// 移除自选股后重新加载配置，保证设置面板和后端状态一致。
const removeWatchStock = async (id) => {
  if (!id || connectionStatus.value === 'connecting') {
    return
  }
  const res = await deleteStockWatchlist(id)
  if (res.data.code === 0) {
    antMessage.success('自选股已移除')
    await loadStockPreferences()
    return
  }
  antMessage.error(res.data.message || '自选股移除失败')
}

const loadConversation = async (conversationId) => {
  if (!conversationId || conversationId === chatId.value) {
    return
  }
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  rememberChatId(conversationId)
  const res = await getStockConversationMessages(conversationId)
  if (res.data.code === 0 && res.data.data?.length) {
    messages.value = res.data.data.map((item) => ({
      content: item.isUser ? getDisplayMessageForStoredContent(item.content) : item.content,
      isUser: item.isUser,
      time: normalizeMessageTime(item.time),
      requestContent: item.isUser ? item.content : undefined,
    }))
  } else {
    showWelcomeMessage()
  }
}

const startNewConversation = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  rememberChatId(generateChatId())
  showWelcomeMessage()
}

const removeConversation = async (conversationId) => {
  if (connectionStatus.value === 'connecting') {
    return
  }
  await deleteStockConversation(conversationId)
  await refreshConversations()

  if (conversationId === chatId.value) {
    const nextConversation = conversations.value[0]
    if (nextConversation) {
      await loadConversation(nextConversation.conversationId)
    } else {
      startNewConversation()
    }
  }
}

const renameConversation = async (conversation) => {
  if (connectionStatus.value === 'connecting' || !conversation?.conversationId) {
    return
  }
  const nextTitle = window.prompt('请输入会话名称', conversation.title || '新对话')
  if (nextTitle === null) {
    return
  }
  const normalizedTitle = nextTitle.trim()
  if (!normalizedTitle) {
    antMessage.warning('会话名称不能为空')
    return
  }
  const res = await renameStockConversation(conversation.conversationId, normalizedTitle)
  if (res.data.code === 0) {
    antMessage.success('会话名称已更新')
    await refreshConversations()
    return
  }
  antMessage.error(res.data.message || '会话重命名失败')
}

const initConversation = async () => {
  await refreshConversations()
  const savedChatId = localStorage.getItem(STOCK_CHAT_ID_KEY)
  const savedConversation = conversations.value.find((item) => item.conversationId === savedChatId)
  const latestConversation = savedConversation || conversations.value[0]

  if (latestConversation) {
    await loadConversation(latestConversation.conversationId)
    return
  }

  startNewConversation()
}

const ensureChatId = () => {
  if (!chatId.value) {
    rememberChatId(generateChatId())
  }
  return chatId.value
}

const sendMessage = (message, displayMessage = '') => {
  const currentChatId = ensureChatId()
  addMessage(displayMessage || getDisplayMessageForStoredContent(message), true, {
    requestContent: message,
  })

  if (eventSource) {
    eventSource.close()
  }

  const aiMessageIndex = messages.value.length
  activeAiMessageIndex.value = aiMessageIndex
  addMessage('', false)

  connectionStatus.value = 'connecting'
  eventSource = chatWithStockApp(message, currentChatId)
  let refreshedAfterSend = false

  const closeStream = async (shouldRefresh = false) => {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    activeAiMessageIndex.value = -1
    if (shouldRefresh && !refreshedAfterSend) {
      refreshedAfterSend = true
      await refreshConversations()
    }
  }

  const setAiMessageContent = (content) => {
    if (aiMessageIndex < messages.value.length) {
      messages.value[aiMessageIndex].content = content
    }
  }

  const getErrorMessage = (data) => {
    try {
      const parsed = JSON.parse(data)
      return parsed?.message || '消息发送失败，请稍后重试'
    } catch (e) {
      return data || '消息发送失败，请稍后重试'
    }
  }

  eventSource.addEventListener('business-error', (event) => {
    setAiMessageContent(getErrorMessage(event.data))
    connectionStatus.value = 'error'
    closeStream(false)
  })

  eventSource.addEventListener('done', () => {
    connectionStatus.value = 'disconnected'
    closeStream(true)
  })

  eventSource.onmessage = (event) => {
    const data = event.data
    if (data && data !== '[DONE]') {
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content += data
      }
    }

    if (data === '[DONE]') {
      connectionStatus.value = 'disconnected'
      closeStream(true)
    }
  }

  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    if (aiMessageIndex < messages.value.length && !messages.value[aiMessageIndex].content) {
      setAiMessageContent('消息发送失败，请稍后重试')
      connectionStatus.value = 'error'
      closeStream(false)
      return
    }
    connectionStatus.value = 'disconnected'
    closeStream(true)
  }
}

// 重新生成会复用当前 AI 回复前最近一条用户消息。
const regenerateMessage = (index) => {
  if (connectionStatus.value === 'connecting') {
    return
  }
  for (let i = index - 1; i >= 0; i -= 1) {
    if (messages.value[i]?.isUser && messages.value[i]?.content) {
      sendMessage(messages.value[i].requestContent || messages.value[i].content, messages.value[i].content)
      return
    }
  }
  antMessage.warning('没有找到可重新生成的上一条提问')
}

// 停止生成时关闭当前 EventSource；如果还没有任何内容，给用户一个明确状态。
const stopGeneration = async () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  const messageIndex = activeAiMessageIndex.value
  if (messageIndex >= 0 && messages.value[messageIndex] && !messages.value[messageIndex].content) {
    messages.value[messageIndex].content = '已停止生成'
  }
  activeAiMessageIndex.value = -1
  connectionStatus.value = 'disconnected'
  await refreshConversations()
}

const formatConversationTime = (value) => {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  return date.toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  })
}

const goBack = () => {
  router.push('/')
}

onMounted(async () => {
  await initConversation()
  loadStockPreferences()
  const pendingPrompt = sessionStorage.getItem(PENDING_STOCK_PROMPT_KEY)
  if (pendingPrompt) {
    sessionStorage.removeItem(PENDING_STOCK_PROMPT_KEY)
    let prompt = pendingPrompt
    let displayMessage = ''
    try {
      const parsedPrompt = JSON.parse(pendingPrompt)
      prompt = parsedPrompt.prompt || pendingPrompt
      displayMessage = parsedPrompt.displayMessage || ''
    } catch (error) {
      prompt = pendingPrompt
    }
    sendMessage(prompt, displayMessage)
  }
})

watch(conversationKeyword, () => {
  if (conversationSearchTimer) {
    clearTimeout(conversationSearchTimer)
  }
  conversationSearchTimer = setTimeout(() => {
    refreshConversations()
  }, 250)
})

onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
  if (conversationSearchTimer) {
    clearTimeout(conversationSearchTimer)
  }
})
</script>

<style scoped>
.stock-master-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: var(--page-background);
  background-size: auto, 44px 44px, 44px 44px, auto;
}

.header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  min-height: 70px;
  padding: 14px 28px;
  border-bottom: 1px solid var(--header-border);
  background: var(--header-background);
  color: var(--header-text);
  box-shadow: var(--header-shadow);
  position: sticky;
  top: 0;
  z-index: 10;
  flex-shrink: 0;
}

.back-button {
  width: fit-content;
  padding: 8px 12px;
  border: 1px solid var(--header-button-border);
  border-radius: 8px;
  background: var(--header-button-bg);
  color: var(--header-text);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: background-color 0.2s ease, border-color 0.2s ease;
}

.back-button:hover {
  border-color: var(--header-button-border-hover);
  background: var(--header-button-bg-hover);
}

.back-button:before {
  content: '←';
  margin-right: 8px;
}

.title {
  font-size: 21px;
  font-weight: 800;
  margin: 0;
  text-align: center;
  letter-spacing: 0;
  text-shadow: var(--title-shadow);
}

.header-user {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-self: end;
}

.watchlist-entry-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 14px;
  border: 1px solid var(--header-button-border);
  border-radius: 8px;
  background: var(--header-button-bg);
  color: var(--header-text);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, color 0.2s ease;
}

.watchlist-entry-button:hover {
  border-color: var(--header-button-border-hover);
  background: var(--header-button-bg-hover);
}

.watchlist-entry-button span {
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--app-accent-soft);
  color: var(--app-accent);
  line-height: 22px;
  text-align: center;
  font-size: 12px;
  font-weight: 800;
}

.workspace {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 20px;
  width: 100%;
  flex: 1;
  min-height: 0;
  padding: 20px 28px 24px;
  overflow: hidden;
}

.conversation-sidebar {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 16px;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: var(--panel-bg);
  box-shadow: 0 18px 48px rgba(15, 47, 37, 0.1);
  backdrop-filter: blur(12px);
  overflow: hidden;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--app-accent) 34%, transparent) transparent;
}

.conversation-sidebar ::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.conversation-sidebar ::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: color-mix(in srgb, var(--app-accent) 28%, transparent);
}

.new-chat-button {
  height: 46px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, var(--app-accent), var(--app-accent-dark));
  color: #ffffff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: var(--button-shadow);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.new-chat-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--button-shadow-hover);
}

.new-chat-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.conversation-search {
  height: 40px;
  margin-top: 14px;
  padding: 0 14px;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: var(--sidebar-input-bg, var(--chat-input-bg, rgba(248, 250, 252, 0.92)));
  color: var(--text-strong);
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}

.conversation-search:focus {
  border-color: var(--app-accent-line);
  background: var(--sidebar-input-focus-bg, #ffffff);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-accent) 14%, transparent);
}

.conversation-search::placeholder {
  color: var(--text-muted);
}

.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
  margin-top: 14px;
  overflow-y: auto;
  padding: 14px 2px 0 0;
  border-top: 1px solid var(--panel-border);
}

.conversation-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-height: 62px;
  padding: 11px 60px 10px 12px;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: var(--sidebar-item-bg, rgba(245, 250, 248, 0.86));
  color: var(--text-strong);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.conversation-item:hover,
.conversation-item.active {
  border-color: var(--app-accent-line);
  background: var(--app-accent-soft);
  box-shadow: inset 3px 0 0 var(--app-accent);
}

.conversation-title {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.35;
}

.conversation-time {
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 12px;
}

.rename-chat,
.delete-chat {
  position: absolute;
  top: 8px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  color: var(--text-muted);
  font-size: 16px;
  line-height: 18px;
  text-align: center;
  opacity: 0.8;
  transition: background-color 0.2s ease, color 0.2s ease, opacity 0.2s ease;
}

.conversation-item:hover .rename-chat,
.conversation-item:hover .delete-chat {
  opacity: 1;
}

.rename-chat {
  right: 36px;
}

.delete-chat {
  right: 10px;
  font-size: 18px;
}

.rename-chat:hover {
  background: var(--app-accent-soft);
  color: var(--app-accent);
}

.delete-chat:hover {
  background: var(--danger-bg);
  color: #b42318;
}

.empty-conversations {
  padding: 24px 8px;
  color: var(--text-muted);
  text-align: center;
  font-size: 14px;
}

.chat-area {
  display: flex;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: var(--panel-bg);
  box-shadow: 0 18px 48px rgba(15, 47, 37, 0.1);
}

.chat-area :deep(.chat-container) {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.stock-master-container.theme-cyber {
  --app-accent: #22d3ee;
  --app-accent-dark: #0891b2;
  --app-accent-soft: rgba(34, 211, 238, 0.12);
  --app-accent-line: rgba(103, 232, 249, 0.58);
  --page-bg: #08111f;
  --panel-bg: rgba(10, 18, 34, 0.88);
  --panel-border: rgba(103, 232, 249, 0.22);
  --text-strong: #e0f2fe;
  --text-muted: #94a3b8;
  --chat-user-bg: #0891b2;
  --chat-user-bg-hover: #0e7490;
  --chat-ai-bg: rgba(15, 23, 42, 0.9);
  --chat-ai-text: #e5f4ff;
  --chat-surface: rgba(8, 13, 29, 0.72);
  --chat-input-panel-bg: rgba(10, 18, 34, 0.96);
  --chat-input-bg: rgba(15, 23, 42, 0.72);
  --chat-input-focus-bg: rgba(15, 23, 42, 0.9);
  --chat-input-text: #e0f2fe;
  --sidebar-card-bg: rgba(15, 23, 42, 0.62);
  --sidebar-item-bg: rgba(15, 23, 42, 0.56);
  --sidebar-input-bg: rgba(8, 18, 34, 0.72);
  --sidebar-input-focus-bg: rgba(15, 23, 42, 0.9);
  --page-background:
    linear-gradient(135deg, rgba(8, 13, 29, 0.96), rgba(15, 23, 42, 0.94)),
    linear-gradient(rgba(34, 211, 238, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 211, 238, 0.05) 1px, transparent 1px);
  --header-background:
    linear-gradient(135deg, rgba(6, 18, 32, 0.98), rgba(8, 80, 91, 0.94)),
    #07111f;
  --header-border: rgba(103, 232, 249, 0.18);
  --header-text: #e0f2fe;
  --header-shadow: 0 16px 34px rgba(0, 0, 0, 0.28);
  --header-button-bg: rgba(255, 255, 255, 0.08);
  --header-button-bg-hover: rgba(34, 211, 238, 0.14);
  --header-button-border: rgba(103, 232, 249, 0.22);
  --header-button-border-hover: rgba(103, 232, 249, 0.58);
  --title-shadow: 0 0 16px rgba(34, 211, 238, 0.42);
  --button-shadow: 0 10px 22px rgba(34, 211, 238, 0.18);
  --button-shadow-hover: 0 14px 28px rgba(34, 211, 238, 0.26);
  --danger-bg: rgba(239, 68, 68, 0.12);
  background-size: auto, 48px 48px, 48px 48px;
}

.stock-master-container.theme-cyber .conversation-sidebar,
.stock-master-container.theme-cyber .chat-area {
  box-shadow: 0 24px 72px rgba(0, 0, 0, 0.24);
}

.stock-master-container.theme-cyber .conversation-item {
  border-color: rgba(103, 232, 249, 0.12);
}

.stock-master-container.theme-cyber .conversation-item {
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.stock-master-container.theme-light {
  --app-accent: #0f8b5f;
  --app-accent-dark: #0b6748;
  --app-accent-soft: #e5f5ee;
  --app-accent-line: #8fd0b8;
  --panel-bg: rgba(255, 255, 255, 0.94);
  --panel-border: #d5e5dd;
  --text-strong: #0f2f25;
  --text-muted: #60776e;
  --chat-user-bg: #0f8b5f;
  --chat-user-bg-hover: #0b7651;
  --chat-ai-bg: #f4f8f6;
  --chat-ai-text: #172b24;
  --chat-surface: #f8fafc;
  --chat-input-panel-bg: rgba(255, 255, 255, 0.96);
  --chat-input-bg: #f8fafc;
  --chat-input-focus-bg: #ffffff;
  --chat-input-text: #172033;
  --sidebar-card-bg: rgba(255, 255, 255, 0.74);
  --sidebar-item-bg: rgba(247, 252, 250, 0.88);
  --sidebar-input-bg: #f8fafc;
  --sidebar-input-focus-bg: #ffffff;
  --page-background:
    linear-gradient(180deg, rgba(14, 165, 233, 0.12), transparent 42%),
    linear-gradient(90deg, rgba(15, 23, 42, 0.05) 1px, transparent 1px),
    linear-gradient(rgba(15, 23, 42, 0.05) 1px, transparent 1px),
    #f7fbff;
  --header-background: rgba(255, 255, 255, 0.94);
  --header-border: #dbe5f2;
  --header-text: #172033;
  --header-shadow: 0 12px 34px rgba(15, 23, 42, 0.08);
  --header-button-bg: #f8fafc;
  --header-button-bg-hover: #ffffff;
  --header-button-border: #d6e0ee;
  --header-button-border-hover: rgba(15, 139, 95, 0.42);
  --title-shadow: none;
  --button-shadow: 0 10px 22px rgba(15, 139, 95, 0.18);
  --button-shadow-hover: 0 14px 28px rgba(15, 139, 95, 0.24);
  --danger-bg: #fee2e2;
}

@media (max-width: 900px) {
  .workspace {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 12px 16px;
  }

  .conversation-sidebar {
    max-height: 360px;
  }
}

@media (max-width: 768px) {
  .header {
    padding: 12px 16px;
    grid-template-columns: auto 1fr auto;
    gap: 12px;
  }

  .title {
    font-size: 18px;
  }
}

@media (max-width: 480px) {
  .header {
    padding: 10px 12px;
  }

  .back-button {
    font-size: 14px;
  }

  .title {
    font-size: 16px;
  }

  .workspace {
    padding: 8px;
  }
}
</style>
