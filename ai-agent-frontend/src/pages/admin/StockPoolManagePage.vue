<template>
  <div id="stockPoolManagePage" :class="themeClass">
    <div class="page-header">
      <div>
        <h1>股票池管理</h1>
      </div>
      <div class="header-actions">
        <a-button @click="router.push('/admin/userManage')">用户管理</a-button>
        <a-button @click="router.push('/')">返回首页</a-button>
      </div>
    </div>

    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="代码">
        <a-input v-model:value="searchParams.stockCode" placeholder="股票代码" allow-clear />
      </a-form-item>
      <a-form-item label="名称">
        <a-input v-model:value="searchParams.stockName" placeholder="股票名称" allow-clear />
      </a-form-item>
      <a-form-item label="市场">
        <a-input v-model:value="searchParams.market" placeholder="A股 / 港股 / 美股" allow-clear />
      </a-form-item>
      <a-form-item label="行业">
        <a-input v-model:value="searchParams.industry" placeholder="所属行业" allow-clear />
      </a-form-item>
      <a-form-item label="主题">
        <a-input v-model:value="searchParams.themeKeyword" placeholder="主题关键词" allow-clear />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
      <a-form-item>
        <a-button @click="resetSearch">重置</a-button>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="openCreateDrawer">新增股票</a-button>
      </a-form-item>
      <a-form-item>
        <a-button @click="exportCsv">导出 CSV</a-button>
      </a-form-item>
      <a-form-item>
        <label class="csv-import-button">
          导入 CSV
          <input type="file" accept=".csv" @change="importCsv" />
        </label>
      </a-form-item>
    </a-form>

    <a-divider />

    <a-table
      row-key="id"
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      :scroll="{ x: 1300 }"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'themes'">
          <div class="tag-list">
            <a-tag v-for="tag in splitTags(record.themes)" :key="tag" color="blue">{{ tag }}</a-tag>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'riskTags'">
          <div class="tag-list">
            <a-tag v-for="tag in splitTags(record.riskTags)" :key="tag" color="orange">{{ tag }}</a-tag>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'reason'">
          <span class="reason-cell">{{ record.reason }}</span>
        </template>
        <template v-else-if="column.dataIndex === 'updateTime'">
          {{ record.updateTime ? dayjs(record.updateTime).format('YYYY-MM-DD HH:mm') : '-' }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button size="small" @click="openEditDrawer(record)">编辑</a-button>
            <a-popconfirm title="确定删除这条股票池记录？" @confirm="doDelete(record.id)">
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-drawer
      v-model:open="drawerOpen"
      :title="editingId ? '编辑股票' : '新增股票'"
      width="520"
      destroy-on-close
      @close="closeDrawer"
    >
      <a-form ref="formRef" layout="vertical" :model="formState" :rules="formRules">
        <a-form-item label="股票代码" name="stockCode">
          <a-input v-model:value="formState.stockCode" placeholder="例如 300394" />
        </a-form-item>
        <a-form-item label="股票名称" name="stockName">
          <a-input v-model:value="formState.stockName" placeholder="例如 天孚通信" />
        </a-form-item>
        <a-form-item label="市场" name="market">
          <a-input v-model:value="formState.market" placeholder="A股 / 港股 / 美股" />
        </a-form-item>
        <a-form-item label="所属行业" name="industry">
          <a-input v-model:value="formState.industry" placeholder="例如 光通信" />
        </a-form-item>
        <a-form-item label="主题标签" name="themes">
          <a-input v-model:value="formState.themes" placeholder="多个标签用逗号或分号分隔" />
        </a-form-item>
        <a-form-item label="风险标签" name="riskTags">
          <a-input v-model:value="formState.riskTags" placeholder="多个标签用逗号或分号分隔" />
        </a-form-item>
        <a-form-item label="热度分" name="hotScore">
          <a-input-number v-model:value="formState.hotScore" :min="0" :max="1000" style="width: 100%" />
        </a-form-item>
        <a-form-item label="入池理由" name="reason">
          <a-textarea
            v-model:value="formState.reason"
            :rows="5"
            show-count
            :maxlength="1000"
            placeholder="说明该股票为什么值得进入股票池"
          />
        </a-form-item>
      </a-form>
      <template #footer>
        <div class="drawer-footer">
          <a-button @click="closeDrawer">取消</a-button>
          <a-button type="primary" :loading="submitting" @click="submitForm">保存</a-button>
        </div>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  addStockInfo,
  deleteStockInfo,
  listStockInfoByPage,
  updateStockInfo,
} from '@/api/stockInfoController'
import { useInterfaceTheme } from '@/composables/useInterfaceTheme'

const router = useRouter()
const { themeClass } = useInterfaceTheme()

const columns = [
  { title: '代码', dataIndex: 'stockCode', width: 110 },
  { title: '名称', dataIndex: 'stockName', width: 120 },
  { title: '市场', dataIndex: 'market', width: 90 },
  { title: '行业', dataIndex: 'industry', width: 130 },
  { title: '主题标签', dataIndex: 'themes', width: 230 },
  { title: '风险标签', dataIndex: 'riskTags', width: 210 },
  { title: '入池理由', dataIndex: 'reason', width: 260 },
  { title: '热度分', dataIndex: 'hotScore', sorter: true, width: 90 },
  { title: '更新时间', dataIndex: 'updateTime', sorter: true, width: 150 },
  { title: '操作', key: 'action', fixed: 'right', width: 130 },
]

const data = ref<API.StockInfo[]>([])
const total = ref(0)
const drawerOpen = ref(false)
const submitting = ref(false)
const editingId = ref<number | undefined>()
const formRef = ref<FormInstance>()

const searchParams = reactive<API.StockInfoQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

const formState = reactive<API.StockInfoUpdateRequest>({
  stockCode: '',
  stockName: '',
  market: 'A股',
  industry: '',
  themes: '',
  riskTags: '',
  reason: '',
  hotScore: 0,
})

const formRules = {
  stockCode: [{ required: true, message: '请输入股票代码' }],
  stockName: [{ required: true, message: '请输入股票名称' }],
}

const pagination = computed(() => ({
  current: searchParams.pageNum ?? 1,
  pageSize: searchParams.pageSize ?? 10,
  total: total.value,
  showSizeChanger: true,
  showTotal: (value: number) => `共 ${value} 条`,
}))

const fetchData = async () => {
  const res = await listStockInfoByPage({ ...searchParams })
  if (res.data.code === 0 && res.data.data) {
    data.value = res.data.data.records || []
    total.value = res.data.data.total || 0
    return
  }
  message.error(res.data.message || '获取股票池失败')
}

const doSearch = () => {
  searchParams.pageNum = 1
  fetchData()
}

const resetSearch = () => {
  Object.assign(searchParams, {
    pageNum: 1,
    pageSize: 10,
    stockCode: undefined,
    stockName: undefined,
    market: undefined,
    industry: undefined,
    themeKeyword: undefined,
    riskKeyword: undefined,
    reasonKeyword: undefined,
    sortField: undefined,
    sortOrder: undefined,
  })
  fetchData()
}

const doTableChange = (page: { current: number; pageSize: number }, filters: any, sorter: any) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  if (sorter?.field) {
    searchParams.sortField = sorter.field
    searchParams.sortOrder = sorter.order
  }
  fetchData()
}

const resetForm = () => {
  editingId.value = undefined
  Object.assign(formState, {
    id: undefined,
    stockCode: '',
    stockName: '',
    market: 'A股',
    industry: '',
    themes: '',
    riskTags: '',
    reason: '',
    hotScore: 0,
  })
}

const openCreateDrawer = () => {
  resetForm()
  drawerOpen.value = true
}

const openEditDrawer = (record: API.StockInfo) => {
  editingId.value = record.id
  Object.assign(formState, {
    id: record.id,
    stockCode: record.stockCode,
    stockName: record.stockName,
    market: record.market || 'A股',
    industry: record.industry || '',
    themes: record.themes || '',
    riskTags: record.riskTags || '',
    reason: record.reason || '',
    hotScore: record.hotScore ?? 0,
  })
  drawerOpen.value = true
}

const closeDrawer = () => {
  drawerOpen.value = false
  resetForm()
}

const submitForm = async () => {
  if (submitting.value) {
    return
  }
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = { ...formState }
    const res = editingId.value ? await updateStockInfo(payload) : await addStockInfo(payload)
    if (res.data.code === 0) {
      message.success('保存成功')
      closeDrawer()
      await fetchData()
      return
    }
    message.error(res.data.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const doDelete = async (id?: number) => {
  if (!id) {
    return
  }
  const res = await deleteStockInfo({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    await fetchData()
    return
  }
  message.error(res.data.message || '删除失败')
}

const downloadBlob = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const escapeCsv = (value?: string | number) => {
  return `"${String(value ?? '').replace(/"/g, '""')}"`
}

const csvColumns = [
  { label: '股票代码', key: 'stockCode' },
  { label: '股票名称', key: 'stockName' },
  { label: '市场', key: 'market' },
  { label: '所属行业', key: 'industry' },
  { label: '主题标签', key: 'themes' },
  { label: '风险标签', key: 'riskTags' },
  { label: '热度分', key: 'hotScore' },
  { label: '入池理由', key: 'reason' },
]

const csvHeaderAliasMap: Record<string, string> = {
  股票代码: 'stockCode',
  代码: 'stockCode',
  stockCode: 'stockCode',
  股票名称: 'stockName',
  名称: 'stockName',
  stockName: 'stockName',
  市场: 'market',
  market: 'market',
  所属行业: 'industry',
  行业: 'industry',
  industry: 'industry',
  主题标签: 'themes',
  主题: 'themes',
  themes: 'themes',
  风险标签: 'riskTags',
  风险: 'riskTags',
  riskTags: 'riskTags',
  热度分: 'hotScore',
  热度: 'hotScore',
  hotScore: 'hotScore',
  入池理由: 'reason',
  理由: 'reason',
  reason: 'reason',
}

const exportCsv = () => {
  const header = csvColumns.map((column) => column.label)
  const rows = data.value.map((stock) => csvColumns
    .map((column) => escapeCsv(stock[column.key as keyof API.StockInfo] as string | number))
    .join(','))
  const csv = [header.map(escapeCsv).join(','), ...rows].join('\n')
  downloadBlob(new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8' }), 'stock-pool.csv')
}

const parseCsvLine = (line: string) => {
  const cells: string[] = []
  let current = ''
  let inQuotes = false
  for (let index = 0; index < line.length; index += 1) {
    const char = line[index]
    const nextChar = line[index + 1]
    if (char === '"' && nextChar === '"') {
      current += '"'
      index += 1
    } else if (char === '"') {
      inQuotes = !inQuotes
    } else if (char === ',' && !inQuotes) {
      cells.push(current)
      current = ''
    } else {
      current += char
    }
  }
  cells.push(current)
  return cells
}

const importCsv = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  const text = await file.text()
  const lines = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter(Boolean)
  const [headerLine, ...rows] = lines
  const header = parseCsvLine(headerLine).map((item) => csvHeaderAliasMap[item.trim()] || item.trim())
  let successCount = 0
  for (const row of rows) {
    const values = parseCsvLine(row)
    const record = Object.fromEntries(header.map((key, index) => [key, values[index]]))
    if (!record.stockCode || !record.stockName) {
      continue
    }
    const res = await addStockInfo({
      stockCode: record.stockCode,
      stockName: record.stockName,
      market: record.market || 'A股',
      industry: record.industry || '',
      themes: record.themes || '',
      riskTags: record.riskTags || '',
      reason: record.reason || '',
      hotScore: Number(record.hotScore || 0),
    })
    if (res.data.code === 0) {
      successCount += 1
    }
  }
  input.value = ''
  message.success(`CSV 导入完成，成功 ${successCount} 条`)
  await fetchData()
}

const splitTags = (value?: string) => {
  return String(value || '')
    .split(/[;,，；]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 5)
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#stockPoolManagePage {
  --admin-accent: #2563eb;
  --admin-bg: #f4f8fc;
  --admin-panel: rgba(255, 255, 255, 0.94);
  --admin-border: #dbe5f2;
  --admin-text: #172033;
  --admin-muted: #64748b;
  min-height: 100vh;
  padding: 92px 28px 36px;
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.1), transparent 42%),
    linear-gradient(90deg, rgba(15, 23, 42, 0.05) 1px, transparent 1px),
    linear-gradient(rgba(15, 23, 42, 0.05) 1px, transparent 1px),
    var(--admin-bg);
  background-size: auto, 44px 44px, 44px 44px, auto;
  color: var(--admin-text);
}

.page-header,
.header-actions,
.drawer-footer,
.tag-list {
  display: flex;
}

.page-header {
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.page-header h1 {
  margin: 0;
  color: var(--admin-text);
  font-size: 24px;
  line-height: 1.25;
}

.header-actions,
.drawer-footer {
  gap: 10px;
}

.csv-import-button {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 15px;
  border: 1px solid var(--admin-border);
  border-radius: 8px;
  background: var(--admin-panel);
  color: var(--admin-text);
  cursor: pointer;
  font-weight: 600;
}

.csv-import-button input {
  display: none;
}

.drawer-footer {
  justify-content: flex-end;
}

.tag-list {
  flex-wrap: wrap;
  gap: 4px;
}

.reason-cell {
  display: -webkit-box;
  overflow: hidden;
  color: var(--admin-muted);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

#stockPoolManagePage :deep(.ant-form),
#stockPoolManagePage :deep(.ant-table-wrapper) {
  border: 1px solid var(--admin-border);
  border-radius: 8px;
  background: var(--admin-panel);
  box-shadow: 0 24px 72px rgba(15, 23, 42, 0.1);
}

#stockPoolManagePage :deep(.ant-form) {
  padding: 18px;
}

#stockPoolManagePage :deep(.ant-table-wrapper) {
  overflow: hidden;
}

#stockPoolManagePage :deep(.ant-divider) {
  border-color: transparent;
}

#stockPoolManagePage :deep(.ant-input),
#stockPoolManagePage :deep(.ant-input-affix-wrapper),
#stockPoolManagePage :deep(.ant-input-number),
#stockPoolManagePage :deep(.ant-input-number-input) {
  border-radius: 8px;
}

#stockPoolManagePage :deep(.ant-input),
#stockPoolManagePage :deep(.ant-input-affix-wrapper) {
  border-color: var(--admin-border);
  background: #f8fafc;
  color: var(--admin-text);
}

#stockPoolManagePage :deep(.ant-input-affix-wrapper > input.ant-input) {
  background: transparent;
}

#stockPoolManagePage :deep(.ant-btn) {
  border-radius: 8px;
  font-weight: 600;
}

#stockPoolManagePage :deep(.ant-btn-primary) {
  border: none;
  background: linear-gradient(90deg, var(--admin-accent), #0f766e);
}

#stockPoolManagePage.theme-cyber {
  --admin-accent: #22d3ee;
  --admin-bg: #08111f;
  --admin-panel: rgba(10, 18, 34, 0.88);
  --admin-border: rgba(103, 232, 249, 0.22);
  --admin-text: #e0f2fe;
  --admin-muted: #94a3b8;
  background:
    linear-gradient(135deg, rgba(8, 13, 29, 0.96), rgba(15, 23, 42, 0.94)),
    linear-gradient(rgba(34, 211, 238, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(129, 140, 248, 0.05) 1px, transparent 1px);
  background-size: auto, 48px 48px, 48px 48px;
}

#stockPoolManagePage.theme-cyber :deep(.ant-form),
#stockPoolManagePage.theme-cyber :deep(.ant-table-wrapper) {
  box-shadow: 0 24px 72px rgba(0, 0, 0, 0.26);
}

#stockPoolManagePage.theme-cyber :deep(.ant-form-item-label > label),
#stockPoolManagePage.theme-cyber :deep(.ant-table),
#stockPoolManagePage.theme-cyber :deep(.ant-table-thead > tr > th),
#stockPoolManagePage.theme-cyber :deep(.ant-pagination),
#stockPoolManagePage.theme-cyber :deep(.ant-pagination-total-text) {
  color: var(--admin-text);
}

#stockPoolManagePage.theme-cyber :deep(.ant-table),
#stockPoolManagePage.theme-cyber :deep(.ant-table-thead > tr > th),
#stockPoolManagePage.theme-cyber :deep(.ant-table-tbody > tr > td) {
  background: transparent;
  border-color: rgba(103, 232, 249, 0.12);
}

#stockPoolManagePage.theme-cyber :deep(.ant-table-tbody > tr:hover > td) {
  background: rgba(34, 211, 238, 0.08);
}

#stockPoolManagePage.theme-cyber :deep(.ant-input),
#stockPoolManagePage.theme-cyber :deep(.ant-input-affix-wrapper),
#stockPoolManagePage.theme-cyber :deep(.ant-input-number),
#stockPoolManagePage.theme-cyber :deep(.ant-input-number-input) {
  border-color: rgba(103, 232, 249, 0.18);
  background: rgba(15, 23, 42, 0.72);
  color: var(--admin-text);
}

#stockPoolManagePage.theme-cyber :deep(.ant-input-affix-wrapper > input.ant-input) {
  border: 0;
  background: transparent;
  box-shadow: none;
}

#stockPoolManagePage.theme-cyber :deep(.ant-input:hover),
#stockPoolManagePage.theme-cyber :deep(.ant-input-affix-wrapper:hover),
#stockPoolManagePage.theme-cyber :deep(.ant-input:focus),
#stockPoolManagePage.theme-cyber :deep(.ant-input-affix-wrapper-focused) {
  border-color: rgba(103, 232, 249, 0.58);
  background: rgba(15, 23, 42, 0.9);
  box-shadow: 0 0 0 3px rgba(34, 211, 238, 0.1);
}

#stockPoolManagePage.theme-cyber :deep(.ant-input::placeholder),
#stockPoolManagePage.theme-cyber :deep(.ant-input-affix-wrapper input::placeholder),
#stockPoolManagePage.theme-cyber :deep(.ant-input-clear-icon) {
  color: rgba(148, 163, 184, 0.8);
}

@media (max-width: 760px) {
  #stockPoolManagePage {
    padding: 76px 12px 24px;
  }

  .page-header {
    flex-direction: column;
  }
}
</style>
