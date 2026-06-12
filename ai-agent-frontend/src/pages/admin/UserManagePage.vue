<template>
  <div id="userManagePage" :class="themeClass">
    <div class="page-header">
      <div>
        <h1>用户管理</h1>
      </div>
      <div class="header-actions">
        <a-button @click="router.push('/admin/stockPool')">股票池管理</a-button>
        <a-button @click="router.push('/')">返回首页</a-button>
      </div>
    </div>
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="账号">
        <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" />
      </a-form-item>
      <a-form-item label="用户名">
        <a-input v-model:value="searchParams.userName" placeholder="输入用户名" />
      </a-form-item>
      <a-form-item label="角色">
        <a-select
          v-model:value="searchParams.userRole"
          allow-clear
          placeholder="全部"
          style="width: 120px"
          :options="roleOptions"
        />
      </a-form-item>
      <a-form-item label="状态">
        <a-select
          v-model:value="searchParams.userStatus"
          allow-clear
          placeholder="全部"
          style="width: 120px"
          :options="statusOptions"
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
      <a-form-item>
        <a-button html-type="button" @click="resetSearch">重置</a-button>
      </a-form-item>
      <a-form-item>
        <a-button html-type="button" @click="batchEnableSelected">
          批量启用
        </a-button>
      </a-form-item>
      <a-form-item>
        <a-button html-type="button" @click="batchDisableSelected">
          批量禁用
        </a-button>
      </a-form-item>
      <a-form-item v-if="selectedRowKeys.length">
        <span class="selected-count">已选 {{ selectedRowKeys.length }} 个用户</span>
      </a-form-item>
    </a-form>
    <a-divider />
    <!-- 表格 -->
    <a-table
      row-key="id"
      :row-selection="rowSelection"
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userAvatar'">
          <a-image :src="record.userAvatar" :width="120" />
        </template>
        <template v-else-if="column.dataIndex === 'userRole'">
          <a-select
            :value="record.userRole"
            style="width: 108px"
            :options="roleOptions"
            @change="(value) => updateUserRole(record, value)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'userStatus'">
          <a-switch
            :checked="record.userStatus !== 1"
            checked-children="启用"
            un-checked-children="禁用"
            @change="(checked) => updateUserStatus(record, checked)"
          />
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button danger html-type="button" @click="doDelete(record.id)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deleteUser, listUserVoByPage, updateUser } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { useInterfaceTheme } from '@/composables/useInterfaceTheme'
import { useLoginUserStore } from '@/stores/loginUser'

const router = useRouter()
const { themeClass } = useInterfaceTheme()
const loginUserStore = useLoginUserStore()

const roleOptions = [
  { label: '普通用户', value: 'user' },
  { label: '管理员', value: 'admin' },
]

const statusOptions = [
  { label: '启用', value: 0 },
  { label: '禁用', value: 1 },
]

const columns = [
  {
    title: 'id',
    dataIndex: 'id',
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
  },
  {
    title: '用户名',
    dataIndex: 'userName',
  },
  {
    title: '头像',
    dataIndex: 'userAvatar',
  },
  {
    title: '简介',
    dataIndex: 'userProfile',
  },
  {
    title: '用户角色',
    dataIndex: 'userRole',
  },
  {
    title: '账号状态',
    dataIndex: 'userStatus',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '操作',
    key: 'action',
  },
]

// 展示的数据
const data = ref<API.UserVO[]>([])
const total = ref(0)
const selectedRowKeys = ref<(string | number)[]>([])

// 搜索条件
const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
  userRole: undefined,
  userStatus: undefined,
})

// 获取数据
const fetchData = async () => {
  const res = await listUserVoByPage({
    ...searchParams,
  })
  if (res.data.data) {
    data.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: (string | number)[]) => {
    selectedRowKeys.value = keys
  },
}))

// 表格分页变化时的操作
const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  selectedRowKeys.value = []
  fetchData()
}

// 搜索数据
const doSearch = () => {
  // 重置页码
  searchParams.pageNum = 1
  fetchData()
}

const resetSearch = () => {
  searchParams.pageNum = 1
  searchParams.pageSize = 10
  searchParams.userAccount = undefined
  searchParams.userName = undefined
  searchParams.userRole = undefined
  searchParams.userStatus = undefined
  fetchData()
}

// 删除数据
const doDelete = async (id?: number) => {
  if (!id) {
    return
  }
  const res = await deleteUser({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 刷新数据
    fetchData()
  } else {
    message.error('删除失败')
  }
}

const updateUserRole = async (record: API.UserVO, userRole: string) => {
  if (!record.id) {
    return
  }
  const res = await updateUser({
    id: record.id,
    userRole,
    userStatus: record.userStatus,
    userName: record.userName,
    userAvatar: record.userAvatar,
    userProfile: record.userProfile,
  })
  if (res.data.code === 0) {
    message.success('角色已更新')
    await fetchData()
    return
  }
  message.error(res.data.message || '角色更新失败')
}

const updateUserStatus = async (record: API.UserVO, checked: boolean) => {
  if (!record.id) {
    return
  }
  const res = await updateUser({
    id: record.id,
    userRole: record.userRole,
    userStatus: checked ? 0 : 1,
    userName: record.userName,
    userAvatar: record.userAvatar,
    userProfile: record.userProfile,
  })
  if (res.data.code === 0) {
    message.success(checked ? '已启用' : '已禁用')
    await fetchData()
    return
  }
  message.error(res.data.message || '状态更新失败')
}

const batchUpdateStatus = async (status: number) => {
  if (!selectedRowKeys.value.length) {
    message.warning('请先选择用户')
    return
  }
  const loginUserId = loginUserStore.loginUser.id
  const updateIds = status === 1
    ? selectedRowKeys.value.filter((id) => Number(id) !== loginUserId)
    : selectedRowKeys.value
  if (!updateIds.length) {
    message.warning('不能禁用当前登录账号')
    return
  }
  const responses = await Promise.all(
    updateIds.map((id) =>
      updateUser({
        id: Number(id),
        userStatus: status,
      }),
    ),
  )
  const failedResponse = responses.find((res) => res.data.code !== 0)
  if (failedResponse) {
    message.error(failedResponse.data.message || '批量更新失败')
    await fetchData()
    return
  }
  if (status === 1 && updateIds.length < selectedRowKeys.value.length) {
    message.success('已跳过当前登录账号，其余用户已禁用')
  } else {
    message.success(status === 0 ? '批量启用成功' : '批量禁用成功')
  }
  selectedRowKeys.value = []
  await fetchData()
}

const batchEnableSelected = () => batchUpdateStatus(0)
const batchDisableSelected = () => batchUpdateStatus(1)

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#userManagePage {
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

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.page-header h1 {
  margin: 0 0 8px;
  color: var(--admin-text);
  font-size: 24px;
  line-height: 1.25;
}

.page-header p {
  margin: 0;
  color: var(--admin-muted);
}

#userManagePage :deep(.ant-form),
#userManagePage :deep(.ant-table-wrapper) {
  border: 1px solid var(--admin-border);
  border-radius: 8px;
  background: var(--admin-panel);
  box-shadow: 0 24px 72px rgba(15, 23, 42, 0.1);
}

#userManagePage :deep(.ant-form) {
  padding: 18px;
}

#userManagePage :deep(.ant-table-wrapper) {
  overflow: hidden;
}

#userManagePage :deep(.ant-divider) {
  border-color: transparent;
}

#userManagePage :deep(.ant-input) {
  border-color: var(--admin-border);
  border-radius: 8px;
  background: #f8fafc;
}

#userManagePage :deep(.ant-btn) {
  border-radius: 8px;
  font-weight: 600;
}

#userManagePage :deep(.ant-btn-primary) {
  border: none;
  background: linear-gradient(90deg, var(--admin-accent), #0f766e);
}

#userManagePage :deep(.ant-checkbox-wrapper:hover .ant-checkbox-inner),
#userManagePage :deep(.ant-checkbox:hover .ant-checkbox-inner) {
  border-color: var(--admin-accent);
}

#userManagePage :deep(.ant-checkbox-checked .ant-checkbox-inner),
#userManagePage :deep(.ant-checkbox-indeterminate .ant-checkbox-inner::after) {
  border-color: var(--admin-accent);
  background: var(--admin-accent);
}

#userManagePage :deep(.ant-table-tbody > tr.ant-table-row-selected > td) {
  background: #ffffff;
}

#userManagePage :deep(.ant-table-tbody > tr.ant-table-row-selected:hover > td) {
  background: #fafafa;
}

.selected-count {
  color: var(--admin-muted);
  font-size: 13px;
  font-weight: 600;
}

#userManagePage.theme-cyber {
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

#userManagePage.theme-cyber :deep(.ant-form),
#userManagePage.theme-cyber :deep(.ant-table-wrapper) {
  box-shadow: 0 24px 72px rgba(0, 0, 0, 0.26);
}

#userManagePage.theme-cyber :deep(.ant-form-item-label > label),
#userManagePage.theme-cyber :deep(.ant-table),
#userManagePage.theme-cyber :deep(.ant-table-thead > tr > th),
#userManagePage.theme-cyber :deep(.ant-pagination),
#userManagePage.theme-cyber :deep(.ant-pagination-total-text) {
  color: var(--admin-text);
}

#userManagePage.theme-cyber :deep(.ant-table),
#userManagePage.theme-cyber :deep(.ant-table-thead > tr > th),
#userManagePage.theme-cyber :deep(.ant-table-tbody > tr > td) {
  background: transparent;
  border-color: rgba(103, 232, 249, 0.12);
}

#userManagePage.theme-cyber :deep(.ant-table-tbody > tr:hover > td) {
  background: rgba(34, 211, 238, 0.08);
}

#userManagePage.theme-cyber :deep(.ant-table-tbody > tr.ant-table-row-selected > td) {
  background: transparent;
}

#userManagePage.theme-cyber :deep(.ant-table-tbody > tr.ant-table-row-selected:hover > td) {
  background: rgba(34, 211, 238, 0.08);
}

#userManagePage.theme-cyber :deep(.ant-input) {
  border-color: rgba(103, 232, 249, 0.18);
  background: rgba(15, 23, 42, 0.72);
  color: var(--admin-text);
}

@media (max-width: 640px) {
  #userManagePage {
    padding: 76px 12px 24px;
  }
}

</style>
