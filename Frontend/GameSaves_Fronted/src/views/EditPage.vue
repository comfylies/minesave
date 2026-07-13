<template>
  <div class="edit-page">
    <h1 class="page-title">编辑存档</h1>
    <p class="page-subtitle">{{ article?.title || '加载中...' }}</p>

    <!-- 加载中 -->
    <div v-if="loading" class="edit-card card">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- 错误 -->
    <div v-else-if="loadError" class="edit-card card">
      <el-empty :description="loadError">
        <el-button type="primary" @click="$router.back()">返回</el-button>
      </el-empty>
    </div>

    <!-- 编辑表单 -->
    <div v-else class="edit-card card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="default"
        :disabled="saving"
      >
        <!-- 游戏名（只读） -->
        <el-form-item label="游戏">
          <el-input :model-value="article.gameName" disabled />
          <div class="field-hint">游戏不可修改</div>
        </el-form-item>

        <!-- 标题和版本 -->
        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="存档标题" prop="title">
              <el-input v-model="form.title" placeholder="例如：58K一桶岩浆生炸刷石机" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="游戏版本" prop="version">
              <el-input v-model="form.version" placeholder="例如：1.21.10-Fabric 0.18.0" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 描述 -->
        <el-form-item label="存档描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="简单描述存档内容（可选）"
          />
        </el-form-item>

        <!-- 标签 -->
        <el-form-item label="标签">
          <TagSelector v-model="form.tagIds" :disabled="saving" />
          <div class="tag-hint">选择标签或输入新标签名创建</div>
        </el-form-item>

        <!-- 封面图 -->
        <el-form-item>
          <template #label>
            <span class="form-label-row">
              封面图（可选）
              <el-button type="primary" link size="small" @click="openDetail('cover')">
                <el-icon><InfoFilled /></el-icon>详情
              </el-button>
            </span>
          </template>
          <div class="cover-wrapper">
            <!-- 当前封面预览 -->
            <div v-if="!selectedCoverFile && existingCoverUrl" class="existing-cover">
              <span class="existing-cover-label">当前封面：</span>
              <img :src="existingCoverUrl" alt="当前封面" class="existing-cover-img" />
            </div>

            <el-upload
              ref="coverUploadRef"
              :auto-upload="false"
              :limit="1"
              accept="image/png,image/jpeg,image/gif,image/webp"
              :on-change="handleCoverChange"
              :on-remove="handleCoverRemove"
              :file-list="coverFileList"
              :show-file-list="false"
            >
              <el-button type="default" :disabled="saving">
                <el-icon><Picture /></el-icon>
                {{ existingCoverUrl ? '更换封面图' : '选择封面图' }}
              </el-button>
            </el-upload>

            <!-- 新封面文件信息 -->
            <div v-if="selectedCoverFile" class="cover-file-info">
              <span class="cover-file-name" :title="selectedCoverFile.name">{{ selectedCoverFile.name }}</span>
              <span class="cover-file-size">{{ formatFileSize(selectedCoverFile.size) }}</span>
              <el-button type="danger" link size="small" @click="handleCoverRemove">取消更换</el-button>
            </div>
            <!-- 新封面预览 -->
            <div v-if="coverPreviewUrl" class="cover-preview">
              <img :src="coverPreviewUrl" alt="新封面预览" />
            </div>
          </div>
        </el-form-item>

        <!-- README（Markdown） -->
        <el-form-item>
          <template #label>
            <span class="form-label-row">
              README（Markdown）
              <el-button type="primary" link size="small" @click="openDetail('readme')">
                <el-icon><InfoFilled /></el-icon>详情
              </el-button>
            </span>
          </template>
          <div class="readme-inputs">
            <div class="readme-tabs">
              <el-radio-group v-model="readmeMode" size="small">
                <el-radio-button value="write">手写编辑</el-radio-button>
                <el-radio-button value="upload">上传文件替换</el-radio-button>
              </el-radio-group>
              <span v-if="readmeMode === 'upload'" class="readme-replace-warning">
                <el-icon><WarningFilled /></el-icon>
                上传新文件将完全替换现有 README 并删除所有旧批注
              </span>
            </div>

            <el-input
              v-if="readmeMode === 'write'"
              v-model="form.readmeRaw"
              type="textarea"
              :rows="8"
              placeholder="使用 Markdown 格式详细介绍存档内容（可选）"
            />

            <el-upload
              v-if="readmeMode === 'upload'"
              ref="readmeUploadRef"
              :auto-upload="false"
              :limit="1"
              accept=".md,.markdown,.txt"
              :on-change="handleReadmeFileChange"
              :on-remove="handleReadmeFileRemove"
              :file-list="readmeFileList"
              drag
              class="readme-upload"
            >
              <div class="upload-area readme-area">
                <el-icon class="upload-icon"><Document /></el-icon>
                <div class="upload-text">
                  <p>将 .md 文件拖到此处，或 <em>点击选择</em></p>
                  <p class="upload-hint">支持 .md / .markdown / .txt 格式，文件不超过 1 MB</p>
                </div>
              </div>
            </el-upload>
          </div>
        </el-form-item>

        <!-- 操作按钮 -->
        <el-form-item>
          <div class="action-row">
            <el-button @click="$router.back()" :disabled="saving">取消</el-button>
            <el-button
              type="primary"
              :loading="saving"
              class="submit-btn"
              @click="handleSave"
            >
              {{ saving ? '保存中...' : '保存修改' }}
            </el-button>
          </div>
          <div class="edit-info">
            今日剩余编辑次数：<strong>{{ remainingEdits }}</strong> / {{ maxDailyEdits }}
          </div>
        </el-form-item>
      </el-form>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      direction="rtl"
      size="420px"
    >
      <template v-if="drawerType === 'cover'">
        <h4>封面图要求</h4>
        <ul class="detail-list">
          <li><strong>支持格式：</strong>PNG、JPG/JPEG、GIF、WebP</li>
          <li><strong>建议宽高比：</strong>16:9（如 1920×1080），上传后自动裁剪</li>
          <li><strong>文件大小：</strong>不限（后端生成缩略图）</li>
          <li><strong>缩略图规格：</strong>自动生成 270p、360p、720p 三档</li>
          <li><strong>默认行为：</strong>不上传封面则保留原有封面图</li>
        </ul>
      </template>
      <template v-else>
        <h4>README 文档要求</h4>
        <ul class="detail-list">
          <li><strong>支持格式：</strong>Markdown（.md）、纯文本（.txt / .markdown）</li>
          <li><strong>手写编辑模式：</strong>直接修改现有 Markdown 内容，保留所有批注</li>
          <li><strong>上传文件替换模式：</strong>用新文件完全替换 README，<span style="color: var(--el-color-danger)">旧批注将被全部删除</span></li>
          <li><strong>文档中的图片：</strong>使用已有图片路径（编辑不支持上传新图片）</li>
          <li><strong>建议内容：</strong>版本信息、Mod 列表、存档介绍、使用方法、注意事项</li>
        </ul>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { InfoFilled, WarningFilled } from '@element-plus/icons-vue'
import { articleApi } from '../api/articleApi'
import TagSelector from '../components/tag/TagSelector.vue'

const route = useRoute()
const router = useRouter()

const articleId = computed(() => Number(route.params.articleId) || 0)

const article = ref(null)
const loading = ref(true)
const loadError = ref('')
const saving = ref(false)
const formRef = ref(null)

// 编辑次数显示
const maxDailyEdits = 2

// README 模式
const readmeMode = ref('write')
const readmeUploadRef = ref(null)
const selectedReadmeFile = ref(null)
const readmeFileList = ref([])

// 封面图
const coverUploadRef = ref(null)
const selectedCoverFile = ref(null)
const coverFileList = ref([])
const coverPreviewUrl = ref(null)
const existingCoverUrl = computed(() => {
  return article.value?.coverThumbnail || article.value?.coverImage || ''
})

// 详情抽屉
const drawerVisible = ref(false)
const drawerType = ref('readme')
const drawerTitle = ref('')

const form = reactive({
  title: '',
  version: '',
  description: '',
  readmeRaw: '',
  tagIds: []
})

const rules = {
  title: [{ required: true, message: '请输入存档标题', trigger: 'blur' }],
  version: [{ required: true, message: '请输入游戏版本', trigger: 'blur' }]
}

// 剩余编辑次数（客户端估算）
const remainingEdits = computed(() => {
  return Math.max(0, maxDailyEdits - (article.value?.dailyEditCount || 0))
})

// ── README 文件处理 ──
function handleReadmeFileChange(file) {
  const rawFile = file.raw
  if (rawFile) {
    if (rawFile.size > 1 * 1024 * 1024) {
      ElMessage.error('README 文件大小不能超过 1 MB')
      readmeFileList.value = []
      selectedReadmeFile.value = null
      return
    }
    selectedReadmeFile.value = rawFile
  }
}

function handleReadmeFileRemove() {
  selectedReadmeFile.value = null
  readmeFileList.value = []
}

// ── 封面图文件处理 ──
function handleCoverChange(file) {
  const rawFile = file.raw
  if (rawFile) {
    selectedCoverFile.value = rawFile
    if (coverPreviewUrl.value) {
      URL.revokeObjectURL(coverPreviewUrl.value)
    }
    coverPreviewUrl.value = URL.createObjectURL(rawFile)
  }
}

function handleCoverRemove() {
  selectedCoverFile.value = null
  coverFileList.value = []
  if (coverPreviewUrl.value) {
    URL.revokeObjectURL(coverPreviewUrl.value)
    coverPreviewUrl.value = null
  }
}

function formatFileSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function openDetail(type) {
  drawerType.value = type
  drawerTitle.value = type === 'cover' ? '封面图要求' : 'README 文档要求'
  drawerVisible.value = true
}

// ── 加载文章数据 ──
async function loadArticle() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await articleApi.getById(articleId.value)
    article.value = data

    // 预填表单
    form.title = data.title || ''
    form.version = data.version || ''
    form.description = data.description || ''
    form.readmeRaw = data.readmeRaw || ''
    form.tagIds = data.tags?.map(t => t.id) || []

    // 预选手写编辑模式
    readmeMode.value = 'write'
    selectedReadmeFile.value = null
    readmeFileList.value = []
    selectedCoverFile.value = null
    coverFileList.value = []
  } catch (e) {
    loadError.value = e.message || '加载存档数据失败'
  } finally {
    loading.value = false
  }
}

// ── 保存 ──
async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true

  try {
    const metadata = {
      title: form.title,
      version: form.version,
      description: form.description || undefined,
      readmeRaw: readmeMode.value === 'write' ? (form.readmeRaw || undefined) : undefined,
      tagIds: form.tagIds.length > 0 ? form.tagIds : undefined
    }

    const formData = new FormData()
    const jsonStr = JSON.stringify(metadata)
    formData.append('metadata', new File([jsonStr], 'metadata.json', { type: 'application/json' }))

    if (readmeMode.value === 'upload' && selectedReadmeFile.value) {
      formData.append('readmeFile', selectedReadmeFile.value)
    }

    if (selectedCoverFile.value) {
      formData.append('coverFile', selectedCoverFile.value)
    }

    await articleApi.updateFull(articleId.value, formData)
    ElMessage.success('存档信息已更新')
    router.push(`/articles/${articleId.value}`)
  } catch (e) {
    // 错误已在拦截器中提示
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadArticle()
})

onUnmounted(() => {
  if (coverPreviewUrl.value) {
    URL.revokeObjectURL(coverPreviewUrl.value)
  }
})
</script>

<style scoped>
.edit-page {
  padding: var(--spacing-lg) 0;
  max-width: 720px;
  margin: 0 auto;
}

.page-title {
  font-size: var(--font-size-title);
  font-weight: 600;
}

.page-subtitle {
  font-size: var(--font-size-normal);
  color: var(--color-secondary-text);
  margin-bottom: var(--spacing-md);
}

.edit-card {
  padding: var(--spacing-lg);
}

/* ---- 字段提示 ---- */
.field-hint {
  font-size: 12px;
  color: var(--color-secondary-text);
  margin-top: 2px;
}

/* ---- 表单紧凑化 ---- */
.edit-card :deep(.el-form-item) {
  margin-bottom: 16px;
}

.edit-card :deep(.el-form-item__label) {
  margin-bottom: 4px;
}

/* ---- 表单标签行 ---- */
.form-label-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* ---- 封面图 ---- */
.cover-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
}

.existing-cover {
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.existing-cover-label {
  font-size: 13px;
  color: var(--color-secondary-text);
}

.existing-cover-img {
  max-width: 320px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  object-fit: cover;
  aspect-ratio: 16 / 9;
}

.cover-file-info {
  margin-top: 6px;
  font-size: 13px;
  color: var(--color-secondary-text);
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.cover-file-name {
  color: var(--color-body-text);
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 0;
}

.cover-file-size {
  color: var(--color-secondary-text);
  font-size: 12px;
  flex-shrink: 0;
}

.cover-file-info .el-button {
  margin-left: auto;
  flex-shrink: 0;
}

.cover-preview {
  margin-top: 10px;
  max-width: 480px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  overflow: hidden;
}

.cover-preview img {
  width: 100%;
  height: auto;
  display: block;
  object-fit: cover;
}

/* ---- README ---- */
.readme-inputs {
  width: 100%;
}

.readme-tabs {
  margin-bottom: var(--spacing-sm);
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.readme-replace-warning {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-color-danger);
}

.readme-upload {
  width: 100%;
}

.readme-area {
  padding: var(--spacing-sm) 0;
}

/* ---- 上传区域 ---- */
.upload-area {
  text-align: center;
  padding: var(--spacing-md) 0;
}

.upload-icon {
  font-size: 40px;
  color: var(--color-secondary-text);
}

.upload-text p {
  margin-top: var(--spacing-sm);
  font-size: var(--font-size-normal);
  color: var(--color-body-text);
}

.upload-text em {
  color: var(--color-link);
  font-style: normal;
  cursor: pointer;
}

.upload-hint {
  font-size: var(--font-size-small) !important;
  color: var(--color-secondary-text) !important;
}

/* ---- 标签 ---- */
.tag-hint {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  margin-top: 4px;
}

/* ---- 操作按钮 ---- */
.action-row {
  display: flex;
  gap: 12px;
  width: 100%;
}

.submit-btn {
  flex: 1;
  padding: 14px 0;
  font-size: 16px;
}

.edit-info {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-secondary-text);
  text-align: right;
}

.edit-info strong {
  color: var(--color-body-text);
}

/* ---- Textarea 统一内边距 ---- */
.edit-card :deep(.el-textarea__inner) {
  line-height: 1.6;
  padding: 8px 12px;
}

/* ---- 详情抽屉 ---- */
.detail-list {
  padding-left: 18px;
  line-height: 2;
  color: var(--color-body-text);
}

.detail-list li {
  margin-bottom: 4px;
}

.detail-list h4 {
  margin: 12px 0 8px;
}
</style>
