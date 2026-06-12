<template>
  <div class="watchlist-panel-mask" @click.self="closePanel">
    <section class="watchlist-panel" aria-label="自选股设置">
      <div class="watchlist-panel-header">
        <div>
          <h2>自选股</h2>
          <p>管理分析上下文中的关注股票和风险偏好</p>
        </div>
        <button type="button" class="panel-close-button" aria-label="关闭自选股面板" @click="closePanel">×</button>
      </div>

      <div class="preference-title">风险偏好</div>
      <div class="risk-options">
        <button
          v-for="option in riskOptions"
          :key="option.value"
          class="risk-option"
          :class="{ active: riskPreference === option.value }"
          :disabled="disabled"
          @click="changeRiskPreference(option.value)"
        >
          {{ option.label }}
        </button>
      </div>

      <form class="watchlist-form" @submit.prevent="submitStock">
        <input v-model="stockForm.stockCode" type="text" placeholder="代码" :disabled="disabled" />
        <input v-model="stockForm.stockName" type="text" placeholder="名称" :disabled="disabled" />
        <input
          v-model="stockForm.remark"
          class="watchlist-remark"
          type="text"
          placeholder="关注点"
          :disabled="disabled"
        />
        <button type="submit" :disabled="disabled">添加</button>
      </form>

      <div class="watchlist-list">
        <div v-for="stock in watchlist" :key="stock.id" class="watchlist-item">
          <div>
            <strong>{{ stock.stockCode }}</strong>
            <span>{{ stock.stockName }}</span>
            <small v-if="stock.remark">{{ stock.remark }}</small>
          </div>
          <button type="button" title="移除自选股" @click="removeStock(stock.id)">×</button>
        </div>
        <div v-if="!watchlist.length" class="empty-watchlist">暂无自选股</div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'
import { message as antMessage } from 'ant-design-vue'

const props = defineProps({
  watchlist: {
    type: Array,
    default: () => [],
  },
  riskPreference: {
    type: String,
    default: 'balanced',
  },
  riskOptions: {
    type: Array,
    default: () => [],
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  resetKey: {
    type: Number,
    default: 0,
  },
})

const emit = defineEmits(['close', 'change-risk', 'add-stock', 'remove-stock'])

const stockForm = reactive({
  stockCode: '',
  stockName: '',
  remark: '',
})

const resetForm = () => {
  stockForm.stockCode = ''
  stockForm.stockName = ''
  stockForm.remark = ''
}

watch(
  () => props.resetKey,
  () => {
    resetForm()
  }
)

const closePanel = () => {
  emit('close')
}

const changeRiskPreference = (value) => {
  emit('change-risk', value)
}

const submitStock = () => {
  const stockCode = stockForm.stockCode.trim()
  const stockName = stockForm.stockName.trim()
  const remark = stockForm.remark.trim()
  if (!stockCode || !stockName) {
    antMessage.warning('请填写股票代码和名称')
    return
  }
  emit('add-stock', {
    stockCode: stockCode.toUpperCase(),
    stockName,
    remark,
  })
}

const removeStock = (id) => {
  emit('remove-stock', id)
}
</script>

<style scoped>
.watchlist-panel-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  justify-content: flex-end;
  background: rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(2px);
}

.watchlist-panel {
  display: flex;
  flex-direction: column;
  width: min(420px, calc(100vw - 16px));
  height: 100%;
  padding: 22px;
  border-left: 1px solid var(--panel-border);
  background: var(--panel-bg);
  color: var(--text-strong);
  box-shadow: -18px 0 44px rgba(15, 23, 42, 0.14);
}

.watchlist-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}

.watchlist-panel-header h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 22px;
  line-height: 1.2;
}

.watchlist-panel-header p {
  margin: 7px 0 0;
  color: var(--text-muted);
  font-size: 13px;
}

.panel-close-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  min-width: 32px;
  padding: 0;
  border: 1px solid var(--panel-border);
  border-radius: 50%;
  background: var(--sidebar-item-bg, rgba(255, 255, 255, 0.56));
  color: var(--text-muted);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;
}

.panel-close-button:hover {
  border-color: var(--danger-bg);
  background: var(--danger-bg);
  color: #b42318;
}

.preference-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 800;
}

.preference-title::before {
  content: '';
  width: 4px;
  height: 14px;
  border-radius: 99px;
  background: var(--app-accent);
}

.risk-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.risk-option {
  height: 34px;
  border: 1px solid var(--panel-border);
  border-radius: 7px;
  background: var(--sidebar-input-bg, var(--chat-input-bg, rgba(248, 250, 252, 0.92)));
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease, color 0.2s ease;
}

.risk-option.active,
.risk-option:hover:not(:disabled) {
  border-color: var(--app-accent-line);
  background: var(--app-accent-soft);
  color: var(--app-accent);
}

.watchlist-form {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 8px;
  margin-top: 16px;
}

.watchlist-form input {
  min-width: 0;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--panel-border);
  border-radius: 7px;
  background: var(--sidebar-input-bg, var(--chat-input-bg, rgba(248, 250, 252, 0.92)));
  color: var(--text-strong);
  outline: none;
  font-size: 13px;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.watchlist-form input:focus {
  border-color: var(--app-accent-line);
  background: var(--sidebar-input-focus-bg, #ffffff);
}

.watchlist-form input::placeholder {
  color: var(--text-muted);
}

.watchlist-form .watchlist-remark {
  grid-column: span 2;
}

.watchlist-form button {
  grid-column: span 2;
  height: 34px;
  border: none;
  border-radius: 7px;
  background: linear-gradient(135deg, var(--app-accent), var(--app-accent-dark));
  color: #ffffff;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.watchlist-form button:disabled,
.risk-option:disabled {
  opacity: 0.56;
  cursor: not-allowed;
}

.watchlist-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 18px;
  flex: 1;
  min-height: 0;
  overflow: auto;
  overflow-x: hidden;
  padding-right: 2px;
}

.watchlist-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 26px;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 9px 8px 9px 12px;
  border: 1px solid var(--panel-border);
  border-radius: 7px;
  background: var(--sidebar-item-bg, rgba(255, 255, 255, 0.56));
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.watchlist-item:hover {
  border-color: var(--app-accent-line);
  background: var(--app-accent-soft);
}

.watchlist-item > div {
  min-width: 0;
}

.watchlist-item strong {
  display: inline-block;
  margin-right: 6px;
  color: var(--text-strong);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.watchlist-item span {
  color: var(--text-strong);
  font-size: 13px;
  overflow-wrap: anywhere;
}

.watchlist-item small {
  display: block;
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.watchlist-item button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  flex: 0 0 24px;
  width: 26px;
  height: 26px;
  min-width: 0;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 50%;
  background: color-mix(in srgb, var(--danger-bg, #fee2e2) 36%, transparent);
  color: color-mix(in srgb, #b42318 58%, var(--text-muted));
  font-size: 18px;
  line-height: 1;
  font-weight: 700;
  cursor: pointer;
  opacity: 0.72;
  transition: opacity 0.2s ease, color 0.2s ease, background-color 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.watchlist-item button:hover {
  border-color: rgba(180, 35, 24, 0.18);
  background: var(--danger-bg);
  color: #b42318;
  opacity: 1;
  transform: scale(1.04);
}

.watchlist-item button:active {
  transform: scale(0.96);
}

.empty-watchlist {
  padding: 6px 0 2px;
  color: var(--text-muted);
  font-size: 12px;
  text-align: center;
}

:global(.theme-cyber) .watchlist-panel,
:global(.theme-cyber) .watchlist-item {
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}
</style>
