<template>
  <div class="stock-workbench" :class="themeClass">
    <header class="workbench-header">
      <div>
        <h1>自选股</h1>
      </div>
      <div class="header-actions">
        <button type="button" @click="generateTodaySummary">今日关注摘要</button>
        <button type="button" @click="router.push('/stock-master')">返回股票大师</button>
      </div>
    </header>

    <section class="workbench-layout">
      <aside class="watchlist-panel">
        <div class="toolbar">
          <input v-model="keyword" type="search" placeholder="搜索代码、名称、主题或备注" />
          <select v-model="activeGroup">
            <option value="">全部分组</option>
            <option v-for="group in groups" :key="group" :value="group">{{ group }}</option>
          </select>
        </div>

        <div class="watch-card-list">
          <article
            v-for="stock in filteredWatchlist"
            :key="stock.id"
            class="watch-card"
            :class="{ active: selectedStock?.id === stock.id }"
            @click="selectStock(stock)"
          >
            <div class="watch-card-top">
              <div class="watch-card-main">
                <strong>{{ stock.stockCode }}</strong>
                <span>{{ stock.stockName }}</span>
              </div>
              <span :class="['risk-pill', `risk-${getMeta(stock).riskLevel}`]">
                {{ riskLabelMap[getMeta(stock).riskLevel] }}
              </span>
            </div>
            <div class="watch-card-tags">
              <span>{{ getMeta(stock).groupName || '未分组' }}</span>
            </div>
          </article>
          <div v-if="!filteredWatchlist.length" class="empty-panel">暂无匹配的自选股</div>
        </div>
      </aside>

      <main class="detail-panel" v-if="selectedStock">
        <section class="detail-hero">
          <div>
            <div class="detail-code">{{ selectedStock.stockCode }}</div>
            <h2>{{ selectedStock.stockName }}</h2>
            <div class="detail-tags">
              <span>{{ activeMeta.groupName || '未分组' }}</span>
              <span :class="['risk-pill', `risk-${activeMeta.riskLevel}`]">{{ riskLabelMap[activeMeta.riskLevel] }}</span>
            </div>
          </div>
          <button type="button" @click="openStockResearch(selectedStock)">生成分析档案</button>
        </section>

        <section class="editor-panel">
          <div class="editor-toolbar">
            <div class="form-row">
              <label>
                分组 / 主题
                <input v-model="activeMeta.groupName" placeholder="例如 AI算力 / 光模块 / 半导体" />
              </label>
              <label>
                风险等级
                <select v-model="activeMeta.riskLevel">
                  <option value="low">低</option>
                  <option value="medium">中</option>
                  <option value="high">高</option>
                </select>
              </label>
            </div>

            <div class="detail-actions">
              <button type="button" @click="saveActiveMeta">保存档案</button>
            </div>
          </div>

          <div class="note-grid">
            <label class="note-card">
              <span>关注点</span>
              <textarea v-model="activeMeta.focusPoint" rows="5" placeholder="为什么关注这只股票" />
            </label>
            <label class="note-card">
              <span>跟踪理由</span>
              <textarea v-model="activeMeta.holdingReason" rows="5" placeholder="记录研究假设、跟踪理由或持仓逻辑" />
            </label>
            <label class="note-card">
              <span>风险备注</span>
              <textarea v-model="activeMeta.riskNote" rows="5" placeholder="记录估值、业绩、政策、资金或情绪风险" />
            </label>
          </div>
        </section>

      </main>

      <main class="detail-panel empty-detail" v-else>
        <div>
          <h2>选择一只自选股</h2>
          <p>在左侧选择股票后，可编辑关注逻辑、风险备注和后续跟踪任务。</p>
        </div>
      </main>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message as antMessage } from 'ant-design-vue'
import { listStockWatchlist } from '@/services/chat'
import { useInterfaceTheme } from '@/composables/useInterfaceTheme'

const WORKBENCH_META_KEY = 'stock_watchlist_workbench_meta'
const PENDING_STOCK_PROMPT_KEY = 'stock_master_pending_prompt'

const router = useRouter()
const { themeClass } = useInterfaceTheme()

const watchlist = ref([])
const keyword = ref('')
const activeGroup = ref('')
const selectedStock = ref(null)
const stockMetaMap = ref({})
const activeMeta = reactive({
  groupName: '',
  riskLevel: 'medium',
  focusPoint: '',
  holdingReason: '',
  riskNote: '',
})

const riskLabelMap = {
  low: '低风险',
  medium: '中风险',
  high: '高风险',
}

const loadMeta = () => {
  try {
    stockMetaMap.value = JSON.parse(localStorage.getItem(WORKBENCH_META_KEY) || '{}')
  } catch (error) {
    stockMetaMap.value = {}
  }
}

const persistMeta = () => {
  localStorage.setItem(WORKBENCH_META_KEY, JSON.stringify(stockMetaMap.value))
}

const getMetaKey = (stock) => stock?.stockCode || stock?.id

const getMeta = (stock) => {
  return stockMetaMap.value[getMetaKey(stock)] || {
    groupName: '',
    riskLevel: 'medium',
    focusPoint: stock?.remark || '',
    holdingReason: '',
    riskNote: '',
  }
}

const syncActiveMeta = (stock) => {
  Object.assign(activeMeta, getMeta(stock))
}

const selectStock = (stock) => {
  selectedStock.value = stock
  syncActiveMeta(stock)
}

const saveActiveMeta = () => {
  if (!selectedStock.value) {
    return
  }
  stockMetaMap.value = {
    ...stockMetaMap.value,
    [getMetaKey(selectedStock.value)]: { ...activeMeta },
  }
  persistMeta()
  antMessage.success('自选股档案已保存')
}

const fetchWatchlist = async () => {
  const res = await listStockWatchlist()
  if (res.data.code === 0) {
    watchlist.value = res.data.data || []
    if (!selectedStock.value && watchlist.value.length) {
      selectStock(watchlist.value[0])
    }
    return
  }
  antMessage.error(res.data.message || '获取自选股失败')
}

const groups = computed(() => {
  return [...new Set(watchlist.value.map((stock) => getMeta(stock).groupName).filter(Boolean))]
})

const filteredWatchlist = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase()
  return watchlist.value.filter((stock) => {
    const meta = getMeta(stock)
    const matchesGroup = !activeGroup.value || meta.groupName === activeGroup.value
    const searchable = [
      stock.stockCode,
      stock.stockName,
      stock.remark,
      meta.groupName,
      meta.focusPoint,
      meta.holdingReason,
      meta.riskNote,
    ].join(' ').toLowerCase()
    return matchesGroup && (!normalizedKeyword || searchable.includes(normalizedKeyword))
  })
})

const highRiskCount = computed(() => watchlist.value.filter((stock) => getMeta(stock).riskLevel === 'high').length)
const trackingCount = computed(() => watchlist.value.filter((stock) => {
  const meta = getMeta(stock)
  return meta.focusPoint || meta.holdingReason || meta.riskNote || stock.remark
}).length)

const clipText = (value, maxLength = 96) => {
  const normalizedValue = String(value || '').replace(/\s+/g, ' ').trim()
  if (!normalizedValue) {
    return '暂无'
  }
  return normalizedValue.length > maxLength
    ? `${normalizedValue.slice(0, maxLength)}...（内容较长，已摘要）`
    : normalizedValue
}

const buildStockPrompt = (stock) => {
  const meta = getMeta(stock)
  const context = [
    `标的：${stock.stockCode} ${stock.stockName}`,
    `分组：${meta.groupName || '未分组'}`,
    `风险等级：${riskLabelMap[meta.riskLevel] || '中风险'}`,
    `用户关注点摘要：${clipText(meta.focusPoint || stock.remark, 90)}`,
    `用户跟踪理由摘要：${clipText(meta.holdingReason, 90)}`,
    `用户风险备注摘要：${clipText(meta.riskNote, 90)}`,
  ].join('\n')
  const base = [
    '请基于以下自选股上下文生成结构化投研分析。',
    context,
    '要求：不要复述用户备注原文，只提炼判断；区分“用户备注”“内部检索资料”和“你的分析推断”；如果数据口径、日期或字段不完整，请写“需补充公告/财报/行情数据验证”；不要把不完整备注扩写成确定事实；不要输出引用来源、参考来源、来源或知识来源章节；不要暴露文件名、表名、数据库名、RAG、CSV、Markdown、PDF、stock_info 等内部实现细节；保持专业表达，不使用 emoji。',
  ].join('\n')
  return `${base}\n请输出投资结论、关注逻辑、风险标签、观察指标和后续跟踪。`
}

const openPromptInStockMaster = (prompt, displayMessage) => {
  sessionStorage.setItem(PENDING_STOCK_PROMPT_KEY, JSON.stringify({
    prompt,
    displayMessage: displayMessage || prompt,
  }))
  router.push('/stock-master')
}

const openStockResearch = (stock) => openPromptInStockMaster(
  buildStockPrompt(stock),
  `请生成 ${stock.stockCode} ${stock.stockName} 的结构化分析档案`
)
const generateTodaySummary = () => {
  if (!watchlist.value.length) {
    antMessage.warning('请先添加自选股')
    return
  }
  const stockLines = watchlist.value.slice(0, 20).map((stock) => {
    const meta = getMeta(stock)
    return `- ${stock.stockCode} ${stock.stockName}：分组=${meta.groupName || '未分组'}；风险等级=${riskLabelMap[meta.riskLevel] || '中风险'}；关注点摘要=${clipText(meta.focusPoint || stock.remark, 72)}；风险摘要=${clipText(meta.riskNote || riskLabelMap[meta.riskLevel], 72)}`
  }).join('\n')
  const overflowNote = watchlist.value.length > 20 ? `\n另有 ${watchlist.value.length - 20} 只自选股未展开，请提示我分批分析。` : ''
  openPromptInStockMaster([
    '请基于我的自选股生成“今日关注摘要”，按高优先级、中优先级、低优先级分组，并给出后续公告、财报、新闻和行业数据跟踪清单。',
    '要求：不要复述用户备注原文；不要把用户备注中的长文本或不完整数字当成完整事实；不要输出引用来源、参考来源、来源或知识来源章节；不要暴露文件名、表名、数据库名、RAG、CSV、Markdown、PDF、stock_info 等内部实现细节；保持专业表达，不使用 emoji。',
    `${stockLines}${overflowNote}`,
  ].join('\n'), '请生成我的自选股今日关注摘要')
}

watch(selectedStock, (stock) => {
  if (stock) {
    syncActiveMeta(stock)
  }
})

onMounted(() => {
  loadMeta()
  fetchWatchlist()
})
</script>

<style scoped>
.stock-workbench {
  --workbench-bg: #f7fbff;
  --workbench-panel: rgba(255, 255, 255, 0.94);
  --workbench-border: #d5e5dd;
  --workbench-text: #0f2f25;
  --workbench-muted: #60776e;
  --workbench-accent: #0f8b5f;
  --workbench-accent-soft: #e5f5ee;
  --workbench-field: #f8fafc;
  min-height: 100vh;
  padding: 22px 28px 28px;
  background:
    linear-gradient(180deg, rgba(14, 165, 233, 0.12), transparent 42%),
    linear-gradient(90deg, rgba(15, 23, 42, 0.05) 1px, transparent 1px),
    linear-gradient(rgba(15, 23, 42, 0.05) 1px, transparent 1px),
    var(--workbench-bg);
  background-size: auto, 44px 44px, 44px 44px, auto;
  color: var(--workbench-text);
}

.workbench-header,
.header-actions,
.toolbar,
.watch-card-top,
.watch-card-tags,
.detail-hero,
.detail-tags,
.detail-actions {
  display: flex;
}

.workbench-header {
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  max-width: 1480px;
  margin: 0 auto 18px;
}

.workbench-header h1,
.detail-hero h2,
.detail-hero h2 {
  margin: 0;
}

.workbench-header h1 {
  position: relative;
  display: inline-flex;
  align-items: center;
  padding-left: 16px;
  color: var(--workbench-text);
  font-size: clamp(30px, 3.1vw, 40px);
  line-height: 1.08;
  font-weight: 850;
  letter-spacing: 0;
}

.workbench-header h1::before {
  content: '';
  position: absolute;
  left: 0;
  width: 5px;
  height: 0.86em;
  border-radius: 999px;
  background: linear-gradient(180deg, var(--workbench-accent), #0b6748);
}

.workbench-header h1::after {
  content: '';
  position: absolute;
  left: 16px;
  right: 0;
  bottom: -7px;
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--workbench-accent), transparent);
  opacity: 0.28;
}

.detail-code,
.watch-card p {
  color: var(--workbench-muted);
}

.header-actions,
.toolbar,
.detail-actions {
  gap: 10px;
}

button,
input,
select,
textarea {
  border: 1px solid var(--workbench-border);
  border-radius: 8px;
  font: inherit;
}

button {
  background: var(--workbench-panel);
  color: var(--workbench-text);
  cursor: pointer;
  font-weight: 700;
}

.header-actions button:first-child,
.detail-actions button:first-child {
  border: none;
  background: linear-gradient(135deg, var(--workbench-accent), #0b6748);
  color: #fff;
}

.header-actions button {
  height: 42px;
  padding: 0 16px;
  box-shadow: 0 10px 22px rgba(15, 139, 95, 0.1);
}

.watchlist-panel,
.watch-card,
.detail-hero,
.editor-panel,
.detail-panel,
.empty-panel {
  border: 1px solid var(--workbench-border);
  border-radius: 8px;
  background: var(--workbench-panel);
  box-shadow: 0 18px 48px rgba(15, 47, 37, 0.08);
}

.toolbar {
  margin-bottom: 14px;
}

.toolbar input {
  flex: 1;
}

.toolbar input,
.toolbar select,
.detail-panel input,
.detail-panel select,
.detail-panel textarea {
  padding: 9px 11px;
  background: var(--workbench-field);
  color: var(--workbench-text);
}

.workbench-layout {
  display: grid;
  grid-template-columns: minmax(320px, 380px) minmax(0, 1fr);
  gap: 16px;
  max-width: 1480px;
  margin: 0 auto;
  align-items: start;
}

.watchlist-panel {
  padding: 16px;
  position: sticky;
  top: 18px;
  min-height: calc(100vh - 142px);
}

.watch-card-list {
  display: grid;
  gap: 10px;
  align-content: start;
  max-height: calc(100vh - 236px);
  min-height: calc(100vh - 236px);
  overflow: auto;
  padding-right: 2px;
}

.watch-card {
  padding: 13px 14px;
  cursor: pointer;
  box-shadow: none;
}

.watch-card.active,
.watch-card:hover {
  border-color: var(--workbench-accent);
  background: color-mix(in srgb, var(--workbench-accent) 8%, var(--workbench-panel));
}

.watch-card-top {
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.watch-card-main {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px;
  align-items: baseline;
}

.watch-card-main strong {
  font-size: 15px;
  font-variant-numeric: tabular-nums;
}

.watch-card-main span {
  font-size: 15px;
  font-weight: 800;
}

.watch-card-tags {
  flex-wrap: wrap;
  gap: 6px;
  margin: 9px 0 0;
}

.watch-card-tags span,
.detail-tags span {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--workbench-accent-soft);
  color: var(--workbench-accent);
  font-size: 12px;
  font-weight: 700;
}

.risk-pill {
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
  line-height: 1.4;
}

.risk-high {
  background: #fee2e2;
  color: #b42318;
}

.risk-medium {
  background: #fef3c7;
  color: #a16207;
}

.risk-low {
  background: #dcfce7;
  color: #15803d;
}

.detail-panel {
  display: grid;
  gap: 14px;
  padding: 0;
  border: none;
  background: transparent;
  box-shadow: none;
}

.detail-hero {
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 22px;
}

.detail-code {
  margin-bottom: 5px;
  font-size: 15px;
  font-weight: 700;
}

.detail-hero h2 {
  font-size: 28px;
  line-height: 1.15;
}

.detail-tags {
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.detail-hero button {
  padding: 10px 14px;
  border: none;
  background: linear-gradient(135deg, var(--workbench-accent), #0b6748);
  color: #fff;
}

.editor-panel {
  padding: 18px;
}

.editor-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: end;
  margin-bottom: 14px;
}

.detail-panel label {
  display: grid;
  gap: 6px;
  color: var(--workbench-muted);
  font-size: 13px;
  font-weight: 700;
}

.form-row {
  display: grid;
  grid-template-columns: minmax(220px, 340px) 150px;
  gap: 12px;
  margin-bottom: 0;
}

.note-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.note-card {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--workbench-border);
  border-radius: 8px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--workbench-accent) 5%, transparent), transparent 62%),
    color-mix(in srgb, var(--workbench-panel) 84%, var(--workbench-field));
}

.note-card {
  grid-template-columns: 64px minmax(0, 1fr);
  align-items: start;
  column-gap: 8px;
}

.note-card span {
  color: var(--workbench-text);
  font-size: 14px;
  font-weight: 800;
}

.note-card small {
  display: block;
  margin-bottom: 4px;
  color: var(--workbench-muted);
  font-size: 12px;
  line-height: 1.5;
  font-weight: 500;
}

.note-card small {
  display: none;
}

.note-card textarea {
  grid-column: 2;
  grid-row: 1 / span 2;
  margin-top: 0;
  border-color: transparent;
  background: rgba(255, 255, 255, 0.66);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--workbench-border) 72%, transparent);
}

.note-card textarea:focus {
  border-color: var(--workbench-accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--workbench-accent) 12%, transparent);
  outline: none;
}

.detail-panel textarea {
  resize: vertical;
  min-height: 126px;
  line-height: 1.6;
}

.detail-actions button,
.header-actions button,
.detail-hero button {
  padding: 9px 13px;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 0;
}

.empty-detail {
  min-height: 360px;
  place-items: center;
  text-align: center;
}

.empty-panel {
  padding: 24px;
  color: var(--workbench-muted);
  text-align: center;
}

.stock-workbench.theme-cyber {
  --workbench-bg: #08111f;
  --workbench-panel: rgba(10, 18, 34, 0.88);
  --workbench-border: rgba(103, 232, 249, 0.22);
  --workbench-text: #e0f2fe;
  --workbench-muted: #94a3b8;
  --workbench-accent: #22d3ee;
  --workbench-accent-soft: rgba(34, 211, 238, 0.12);
  --workbench-field: rgba(15, 23, 42, 0.72);
  background:
    linear-gradient(135deg, rgba(8, 13, 29, 0.96), rgba(15, 23, 42, 0.94)),
    linear-gradient(rgba(34, 211, 238, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(129, 140, 248, 0.05) 1px, transparent 1px);
  background-size: auto, 48px 48px, 48px 48px;
}

.stock-workbench.theme-cyber input,
.stock-workbench.theme-cyber select,
.stock-workbench.theme-cyber textarea {
  background: var(--workbench-field);
}

.stock-workbench.theme-cyber .note-card textarea {
  background: rgba(15, 23, 42, 0.78);
}

@media (max-width: 900px) {
  .stock-workbench {
    padding: 16px;
  }

  .workbench-header,
  .detail-hero {
    flex-direction: column;
  }

  .workbench-layout,
  .form-row,
  .note-grid,
  .editor-toolbar {
    grid-template-columns: 1fr;
  }

  .note-card {
    grid-template-columns: 1fr;
  }

  .note-card textarea {
    grid-column: auto;
    grid-row: auto;
  }

  .watchlist-panel {
    position: static;
    min-height: auto;
  }

  .watch-card-list {
    max-height: none;
    min-height: 0;
  }
}
</style>
