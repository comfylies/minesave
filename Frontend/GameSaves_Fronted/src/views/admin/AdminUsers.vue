<template>
  <div class="admin-users">
    <h2 class="page-title">用户管理</h2>

    <!-- 搜索 -->
    <el-card shadow="never" class="search-card">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名、昵称或邮箱..."
        clearable
        style="width: 320px"
        @clear="search"
        @keyup.enter="search"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" @click="search" style="margin-left: 12px">
        <el-icon><Search /></el-icon>
        搜索
      </el-button>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="hover" class="table-card">
      <el-table :data="users" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="160" />
        <el-table-column prop="role" label="角色" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small">
              {{ row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isActive" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" size="small">
              {{ row.isActive ? '正常' : '封禁' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" min-width="160">
          <template #default="{ row }">
            {{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              v-if="row.role !== 'admin'"
              :title="`确定要${row.isActive ? '封禁' : '解封'}用户「${row.username}」吗？`"
              confirm-button-text="确定"
              cancel-button-text="取消"
              @confirm="handleToggleBan(row)"
            >
              <template #reference>
                <el-button
                  :type="row.isActive ? 'danger' : 'success'"
                  size="small"
                  link
                >
                  {{ row.isActive ? '封禁' : '解封' }}
                </el-button>
              </template>
            </el-popconfirm>
            <span v-else style="color: #909399; font-size: 12px;">管理员</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @change="fetchUsers"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../api/adminApi'
import { Search } from '@element-plus/icons-vue'

const users = ref([])
const loading = ref(false)
const keyword = ref('')
const page = ref(0)
const size = ref(20)
const total = ref(0)

async function fetchUsers() {
  loading.value = true
  try {
    const result = await adminApi.listUsers(page.value, size.value, keyword.value)
    users.value = result.content
    total.value = result.totalElements || result.total
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 0
  fetchUsers()
}

async function handleToggleBan(row) {
  try {
    await adminApi.toggleUserBan(row.id)
    ElMessage.success(row.isActive ? `已封禁用户「${row.username}」` : `已解封用户「${row.username}」`)
    fetchUsers()
  } catch {
    // error handled by interceptor
  }
}

onMounted(() => {
  fetchUsers()
})
</script>

<style scoped>
.admin-users {
  max-width: 1400px;
}

.page-title {
  margin: 0 0 24px;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.search-card {
  margin-bottom: 16px;
}

.search-card .el-card__body {
  display: flex;
  align-items: center;
}

.table-card {
  margin-bottom: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
