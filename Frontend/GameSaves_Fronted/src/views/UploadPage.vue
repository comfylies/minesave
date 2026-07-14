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
        size="default"
        :disabled="uploading"
      >
        <!-- 游戏搜索/选择 -->
        <el-form-item label="游戏" prop="gameId">
          <el-autocomplete
            v-model="gameSearchText"
            :fetch-suggestions="searchGames"
            :trigger-on-focus="false"
            placeholder="输入游戏名搜索..."
            class="full-width"
            clearable
            :debounce="300"
            @select="handleGameSelect"
            @clear="handleGameClear"
          >
            <template #default="{ item }">
              <div class="game-option" :class="{ 'is-create': item.isCreate }">
                <template v-if="item.isCreate">
                  <el-icon><Plus /></el-icon>
                  <span>创建新游戏 "<em>{{ item.value }}</em>"</span>
                </template>
                <template v-else>
                  <span class="game-name">{{ item.value }}</span>
                  <span class="game-count">{{ item.articleCount || 0 }} 个存档</span>
                </template>
              </div>
            </template>
          </el-autocomplete>
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
          <TagSelector v-model="form.tagIds" :disabled="uploading" />
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
            <el-tooltip content="支持 PNG/JPG/GIF/WebP，建议 16:9。不上传则显示默认占位图。" placement="top">
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
                <el-button type="default" :disabled="uploading">
                  <el-icon><Picture /></el-icon>
                  选择封面图
                </el-button>
              </el-upload>
            </el-tooltip>
            <!-- 封面图本地文件信息 + 删除 -->
            <div v-if="selectedCoverFile" class="cover-file-info">
              <span class="cover-file-name" :title="selectedCoverFile.name">{{ selectedCoverFile.name }}</span>
              <span class="cover-file-size">{{ formatFileSize(selectedCoverFile.size) }}</span>
              <el-button type="danger" link size="small" @click="handleCoverRemove">删除封面</el-button>
            </div>
            <!-- 封面图本地预览 -->
            <div v-if="coverPreviewUrl" class="cover-preview">
              <img :src="coverPreviewUrl" alt="封面预览" />
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
                <el-radio-button value="write">手写</el-radio-button>
                <el-radio-button value="upload">上传文件</el-radio-button>
                <el-radio-button value="auto">自动识别</el-radio-button>
              </el-radio-group>
            </div>

            <div v-if="readmeMode === 'auto'" class="readme-auto-hint">
              <el-icon><MagicStick /></el-icon>
              <span>ZIP 包根目录下自动识别 README.md、readme.md、README.txt、readme.txt、README.markdown、readme.markdown 文件</span>
            </div>

            <el-input
              v-if="readmeMode === 'write'"
              v-model="form.readmeRaw"
              type="textarea"
              :rows="5"
              placeholder="使用 Markdown 格式详细介绍存档内容（可选）&#10;## 版本信息&#10;- 游戏版本：1.21.10&#10;- Mod加载器：Fabric 0.18.0&#10;## 存档介绍&#10;...&#10;## 使用方法&#10;...&#10;## 注意事项&#10;..."
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
            accept=".zip,.7z,.rar,.tar,.tgz,.gz"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :file-list="fileList"
            drag
          >
            <div class="upload-area">
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-text">
                <p>将压缩包拖到此处，或 <em>点击选择</em></p>
                <p class="upload-hint">支持 ZIP / 7z / tar.gz / tar / RAR 格式，最大 {{ maxUploadSizeText }}</p>
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
            {{ uploading ? '上传中...' : '上传存档' }}
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
        <!-- 进度条 — 上传和提取阶段共用 -->
        <div v-if="showProgressBar" class="progress-section">
          <div class="progress-bar-row">
            <el-progress
              :percentage="uploadInProgress ? uploadProgressPct : (progressPct >= 0 ? progressPct : 0)"
              :indeterminate="!uploadInProgress && progressPct < 0"
              :stroke-width="6"
              :show-text="false"
            />
            <span class="progress-pct-text">{{ uploadInProgress ? uploadProgressPct + '%' : (progressPct >= 0 ? progressPct + '%' : '') }}</span>
          </div>

          <!-- HTTP 上传阶段 -->
          <template v-if="uploadInProgress">
            <div class="progress-detail">
              <span>正在上传存档文件</span>
              <span v-if="uploadTotal > 0">（{{ formatFileSize(uploadLoaded) }} / {{ formatFileSize(uploadTotal) }}）</span>
            </div>
            <div v-if="uploadSpeed > 0" class="progress-speed">
              {{ formatSpeed(uploadSpeed) }}
            </div>
            <div class="progress-time">
              <span v-if="uploadSpeed > 0 && uploadEta > 0" class="progress-eta">预计剩余 {{ formatElapsed(uploadEta) }}</span>
            </div>
          </template>

          <!-- 提取/存储上传阶段 -->
          <template v-else>
            <div class="progress-detail">
              <template v-if="progressPhase === 'EXTRACTING'">
                <span>正在解压文件</span>
                <span v-if="progressTotal > 0">（{{ progressProcessed }}/{{ progressTotal }}）</span>
                <span v-else>（{{ progressProcessed }} 个文件）</span>
              </template>
              <template v-else-if="progressPhase === 'UPLOADING'">
                <span>正在上传文件到存储</span>
                <span v-if="progressTotal > 0">（{{ progressProcessed }}/{{ progressTotal }}）</span>
                <span v-else>（{{ progressProcessed }} 个文件）</span>
              </template>
              <template v-else>
                <span>正在处理中...</span>
              </template>
            </div>
            <div v-if="progressCurrentFile" class="progress-file" :title="progressCurrentFile">
              <el-icon><Document /></el-icon>
              {{ progressCurrentFile }}
            </div>
            <div class="progress-time">
              <span v-if="progressElapsed > 0">已用时 {{ formatElapsed(progressElapsed) }}</span>
              <span v-if="progressEta > 0" class="progress-eta"> · 预计剩余 {{ formatElapsed(progressEta) }}</span>
            </div>
          </template>
        </div>
      </div>
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
          <li><strong>默认行为：</strong>不上传封面则显示游戏默认占位图</li>
          <li><strong>建议内容：</strong>游戏截图、标题画面或代表性场景</li>
        </ul>
      </template>
      <template v-else>
        <h4>README 文档要求</h4>
        <ul class="detail-list">
          <li><strong>支持格式：</strong>Markdown（.md）、纯文本（.txt / .markdown）</li>
          <li><strong>三种提供方式：</strong>
            <ul>
              <li><strong>手写：</strong>在上方文本框直接编写 Markdown</li>
              <li><strong>上传文件：</strong>上传本地 .md / .txt 文件</li>
              <li><strong>自动识别：</strong>ZIP 包根目录下的 README 文件会被自动提取</li>
            </ul>
          </li>
          <li><strong>自动识别文件名：</strong><br/>
            <code>README.md</code>、<code>readme.md</code>、<code>README.txt</code>、<code>readme.txt</code>、<code>README.markdown</code>、<code>readme.markdown</code>
          </li>
          <li><strong>文档中的图片：</strong>使用相对路径引用 ZIP 包内图片<br/>
            <code>![描述](./images/screenshot.png)</code> 或 <code>![描述](screenshot.jpg)</code>
          </li>
          <li><strong>图片格式支持：</strong>PNG、JPG、GIF、WebP</li>
          <li><strong>优先级：</strong>手写/上传 &gt; 自动识别（手动提供的内容优先）</li>
          <li><strong>建议内容：</strong>版本信息、Mod 列表、存档介绍、使用方法、注意事项</li>
        </ul>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, InfoFilled, MagicStick, Document } from '@element-plus/icons-vue'
import { useGameStore } from '../stores/games'
import { useAuthStore } from '../stores/auth'
import { articleApi } from '../api/articleApi'
import { gameApi } from '../api/gameApi'
import TagSelector from '../components/tag/TagSelector.vue'

const router = useRouter()
const gameStore = useGameStore()
const auth = useAuthStore()

// ── 管理员上传限制（1GB），普通用户 200MB ──
const maxUploadSize = computed(() => auth.isAdmin ? 1024 * 1024 * 1024 : 200 * 1024 * 1024)
const maxUploadSizeText = computed(() => auth.isAdmin ? '1 GB' : '200 MB')

const formRef = ref(null)
const uploadRef = ref(null)
const uploading = ref(false)
const selectedFile = ref(null)
const fileList = ref([])

// README 模式: 'write' 手写 / 'upload' 上传文件 / 'auto' 自动识别
const readmeMode = ref('write')
const readmeUploadRef = ref(null)
const selectedReadmeFile = ref(null)
const readmeFileList = ref([])

// 封面图
const coverUploadRef = ref(null)
const selectedCoverFile = ref(null)
const coverFileList = ref([])
const coverPreviewUrl = ref(null)

// 详情抽屉
const drawerVisible = ref(false)
const drawerType = ref('readme')
const drawerTitle = ref('')

// 处理状态
const processingStatus = ref('')
const processingType = ref('info')
const processingTitle = ref('')
const processingDesc = ref('')
let pollingTimer = null

// 进度数据
const showProgressBar = ref(false)
const progressPhase = ref('')
const progressPct = ref(-1)
const progressProcessed = ref(0)
const progressTotal = ref(-1)
const progressCurrentFile = ref('')
const progressElapsed = ref(0)
const progressEta = ref(-1)

// 上传进度（HTTP 文件上传阶段，axios onUploadProgress）
const uploadProgressPct = ref(0)
const uploadSpeed = ref(0)       // bytes/s
const uploadEta = ref(-1)        // 秒
const uploadLoaded = ref(0)
const uploadTotal = ref(0)
let lastUploadLoaded = 0
let lastUploadTime = 0

// HTTP 文件上传是否正在进行（独立于 processingStatus，避免被后端轮询状态覆盖）
const uploadInProgress = ref(false)

const form = reactive({
  gameId: null,
  title: '',
  version: '',
  description: '',
  readmeRaw: '',
  tagIds: []
})

// ── 游戏搜索 + 自动创建 ──
const gameSearchText = ref('')
const newGameName = ref('')   // 待创建的游戏名（选中"创建新游戏"时暂存）
let allGamesCache = []   // 完整游戏列表（冷数据）
let cachedGameMap = {}   // gameId → game name

/** 联想搜索（el-autocomplete callback） */
async function searchGames(queryString, cb) {
  const q = (queryString || '').trim()
  if (!q || q.length < 1) {
    if (allGamesCache.length === 0) {
      try { allGamesCache = await gameApi.getAll() } catch { allGamesCache = [] }
    }
    const suggestions = allGamesCache.map(g => ({ value: g.name, id: g.id, articleCount: g.articleCount }))
    cb(suggestions)
    return
  }

  try {
    const results = await gameApi.search(q)
    const suggestions = results.map(g => ({ value: g.name, id: g.id, articleCount: g.articleCount }))
    const exactMatch = results.some(g => g.name.toLowerCase() === q.toLowerCase())
    if (!exactMatch && q.length >= 2) {
      suggestions.push({ value: q, id: -1, isCreate: true, articleCount: 0 })
    }
    cb(suggestions)
  } catch {
    if (allGamesCache.length === 0) {
      try { allGamesCache = await gameApi.getAll() } catch { allGamesCache = [] }
    }
    const lower = q.toLowerCase()
    const filtered = allGamesCache
      .filter(g => g.name.toLowerCase().includes(lower))
      .map(g => ({ value: g.name, id: g.id, articleCount: g.articleCount }))
    if (!filtered.some(g => g.value.toLowerCase() === lower) && q.length >= 2) {
      filtered.push({ value: q, id: -1, isCreate: true, articleCount: 0 })
    }
    cb(filtered)
  }
}

function handleGameSelect(item) {
  if (item.isCreate) {
    newGameName.value = item.value
    form.gameId = -1
    gameSearchText.value = item.value
    formRef.value?.validateField('gameId')
  } else {
    newGameName.value = ''
    form.gameId = item.id
    gameSearchText.value = item.value
    cachedGameMap[item.id] = item.value
    formRef.value?.validateField('gameId')
  }
}

function handleGameClear() {
  form.gameId = null
  gameSearchText.value = ''
  newGameName.value = ''
}

const rules = {
  gameId: [{ required: true, message: '请搜索并选择游戏', trigger: 'change' }],
  title: [{ required: true, message: '请输入存档标题', trigger: 'blur' }],
  version: [{ required: true, message: '请输入游戏版本', trigger: 'blur' }]
}

const ALLOWED_ARCHIVE_EXTS = ['.zip', '.7z', '.rar', '.tar', '.tgz', '.tar.gz', '.gz']

function handleFileChange(file) {
  const rawFile = file.raw
  if (rawFile) {
    // 扩展名白名单检查
    const name = (rawFile.name || '').toLowerCase()
    const allowed = ALLOWED_ARCHIVE_EXTS.some(ext => name.endsWith(ext))
    if (!allowed) {
      ElMessage.error('不支持的压缩格式，请上传 ZIP / 7z / tar.gz / tar / RAR 文件')
      fileList.value = []
      selectedFile.value = null
      return
    }
    if (rawFile.size > maxUploadSize.value) {
      ElMessage.error('文件大小不能超过 ' + maxUploadSizeText.value)
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

function handleCoverChange(file) {
  const rawFile = file.raw
  if (rawFile) {
    selectedCoverFile.value = rawFile
    // 生成本地预览 URL
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

function formatElapsed(seconds) {
  if (!seconds || seconds < 0) return ''
  if (seconds < 60) return Math.floor(seconds) + ' 秒'
  const min = Math.floor(seconds / 60)
  const sec = Math.floor(seconds % 60)
  if (min < 60) return min + ' 分 ' + sec + ' 秒'
  const hrs = Math.floor(min / 60)
  const mins = min % 60
  return hrs + ' 小时 ' + mins + ' 分'
}

function formatSpeed(bytesPerSec) {
  if (!bytesPerSec || bytesPerSec <= 0) return ''
  if (bytesPerSec < 1024) return bytesPerSec + ' B/s'
  const kb = bytesPerSec / 1024
  if (kb < 1024) return kb.toFixed(1) + ' KB/s'
  const mb = kb / 1024
  return mb.toFixed(1) + ' MB/s'
}

function openDetail(type) {
  drawerType.value = type
  drawerTitle.value = type === 'cover' ? '封面图要求' : 'README 文档要求'
  drawerVisible.value = true
}

function resetProgress() {
  showProgressBar.value = false
  uploadInProgress.value = false
  progressPhase.value = ''
  progressPct.value = -1
  progressProcessed.value = 0
  progressTotal.value = -1
  progressCurrentFile.value = ''
  progressElapsed.value = 0
  progressEta.value = -1
  // 上传进度
  uploadProgressPct.value = 0
  uploadSpeed.value = 0
  uploadEta.value = -1
  uploadLoaded.value = 0
  uploadTotal.value = 0
  lastUploadLoaded = 0
  lastUploadTime = 0
}

function onUploadProgress(progressEvent) {
  const now = Date.now()
  uploadLoaded.value = progressEvent.loaded
  uploadTotal.value = progressEvent.total

  if (progressEvent.total > 0) {
    uploadProgressPct.value = Math.round((progressEvent.loaded / progressEvent.total) * 100)
  }

  // 每秒计算一次上传速度
  if (lastUploadTime > 0 && now - lastUploadTime >= 1000) {
    const deltaBytes = progressEvent.loaded - lastUploadLoaded
    const deltaSec = (now - lastUploadTime) / 1000
    if (deltaSec > 0) {
      uploadSpeed.value = Math.round(deltaBytes / deltaSec)
    }
    if (uploadSpeed.value > 0 && progressEvent.total > 0) {
      const remaining = progressEvent.total - progressEvent.loaded
      uploadEta.value = Math.round(remaining / uploadSpeed.value)
    }
    lastUploadLoaded = progressEvent.loaded
    lastUploadTime = now
  } else if (lastUploadTime === 0) {
    lastUploadLoaded = progressEvent.loaded
    lastUploadTime = now
  }
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
  clearPolling()
  pollingTimer = setInterval(async () => {
    try {
      const statusData = await articleApi.getStatus(articleId)
      const status = statusData.status || statusData

      updateProcessing(status, statusData.errorMessage)

      // ── 解析进度数据 ──
      if (statusData.progress) {
        const p = statusData.progress
        showProgressBar.value = true
        progressPhase.value = p.phase || ''
        progressPct.value = p.pct != null ? p.pct : -1
        progressProcessed.value = p.processed || 0
        progressTotal.value = p.total || -1
        progressCurrentFile.value = p.currentFile || ''
        progressElapsed.value = p.elapsedSec || 0
        progressEta.value = p.etaSec || -1
      }

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
      // 不再超时 — 持续轮询直到 READY 或 FAILED
    } catch (e) {
      clearPolling()
      updateProcessing('FAILED', '无法获取处理状态')
      uploading.value = false
    }
  }, 2000)
}

async function handleUpload() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (!selectedFile.value) {
    ElMessage.warning('请选择存档文件')
    return
  }

  uploading.value = true
  resetProgress()
  updateProcessing('UPLOADING')
  showProgressBar.value = true   // 立即显示进度条
  uploadInProgress.value = true  // HTTP 上传阶段开始

  try {
    if (form.gameId === -1 && newGameName.value) {
      try {
        const newGame = await gameApi.create({ name: newGameName.value })
        form.gameId = newGame.id
        cachedGameMap[newGame.id] = newGame.name
        allGamesCache.push(newGame)
        ElMessage.success(`已创建游戏 "${newGame.name}"`)
      } catch (e) {
        ElMessage.error(e.message || '创建游戏失败，请重新选择游戏')
        uploading.value = false
        showProgressBar.value = false
        uploadInProgress.value = false
        return
      }
    }

    // README: 自动识别模式下不发送任何 README 内容
    const readmeRawValue = readmeMode.value === 'write'
      ? (form.readmeRaw || undefined)
      : undefined

    const uid = auth.userId
    if (uid == null || isNaN(uid) || uid <= 0) {
      ElMessage.error('登录状态异常，请重新登录')
      uploading.value = false
      showProgressBar.value = false
      return
    }

    const metadata = {
      gameId: form.gameId,
      userId: uid,
      title: form.title,
      version: form.version,
      description: form.description || undefined,
      readmeRaw: readmeRawValue,
      tagIds: form.tagIds.length > 0 ? form.tagIds : undefined
    }

    const formData = new FormData()
    const jsonStr = JSON.stringify(metadata)
    formData.append('metadata', new File([jsonStr], 'metadata.json', { type: 'application/json' }))
    formData.append('file', selectedFile.value)

    if (readmeMode.value === 'upload' && selectedReadmeFile.value) {
      formData.append('readmeFile', selectedReadmeFile.value)
    }

    if (selectedCoverFile.value) {
      formData.append('coverFile', selectedCoverFile.value)
    }

    const article = await articleApi.create(formData, onUploadProgress)
    uploadInProgress.value = false  // HTTP 上传完成，切换到提取阶段
    updateProcessing('EXTRACTING')

    pollStatus(article.id)
  } catch (e) {
    uploadInProgress.value = false
    updateProcessing('FAILED', e.message || '上传失败')
    uploading.value = false
    showProgressBar.value = false
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
  margin-bottom: var(--spacing-md);
}

.upload-card {
  padding: var(--spacing-lg);
}

.full-width {
  width: 100%;
}

/* ---- 表单紧凑化 ---- */
.upload-card :deep(.el-form-item) {
  margin-bottom: 16px;
}

.upload-card :deep(.el-form-item__label) {
  margin-bottom: 4px;
}

/* ---- 表单标签行（标签 + 详情按钮） ---- */
.form-label-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* ---- 游戏搜索建议 ---- */
.game-option {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: space-between;
  width: 100%;
}

.game-option .game-name {
  font-weight: 500;
}

.game-option .game-count {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  flex-shrink: 0;
}

.game-option.is-create {
  color: var(--color-link);
  font-weight: 500;
  justify-content: flex-start;
  gap: 6px;
}

.game-option.is-create em {
  font-style: normal;
  font-weight: 700;
}

/* ---- 封面图 ---- */
.cover-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
}

.cover-file-info {
  margin-top: 6px;
  font-size: 13px;
  color: var(--color-secondary-text, #666);
  display: flex;
  align-items: center;
  gap: 8px;
  width: 480px;
}

.cover-file-name {
  color: var(--color-body-text, #333);
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 0;
}

.cover-file-size {
  color: var(--color-secondary-text, #999);
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
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 6px;
  overflow: hidden;
}

.cover-preview img {
  width: 100%;
  height: auto;
  display: block;
  object-fit: cover;
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

/* ---- README ---- */
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
  padding: var(--spacing-sm) 0;
}

.readme-auto-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 10px 12px;
  background: var(--el-color-info-light-9, #f4f4f5);
  border-radius: 6px;
  font-size: 13px;
  color: var(--color-secondary-text, #666);
  line-height: 1.6;
}

.readme-auto-hint .el-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--el-color-info, #909399);
}

/* ---- 标签 ---- */
.tag-hint {
  font-size: var(--font-size-small, 12px);
  color: var(--color-secondary-text, #999);
  margin-top: 4px;
}

/* ---- Textarea 统一内边距 ---- */
.upload-card :deep(.el-textarea__inner) {
  line-height: 1.6;
  padding: 8px 12px;
}

/* ---- 提交按钮 ---- */
.submit-btn {
  width: 100%;
  padding: 14px 0;
  font-size: 16px;
  margin-top: 4px;
}

/* ---- 处理进度 ---- */
.processing-status {
  margin-top: var(--spacing-md);
}

.progress-section {
  margin-top: 12px;
}

.progress-bar-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-bar-row .el-progress {
  flex: 1;
}

.progress-pct-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-body-text);
  min-width: 40px;
  text-align: right;
}

.progress-detail {
  font-size: 13px;
  color: var(--color-secondary-text);
  margin-top: 6px;
  text-align: center;
}

.progress-speed {
  font-size: 13px;
  color: var(--color-link, #409eff);
  margin-top: 2px;
  text-align: center;
  font-weight: 500;
}

.progress-file {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-secondary-text);
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.progress-time {
  font-size: 12px;
  color: var(--color-secondary-text);
  margin-top: 4px;
  text-align: center;
}

.progress-eta {
  color: var(--el-color-warning);
}

/* ---- 详情抽屉 ---- */
.detail-list {
  padding-left: 18px;
  line-height: 2;
  color: var(--color-body-text, #333);
}

.detail-list li {
  margin-bottom: 4px;
}

.detail-list ul {
  padding-left: 16px;
  margin-top: 4px;
}

.detail-list code {
  background: var(--el-color-info-light-9, #f4f4f5);
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 90%;
}

.detail-list h4 {
  margin: 12px 0 8px;
}
</style>
