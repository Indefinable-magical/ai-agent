<template>
  <div class="super-agent-container" :class="themeClass">
    <div class="header">
      <div class="back-button" @click="goBack">返回</div>
      <h1 class="title">AI超级智能体</h1>
      <div class="header-user">
        <AppHeader />
      </div>
    </div>

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
        <section v-if="executionTimeline.length" class="execution-timeline" aria-label="任务执行时间线">
          <div class="timeline-header">
            <span class="timeline-title">任务执行时间线</span>
            <span class="timeline-count">{{ executionTimeline.length }} 个步骤</span>
          </div>
          <div class="timeline-list">
            <div
              v-for="item in executionTimeline"
              :key="item.id"
              class="timeline-item"
              :class="[`status-${item.status}`, `type-${item.type}`]"
            >
              <span class="timeline-dot"></span>
              <div class="timeline-content">
                <div class="timeline-item-head">
                  <span class="timeline-badge">{{ getTimelineTypeText(item.type) }}</span>
                  <span class="timeline-item-title">{{ item.title }}</span>
                  <span class="timeline-time">{{ formatTimelineTime(item.timestamp) }}</span>
                </div>
                <p class="timeline-desc">{{ item.description }}</p>
              </div>
            </div>
          </div>
        </section>
        <ChatRoom
          :messages="messages"
          :connection-status="connectionStatus"
          ai-type="super"
          @send-message="sendMessage"
          @regenerate-message="regenerateMessage"
          @stop-generation="stopGeneration"
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
import { useInterfaceTheme } from '@/composables/useInterfaceTheme'
import {
  chatWithManus,
  deleteManusConversation,
  getManusConversationMessages,
  listManusConversations,
  renameManusConversation,
} from '@/services/chat'

useHead({
  title: 'AI超级智能体 - AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: 'AI超级智能体是支持工具调用和自主规划的智能助手，可处理多步骤专业问题。',
    },
    {
      name: 'keywords',
      content: 'AI超级智能体,智能助手,工具调用,多步骤任务,AI应用',
    },
  ],
})

const router = useRouter()
const { themeClass } = useInterfaceTheme()
const messages = ref([])
const conversations = ref([])
const executionTimeline = ref([])
const conversationKeyword = ref('')
const chatId = ref('')
const connectionStatus = ref('disconnected')
const activeAiMessageIndex = ref(-1)
const MANUS_CHAT_ID_KEY = 'super_agent_chat_id'
let eventSource = null
let typingTimer = null
let conversationSearchTimer = null

const welcomeMessage =
  '你好，我是AI超级智能体。我可以解答各类问题，也可以调用工具完成多步骤任务，请问有什么可以帮你的吗？'

const timelineTypeTextMap = {
  thinking: '思考',
  searching: '搜索',
  reading_file: '读取',
  calling_tool: '工具',
  generating_report: '生成',
  // 后端循环检测命中时推送，用来提示用户当前任务已主动暂停。
  loop_detected: '循环',
  // 后端 askUser 工具命中时推送，用来提示用户需要补充信息或确认方向。
  interaction_required: '询问',
  done: '完成',
  error: '异常',
}

const generateChatId = () => {
  return 'manus_' + Math.random().toString(36).substring(2, 10)
}

const normalizeMessageTime = (time) => {
  const timestamp = typeof time === 'string' && /^\d+$/.test(time) ? Number(time) : time
  const date = new Date(timestamp || Date.now())
  return Number.isNaN(date.getTime()) ? Date.now() : date.getTime()
}

const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    content,
    isUser,
    type,
    time: Date.now(),
  })
}

const showWelcomeMessage = () => {
  messages.value = []
  executionTimeline.value = []
  addMessage(welcomeMessage, false)
}

const rememberChatId = (conversationId) => {
  chatId.value = conversationId
  localStorage.setItem(MANUS_CHAT_ID_KEY, conversationId)
}

const refreshConversations = async () => {
  const res = await listManusConversations(conversationKeyword.value.trim())
  if (res.data.code === 0) {
    conversations.value = res.data.data || []
  }
}

const loadConversation = async (conversationId) => {
  if (!conversationId || conversationId === chatId.value) {
    return
  }
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (typingTimer) {
    clearTimeout(typingTimer)
    typingTimer = null
  }

  rememberChatId(conversationId)
  const res = await getManusConversationMessages(conversationId)
  if (res.data.code === 0 && res.data.data?.length) {
    executionTimeline.value = []
    messages.value = res.data.data.map((item) => ({
      content: item.content,
      isUser: item.isUser,
      time: normalizeMessageTime(item.time),
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
  if (typingTimer) {
    clearTimeout(typingTimer)
    typingTimer = null
  }
  rememberChatId(generateChatId())
  executionTimeline.value = []
  showWelcomeMessage()
}

const removeConversation = async (conversationId) => {
  if (connectionStatus.value === 'connecting') {
    return
  }
  await deleteManusConversation(conversationId)
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
  const res = await renameManusConversation(conversation.conversationId, normalizedTitle)
  if (res.data.code === 0) {
    antMessage.success('会话名称已更新')
    await refreshConversations()
    return
  }
  antMessage.error(res.data.message || '会话重命名失败')
}

const initConversation = async () => {
  await refreshConversations()
  const savedChatId = localStorage.getItem(MANUS_CHAT_ID_KEY)
  const savedConversation = conversations.value.find((item) => item.conversationId === savedChatId)
  const latestConversation = savedConversation || conversations.value[0]

  if (latestConversation) {
    await loadConversation(latestConversation.conversationId)
    return
  }

  startNewConversation()
}

const sendMessage = (message) => {
  addMessage(message, true, 'user-question')
  executionTimeline.value = []

  if (eventSource) {
    eventSource.close()
  }
  if (typingTimer) {
    clearTimeout(typingTimer)
    typingTimer = null
  }

  const aiMessageIndex = messages.value.length
  activeAiMessageIndex.value = aiMessageIndex
  addMessage('', false, 'ai-answer')

  connectionStatus.value = 'connecting'
  let pendingText = ''
  let streamDone = false
  let refreshedAfterSend = false
  const typingInterval = 18

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

  const finishStream = () => {
    connectionStatus.value = 'disconnected'
    activeAiMessageIndex.value = -1
    closeStream(true)
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

  // 后端会通过 agent-step 事件推送结构化过程信息，这里只更新当前任务的时间线。
  const appendTimelineEvent = (data) => {
    try {
      const parsed = JSON.parse(data)
      executionTimeline.value.push({
        id: `${Date.now()}_${executionTimeline.value.length}`,
        step: parsed.step || 0,
        type: parsed.type || 'calling_tool',
        title: parsed.title || '执行步骤',
        description: parsed.description || '正在处理任务',
        status: parsed.status || 'running',
        timestamp: parsed.timestamp || Date.now(),
      })

      if (executionTimeline.value.length > 20) {
        executionTimeline.value = executionTimeline.value.slice(-20)
      }
    } catch (e) {
      console.warn('Agent step event parse failed:', e)
    }
  }

  const typeNextChar = () => {
    if (pendingText.length > 0) {
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content += pendingText.charAt(0)
      }
      pendingText = pendingText.slice(1)
      typingTimer = setTimeout(typeNextChar, typingInterval)
      return
    }

    typingTimer = null
    if (streamDone) {
      finishStream()
    }
  }

  eventSource = chatWithManus(message, chatId.value)

  eventSource.addEventListener('agent-step', (event) => {
    appendTimelineEvent(event.data)
  })

  eventSource.addEventListener('business-error', (event) => {
    pendingText = ''
    streamDone = true
    if (typingTimer) {
      clearTimeout(typingTimer)
      typingTimer = null
    }
    setAiMessageContent(getErrorMessage(event.data))
    connectionStatus.value = 'error'
    closeStream(false)
  })

  eventSource.addEventListener('done', () => {
    streamDone = true
    if (!typingTimer && pendingText.length === 0) {
      finishStream()
    }
  })

  eventSource.onmessage = (event) => {
    const data = event.data

    if (data && data !== '[DONE]') {
      pendingText += data
      if (!typingTimer) {
        typeNextChar()
      }
    }

    if (data === '[DONE]') {
      streamDone = true
      if (!typingTimer && pendingText.length === 0) {
        finishStream()
      }
    }
  }

  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    if (typingTimer || pendingText.length > 0) {
      streamDone = true
      connectionStatus.value = 'disconnected'
      if (!typingTimer) {
        typeNextChar()
      }
      return
    }
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

const regenerateMessage = (index) => {
  if (connectionStatus.value === 'connecting') {
    return
  }
  for (let i = index - 1; i >= 0; i -= 1) {
    if (messages.value[i]?.isUser && messages.value[i]?.content) {
      sendMessage(messages.value[i].content)
      return
    }
  }
  antMessage.warning('没有找到可重新生成的上一条提问')
}

// 停止超级智能体生成：关闭 SSE，并清理本地打字机计时器。
const stopGeneration = async () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (typingTimer) {
    clearTimeout(typingTimer)
    typingTimer = null
  }
  const messageIndex = activeAiMessageIndex.value
  if (messageIndex >= 0 && messages.value[messageIndex] && !messages.value[messageIndex].content) {
    messages.value[messageIndex].content = '已停止生成'
  }
  executionTimeline.value.push({
    id: `${Date.now()}_stopped`,
    step: executionTimeline.value.length + 1,
    type: 'error',
    title: '已停止生成',
    description: '你已手动停止本次超级智能体任务',
    status: 'error',
    timestamp: Date.now(),
  })
  activeAiMessageIndex.value = -1
  connectionStatus.value = 'disconnected'
  await refreshConversations()
}

const getTimelineTypeText = (type) => {
  return timelineTypeTextMap[type] || '步骤'
}

const formatTimelineTime = (value) => {
  const date = new Date(value || Date.now())
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

const formatConversationTime = (value) => {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
  })
}

const goBack = () => {
  router.push('/')
}

onMounted(() => {
  initConversation()
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
  if (typingTimer) {
    clearTimeout(typingTimer)
  }
  if (conversationSearchTimer) {
    clearTimeout(conversationSearchTimer)
  }
})
</script>

<style scoped>
.super-agent-container {
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
  justify-self: start;
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
  justify-self: center;
  letter-spacing: 0;
  text-shadow: var(--title-shadow);
}

.header-user {
  justify-self: end;
}

.workspace {
  display: grid;
  grid-template-columns: 292px minmax(0, 1fr);
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
  padding: 14px;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: var(--panel-bg);
  box-shadow: 0 18px 48px rgba(23, 35, 61, 0.1);
  backdrop-filter: blur(12px);
}

.new-chat-button {
  height: 42px;
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
  height: 38px;
  margin-top: 12px;
  padding: 0 12px;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: var(--chat-input-bg, rgba(248, 250, 252, 0.92));
  color: var(--text-strong);
  outline: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.conversation-search:focus {
  border-color: var(--app-accent-line);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-accent) 14%, transparent);
}

.conversation-search::placeholder {
  color: var(--text-muted);
}

.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  margin-top: 14px;
  overflow-y: auto;
  padding-right: 2px;
}

.conversation-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-height: 58px;
  padding: 10px 60px 10px 12px;
  border: 1px solid #e2e8f5;
  border-radius: 8px;
  background: rgba(247, 249, 253, 0.88);
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
  font-size: 14px;
  font-weight: 600;
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
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  background: var(--panel-bg);
  box-shadow: 0 18px 48px rgba(23, 35, 61, 0.1);
}

.execution-timeline {
  flex-shrink: 0;
  margin: 12px 12px 0;
  padding: 12px;
  border: 1px solid var(--timeline-border);
  border-radius: 8px;
  background: var(--timeline-bg);
  color: var(--text-strong);
}

.timeline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.timeline-title {
  font-size: 14px;
  font-weight: 800;
}

.timeline-count {
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.timeline-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
  max-height: 158px;
  overflow-y: auto;
  padding-right: 2px;
}

.timeline-item {
  position: relative;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 8px;
  min-height: 68px;
  padding: 10px;
  border: 1px solid var(--timeline-item-border);
  border-radius: 8px;
  background: var(--timeline-item-bg);
}

.timeline-dot {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--timeline-dot-running);
  box-shadow: 0 0 0 4px var(--timeline-dot-ring);
}

.timeline-content {
  min-width: 0;
}

.timeline-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.timeline-badge {
  flex-shrink: 0;
  min-width: 38px;
  padding: 2px 6px;
  border-radius: 999px;
  background: var(--timeline-badge-bg);
  color: var(--app-accent);
  font-size: 12px;
  font-weight: 700;
  text-align: center;
}

.timeline-item-title {
  min-width: 0;
  overflow: hidden;
  color: var(--text-strong);
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline-time {
  flex-shrink: 0;
  margin-left: auto;
  color: var(--text-muted);
  font-size: 11px;
}

.timeline-desc {
  display: -webkit-box;
  margin: 6px 0 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.timeline-item.status-success .timeline-dot {
  background: var(--timeline-dot-success);
}

.timeline-item.status-error .timeline-dot {
  background: var(--timeline-dot-error);
}

.timeline-item.type-searching .timeline-badge {
  color: var(--timeline-search-color);
}

.timeline-item.type-reading_file .timeline-badge {
  color: var(--timeline-read-color);
}

.timeline-item.type-generating_report .timeline-badge {
  color: var(--timeline-report-color);
}

/* 循环检测和用户询问都属于“需要用户介入”的状态，因此共用一组更醒目的强调色。 */
.timeline-item.type-loop_detected .timeline-badge,
.timeline-item.type-interaction_required .timeline-badge {
  color: var(--timeline-interaction-color);
}

.chat-area :deep(.chat-container) {
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
}

.super-agent-container.theme-cyber {
  --app-accent: #67e8f9;
  --app-accent-dark: #2563eb;
  --app-accent-soft: rgba(103, 232, 249, 0.12);
  --app-accent-line: rgba(129, 140, 248, 0.62);
  --page-bg: #08111f;
  --panel-bg: rgba(10, 18, 34, 0.88);
  --panel-border: rgba(129, 140, 248, 0.24);
  --text-strong: #e0f2fe;
  --text-muted: #94a3b8;
  --chat-user-bg: #2563eb;
  --chat-user-bg-hover: #1d4ed8;
  --chat-ai-bg: rgba(15, 23, 42, 0.9);
  --chat-ai-text: #e5f4ff;
  --chat-surface: rgba(8, 13, 29, 0.72);
  --chat-input-panel-bg: rgba(10, 18, 34, 0.96);
  --chat-input-bg: rgba(15, 23, 42, 0.72);
  --chat-input-focus-bg: rgba(15, 23, 42, 0.9);
  --chat-input-text: #e0f2fe;
  --page-background:
    linear-gradient(135deg, rgba(8, 13, 29, 0.96), rgba(15, 23, 42, 0.94)),
    linear-gradient(rgba(129, 140, 248, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(34, 211, 238, 0.05) 1px, transparent 1px);
  --header-background:
    linear-gradient(135deg, rgba(18, 24, 48, 0.98), rgba(37, 99, 235, 0.92)),
    #07111f;
  --header-border: rgba(129, 140, 248, 0.2);
  --header-text: #e0f2fe;
  --header-shadow: 0 16px 34px rgba(0, 0, 0, 0.28);
  --header-button-bg: rgba(255, 255, 255, 0.08);
  --header-button-bg-hover: rgba(129, 140, 248, 0.14);
  --header-button-border: rgba(129, 140, 248, 0.24);
  --header-button-border-hover: rgba(103, 232, 249, 0.56);
  --title-shadow: 0 0 16px rgba(103, 232, 249, 0.38);
  --button-shadow: 0 10px 22px rgba(37, 99, 235, 0.2);
  --button-shadow-hover: 0 14px 28px rgba(37, 99, 235, 0.28);
  --danger-bg: rgba(239, 68, 68, 0.12);
  --timeline-bg: rgba(8, 13, 29, 0.82);
  --timeline-border: rgba(103, 232, 249, 0.18);
  --timeline-item-bg: rgba(15, 23, 42, 0.74);
  --timeline-item-border: rgba(129, 140, 248, 0.16);
  --timeline-badge-bg: rgba(103, 232, 249, 0.12);
  --timeline-dot-running: #67e8f9;
  --timeline-dot-success: #22c55e;
  --timeline-dot-error: #f87171;
  --timeline-dot-ring: rgba(103, 232, 249, 0.16);
  --timeline-search-color: #38bdf8;
  --timeline-read-color: #a78bfa;
  --timeline-report-color: #34d399;
  --timeline-interaction-color: #fbbf24;
  background-size: auto, 48px 48px, 48px 48px;
}

.super-agent-container.theme-cyber .conversation-sidebar,
.super-agent-container.theme-cyber .chat-area {
  box-shadow: 0 24px 72px rgba(0, 0, 0, 0.24);
}

.super-agent-container.theme-cyber .conversation-item {
  border-color: rgba(129, 140, 248, 0.14);
  background: rgba(15, 23, 42, 0.72);
}

.super-agent-container.theme-light {
  --app-accent: #2f6fdd;
  --app-accent-dark: #234fa9;
  --app-accent-soft: #edf4ff;
  --app-accent-line: #9abaf3;
  --panel-bg: rgba(255, 255, 255, 0.94);
  --panel-border: #d9e2f2;
  --text-strong: #17233d;
  --text-muted: #61708b;
  --chat-user-bg: #2f6fdd;
  --chat-user-bg-hover: #255ec0;
  --chat-ai-bg: #f5f7fb;
  --chat-ai-text: #17233d;
  --chat-surface: #f8fafc;
  --chat-input-panel-bg: rgba(255, 255, 255, 0.96);
  --chat-input-bg: #f8fafc;
  --chat-input-focus-bg: #ffffff;
  --chat-input-text: #172033;
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
  --header-button-border-hover: rgba(47, 111, 221, 0.42);
  --title-shadow: none;
  --button-shadow: 0 10px 22px rgba(47, 111, 221, 0.18);
  --button-shadow-hover: 0 14px 28px rgba(47, 111, 221, 0.24);
  --danger-bg: #fee2e2;
  --timeline-bg: rgba(248, 250, 252, 0.94);
  --timeline-border: #dbe5f2;
  --timeline-item-bg: #ffffff;
  --timeline-item-border: #e2e8f0;
  --timeline-badge-bg: #eef5ff;
  --timeline-dot-running: #2f6fdd;
  --timeline-dot-success: #16a34a;
  --timeline-dot-error: #dc2626;
  --timeline-dot-ring: rgba(47, 111, 221, 0.14);
  --timeline-search-color: #0284c7;
  --timeline-read-color: #7c3aed;
  --timeline-report-color: #059669;
  --timeline-interaction-color: #d97706;
}

@media (max-width: 900px) {
  .workspace {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 12px 16px;
  }

  .conversation-sidebar {
    max-height: 190px;
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
