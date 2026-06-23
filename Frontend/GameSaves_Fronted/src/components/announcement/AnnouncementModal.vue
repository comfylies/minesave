<template>
  <el-dialog
    v-model="visible"
    :title="currentAnnouncement ? '🔔 ' + currentAnnouncement.title : '🔔 站内公告'"
    :width="dialogWidth"
    :close-on-click-modal="false"
    draggable
    destroy-on-close
    class="announcement-dialog"
    @closed="onClosed"
  >
    <div
      v-if="currentAnnouncement"
      class="announcement-body markdown-body"
      v-html="currentAnnouncement.contentHtml"
    />

    <template #footer>
      <div class="dialog-footer">
        <div v-if="announcements.length > 1" class="page-info">
          {{ currentIndex + 1 }} / {{ announcements.length }}
        </div>
        <div v-if="announcements.length > 1" class="footer-actions">
          <el-button @click="prevAnnouncement">
            <el-icon><ArrowLeft /></el-icon>
            上一条
          </el-button>
          <el-button @click="nextAnnouncement">
            下一条
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <div class="footer-right">
          <el-button @click="justClose">关闭</el-button>
          <el-button type="primary" @click="dismissToday">今日不再提示</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { announcementApi } from '../../api/announcementApi'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'closed'])

const announcements = ref([])
const currentIndex = ref(0)
const loaded = ref(false)

const visible = computed({
  get: () => props.modelValue && announcements.value.length > 0,
  set: (val) => emit('update:modelValue', val)
})

const currentAnnouncement = computed(() => {
  if (announcements.value.length === 0) return null
  return announcements.value[currentIndex.value] || null
})

const dialogWidth = computed(() => {
  const content = currentAnnouncement.value?.contentHtml || ''
  // 根据内容长度自适应宽度
  if (content.length > 5000) return '70%'
  if (content.length > 2000) return '60%'
  return '580px'
})

const hasMultiple = computed(() => announcements.value.length > 1)

function prevAnnouncement() {
  if (currentIndex.value > 0) {
    currentIndex.value--
  } else {
    currentIndex.value = announcements.value.length - 1
  }
}

function nextAnnouncement() {
  if (currentIndex.value < announcements.value.length - 1) {
    currentIndex.value++
  } else {
    currentIndex.value = 0
  }
}

function getTodayKey() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function justClose() {
  emit('update:modelValue', false)
  emit('closed')
}

function dismissToday() {
  localStorage.setItem('announcement_dismissed_date', getTodayKey())
  emit('update:modelValue', false)
  emit('closed')
}

function onClosed() {
  // 弹窗关闭时不做额外处理
}

async function loadAnnouncements() {
  if (loaded.value) return
  try {
    const data = await announcementApi.getActive()
    announcements.value = data || []
    loaded.value = true
  } catch {
    announcements.value = []
    loaded.value = true
  }
}

watch(() => props.modelValue, (val) => {
  if (val && !loaded.value) {
    loadAnnouncements()
  }
})

onMounted(() => {
  if (props.modelValue) {
    loadAnnouncements()
  }
})
</script>

<style scoped>
.announcement-body {
  max-height: 65vh;
  overflow-y: auto;
  padding: 0 8px;
  line-height: 1.8;
  font-size: 15px;
  color: #303133;
}

/* 当内容较少时，移除底部多余留白 */
.announcement-body :deep(p:last-child) {
  margin-bottom: 0;
}

/* Markdown 渲染样式 */
.announcement-body :deep(h1), .announcement-body :deep(h2), .announcement-body :deep(h3) {
  margin-top: 20px;
  margin-bottom: 12px;
  font-weight: 600;
  color: #1a1a1a;
}
.announcement-body :deep(h1) { font-size: 1.6em; border-bottom: 2px solid #eee; padding-bottom: 8px; }
.announcement-body :deep(h2) { font-size: 1.35em; border-bottom: 1px solid #eee; padding-bottom: 6px; }
.announcement-body :deep(h3) { font-size: 1.15em; }

.announcement-body :deep(p) { margin: 10px 0; }

.announcement-body :deep(pre) {
  background: #f6f8fa;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  padding: 14px;
  overflow-x: auto;
  margin: 14px 0;
}
.announcement-body :deep(pre code) {
  background: none;
  border: none;
  padding: 0;
  font-size: 13px;
  line-height: 1.5;
  font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
}
.announcement-body :deep(code) {
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.9em;
}
.announcement-body :deep(ul), .announcement-body :deep(ol) {
  padding-left: 24px;
  margin: 10px 0;
}
.announcement-body :deep(blockquote) {
  border-left: 4px solid #409EFF;
  padding: 8px 16px;
  margin: 14px 0;
  color: #606266;
  background: #ecf5ff;
  border-radius: 0 4px 4px 0;
}
.announcement-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 14px 0;
}
.announcement-body :deep(th), .announcement-body :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 8px 12px;
  text-align: left;
}
.announcement-body :deep(th) { background: #f5f7fa; font-weight: 600; }
.announcement-body :deep(a) { color: #409EFF; }
.announcement-body :deep(hr) { border: none; border-top: 1px solid #eee; margin: 20px 0; }
.announcement-body :deep(img) { max-width: 100%; border-radius: 4px; }

/* Footer */
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.page-info {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}

.footer-actions {
  display: flex;
  gap: 8px;
}

.footer-right {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
</style>
