<template>
  <div class="admin-ghosts">
    <h2 class="admin-page-title">幽灵文章诊断</h2>
    <p class="admin-page-desc">
      扫描数据库中所有文章，检测文件是否真实存在于存储中。常见于跨机器迁移（文件在另一台电脑）、
      手动删除磁盘文件、存储后端切换后文件丢失等场景。
    </p>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="admin-card-row">
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover" class="admin-stat-card">
          <div class="admin-stat-content">
            <div class="admin-stat-icon" style="background: #3b82f6">
              <el-icon :size="24"><Document /></el-icon>
            </div>
            <div class="admin-stat-info">
              <div class="admin-stat-value">{{ stats.total }}</div>
              <div class="admin-stat-label">总文章数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover" class="admin-stat-card">
          <div class="admin-stat-content">
            <div class="admin-stat-icon" style="background: #ef4444">
              <el-icon :size="24"><WarningFilled /></el-icon>
            </div>
            <div class="admin-stat-info">
              <div class="admin-stat-value danger">{{ stats.ghostCount }}</div>
              <div class="admin-stat-label">幽灵文章</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover" class="admin-stat-card">
          <div class="admin-stat-content">
            <div class="admin-stat-icon" style="background: #22c55e">
              <el-icon :size="24"><CircleCheck /></el-icon>
            </div>
            <div class="admin-stat-info">
              <div class="admin-stat-value success">{{ stats.healthyCount }}</div>
              <div class="admin-stat-label">正常文章</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 操作区 -->
    <el-card shadow="hover" class="admin-card-row">
      <template #header>
        <div class="admin-card-header-row">
          <span class="admin-card-header-title">扫描与清理</span>
          <el-tag v-if="scanTime" size="small" type="info">
            上次扫描：{{ scanTime }}
          </el-tag>
        </div>
      </template>

      <div class="admin-action-row">
        <el-button
          type="primary"
          :icon="Search"
          :loading="scanning"
          @click="scan"
        >
          {{ scanning ? '扫描中...' : '扫描幽灵文章' }}
        </el-button>

        <el-button
          type="danger"
          :icon="Delete"
          :disabled="selectedGhosts.length === 0"
          @click="batchDelete"
        >
          批量删除选中 ({{ selectedGhosts.length }})
        </el-button>

        <el-checkbox
          v-model="showHealthy"
          style="margin-left: var(--spacing-md)"
          @change="updateFilteredList"
        >
          显示正常文章
        </el-checkbox>
      </div>
    </el-card>

    <!-- 结果表格 -->
    <el-card v-if="hasScanned" shadow="hover" class="admin-table-card">
      <template #header>
        <span class="admin-card-header-title">
          扫描结果
          <el-tag v-if="ghostCount > 0" type="danger" size="small" style="margin-left: var(--spacing-sm)">
            {{ ghostCount }} 篇幽灵文章
          </el-tag>
          <el-tag v-else type="success" size="small" style="margin-left: var(--spacing-sm)">
            全部正常
          </el-tag>
        </span>
      </template>

      <el-empty v-if="filteredArticles.length === 0" description="没有匹配的文章" />

      <el-table
        v-else
        ref="tableRef"
        :data="filteredArticles"
        v-loading="scanning"
        stripe
        border
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span :style="{ color: row.hasAnyFile ? '' : 'var(--color-danger-text)' }">
              {{ row.title }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="gameName" label="游戏" min-width="100" />
        <el-table-column prop="username" label="作者" min-width="90" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="文件状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.hasAnyFile" type="success" size="small">正常</el-tag>
            <el-tag v-else type="danger" size="small" effect="dark">幽灵</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="存储路径" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="admin-storage-path">{{ row.storagePrefix }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="文件大小" width="100" align="center">
          <template #default="{ row }">
            {{ row.fileSize ? formatSize(row.fileSize) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160">
          <template #default="{ row }">
            {{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="!row.hasAnyFile"
              text
              type="danger"
              size="small"
              @click="deleteSingle(row)"
            >
              删除
            </el-button>
            <el-button
              text
              type="primary"
              size="small"
              @click="$router.push(`/articles/${row.id}`)"
            >
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 首次提示 -->
    <el-card v-if="!hasScanned && !scanning" shadow="hover">
      <el-empty description="点击「扫描幽灵文章」开始检测">
        <template #image>
          <el-icon :size="64" color="#c0c4cc"><Search /></el-icon>
        </template>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api/adminApi'
import { Search, Delete, WarningFilled, CircleCheck, Document } from '@element-plus/icons-vue'
import { statusType, statusText, formatSize } from '@/utils/format'

const tableRef = ref(null)
const allArticles = ref([])
const selectedGhosts = ref([])
const scanning = ref(false)
const hasScanned = ref(false)
const scanTime = ref('')
const showHealthy = ref(false)

const ghostCount = computed(() => allArticles.value.filter(a => !a.hasAnyFile).length)

const filteredArticles = computed(() => {
  if (showHealthy.value) return allArticles.value
  return allArticles.value.filter(a => !a.hasAnyFile)
})

const stats = computed(() => ({
  total: allArticles.value.length,
  ghostCount: ghostCount.value,
  healthyCount: allArticles.value.length - ghostCount.value
}))

function updateFilteredList() {
  // computed reacts automatically
}

async function scan() {
  scanning.value = true
  try {
    const result = await adminApi.scanGhostArticles()
    allArticles.value = result
    hasScanned.value = true
    scanTime.value = new Date().toLocaleTimeString()
    ElMessage.success(
      `扫描完成：共 ${stats.value.total} 篇文章，${stats.value.ghostCount} 篇幽灵`
    )
  } catch {
    // error handled by interceptor
  } finally {
    scanning.value = false
  }
}

function handleSelectionChange(selection) {
  selectedGhosts.value = selection
}

async function deleteSingle(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除幽灵文章「${row.title}」吗？将同时尝试清理残留文件和数据库记录。`,
      '确认删除',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // cancelled
  }

  try {
    await adminApi.deleteGhostArticles([row.id])
    ElMessage.success(`已删除幽灵文章「${row.title}」`)
    allArticles.value = allArticles.value.filter(a => a.id !== row.id)
  } catch {
    // error handled by interceptor
  }
}

async function batchDelete() {
  if (selectedGhosts.value.length === 0) return

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedGhosts.value.length} 篇幽灵文章吗？将同时尝试清理残留文件和数据库记录。此操作不可恢复。`,
      '批量删除幽灵文章',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  const ids = selectedGhosts.value.map(a => a.id)
  try {
    const result = await adminApi.deleteGhostArticles(ids)
    ElMessage.success(`已删除 ${result.deleted} 篇幽灵文章`)
    // Remove from local list
    const idSet = new Set(ids)
    allArticles.value = allArticles.value.filter(a => !idSet.has(a.id))
    selectedGhosts.value = []
  } catch {
    // error handled by interceptor
  }
}
</script>

<style scoped>
.admin-ghosts {
  max-width: 1200px;
}
</style>
