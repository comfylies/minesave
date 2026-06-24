<template>
  <div class="admin-dashboard">
    <h2 class="page-title">仪表盘</h2>

    <el-row :gutter="20">
      <el-col :xs="24" :sm="12" :md="6" v-for="card in statCards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: card.color }">
              <el-icon :size="28"><component :is="card.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 24px">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <span class="card-header-title">系统概览</span>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="用户总数">
              {{ stats.userCount }}
            </el-descriptions-item>
            <el-descriptions-item label="文章总数">
              {{ stats.articleCount }}
            </el-descriptions-item>
            <el-descriptions-item label="游戏总数">
              {{ stats.gameCount }}
            </el-descriptions-item>
            <el-descriptions-item label="评论总数">
              {{ stats.commentCount }}
            </el-descriptions-item>
            <el-descriptions-item label="下载总次数">
              {{ stats.downloadCount }}
            </el-descriptions-item>
            <el-descriptions-item label="图片存储空间">
              {{ formatStorage(stats.imageStorageBytes) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { adminApi } from '../../api/adminApi'
import {
  User, Document, Monitor, ChatLineSquare, PictureFilled
} from '@element-plus/icons-vue'

const stats = ref({
  userCount: 0,
  articleCount: 0,
  gameCount: 0,
  commentCount: 0,
  downloadCount: 0,
  imageStorageBytes: 0
})

function formatStorage(bytes) {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

const statCards = computed(() => [
  { label: '用户数', value: stats.value.userCount, icon: User, color: '#409EFF' },
  { label: '文章数', value: stats.value.articleCount, icon: Document, color: '#67C23A' },
  { label: '游戏数', value: stats.value.gameCount, icon: Monitor, color: '#E6A23C' },
  { label: '评论数', value: stats.value.commentCount, icon: ChatLineSquare, color: '#F56C6C' },
  { label: '图片存储', value: formatStorage(stats.value.imageStorageBytes), icon: PictureFilled, color: '#8B5CF6' }
])

onMounted(async () => {
  try {
    stats.value = await adminApi.getDashboard()
  } catch {
    // error handled by interceptor
  }
})
</script>

<style scoped>
.admin-dashboard {
  max-width: 1200px;
}

.page-title {
  margin: 0 0 24px;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.stat-card {
  margin-bottom: 20px;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.card-header-title {
  font-weight: 600;
}
</style>
