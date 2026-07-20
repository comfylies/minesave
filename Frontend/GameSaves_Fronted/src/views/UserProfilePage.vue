<template>
  <div class="profile-page">
    <!-- 加载中 -->
    <LoadingSkeleton v-if="loadingUser" :rows="6" />

    <!-- 错误 -->
    <div v-else-if="userError" class="profile-error">
      <EmptyState description="加载用户信息失败">
        <el-button type="primary" @click="loadUserData">重试</el-button>
      </EmptyState>
    </div>

    <template v-else-if="user">
      <!-- 用户信息头部 -->
      <div class="profile-header">
        <el-avatar :size="80" :src="user.avatarUrl" icon="UserFilled" class="profile-avatar" />
        <div class="profile-info">
          <span v-if="isSelf" class="profile-center-label">个人中心</span>
          <div class="profile-name-row">
            <h1 class="profile-name" :title="user.nickname || user.username">{{ user.nickname || user.username }}</h1>
            <el-button v-if="isSelf" text type="primary" @click="openProfileDialog">编辑个人资料</el-button>
          </div>
          <p class="profile-username" :title="'@' + user.username">@{{ user.username }}</p>
          <p v-if="user.bio" class="profile-bio" :title="user.bio">{{ user.bio }}</p>
          <div class="profile-meta">
            <span class="meta-item">
              <el-icon><Calendar /></el-icon>
              {{ formatDate(user.createdAt) }} 加入
            </span>
            <span v-if="user.role === 'admin'" class="meta-badge admin-badge">管理员</span>
          </div>
        </div>
      </div>

      <div class="profile-divider" />

      <section v-if="isSelf" class="account-center">
        <div>
          <h2 class="section-title">账户中心</h2>
          <p>管理头像、公开资料和账户密码。</p>
        </div>
        <div class="account-actions">
          <el-upload accept="image/jpeg,image/png,image/webp" :auto-upload="false" :show-file-list="false" :on-change="handleAvatarChange">
            <el-button>更换头像</el-button>
          </el-upload>
          <el-button v-if="user.avatarUrl" @click="removeAvatar">恢复默认头像</el-button>
          <el-button type="primary" plain @click="passwordDialogVisible = true">修改密码</el-button>
        </div>
      </section>

      <!-- 用户存档列表 -->
      <div class="section-header">
        <h2 class="section-title">{{ user.nickname || user.username }} 的存档</h2>
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
      </div>

      <LoadingSkeleton v-if="articleStore.loading" :rows="8" />

      <template v-else>
        <div v-if="articleStore.articleList.length > 0">
          <!-- 列表视图 -->
          <table v-if="viewMode === 'list'" class="data-table article-table">
            <thead>
              <tr>
                <th class="th-cover"></th>
                <th style="width: 36%">标题</th>
                <th style="width: 15%">游戏</th>
                <th style="width: 10%">版本</th>
                <th style="width: 8%">大小</th>
                <th style="width: 8%">下载</th>
                <th style="width: 18%">时间</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="article in articleStore.articleList"
                :key="article.id"
                class="article-row"
                @click="$router.push(`/articles/${article.id}`)"
              >
                <td class="col-cover">
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
                </td>
                <td>
                  <span class="article-title-link" :title="article.title">{{ article.title }}</span>
                </td>
                <td>
                  <router-link
                    :to="`/games/${article.gameId}`"
                    class="article-game"
                    :title="article.gameName"
                    @click.stop
                  >
                    {{ article.gameName }}
                  </router-link>
                </td>
                <td>
                  <span class="article-version" :title="article.version">{{ article.version }}</span>
                </td>
                <td class="col-number">{{ formatSize(article.fileSize) }}</td>
                <td class="col-number">{{ article.downloadCount || 0 }}</td>
                <td class="col-time time-ago">{{ formatTime(article.createdAt) }}</td>
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

        <EmptyState v-else :description="isSelf ? '你还没有上传存档' : '该用户暂无存档'">
          <el-button v-if="isSelf" type="primary" @click="$router.push('/upload')">上传存档</el-button>
        </EmptyState>
      </template>

      <el-dialog v-model="profileDialogVisible" title="编辑个人资料" width="480px">
        <el-form :model="profileForm" label-position="top">
          <el-form-item label="昵称"><el-input v-model="profileForm.nickname" maxlength="24" show-word-limit /></el-form-item>
          <el-form-item label="个人简介"><el-input v-model="profileForm.bio" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="profileForm.phone" maxlength="11" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="profileDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingProfile" @click="saveProfile">保存</el-button></template>
      </el-dialog>

      <el-dialog v-model="passwordDialogVisible" title="修改密码" width="480px">
        <el-form :model="passwordForm" label-position="top">
          <el-form-item label="当前密码"><el-input v-model="passwordForm.currentPassword" type="password" show-password /></el-form-item>
          <el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" show-password /></el-form-item>
          <el-form-item label="确认新密码"><el-input v-model="passwordForm.confirmPassword" type="password" show-password /></el-form-item>
        </el-form>
        <template #footer><el-button @click="passwordDialogVisible = false">取消</el-button><el-button type="primary" :loading="savingPassword" @click="changePassword">确认修改</el-button></template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { userApi } from '../api/userApi'
import { useArticleStore } from '../stores/articles'
import { useAuthStore } from '../stores/auth'
import { useViewMode } from '../composables/useViewMode'
import EmptyState from '../components/common/EmptyState.vue'
import LoadingSkeleton from '../components/common/LoadingSkeleton.vue'
import ArticleCard from '../components/article/ArticleCard.vue'
import { thumbUrl } from '../utils/imageUrl'
import { formatSize, formatTime } from '@/utils/format'
import { authApi } from '../api/authApi'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const articleStore = useArticleStore()
const auth = useAuthStore()
const { viewMode } = useViewMode()

const user = ref(null)
const loadingUser = ref(false)
const userError = ref(null)
const currentPage = ref(1)
const profileDialogVisible = ref(false)
const passwordDialogVisible = ref(false)
const savingProfile = ref(false)
const savingPassword = ref(false)
const profileForm = reactive({ nickname: '', bio: '', phone: '' })
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

const userId = computed(() => Number(route.params.userId))
const isSelf = computed(() => auth.userId === userId.value)

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

function handlePageChange(page) {
  currentPage.value = page
  articleStore.fetchByUser(userId.value, page - 1)
}

async function loadUserData() {
  loadingUser.value = true
  userError.value = null
  try {
    user.value = await userApi.getById(userId.value)
    await articleStore.fetchByUser(userId.value, 0)
  } catch (e) {
    userError.value = e.message
  } finally {
    loadingUser.value = false
  }
}

function openProfileDialog() {
  Object.assign(profileForm, {
    nickname: user.value?.nickname || '',
    bio: user.value?.bio || '',
    phone: user.value?.phone || ''
  })
  profileDialogVisible.value = true
}

async function saveProfile() {
  savingProfile.value = true
  try {
    const updated = await userApi.updateProfile({ ...profileForm, phone: profileForm.phone || null })
    user.value = updated
    auth.updateCurrentUser(updated)
    profileDialogVisible.value = false
    ElMessage.success('个人资料已更新')
  } finally {
    savingProfile.value = false
  }
}

async function handleAvatarChange(uploadFile) {
  if (!uploadFile.raw) return
  if (uploadFile.raw.size > 2 * 1024 * 1024) {
    ElMessage.error('头像不能超过 2 MiB')
    return
  }
  const updated = await userApi.uploadAvatar(uploadFile.raw)
  user.value = updated
  auth.updateCurrentUser(updated)
  ElMessage.success('头像已更新')
}

async function removeAvatar() {
  await ElMessageBox.confirm('确定恢复为默认头像吗？', '恢复默认头像', { type: 'warning' })
  const updated = await userApi.removeAvatar()
  user.value = updated
  auth.updateCurrentUser(updated)
  ElMessage.success('头像已恢复默认')
}

async function changePassword() {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.error('两次输入的新密码不一致')
    return
  }
  savingPassword.value = true
  try {
    await authApi.changePassword(passwordForm)
    ElMessage.success('密码已修改，请重新登录')
    passwordDialogVisible.value = false
    await auth.logout()
    window.location.assign('/login')
  } finally {
    savingPassword.value = false
  }
}

onMounted(loadUserData)

watch(() => route.params.userId, () => {
  currentPage.value = 1
  loadUserData()
})
</script>

<style scoped>
.profile-page {
  padding: var(--spacing-lg) 0;
}

.profile-error {
  padding: var(--spacing-xxl) 0;
}

/* ---- 头部 ---- */
.profile-header {
  display: flex;
  gap: var(--spacing-lg);
  align-items: flex-start;
}

.profile-avatar {
  flex-shrink: 0;
  border: 1px solid var(--color-border-primary);
}

.profile-info {
  flex: 1;
}

.profile-center-label {
  display: block;
  color: var(--color-secondary-text);
  font-size: var(--font-size-small);
  margin-bottom: var(--spacing-xs);
}

.profile-name-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.profile-name {
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-body-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.profile-username {
  font-size: var(--font-size-xlarge);
  color: var(--color-secondary-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.profile-bio {
  font-size: var(--font-size-large);
  color: var(--color-body-text);
  margin-top: var(--spacing-sm);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.profile-meta {
  margin-top: var(--spacing-md);
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}

.meta-badge {
  padding: 2px 8px;
  font-size: var(--font-size-small);
  border-radius: 12px;
  font-weight: 600;
}

.admin-badge {
  background: #fff3cd;
  color: #856404;
}

.profile-divider {
  border-top: 1px solid var(--color-border-primary);
  margin: var(--spacing-lg) 0;
}

.account-center {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-lg);
  align-items: center;
  padding: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
  border: 1px solid var(--color-border-primary);
  border-radius: var(--radius-md);
  background: var(--color-bg-secondary);
}

.account-center p {
  margin: var(--spacing-xs) 0 0;
  color: var(--color-secondary-text);
}

.account-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  justify-content: flex-end;
}

/* ---- 存档列表 ---- */
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-md);
}

.section-title {
  font-size: var(--font-size-xlarge);
  font-weight: 600;
  margin: 0;
}

.article-table {
  margin-bottom: var(--spacing-lg);
}

.article-row {
  cursor: pointer;
}

/* ---- 封面缩略图 ---- */
.th-cover { width: 48px; }

.col-cover {
  padding: var(--spacing-xs) var(--spacing-sm) !important;
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
  margin: 0 auto;
}

.cover-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.article-title-link {
  font-weight: 600;
  color: var(--color-link);
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.article-row:hover .article-title-link {
  text-decoration: underline;
}

.article-game {
  font-size: var(--font-size-normal);
  color: var(--color-link);
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.article-version {
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

/* ---- 画廊网格 ---- */
.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

@media (max-width: 640px) {
  .account-center {
    align-items: flex-start;
    flex-direction: column;
  }

  .account-actions {
    justify-content: flex-start;
  }

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
