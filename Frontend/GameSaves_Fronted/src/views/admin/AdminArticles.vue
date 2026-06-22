<template>
  <div class="admin-articles">
    <h2 class="page-title">文章管理</h2>

    <!-- 搜索与筛选 -->
    <el-card shadow="never" class="search-card">
      <el-input
        v-model="keyword"
        placeholder="搜索文章标题或作者..."
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
        v-model="statusFilter"
        placeholder="状态筛选"
        clearable
        style="width: 160px; margin-left: 12px"
        @change="search"
      >
        <el-option label="全部" value="" />
        <el-option label="就绪" value="READY" />
        <el-option label="上传中" value="UPLOADING" />
        <el-option label="解压中" value="EXTRACTING" />
        <el-option label="失败" value="FAILED" />
      </el-select>
      <el-button type="primary" @click="search" style="margin-left: 12px">
        <el-icon><Search /></el-icon>
        搜索
      </el-button>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="hover" class="table-card">
      <el-table :data="articles" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="gameName" label="游戏" min-width="120" />
        <el-table-column prop="username" label="作者" min-width="100" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="statusType(row.status)"
              size="small"
            >
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="90" align="center" />
        <el-table-column prop="downloadCount" label="下载" width="80" align="center" />
        <el-table-column prop="createdAt" label="创建时间" min-width="160">
          <template #default="{ row }">
            {{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              type="primary"
              size="small"
              @click="$router.push(`/articles/${row.id}`)"
            >
              查看
            </el-button>
            <el-popconfirm
              :title="`确定要删除文章「${row.title}」吗？此操作不可恢复。`"
              confirm-button-text="确定删除"
              cancel-button-text="取消"
              @confirm="handleDelete(row)"
            >
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
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
          @change="fetchArticles"
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

const articles = ref([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const page = ref(0)
const size = ref(20)
const total = ref(0)

function statusType(status) {
  const map = { READY: 'success', UPLOADING: 'info', EXTRACTING: 'warning', FAILED: 'danger' }
  return map[status] || 'info'
}

function statusText(status) {
  const map = { READY: '就绪', UPLOADING: '上传中', EXTRACTING: '解压中', FAILED: '失败' }
  return map[status] || status
}

async function fetchArticles() {
  loading.value = true
  try {
    const result = await adminApi.listArticles(page.value, size.value, keyword.value, statusFilter.value)
    articles.value = result.content
    total.value = result.totalElements || result.total
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 0
  fetchArticles()
}

async function handleDelete(row) {
  try {
    await adminApi.deleteArticle(row.id)
    ElMessage.success(`已删除文章「${row.title}」`)
    fetchArticles()
  } catch {
    // error handled by interceptor
  }
}

onMounted(() => {
  fetchArticles()
})
</script>

<style scoped>
.admin-articles {
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
