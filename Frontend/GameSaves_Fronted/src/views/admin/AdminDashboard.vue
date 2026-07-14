<template>
  <div class="admin-dashboard">
    <h2 class="admin-page-title">仪表盘</h2>

    <!-- 核心统计卡片 -->
    <el-row :gutter="20" class="admin-card-row">
      <el-col :xs="24" :sm="12" :md="6" v-for="card in statCards" :key="card.label">
        <el-card shadow="hover" class="admin-stat-card">
          <div class="admin-stat-content">
            <div class="admin-stat-icon" :style="{ background: card.color }">
              <el-icon :size="24"><component :is="card.icon" /></el-icon>
            </div>
            <div class="admin-stat-info">
              <div class="admin-stat-value">{{ card.value }}</div>
              <div class="admin-stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 辅助统计卡片 -->
    <el-row :gutter="20" class="admin-card-row">
      <el-col :xs="24" :sm="12" :md="6" v-for="card in pendingCards" :key="card.label">
        <el-card shadow="hover" class="admin-stat-card" :class="card.cssClass">
          <div class="admin-stat-content">
            <div class="admin-stat-icon" :style="{ background: card.color }">
              <el-icon :size="24"><component :is="card.icon" /></el-icon>
            </div>
            <div class="admin-stat-info">
              <div class="admin-stat-value">{{ card.value }}</div>
              <div class="admin-stat-label">{{ card.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 系统概览 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <span class="admin-card-header-title">系统概览</span>
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
            <el-descriptions-item label="失败文章">
              <el-tag v-if="stats.failedArticleCount > 0" type="danger" size="small">
                {{ stats.failedArticleCount }} 篇
              </el-tag>
              <span v-else class="stat-ok">0 篇</span>
            </el-descriptions-item>
            <el-descriptions-item label="上传中（可能卡住）">
              <el-tag v-if="stats.uploadingArticleCount > 0" type="warning" size="small">
                {{ stats.uploadingArticleCount }} 篇
              </el-tag>
              <span v-else class="stat-ok">0 篇</span>
            </el-descriptions-item>
            <el-descriptions-item label="待处理联系留言">
              <el-tag v-if="stats.pendingContactCount > 0" type="warning" size="small">
                {{ stats.pendingContactCount }} 条
              </el-tag>
              <span v-else class="stat-ok">0 条</span>
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
  User, Document, Monitor, ChatLineSquare, PictureFilled,
  WarningFilled, CircleCloseFilled, Message
} from '@element-plus/icons-vue'
import { formatSize } from '@/utils/format'

const stats = ref({
  userCount: 0,
  articleCount: 0,
  gameCount: 0,
  commentCount: 0,
  downloadCount: 0,
  imageStorageBytes: 0,
  failedArticleCount: 0,
  uploadingArticleCount: 0,
  pendingContactCount: 0
})

function formatStorage(bytes) {
  return formatSize(bytes)
}

const statCards = computed(() => [
  { label: '用户数', value: stats.value.userCount, icon: User, color: '#3b82f6' },
  { label: '文章数', value: stats.value.articleCount, icon: Document, color: '#22c55e' },
  { label: '游戏数', value: stats.value.gameCount, icon: Monitor, color: '#f59e0b' },
  { label: '评论数', value: stats.value.commentCount, icon: ChatLineSquare, color: '#ef4444' }
])

const pendingCards = computed(() => [
  { label: '下载总次数', value: stats.value.downloadCount, icon: PictureFilled, color: '#8b5cf6', cssClass: '' },
  { label: '图片存储', value: formatStorage(stats.value.imageStorageBytes), icon: PictureFilled, color: '#6366f1', cssClass: '' },
  { label: '失败文章', value: stats.value.failedArticleCount, icon: CircleCloseFilled, color: '#ef4444', cssClass: stats.value.failedArticleCount > 0 ? 'admin-stat-card-warn' : '' },
  { label: '上传中', value: stats.value.uploadingArticleCount, icon: WarningFilled, color: '#f59e0b', cssClass: stats.value.uploadingArticleCount > 0 ? 'admin-stat-card-warn' : '' },
  { label: '待处理留言', value: stats.value.pendingContactCount, icon: Message, color: '#f97316', cssClass: stats.value.pendingContactCount > 0 ? 'admin-stat-card-warn' : '' }
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

.stat-ok {
  color: var(--color-success-text);
}
</style>
