<template>
  <div class="upload-page">
    <h1 class="page-title">上传存档</h1>
    <p class="page-subtitle">上传游戏存档压缩包，分享给其他玩家</p>

    <div class="upload-card card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        :disabled="uploading"
      >
        <!-- 游戏选择 -->
        <el-form-item label="游戏" prop="gameId">
          <el-select
            v-model="form.gameId"
            placeholder="选择游戏"
            class="full-width"
            filterable
          >
            <el-option
              v-for="g in gameStore.games"
              :key="g.id"
              :label="g.name"
              :value="g.id"
            />
          </el-select>
        </el-form-item>

        <!-- 标题和版本 -->
        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="存档标题" prop="title">
              <el-input
                v-model="form.title"
                placeholder="例如：58K一桶岩浆生炸刷石机"
              />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="游戏版本" prop="version">
              <el-input
                v-model="form.version"
                placeholder="例如：1.21.10-Fabric 0.18.0"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 描述 -->
        <el-form-item label="存档描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="简单描述存档内容（可选）"
          />
        </el-form-item>

        <!-- README（Markdown） -->
        <el-form-item label="README（Markdown）">
          <div class="readme-inputs">
            <div class="readme-tabs">
              <el-radio-group v-model="readmeMode" size="small">
                <el-radio-button value="write">手写 Markdown</el-radio-button>
                <el-radio-button value="upload">上传 .md 文件</el-radio-button>
              </el-radio-group>
            </div>

            <el-input
              v-if="readmeMode === 'write'"
              v-model="form.readmeRaw"
              type="textarea"
              :rows="6"
              placeholder="使用 Markdown 格式详细介绍存档内容（可选）&#10;&#10;## 版本信息&#10;- 游戏版本：1.21.10&#10;- Mod加载器：Fabric 0.18.0&#10;&#10;## 存档介绍&#10;...&#10;&#10;## 使用方法&#10;...&#10;&#10;## 注意事项&#10;..."
            />

            <el-upload
              v-else
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
                  <p class="upload-hint">支持 .md / .markdown / .txt 格式</p>
                </div>
              </div>
            </el-upload>
          </div>
        </el-form-item>

        <!-- 文件上传 -->
        <el-form-item label="存档文件" prop="file">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".zip"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :file-list="fileList"
            drag
          >
            <div class="upload-area">
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-text">
                <p>将 ZIP 文件拖到此处，或 <em>点击选择</em></p>
                <p class="upload-hint">仅支持 .zip 文件，最大 200 MB</p>
              </div>
            </div>
          </el-upload>
        </el-form-item>

        <!-- 提交 -->
        <el-form-item>
          <el-button
            type="primary"
            :loading="uploading"
            class="submit-btn"
            @click="handleUpload"
          >
            {{ uploading ? '上传中...' : '提交存档' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 处理进度 -->
      <div v-if="processingStatus" class="processing-status">
        <el-alert
          :title="processingTitle"
          :description="processingDesc"
          :type="processingType"
          show-icon
          :closable="false"
        />
        <el-progress
          v-if="processingStatus === 'EXTRACTING'"
          :percentage="100"
          :indeterminate="true"
          :duration="3"
          style="margin-top: 12px;"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useGameStore } from '../stores/games'
import { useAuthStore } from '../stores/auth'
import { articleApi } from '../api/articleApi'

const router = useRouter()
const gameStore = useGameStore()
const auth = useAuthStore()

const formRef = ref(null)
const uploadRef = ref(null)
const uploading = ref(false)
const selectedFile = ref(null)
const fileList = ref([])

// README 模式: 'write' 手写 或 'upload' 上传文件
const readmeMode = ref('write')
const readmeUploadRef = ref(null)
const selectedReadmeFile = ref(null)
const readmeFileList = ref([])

// 处理状态
const processingStatus = ref('')
const processingType = ref('info')
const processingTitle = ref('')
const processingDesc = ref('')
let pollingTimer = null

const form = reactive({
  gameId: null,
  title: '',
  version: '',
  description: '',
  readmeRaw: ''
})

const rules = {
  gameId: [{ required: true, message: '请选择游戏', trigger: 'change' }],
  title: [{ required: true, message: '请输入存档标题', trigger: 'blur' }],
  version: [{ required: true, message: '请输入游戏版本', trigger: 'blur' }]
}

function handleFileChange(file) {
  // Element Plus upload 组件返回的文件对象
  const rawFile = file.raw
  if (rawFile) {
    // 前端大小检查
    if (rawFile.size > 200 * 1024 * 1024) {
      ElMessage.error('文件大小不能超过 200 MB')
      fileList.value = []
      selectedFile.value = null
      return
    }
    selectedFile.value = rawFile
  }
}

function handleFileRemove() {
  selectedFile.value = null
  fileList.value = []
}

function handleReadmeFileChange(file) {
  const rawFile = file.raw
  if (rawFile) {
    // 前端大小检查（MD 文件不超过 1MB）
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

function updateProcessing(status, errorMessage) {
  processingStatus.value = status

  const statusMap = {
    UPLOADING: { title: '文件上传中', desc: '正在接收存档文件...', type: 'info' },
    EXTRACTING: { title: '正在处理存档', desc: '正在解压并索引文件，请稍候...', type: 'warning' },
    READY: { title: '处理完成', desc: '存档已就绪！正在跳转...', type: 'success' },
    FAILED: { title: '处理失败', desc: errorMessage || '存档处理失败，请重试', type: 'error' }
  }

  const info = statusMap[status] || statusMap.FAILED
  processingType.value = info.type
  processingTitle.value = info.title
  processingDesc.value = info.desc
}

function clearPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

async function pollStatus(articleId) {
  const startTime = Date.now()
  const timeout = 30 * 1000 // 30 秒超时

  clearPolling()
  pollingTimer = setInterval(async () => {
    try {
      const statusData = await articleApi.getStatus(articleId)
      const status = statusData.status || statusData // 可能是 {status: "READY"} 或直接字符串

      updateProcessing(status, statusData.errorMessage)

      if (status === 'READY') {
        clearPolling()
        ElMessage.success('存档处理完成！')
        setTimeout(() => {
          router.push(`/articles/${articleId}`)
        }, 800)
      } else if (status === 'FAILED') {
        clearPolling()
        uploading.value = false
      }

      // 超时检查
      if (Date.now() - startTime > timeout && status !== 'READY' && status !== 'FAILED') {
        clearPolling()
        updateProcessing('FAILED', '处理超时，请稍后在「我的存档」中查看状态')
        uploading.value = false
      }
    } catch (e) {
      clearPolling()
      updateProcessing('FAILED', '无法获取处理状态')
      uploading.value = false
    }
  }, 2000)
}

async function handleUpload() {
  // 表单验证
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (!selectedFile.value) {
    ElMessage.warning('请选择存档文件')
    return
  }

  uploading.value = true
  updateProcessing('UPLOADING')

  try {
    // 组装 FormData
    // README: 手写文本优先于上传的 .md 文件
    const readmeRawValue = readmeMode.value === 'write'
      ? (form.readmeRaw || undefined)
      : undefined

    // 确保 userId 有效
    const uid = auth.userId
    if (uid == null || isNaN(uid) || uid <= 0) {
      ElMessage.error('登录状态异常，请重新登录')
      uploading.value = false
      return
    }

    const metadata = {
      gameId: form.gameId,
      userId: uid,
      title: form.title,
      version: form.version,
      description: form.description || undefined,
      readmeRaw: readmeRawValue
    }

    const formData = new FormData()
    // 用 File 代替 Blob，确保浏览器正确设置 Content-Type 为 application/json
    const jsonStr = JSON.stringify(metadata)
    formData.append('metadata', new File([jsonStr], 'metadata.json', { type: 'application/json' }))
    formData.append('file', selectedFile.value)

    // 上传 .md 文件模式 → 附加 readmeFile
    if (readmeMode.value === 'upload' && selectedReadmeFile.value) {
      formData.append('readmeFile', selectedReadmeFile.value)
    }

    const article = await articleApi.create(formData)
    updateProcessing('EXTRACTING')

    // 开始轮询状态
    pollStatus(article.id)
  } catch (e) {
    updateProcessing('FAILED', e.message || '上传失败')
    uploading.value = false
  }
}

onMounted(() => {
  gameStore.fetchGames()
})

onUnmounted(() => {
  clearPolling()
})
</script>

<style scoped>
.upload-page {
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
  margin-bottom: var(--spacing-lg);
}

.upload-card {
  padding: var(--spacing-xl);
}

.full-width {
  width: 100%;
}

/* ---- 上传区域 ---- */
.upload-area {
  text-align: center;
  padding: var(--spacing-lg) 0;
}

.upload-icon {
  font-size: 48px;
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

/* ---- README 双模式切换 ---- */
.readme-inputs {
  width: 100%;
}

.readme-tabs {
  margin-bottom: var(--spacing-sm);
}

.readme-upload {
  width: 100%;
}

.readme-area {
  padding: var(--spacing-md) 0;
}

.submit-btn {
  width: 100%;
}

/* ---- 处理进度 ---- */
.processing-status {
  margin-top: var(--spacing-md);
}
</style>
