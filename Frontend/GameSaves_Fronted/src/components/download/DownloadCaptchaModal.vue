<template>
  <el-dialog
    v-model="visible"
    title="大文件下载验证"
    width="440px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    @closed="handleClosed"
  >
    <div class="captcha-dialog-body">
      <!-- 文件大小和剩余次数 -->
      <div class="info-row">
        <div class="info-item">
          <span class="info-label">文件大小</span>
          <span class="info-value">{{ formatFileSize(fileSize) }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">今日剩余</span>
          <span class="info-value" :class="{ 'low': dailyRemaining <= 1 }">
            {{ dailyRemaining }} 次
          </span>
        </div>
      </div>

      <p class="captcha-tip">
        存档大小超过 500MB，请输入验证码后下载
      </p>

      <!-- 验证码 -->
      <div class="captcha-section">
        <div class="captcha-row">
          <img
            v-if="captchaImage"
            :src="captchaImage"
            alt="验证码"
            class="captcha-img"
            @click="refreshCaptcha"
            title="点击刷新验证码"
          />
          <div v-else class="captcha-placeholder">
            <el-icon class="is-loading"><Loading /></el-icon>
          </div>
          <el-button
            type="primary"
            link
            size="small"
            class="refresh-btn"
            @click="refreshCaptcha"
            :disabled="captchaLoading"
          >
            换一张
          </el-button>
        </div>
        <el-input
          v-model="captchaCode"
          placeholder="请输入验证码（不区分大小写）"
          maxlength="4"
          class="captcha-input"
          @keyup.enter="handleSubmit"
        />
      </div>

      <!-- 错误提示 -->
      <div v-if="errorMsg" class="error-msg">
        <el-icon><WarningFilled /></el-icon>
        {{ errorMsg }}
      </div>
    </div>

    <template #footer>
      <el-button @click="handleCancel" :disabled="submitting">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!captchaCode || captchaCode.length < 4"
        @click="handleSubmit"
      >
        验证并下载
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { WarningFilled, Loading } from '@element-plus/icons-vue'
import { fileApi } from '../../api/fileApi'
import { authApi } from '../../api/authApi'

const props = defineProps({
  /** 是否显示 */
  modelValue: { type: Boolean, default: false },
  /** 文章 ID */
  articleId: { type: Number, required: true },
  /** 文件大小（字节） */
  fileSize: { type: Number, default: 0 },
  /** 今日剩余下载次数 */
  dailyRemaining: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue', 'verified', 'cancel'])

const visible = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')
const captchaCode = ref('')
const captchaLoading = ref(false)
const submitting = ref(false)
const errorMsg = ref('')

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    refreshCaptcha()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

// 刷新验证码
async function refreshCaptcha() {
  captchaLoading.value = true
  errorMsg.value = ''
  captchaCode.value = ''
  try {
    const data = await authApi.getCaptcha()
    captchaImage.value = data.captchaImage
    captchaKey.value = data.captchaKey
  } catch {
    // 错误已在拦截器提示
  } finally {
    captchaLoading.value = false
  }
}

// 提交验证
async function handleSubmit() {
  if (!captchaCode.value || captchaCode.value.length < 4) return

  submitting.value = true
  errorMsg.value = ''

  try {
    const result = await fileApi.verifyDownloadCaptcha(
      props.articleId,
      captchaKey.value,
      captchaCode.value
    )

    // 验证成功 → 触发下载
    emit('verified', {
      downloadToken: result.downloadToken,
      dailyRemaining: result.dailyRemaining
    })
  } catch (e) {
    // 验证码错误 → 刷新并提示
    errorMsg.value = e.message || '验证失败，请重试'
    refreshCaptcha()
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  visible.value = false
  emit('cancel')
}

function handleClosed() {
  captchaCode.value = ''
  errorMsg.value = ''
}

function formatFileSize(bytes) {
  if (!bytes) return '未知'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}
</script>

<style scoped>
.captcha-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-row {
  display: flex;
  gap: 32px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.info-label {
  font-size: 12px;
  color: var(--color-secondary-text);
}

.info-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-body-text);
}

.info-value.low {
  color: var(--el-color-danger);
}

.captcha-tip {
  margin: 0;
  font-size: 13px;
  color: var(--color-secondary-text);
}

.captcha-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.captcha-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.captcha-img {
  height: 48px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  cursor: pointer;
}

.captcha-placeholder {
  width: 130px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--color-secondary-text);
}

.refresh-btn {
  flex-shrink: 0;
}

.captcha-input {
  width: 100%;
}

.error-msg {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-color-danger);
  padding: 8px 12px;
  background: var(--el-color-danger-light-9);
  border-radius: 4px;
}
</style>
