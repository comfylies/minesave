<template>
  <div class="admin-users">
    <h2 class="admin-page-title">用户管理</h2>

    <!-- 搜索 -->
    <el-card shadow="never" class="admin-search-card">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名、昵称或邮箱..."
        clearable
        style="width: 280px"
        @clear="search"
        @keyup.enter="search"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select
        v-model="roleFilter"
        placeholder="角色筛选"
        clearable
        style="width: 120px"
        @change="search"
      >
        <el-option label="全部" value="" />
        <el-option label="管理员" value="admin" />
        <el-option label="用户" value="user" />
      </el-select>
      <el-select
        v-model="statusFilter"
        placeholder="状态筛选"
        clearable
        style="width: 120px"
        @change="search"
      >
        <el-option label="全部" value="" />
        <el-option label="正常" value="active" />
        <el-option label="封禁" value="banned" />
      </el-select>
      <el-button type="primary" @click="search">
        <el-icon><Search /></el-icon>
        搜索
      </el-button>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="hover" class="admin-table-card">
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
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">
              详情
            </el-button>
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
            <span v-else class="admin-action-disabled">管理员</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="admin-pagination-wrapper">
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

    <!-- 用户详情弹窗 -->
    <el-dialog v-model="detailVisible" title="用户详情" width="500px">
      <el-descriptions v-if="detailUser" :column="2" border>
        <el-descriptions-item label="ID">{{ detailUser.id }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ detailUser.username }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detailUser.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detailUser.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机">{{ detailUser.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag :type="detailUser.role === 'admin' ? 'danger' : 'info'" size="small">
            {{ detailUser.role }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailUser.isActive ? 'success' : 'danger'" size="small">
            {{ detailUser.isActive ? '正常' : '封禁' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="最后登录">
          {{ detailUser.lastLogin ? new Date(detailUser.lastLogin).toLocaleString() : '从未登录' }}
        </el-descriptions-item>
        <el-descriptions-item label="注册时间">
          {{ detailUser.createdAt ? new Date(detailUser.createdAt).toLocaleString() : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="个人简介" :span="2">
          {{ detailUser.bio || '暂未填写' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
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
const roleFilter = ref('')
const statusFilter = ref('')
const page = ref(0)
const size = ref(20)
const total = ref(0)
const detailVisible = ref(false)
const detailUser = ref(null)

async function fetchUsers() {
  loading.value = true
  try {
    const result = await adminApi.listUsers(page.value, size.value, keyword.value)
    let list = result.content || []
    // 前端过滤（后端暂不支持 role/status 筛选参数）
    if (roleFilter.value) {
      list = list.filter(u => u.role === roleFilter.value)
    }
    if (statusFilter.value === 'active') {
      list = list.filter(u => u.isActive)
    } else if (statusFilter.value === 'banned') {
      list = list.filter(u => !u.isActive)
    }
    users.value = list
    total.value = result.totalElements || result.total
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

function openDetail(row) {
  detailUser.value = row
  detailVisible.value = true
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

.admin-action-disabled {
  color: var(--color-secondary-text);
  font-size: var(--font-size-small);
}
</style>
