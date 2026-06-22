<template>
  <div class="my-saves-page">
    <div class="page-header">
      <h1 class="page-title">我的存档</h1>
      <el-button type="primary" size="large" @click="$router.push('/upload')">
        <el-icon><Upload /></el-icon>
        上传新存档
      </el-button>
    </div>

    <div class="page-divider" />

    <!-- 加载中 -->
    <LoadingSkeleton v-if="articleStore.loading" :rows="8" />

    <!-- 存档列表 -->
    <template v-else>
      <div v-if="articleStore.articleList.length > 0">
        <table class="data-table saves-table">
          <thead>
            <tr>
              <th style="width: 25%">标题</th>
              <th style="width: 12%">游戏</th>
              <th style="width: 12%">版本</th>
              <th style="width: 8%">状态</th>
              <th style="width: 8%">大小</th>
              <th style="width: 8%">下载</th>
              <th style="width: 12%">时间</th>
              <th style="width: 15%">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="article in articleStore.articleList"
              :key="article.id"
              class="saves-row"
            >
              <td>
                <router-link :to="`/articles/${article.id}`" class="saves-title">
                  {{ article.title }}
                </router-link>
              </td>
              <td>
                <router-link :to="`/games/${article.gameId}`" class="saves-game">
                  {{ article.gameName }}
                </router-link>
              </td>
              <td>
                <span class="saves-version">{{ article.version }}</span>
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
                  @click="openEdit(article)"
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

    <!-- 编辑对话框 -->
    <el-dialog v-model="showEditDialog" title="编辑存档" width="500px">
      <el-form :model="editForm" label-position="top">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="editForm.version" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useArticleStore } from '../stores/articles'
import { useAuthStore } from '../stores/auth'
import { articleApi } from '../api/articleApi'
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'

const articleStore = useArticleStore()
const auth = useAuthStore()

const currentPage = ref(1)
const showEditDialog = ref(false)
const saving = ref(false)
const editForm = ref({ title: '', version: '', description: '' })
const editingId = ref(null)

function formatSize(bytes) {
  if (bytes == null || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0; let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

function formatTime(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr); const now = new Date()
  const diff = now - date
  const mins = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 30) return `${days} 天前`
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function statusType(status) {
  const map = { READY: 'success', FAILED: 'danger', EXTRACTING: 'warning', UPLOADING: 'info' }
  return map[status] || 'info'
}

function statusText(status) {
  const map = { READY: '就绪', FAILED: '失败', EXTRACTING: '解压中', UPLOADING: '上传中' }
  return map[status] || status
}

function handlePageChange(page) {
  currentPage.value = page
  articleStore.fetchByUser(auth.userId, page - 1)
}

// ---- 编辑 ----
function openEdit(article) {
  editingId.value = article.id
  editForm.value = {
    title: article.title,
    version: article.version,
    description: article.description || ''
  }
  showEditDialog.value = true
}

async function saveEdit() {
  saving.value = true
  try {
    await articleApi.update(editingId.value, editForm.value)
    ElMessage.success('存档信息已更新')
    showEditDialog.value = false
    await articleStore.fetchByUser(auth.userId, currentPage.value - 1)
  } catch (e) {
    // 错误已在拦截器中提示
  } finally {
    saving.value = false
  }
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

.saves-title {
  font-weight: 600;
  color: var(--color-link);
}

.saves-title:hover {
  text-decoration: underline;
}

.saves-game {
  font-size: var(--font-size-normal);
  color: var(--color-link);
}

.saves-version {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  font-family: ui-monospace, monospace;
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

.pagination-wrap {
  display: flex;
  justify-content: center;
  padding: var(--spacing-lg) 0;
}
</style>
