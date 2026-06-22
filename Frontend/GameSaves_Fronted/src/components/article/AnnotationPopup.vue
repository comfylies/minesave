<template>
  <Teleport to="body">
    <!-- 未登录提示对话框 -->
    <div v-if="showLoginDialog" class="popup-overlay" @click.self="showLoginDialog = false">
      <div class="login-dialog">
        <div class="login-dialog-icon">🔒</div>
        <h4>请先登录</h4>
        <p>添加批注需要登录账号。</p>
        <div class="login-dialog-actions">
          <el-button @click="showLoginDialog = false">取消</el-button>
          <el-button type="primary" @click="goToLogin">
            <el-icon><User /></el-icon>去登录
          </el-button>
        </div>
      </div>
    </div>

    <!-- 批注输入气泡 -->
    <div
      v-if="!showLoginDialog"
      ref="popupRef"
      class="annotation-popup"
      :style="popupStyle"
      @click.stop
    >
      <div class="popup-header">
        <el-icon><EditPen /></el-icon>
        <span>添加批注</span>
        <span v-if="pendingSelector" class="popup-quote">
          "{{ truncate(pendingSelector.quote, 30) }}"
        </span>
      </div>
      <el-input
        ref="inputRef"
        v-model="content"
        type="textarea"
        :rows="3"
        placeholder="输入你的批注..."
        class="popup-input"
        @keyup.esc="handleCancel"
        @keyup.enter.ctrl="handleSubmit"
      />
      <div class="popup-actions">
        <el-button size="small" @click="handleCancel">取消</el-button>
        <el-button
          size="small"
          type="primary"
          :disabled="!content.trim()"
          :loading="submitting"
          @click="handleSubmit"
        >
          提交
        </el-button>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { EditPen, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/auth'

const props = defineProps({
  annotator: { type: Object, default: null },
  articleId: { type: Number, required: true },
  pendingSelector: { type: Object, default: null }
})

const emit = defineEmits(['submit', 'cancel'])

const router = useRouter()
const auth = useAuthStore()

const content = ref('')
const popupRef = ref(null)
const inputRef = ref(null)
const popupStyle = ref({})
const showLoginDialog = ref(false)
const submitting = ref(false)

function truncate(text, maxLen) {
  if (!text) return ''
  return text.length > maxLen ? text.slice(0, maxLen) + '…' : text
}

function updatePosition() {
  const selection = window.getSelection()
  if (!selection || selection.isCollapsed) return
  const range = selection.getRangeAt(0)
  const rect = range.getBoundingClientRect()
  popupStyle.value = {
    position: 'fixed',
    left: `${Math.min(rect.left + rect.width / 2, window.innerWidth - 180)}px`,
    top: `${rect.bottom + 8}px`,
    transform: 'translateX(-50%)',
    zIndex: 9999
  }
}

function handleSubmit() {
  if (!content.value.trim()) return

  // 检查登录
  if (!auth.isLoggedIn) {
    showLoginDialog.value = true
    return
  }

  submitting.value = true
  emit('submit', content.value.trim())
  content.value = ''
  submitting.value = false
}

function goToLogin() {
  showLoginDialog.value = false
  router.push({ name: 'Login', query: { redirect: window.location.pathname } })
}

function handleCancel() {
  emit('cancel')
}

function handleClickOutside(e) {
  if (popupRef.value && !popupRef.value.contains(e.target)) {
    handleCancel()
  }
}

onMounted(async () => {
  updatePosition()
  document.addEventListener('mousedown', handleClickOutside, true)
  await nextTick()
  inputRef.value?.focus()
})

onUnmounted(() => {
  document.removeEventListener('mousedown', handleClickOutside, true)
})
</script>

<style scoped>
.annotation-popup {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(0, 0, 0, 0.08);
  padding: 12px;
  width: 320px;
  max-width: calc(100vw - 24px);
}

.popup-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.popup-quote {
  font-size: 11px;
  font-weight: 400;
  color: #909399;
  margin-left: auto;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-style: italic;
}

.popup-input { margin-bottom: 8px; }

.popup-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 登录提示 */
.popup-overlay {
  position: fixed; inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex; align-items: center; justify-content: center;
  z-index: 10000;
}
.login-dialog {
  background: #fff; border-radius: 12px; padding: 32px;
  width: 360px; max-width: calc(100vw - 48px);
  text-align: center; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.login-dialog-icon { font-size: 40px; margin-bottom: 12px; }
.login-dialog h4 { margin: 0 0 8px; font-size: 18px; font-weight: 600; color: #303133; }
.login-dialog p { margin: 0 0 20px; font-size: 14px; color: #909399; }
.login-dialog-actions { display: flex; justify-content: center; gap: 12px; }
</style>
