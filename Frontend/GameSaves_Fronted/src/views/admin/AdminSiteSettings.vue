<template>
  <div class="admin-site-settings">
    <h2 class="page-title">站点设置</h2>

    <el-card shadow="hover" class="settings-card">
      <template #header>
        <span class="card-header-text">首页背景图片</span>
      </template>

      <!-- 当前背景预览 -->
      <div v-if="currentBgUrl" class="bg-preview">
        <img :src="currentBgUrl" class="bg-preview-img" alt="当前背景图" />
        <el-button type="danger" size="small" @click="handleRemove">
          移除背景
        </el-button>
      </div>
      <div v-else class="bg-preview bg-preview-empty">
        <el-icon :size="48" color="#c0c4cc"><PictureFilled /></el-icon>
        <p>尚未设置背景图片</p>
      </div>

      <!-- 上传新背景 -->
      <div class="upload-section">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :show-file-list="false"
          accept="image/*"
          :on-change="handleFileChange"
        >
          <el-button type="primary">
            <el-icon><Upload /></el-icon>
            选择图片
          </el-button>
        </el-upload>

        <div v-if="selectedFile" class="upload-action">
          <span class="file-name">{{ selectedFile.name }}</span>
          <el-button type="success" :loading="uploading" @click="handleUpload">
            确认上传
          </el-button>
        </div>

        <p class="upload-tip">
          支持 JPG、PNG、WebP，建议尺寸 1920×1080 以上，最大 10MB
        </p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { siteSettingsApi } from '../../api/siteSettingsApi'

const currentBgUrl = ref('')
const selectedFile = ref(null)
const uploading = ref(false)

onMounted(async () => {
  try {
    const settings = await siteSettingsApi.getAdmin()
    currentBgUrl.value = settings?.background_image_url || ''
  } catch {
    // 静默处理
  }
})

function handleFileChange(file) {
  // 客户端大小校验
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 10MB')
    return
  }
  selectedFile.value = file.raw
}

async function handleUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const result = await siteSettingsApi.uploadBackground(selectedFile.value)
    currentBgUrl.value = result.url
    selectedFile.value = null
    ElMessage.success('背景图片上传成功')
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function handleRemove() {
  try {
    await ElMessageBox.confirm('确定移除首页背景图片？', '确认', {
      type: 'warning'
    })
    await siteSettingsApi.update({ background_image_url: '' })
    currentBgUrl.value = ''
    ElMessage.success('背景图片已移除')
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
.admin-site-settings {
  max-width: 680px;
}

.page-title {
  font-size: var(--font-size-xlarge);
  font-weight: 600;
  margin: 0 0 var(--spacing-lg) 0;
  color: var(--color-body-text);
}

.settings-card {
  margin-bottom: var(--spacing-lg);
}

.card-header-text {
  font-weight: 600;
}

.bg-preview {
  margin-bottom: var(--spacing-lg);
}

.bg-preview-img {
  width: 100%;
  max-height: 300px;
  object-fit: cover;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-secondary);
  margin-bottom: var(--spacing-md);
}

.bg-preview-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 160px;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
  color: var(--color-secondary-text);
  font-size: var(--font-size-normal);
  margin-bottom: var(--spacing-lg);
}

.bg-preview-empty p {
  margin: var(--spacing-sm) 0 0 0;
}

.upload-section {
  margin-top: var(--spacing-md);
}

.upload-action {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-top: var(--spacing-md);
}

.file-name {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-tip {
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
  margin-top: var(--spacing-sm);
}
</style>
