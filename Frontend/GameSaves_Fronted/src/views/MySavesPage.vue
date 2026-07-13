<template>
  <div class="my-saves-page">
    <div class="page-header">
      <h1 class="page-title">我的存档</h1>
      <div class="page-header-right">
        <el-radio-group v-model="viewMode" size="small">
          <el-radio-button value="list">
            <el-icon><List /></el-icon>
            列表
          </el-radio-button>
          <el-radio-button value="gallery">
            <el-icon><Grid /></el-icon>
            画廊
          </el-radio-button>
        </el-radio-group>
        <el-button type="primary" size="large" @click="$router.push('/upload')">
          <el-icon><Upload /></el-icon>
          上传新存档
        </el-button>
      </div>
    </div>

    <div class="page-divider" />

    <!-- 加载中 -->
    <LoadingSkeleton v-if="articleStore.loading" :rows="8" />

    <!-- 存档内容 -->
    <template v-else>
      <div v-if="articleStore.articleList.length > 0">
        <!-- 列表视图 -->
        <table v-if="viewMode === 'list'" class="data-table saves-table">
          <thead>
            <tr>
              <th class="th-cover"></th>
              <th style="width: 23%">标题</th>
              <th style="width: 11%">游戏</th>
              <th style="width: 10%">版本</th>
              <th style="width: 7%">状态</th>
              <th style="width: 7%">大小</th>
              <th style="width: 7%">下载</th>
              <th style="width: 14%">时间</th>
              <th style="width: 15%">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="article in articleStore.articleList"
              :key="article.id"
              class="saves-row"
            >
              <td class="col-cover">
                <router-link :to="`/articles/${article.id}`" class="cover-link">
                  <div class="cover-thumb">
                    <img
                      v-if="article.coverImage"
                      :src="article.coverThumbnail || thumbUrl(article.coverImage, 270)"
                      :alt="article.title"
                      class="cover-thumb-img"
                      loading="lazy"
                      @error="e => e.target.style.display = 'none'"
                    />
                    <el-icon v-else :size="20"><FolderOpened /></el-icon>
                  </div>
                </router-link>
              </td>
              <td>
                <router-link :to="`/articles/${article.id}`" class="saves-title" :title="article.title">
                  {{ article.title }}
                </router-link>
              </td>
              <td>
                <router-link :to="`/games/${article.gameId}`" class="saves-game" :title="article.gameName">
                  {{ article.gameName }}
                </router-link>
              </td>
              <td>
                <span class="saves-version" :title="article.version">{{ article.version }}</span>
              </td>
              <td>
                <el-tag
                  :type="statusType(article.status)"
                  size="small"
                  effect="plain"
                >
                  {{ statusText(article.status) }}
                </el-tag>
              </td>
              <td class="col-number">{{ formatSize(article.fileSize) }}</td>
              <td class="col-number">{{ article.downloadCount || 0 }}</td>
              <td class="col-time time-ago">{{ formatTime(article.createdAt) }}</td>
              <td class="col-actions">
                <el-button
                  size="small"
                  text
                  type="primary"
                  @click="$router.push(`/articles/${article.id}/edit`)"
                >
                  <el-icon><Edit /></el-icon>
                  编辑
                </el-button>
                <el-button
                  size="small"
                  text
                  type="danger"
                  @click="handleDelete(article)"
                >
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- 画廊视图 -->
        <div v-else class="gallery-grid">
          <ArticleCard
            v-for="article in articleStore.articleList"
            :key="article.id"
            :article="article"
          />
        </div>

        <!-- 分页 -->
        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="articleStore.pagination.size"
            :total="articleStore.pagination.totalElements"
            layout="prev, pager, next"
            @current-change="handlePageChange"
          />
        </div>
      </div>

      <!-- 暂无存档 -->
      <EmptyState v-else description="你还没有上传存档">
        <el-button type="primary" size="large" @click="$router.push('/upload')">
          上传第一个存档
        </el-button>
      </EmptyState>
    </template>

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useArticleStore } from '../stores/articles'
import { useAuthStore } from '../stores/auth'
import { useViewMode } from '../composables/useViewMode'
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'
import ArticleCard from '../components/article/ArticleCard.vue'
import { thumbUrl } from '../utils/imageUrl'
import { formatSize, formatTime, statusType, statusText } from '@/utils/format'

const articleStore = useArticleStore()
const auth = useAuthStore()
const { viewMode } = useViewMode()

const currentPage = ref(1)

function handlePageChange(page) {
  currentPage.value = page
  articleStore.fetchByUser(auth.userId, page - 1)
}

// ---- 删除 ----
async function handleDelete(article) {
  try {
    await ElMessageBox.confirm(
      `确定要删除 "${article.title}" 吗？此操作不可撤销。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await articleStore.deleteArticle(article.id)
    ElMessage.success('存档已删除')
    await articleStore.fetchByUser(auth.userId, currentPage.value - 1)
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  articleStore.fetchByUser(auth.userId, 0)
})
</script>

<style scoped>
.my-saves-page {
  padding: var(--spacing-lg) 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-md);
}

.page-header-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.page-title {
  font-size: var(--font-size-title);
  font-weight: 600;
}

.page-divider {
  border-top: 1px solid var(--color-border-primary);
  margin-bottom: var(--spacing-lg);
}

/* ---- 表格 ---- */
.saves-table {
  margin-bottom: var(--spacing-lg);
}

.saves-row td {
  vertical-align: middle;
}

/* ---- 封面缩略图 ---- */
.th-cover { width: 48px; }

.col-cover {
  padding: var(--spacing-xs) var(--spacing-sm) !important;
}

.cover-link {
  display: flex;
  justify-content: center;
}

.cover-thumb {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  overflow: hidden;
  background: var(--color-bg-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-secondary-text);
  flex-shrink: 0;
}

.cover-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.saves-title {
  font-weight: 600;
  color: var(--color-link);
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.saves-title:hover {
  text-decoration: underline;
}

.saves-game {
  font-size: var(--font-size-normal);
  color: var(--color-link);
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.saves-version {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  font-family: ui-monospace, monospace;
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.col-number {
  text-align: right;
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}

.col-time {
  text-align: right;
}

.col-actions {
  text-align: right;
  white-space: nowrap;
}

/* ---- 画廊网格 ---- */
.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

@media (max-width: 640px) {
  .gallery-grid {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: var(--spacing-md);
  }
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: var(--spacing-lg) 0;
}
</style>
