<template>
  <div class="chat-container" :class="densityClass">
    <!-- 聊天记录区域 -->
    <div class="chat-messages" ref="messagesContainer">
      <div v-for="(msg, index) in messages" :key="index" class="message-wrapper">
        <!-- AI消息 -->
        <div v-if="!msg.isUser" 
             class="message ai-message" 
             :class="[msg.type]">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
            <div class="message-bubble">
              <div class="message-content">
              <div class="rendered-content" v-html="renderMessageContent(sanitizeAiContent(msg.content))"></div>
              <span v-if="connectionStatus === 'connecting' && index === messages.length - 1" class="typing-indicator">▋</span>
            </div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
            <div v-if="msg.content" class="message-actions">
              <button type="button" class="message-action" @click="copyMessage(sanitizeAiContent(msg.content))">复制</button>
              <button
                type="button"
                class="message-action"
                :disabled="connectionStatus === 'connecting'"
                @click="regenerateMessage(index)"
              >
                重新生成
              </button>
              <select
                v-if="enableMessageExport"
                v-model="selectedReportTemplate"
                class="message-action message-template-select"
              >
                <option value="brief">简版</option>
                <option value="professional">专业版</option>
                <option value="presentation">汇报版</option>
              </select>
              <button
                v-if="enableMessageExport"
                type="button"
                class="message-action"
                @click="exportMessageMarkdown(index, selectedReportTemplate)"
              >
                导出 Markdown
              </button>
              <button
                v-if="enableMessageExport"
                type="button"
                class="message-action"
                :disabled="pdfExportingIndex !== -1"
                @click="exportMessagePdf(index, selectedReportTemplate)"
              >
                {{ pdfExportingIndex === index ? '导出中...' : '导出 PDF' }}
              </button>
            </div>
          </div>
        </div>
        
        <!-- 用户消息 -->
        <div v-else class="message user-message" :class="[msg.type]">
          <div class="message-bubble">
            <div class="message-content">{{ msg.content }}</div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
          <div class="avatar user-avatar">
            <div class="avatar-placeholder">我</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-container">
      <div class="chat-input">
        <textarea 
          v-model="inputMessage" 
          @keydown.enter.prevent="sendMessage"
          placeholder="请输入消息..." 
          class="input-box"
          :disabled="connectionStatus === 'connecting'"
        ></textarea>
        <button 
          @click="sendMessage" 
          class="send-button"
          :disabled="connectionStatus === 'connecting' || !inputMessage.trim()"
        >发送</button>
        <!-- 生成过程中提供停止按钮，交给父页面关闭对应 SSE 连接 -->
        <button
          v-if="connectionStatus === 'connecting'"
          @click="stopGeneration"
          class="stop-button"
          type="button"
        >停止</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch, computed } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import MarkdownIt from 'markdown-it'
import AiAvatarFallback from './AiAvatarFallback.vue'
import { useLoginUserStore } from '@/stores/loginUser'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  connectionStatus: {
    type: String,
    default: 'disconnected'
  },
  aiType: {
    type: String,
    default: 'default'  // 'stock' 或 'super'
  },
  enableMessageExport: {
    type: Boolean,
    default: false
  },
  pdfExportingIndex: {
    type: Number,
    default: -1
  }
})

const emit = defineEmits([
  'send-message',
  'regenerate-message',
  'stop-generation',
  'export-message-markdown',
  'export-message-pdf',
])

const inputMessage = ref('')
const messagesContainer = ref(null)
const loginUserStore = useLoginUserStore()
const selectedReportTemplate = ref('professional')

const densityClass = computed(() => {
  return loginUserStore.userPreference.conversationDensity === 'compact'
    ? 'density-compact'
    : 'density-comfortable'
})

const markdownRenderer = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const defaultLinkOpenRenderer = markdownRenderer.renderer.rules.link_open

markdownRenderer.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const targetIndex = tokens[idx].attrIndex('target')
  const relIndex = tokens[idx].attrIndex('rel')

  if (targetIndex < 0) {
    tokens[idx].attrPush(['target', '_blank'])
  } else {
    tokens[idx].attrs[targetIndex][1] = '_blank'
  }

  if (relIndex < 0) {
    tokens[idx].attrPush(['rel', 'noopener noreferrer'])
  } else {
    tokens[idx].attrs[relIndex][1] = 'noopener noreferrer'
  }

  return defaultLinkOpenRenderer
    ? defaultLinkOpenRenderer(tokens, idx, options, env, self)
    : self.renderToken(tokens, idx, options)
}

const renderMessageContent = (content) => {
  return content ? markdownRenderer.render(String(content)) : ''
}

const sanitizeAiContent = (content) => {
  return String(content || '')
    .split(/\r?\n/)
    .filter((line) => {
      const trimmed = line.trim()
      return !/^(#{1,6}\s*)?(引用来源|参考来源|知识来源|来源)\s*[:：]/.test(trimmed)
        && !/^(#{1,6}\s*)?(引用来源|参考来源|知识来源|来源)\s*$/.test(trimmed)
        && !/\b(stock_info|csv_stock_pool|pdf_announcement|web_research)\b/i.test(trimmed)
    })
    .join('\n')
    .replaceAll('股票池示例.csv', '股票池资料')
    .replaceAll('股票关注对象知识库.md', '知识库资料')
    .replaceAll('stock_info数据库条目', '结构化股票资料')
    .replaceAll('stock_info 数据库条目', '结构化股票资料')
    .trim()
}

// 发送消息
const sendMessage = () => {
  if (!inputMessage.value.trim()) return
  
  emit('send-message', inputMessage.value)
  inputMessage.value = ''
}

const copyMessage = async (content) => {
  try {
    await navigator.clipboard.writeText(content)
    antMessage.success('已复制')
  } catch (error) {
    const textarea = document.createElement('textarea')
    textarea.value = content
    textarea.style.position = 'fixed'
    textarea.style.left = '-9999px'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    antMessage.success('已复制')
  }
}

const regenerateMessage = (index) => {
  emit('regenerate-message', index)
}

const exportMessageMarkdown = (index, template) => {
  emit('export-message-markdown', index, template)
}

const exportMessagePdf = (index, template) => {
  emit('export-message-pdf', index, template)
}

// 组件只发出停止事件，具体关闭哪个流由父页面管理。
const stopGeneration = () => {
  emit('stop-generation')
}

// 格式化时间
const formatTime = (timestamp) => {
  const normalizedTimestamp = typeof timestamp === 'string' && /^\d+$/.test(timestamp)
    ? Number(timestamp)
    : timestamp
  const date = new Date(normalizedTimestamp || Date.now())
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 自动滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 监听消息变化与内容变化，自动滚动
watch(() => props.messages.length, () => {
  scrollToBottom()
})

watch(() => props.messages.map(m => m.content).join(''), () => {
  scrollToBottom()
})

onMounted(() => {
  scrollToBottom()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 70vh;
  min-height: 600px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(248, 250, 252, 0.94)),
    var(--chat-surface, #f8fafc);
  border-radius: 8px;
  border: 1px solid var(--panel-border, #e2e8f0);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
  border-radius: 8px;
  overflow: hidden;
  position: relative;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 22px;
  padding-bottom: 20px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  position: relative;
}

.message-wrapper {
  margin-bottom: 18px;
  display: flex;
  flex-direction: column;
  width: 100%;
}

.chat-container.density-compact .chat-messages {
  padding: 14px 16px;
}

.chat-container.density-compact .message-wrapper {
  margin-bottom: 10px;
}

.chat-container.density-compact .message {
  margin-bottom: 4px;
}

.chat-container.density-compact .avatar {
  width: 32px;
  height: 32px;
}

.chat-container.density-compact .message-bubble {
  padding: 9px 11px;
}

.chat-container.density-compact .message-content {
  font-size: 14px;
  line-height: 1.55;
}

.chat-container.density-compact .message-time,
.chat-container.density-compact .message-action {
  font-size: 11px;
}

.message {
  display: flex;
  align-items: flex-start;
  max-width: 85%;
  margin-bottom: 8px;
}

.user-message {
  margin-left: auto; /* 用户消息靠右 */
  flex-direction: row; /* 正常顺序，先气泡后头像 */
}

.ai-message {
  margin-right: auto; /* AI消息靠左 */
}

.avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.12);
}

.user-avatar {
  margin-left: 8px; /* 用户头像在右侧，左边距 */
}

.ai-avatar {
  margin-right: 8px; /* AI头像在左侧，右边距 */
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--chat-user-bg, #2563eb), var(--chat-user-bg-hover, #1d4ed8));
  color: white;
  font-weight: bold;
}

.message-bubble {
  padding: 12px 14px;
  border-radius: 8px;
  position: relative;
  word-wrap: break-word;
  min-width: 100px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.user-message .message-bubble {
  background: linear-gradient(135deg, var(--chat-user-bg, #2563eb), var(--chat-user-bg-hover, #1d4ed8));
  color: white;
  border-bottom-right-radius: 3px;
  text-align: left;
}

.ai-message .message-bubble {
  border: 1px solid var(--panel-border, #e2e8f0);
  background: var(--chat-ai-bg, #ffffff);
  color: var(--chat-ai-text, #1f2937);
  border-bottom-left-radius: 3px;
  text-align: left;
}

.message-content {
  font-size: 15px;
  line-height: 1.68;
  white-space: pre-wrap;
}

.message-content :deep(p) {
  margin: 0 0 8px;
}

.message-content :deep(p:last-child) {
  margin-bottom: 0;
}

.message-content :deep(h1),
.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4),
.message-content :deep(h5),
.message-content :deep(h6) {
  margin: 10px 0 6px;
  font-size: 1em;
  font-weight: 700;
  line-height: 1.35;
}

.message-content :deep(ul),
.message-content :deep(ol) {
  margin: 6px 0 8px;
  padding-left: 22px;
}

.message-content :deep(li) {
  margin: 3px 0;
}

.message-content :deep(code) {
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(15, 23, 42, 0.08);
  font-family: Consolas, Monaco, monospace;
  font-size: 0.92em;
}

.message-content :deep(pre) {
  margin: 8px 0;
  padding: 32px 12px 12px;
  overflow-x: auto;
  border-radius: 6px;
  background: #0f172a;
  color: #e5e7eb;
  position: relative;
}

.message-content :deep(pre code) {
  padding: 0;
  background: transparent;
}

.message-content :deep(pre[data-language]::before) {
  content: attr(data-language);
  position: absolute;
  top: 8px;
  right: 10px;
  padding: 2px 7px;
  border-radius: 4px;
  background: rgba(148, 163, 184, 0.18);
  color: #cbd5e1;
  font-size: 12px;
  line-height: 1.3;
}

.message-content :deep(blockquote) {
  margin: 8px 0;
  padding: 8px 12px;
  border-left: 3px solid var(--app-accent, #2563eb);
  border-radius: 0 6px 6px 0;
  background: color-mix(in srgb, var(--app-accent, #2563eb) 8%, transparent);
}

.message-content :deep(blockquote p) {
  margin: 0 0 6px;
}

.message-content :deep(blockquote p:last-child) {
  margin-bottom: 0;
}

.message-content :deep(.markdown-table-wrapper) {
  max-width: 100%;
  margin: 8px 0;
  overflow-x: auto;
  border: 1px solid var(--panel-border, #e2e8f0);
  border-radius: 6px;
}

.message-content :deep(table) {
  display: block;
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
  min-width: 360px;
  background: rgba(255, 255, 255, 0.02);
}

.message-content :deep(th),
.message-content :deep(td) {
  padding: 8px 10px;
  border-bottom: 1px solid var(--panel-border, #e2e8f0);
  border-right: 1px solid var(--panel-border, #e2e8f0);
  vertical-align: top;
  white-space: normal;
}

.message-content :deep(th:last-child),
.message-content :deep(td:last-child) {
  border-right: none;
}

.message-content :deep(tr:last-child td) {
  border-bottom: none;
}

.message-content :deep(th) {
  background: color-mix(in srgb, var(--app-accent, #2563eb) 10%, transparent);
  font-weight: 700;
}

.message-content :deep(a) {
  color: var(--app-accent, #2458d3);
  text-decoration: none;
}

.message-content :deep(a:hover) {
  text-decoration: underline;
}

.message-time {
  font-size: 12px;
  opacity: 0.64;
  margin-top: 6px;
  text-align: right;
}

.message-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.message-action {
  border: 1px solid var(--panel-border, #e2e8f0);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.64);
  color: var(--chat-ai-text, #1f2937);
  font-size: 12px;
  line-height: 1;
  padding: 5px 8px;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, color 0.2s ease;
}

.message-action:hover:not(:disabled) {
  border-color: var(--app-accent, #2563eb);
  color: var(--app-accent, #2563eb);
  background: rgba(255, 255, 255, 0.9);
}

.message-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.message-template-select {
  height: 24px;
  padding: 0 6px;
}

.chat-input-container {
  position: relative;
  background: var(--chat-input-panel-bg, rgba(255, 255, 255, 0.96));
  border-top: 1px solid var(--panel-border, #e2e8f0);
  z-index: 100;
  min-height: 78px;
  box-shadow: 0 -12px 28px rgba(15, 23, 42, 0.06);
}

.chat-input {
  display: flex;
  padding: 16px 18px;
  height: 100%;
  box-sizing: border-box;
  align-items: center;
}

.input-box {
  flex-grow: 1;
  border: 1px solid var(--panel-border, #d7dee8);
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 15px;
  resize: none;
  min-height: 42px;
  max-height: 42px;
  outline: none;
  background: var(--chat-input-bg, #f8fafc);
  color: var(--chat-input-text, #111827);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

/* 隐藏Webkit浏览器的滚动条 */
.input-box::-webkit-scrollbar {
  display: none;
}

.input-box:focus {
  border-color: var(--app-accent, #2563eb);
  background: var(--chat-input-focus-bg, #ffffff);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-accent, #2563eb) 16%, transparent);
}

.send-button {
  margin-left: 12px;
  background: linear-gradient(135deg, var(--chat-user-bg, #2563eb), var(--chat-user-bg-hover, #1d4ed8));
  color: white;
  border: none;
  border-radius: 8px;
  padding: 0 22px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  height: 42px;
  align-self: center;
  box-shadow: 0 10px 20px color-mix(in srgb, var(--chat-user-bg, #2563eb) 24%, transparent);
}

.send-button:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 14px 26px color-mix(in srgb, var(--chat-user-bg, #2563eb) 30%, transparent);
}

.stop-button {
  margin-left: 8px;
  height: 42px;
  padding: 0 16px;
  border: 1px solid var(--danger-border, rgba(220, 38, 38, 0.28));
  border-radius: 8px;
  background: var(--danger-bg, #fee2e2);
  color: #b42318;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, transform 0.2s ease;
}

.stop-button:hover {
  border-color: rgba(220, 38, 38, 0.46);
  background: #fecaca;
  transform: translateY(-1px);
}

.typing-indicator {
  display: inline-block;
  animation: blink 0.7s infinite;
  margin-left: 2px;
}

@keyframes blink {
  0% { opacity: 0; }
  50% { opacity: 1; }
  100% { opacity: 0; }
}

.input-box:disabled, .send-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .message {
    max-width: 95%;
  }
  
  .message-content {
    font-size: 15px;
  }
  
  .chat-input {
    padding: 12px;
  }
  
  .input-box {
    padding: 8px 12px;
  }
  
  .send-button {
    padding: 0 15px;
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  .avatar {
    width: 32px;
    height: 32px;
  }
  
  .message-bubble {
    padding: 10px;
  }
  
  .message-content {
    font-size: 14px;
  }
  
  .chat-input-container {
    min-height: 66px;
  }
}

/* 新增：不同类型消息的样式 */
.ai-answer {
  animation: fadeIn 0.3s ease-in-out;
}

.ai-final {
  /* 最终回答，可以有不同的样式，例如边框高亮等 */
}

.ai-error {
  opacity: 0.7;
}

.user-question {
  /* 用户提问的特殊样式 */
}

/* 连续消息气泡样式 */
.ai-message + .ai-message {
  margin-top: 4px;
}

.ai-message + .ai-message .avatar {
  visibility: hidden;
}

.ai-message + .ai-message .message-bubble {
  border-top-left-radius: 10px;
}
</style> 
