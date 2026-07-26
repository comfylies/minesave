<template>
  <aside class="annotation-panel">
    <div class="annotation-header">
      <h3 class="annotation-title">批注栏</h3>
    </div>

    <div class="annotation-body" ref="bodyRef" v-loading="loading">
      <div v-if="!loading && allComments.length === 0" class="empty-hint">
        选中 README 中任意文字即可添加批注
      </div>

      <div
        v-else-if="showPositioned"
        ref="layerRef"
        class="positioned-layer"
        :class="{ 'positioned-layer--focused': focusedGroup }"
      >
        <section v-if="focusedGroup" class="focused-group" :style="{ top: focusedGroup.top + 'px' }">
          <template v-if="focusedComment">
            <div class="focused-group-header">
              <el-button text size="small" @click.stop="closeFocusedComment">返回本行批注</el-button>
              <el-button text size="small" @click.stop="closeFocusedGroup">关闭</el-button>
            </div>

            <article
              class="comment-card focused-comment"
              :class="`comment-card--role-${getColorRole(focusedComment)}`"
            >
              <div class="card-expanded">
                <div class="card-expanded-header">
                  <el-avatar :size="20" icon="UserFilled" />
                  <span class="card-author">{{ focusedComment.nickname || '用户' }}</span>
                  <span class="card-role-tag" :class="`role-tag--${getColorRole(focusedComment)}`">
                    {{ roleLabel(focusedComment) }}
                  </span>
                  <span class="card-time">{{ formatTime(focusedComment.createdAt) }}</span>
                </div>
                <div class="card-quote">"{{ focusedComment.selectedText }}"</div>
                <div class="card-content focused-comment-content">{{ focusedComment.content }}</div>

                <div v-if="focusedComment.children?.length" class="card-replies">
                  <div v-for="child in focusedComment.children" :key="child.id" class="reply-item">
                    <span class="reply-author">{{ child.nickname || '用户' }}</span>
                    <span class="reply-role-tag" :class="`role-tag--${getColorRole(child)}`">
                      {{ roleLabel(child) }}
                    </span>：
                    <span class="reply-content">{{ child.content }}</span>
                    <span class="reply-time">{{ formatTime(child.createdAt) }}</span>
                  </div>
                </div>

                <div v-if="canDelete(focusedComment)" class="card-actions">
                  <el-button text size="small" type="danger" @click.stop="handleDelete(focusedComment)">
                    <el-icon><Delete /></el-icon> 删除
                  </el-button>
                </div>
              </div>
            </article>
          </template>

          <template v-else>
            <div class="focused-group-header">
              <span>本行 {{ focusedGroup.commentIds.length }} 条批注</span>
              <el-button text size="small" @click.stop="closeFocusedGroup">关闭</el-button>
            </div>
            <button
              v-for="comment in focusedGroup.comments"
              :key="comment.id"
              type="button"
              class="annotation-detail-summary"
              :class="`annotation-detail-summary--role-${getColorRole(comment)}`"
              @click="openComment(comment)"
            >
              <span>{{ (comment.selectedText || '').trim().slice(0, 2) || '批注' }}</span>
              <span>{{ truncate(comment.content, 52) }}</span>
            </button>
          </template>
        </section>

        <button
          v-else
          v-for="group in annotationGroups"
          :key="group.id"
          type="button"
          class="annotation-summary-card"
          :class="[
            `annotation-summary-card--role-${getGroupColorRole(group)}`,
            { 'annotation-summary-card--active': group.commentIds.includes(Number(activeCommentId)) }
          ]"
          :style="{ top: group.top + 'px' }"
          @click="openGroup(group)"
        >
          <span class="annotation-summary-content">{{ truncate(group.comments[0].content, 42) }}</span>
          <span class="annotation-count-badge">{{ group.commentIds.length }}</span>
        </button>
      </div>

      <!-- 降级列表模式 -->
      <div v-else class="comment-list">
        <div
          v-for="comment in sortedComments"
          :key="comment.id"
          class="comment-card"
          :class="[
            `comment-card--role-${getColorRole(comment)}`,
            {
              'comment-card--active': activeCommentId === comment.id,
              'comment-card--expanded': expandedIds.has(comment.id)
            }
          ]"
          @click="handleClick(comment, comment.id)"
        >
          <div v-if="!expandedIds.has(comment.id)" class="card-collapsed">
            <div class="card-quote">"{{ truncate(comment.selectedText, 60) }}"</div>
            <div class="card-content">{{ truncate(comment.content, 100) }}</div>
          </div>
          <div v-else class="card-expanded">
            <div class="card-expanded-header">
              <el-avatar :size="20" icon="UserFilled" />
              <span class="card-author">{{ comment.nickname || '用户' }}</span>
              <span class="card-role-tag" :class="`role-tag--${getColorRole(comment)}`">
                {{ roleLabel(comment) }}
              </span>
              <span class="card-time">{{ formatTime(comment.createdAt) }}</span>
            </div>
            <div class="card-quote">"{{ comment.selectedText }}"</div>
            <div class="card-content">{{ comment.content }}</div>

            <div v-if="comment.children?.length" class="card-replies">
              <div v-for="child in comment.children" :key="child.id" class="reply-item">
                <span class="reply-author">{{ child.nickname || '用户' }}</span>：
                <span class="reply-content">{{ child.content }}</span>
                <span class="reply-time">{{ formatTime(child.createdAt) }}</span>
              </div>
            </div>

            <div v-if="canDelete(comment)" class="card-actions">
              <el-button text size="small" type="danger" @click.stop="handleDelete(comment)">
                <el-icon><Delete /></el-icon> 删除
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { commentApi } from '../../api/commentApi'
import { useAuthStore } from '../../stores/auth'
import { useArticleStore } from '../../stores/articles'
import { formatTime, truncate } from '@/utils/format'

const props = defineProps({
  articleId: { type: Number, required: true },
  activeCommentId: { type: [Number, String], default: null },
  marginPositions: { type: Array, default: () => [] },
  readmeContentTop: { type: Number, default: 0 }
})

const emit = defineEmits(['select-comment', 'delete-comment'])

const auth = useAuthStore()
const articleStore = useArticleStore()
const comments = ref([])
const loading = ref(false)
const expandedIds = ref(new Set())
const focusedGroupId = ref(null)
const focusedCommentId = ref(null)
const bodyRef = ref(null)
const layerRef = ref(null)
const bodyOffsetTop = ref(0)

const allComments = computed(() => comments.value)

// 角色颜色优先级：admin > 上传者 > 普通用户
function getColorRole(comment) {
  const role = comment.role || 'user'
  const commentUserId = comment.userId
  const articleUserId = articleStore.currentArticle?.userId

  if (role === 'admin') return 'admin'
  if (commentUserId === articleUserId) return 'uploader'
  return 'user'
}

function roleLabel(comment) {
  const r = getColorRole(comment)
  if (r === 'admin') return '管理员'
  if (r === 'uploader') return '上传者'
  return ''
}

function getGroupColorRole(group) {
  const priority = { user: 1, uploader: 2, admin: 3 }
  let highestRole = 'user'
  for (const comment of group.comments) {
    const role = getColorRole(comment)
    if (priority[role] > priority[highestRole]) highestRole = role
  }
  return highestRole
}

// bodyOffsetTop > 0 在页面滚动后会变成负数（元素在viewport上方），
// 导致 showPositioned 变为 false，positioned-layer 被销毁，卡片退回列表模式。
// 改用独立标记：只要执行过一次 measureBodyOffset 就认为坐标系已就绪。
const coordinateReady = ref(false)

const showPositioned = computed(() => {
  return props.marginPositions && props.marginPositions.length > 0 && coordinateReady.value
})

const annotationGroups = computed(() => {
  const bodyTop = bodyOffsetTop.value
  const contentTop = props.readmeContentTop
  const offsetAdjust = bodyTop - contentTop

  const commentMap = new Map()
  for (const c of comments.value) {
    commentMap.set(c.id, c)
  }

  const result = props.marginPositions
    .map(position => ({
      id: position.id,
      commentIds: position.commentIds || [],
      top: Math.round(position.top - offsetAdjust),
      comments: (position.commentIds || [])
        .map(commentId => commentMap.get(commentId))
        .filter(Boolean)
    }))
    .filter(group => group.comments.length > 0)
    .map(group => ({
      ...group,
      commentIds: group.commentIds
    }))
    .sort((a, b) => a.top - b.top)

  return showPositioned.value ? result : []
})

const focusedGroup = computed(() =>
  annotationGroups.value.find(group => group.id === focusedGroupId.value) || null
)

const focusedComment = computed(() =>
  focusedGroup.value?.comments.find(comment => String(comment.id) === String(focusedCommentId.value)) || null
)

function openGroup(group) {
  focusedGroupId.value = group.id
  if (group.comments.length === 1) {
    openComment(group.comments[0])
  } else {
    focusedCommentId.value = null
  }
}

function closeFocusedGroup() {
  focusedGroupId.value = null
  focusedCommentId.value = null
}

function openComment(comment) {
  focusedCommentId.value = comment.id
  emit('select-comment', comment.id)
}

function closeFocusedComment() {
  focusedCommentId.value = null
}

function measureBodyOffset() {
  if (layerRef.value) {
    bodyOffsetTop.value = layerRef.value.getBoundingClientRect().top
  } else if (bodyRef.value) {
    const bodyRect = bodyRef.value.getBoundingClientRect()
    const bodyStyle = window.getComputedStyle(bodyRef.value)
    const padTop = parseFloat(bodyStyle.paddingTop) || 0
    bodyOffsetTop.value = bodyRect.top + padTop
  }
  coordinateReady.value = true
}

function canDelete(comment) {
  if (!auth.currentUser) return false
  return auth.isAdmin || auth.userId === comment.userId
}

function toggleExpand(commentId) {
  if (expandedIds.value.has(commentId)) {
    expandedIds.value.delete(commentId)
  } else {
    expandedIds.value.add(commentId)
  }
  expandedIds.value = new Set(expandedIds.value)
}

function handleClick(comment, commentId) {
  toggleExpand(commentId)
  emit('select-comment', commentId || comment.id)
}

async function handleDelete(comment) {
  try {
    await ElMessageBox.confirm('确定要删除这条批注吗？', '确认删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
    await commentApi.delete(comment.id, auth.userId)
    comments.value = comments.value.filter(c => c.id !== comment.id)
    emit('delete-comment', comment.id)
    ElMessage.success('批注已删除')
  } catch { /* cancelled */ }
}

async function fetchComments() {
  if (!props.articleId) return
  loading.value = true
  try {
    const result = await commentApi.getByArticle(props.articleId)
    comments.value = result || []
  } catch {
    comments.value = []
  } finally {
    loading.value = false
  }
}

const sortedComments = computed(() => {
  return [...comments.value].sort((a, b) => {
    const aPos = a.quoteStart ?? Number.MAX_SAFE_INTEGER
    const bPos = b.quoteStart ?? Number.MAX_SAFE_INTEGER
    return aPos - bPos
  })
})

function addCommentToList(comment) {
  comments.value.unshift(comment)
  if (comment?.id) {
    expandedIds.value.add(comment.id)
    expandedIds.value = new Set(expandedIds.value)
  }
}

function removeCommentFromList(commentId) {
  comments.value = comments.value.filter(c => c.id !== commentId)
  expandedIds.value.delete(commentId)
  expandedIds.value = new Set(expandedIds.value)
}

function onResize() {
  nextTick(() => measureBodyOffset())
}

defineExpose({ fetchComments, addCommentToList, removeCommentFromList })

watch(() => props.marginPositions, () => {
  // 同步测量，确保 adjustedPositions 在本次渲染中使用与 readmeContentTop
  // 同一时刻的坐标快照。避免 scroll 导致 bodyOffsetTop 与 readmeContentTop 不同步。
  measureBodyOffset()
})
watch(() => props.readmeContentTop, () => nextTick(() => measureBodyOffset()))
watch(() => props.articleId, () => { if (props.articleId) fetchComments() })
watch([() => props.activeCommentId, annotationGroups], ([newId]) => {
  if (!newId) return
  const group = annotationGroups.value.find(candidate =>
    candidate.commentIds.some(commentId => String(commentId) === String(newId))
  )
  if (group) {
    const isCurrentFocusedDetail = focusedGroupId.value === group.id &&
      String(focusedCommentId.value) === String(newId)
    focusedGroupId.value = group.id
    if (!isCurrentFocusedDetail) focusedCommentId.value = null
  } else {
    expandedIds.value.add(newId)
    expandedIds.value = new Set(expandedIds.value)
  }
})

onMounted(() => {
  if (props.articleId) fetchComments()
  nextTick(() => measureBodyOffset())
})

if (typeof window !== 'undefined') {
  window.addEventListener('resize', onResize, { passive: true })
}

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', onResize)
  }
})
</script>

<style scoped>
.annotation-panel {
  display: flex;
  flex-direction: column;
}

.annotation-header {
  padding: 0 0 8px;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.annotation-title { font-size: 14px; font-weight: 600; margin: 0; }

.annotation-body { flex: 1; position: relative; }
.empty-hint { text-align: center; padding: 24px 12px; font-size: 13px; color: var(--color-secondary-text); }

.positioned-layer { position: relative; width: 100%; min-height: 0; }
.focused-group {
  position: absolute;
  left: 0;
  right: 0;
  max-height: min(70vh, 640px);
  overflow-y: auto;
  padding-right: 2px;
}
.focused-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--color-secondary-text);
}
.comment-list { display: flex; flex-direction: column; gap: 6px; }

.annotation-summary-card {
  --annotation-role-color: #27ae60;
  --annotation-role-tint: #e8f7ee;
  --annotation-role-text: #16794a;
  position: absolute;
  left: 4px;
  right: 4px;
  width: calc(100% - 8px);
  min-height: 32px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
  padding: 4px 7px;
  border: 1px solid var(--color-border-primary);
  border-left: 3px solid var(--annotation-role-color);
  border-radius: 6px;
  background: #fff;
  color: var(--color-body-text);
  cursor: pointer;
  text-align: left;
  font: inherit;
  transition: border-color .15s, box-shadow .15s;
}
.annotation-summary-card:hover,
.annotation-summary-card--active {
  border-color: var(--color-link);
  box-shadow: 0 1px 5px rgba(0, 0, 0, .09);
}
.annotation-summary-content {
  overflow: hidden;
  color: #5d6879;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.annotation-count-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--annotation-role-tint);
  color: var(--annotation-role-text);
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
}
.annotation-detail-summary {
  --annotation-role-color: #27ae60;
  --annotation-role-text: #16794a;
  width: 100%;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 6px;
  align-items: center;
  min-height: 32px;
  margin-bottom: 6px;
  padding: 5px 7px;
  border: 1px solid var(--color-border-primary);
  border-left: 3px solid var(--annotation-role-color);
  border-radius: 6px;
  background: #fff;
  color: var(--color-body-text);
  cursor: pointer;
  font: inherit;
  text-align: left;
}
.annotation-detail-summary:hover { border-color: var(--color-link); }
.annotation-detail-summary span {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.annotation-detail-summary span:first-child { color: var(--annotation-role-text); font-weight: 600; }
.annotation-detail-summary span:last-child { color: #5d6879; }
.annotation-summary-card--role-admin,
.annotation-detail-summary--role-admin {
  --annotation-role-color: #e74c3c;
  --annotation-role-tint: #fde8e8;
  --annotation-role-text: #c0392b;
}
.annotation-summary-card--role-uploader,
.annotation-detail-summary--role-uploader {
  --annotation-role-color: #3498db;
  --annotation-role-tint: #e3f2fd;
  --annotation-role-text: #1565c0;
}

/* ======== 卡片 ======== */
.comment-card {
  border: 1px solid transparent;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
  overflow: hidden;
}

/* 左侧彩色条 */
.comment-card::before {
  content: '';
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 3px;
  border-radius: 3px 0 0 3px;
}

/* --- 角色颜色 --- */
.comment-card--role-admin { border-left: 3px solid #e74c3c; }
.comment-card--role-admin::before { background: #e74c3c; }

.comment-card--role-uploader { border-left: 3px solid #3498db; }
.comment-card--role-uploader::before { background: #3498db; }

.comment-card--role-user { border-left: 3px solid #27ae60; }
.comment-card--role-user::before { background: #27ae60; }

.comment-card:hover { box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.comment-card--active { box-shadow: 0 0 0 1px var(--color-link); }
.comment-card--expanded { box-shadow: 0 2px 10px rgba(0,0,0,.1); z-index: 10; }

/* 定位卡片 */
.comment-card--positioned {
  position: absolute;
  left: 4px;
  right: 4px;
  z-index: 1;
}

.comment-card--positioned:hover { z-index: 10; }
.focused-comment { position: relative; margin-bottom: 8px; cursor: default; }

/* ======== 折叠视图（内容优先） ======== */
.card-collapsed { padding: 8px 10px; }

.card-quote {
  font-size: 11px;
  color: #909399;
  font-style: italic;
  padding: 2px 8px;
  margin-bottom: 4px;
  border-left: 2px solid #e6a23c;
  background: #fdf6ec;
  border-radius: 0 3px 3px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.card-content {
  font-size: 13px;
  color: #303133;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
.focused-group .card-quote {
  overflow: visible;
  text-overflow: clip;
  white-space: normal;
}
.focused-comment-content {
  display: block;
  overflow: visible;
  -webkit-line-clamp: unset;
}

/* ======== 展开视图 ======== */
.card-expanded { padding: 8px 10px; }

.card-expanded-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.card-author { font-size: 12px; font-weight: 600; color: #303133; max-width: 80px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.card-role-tag {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: 500;
  flex-shrink: 0;
}

.role-tag--admin { background: #fde8e8; color: #c0392b; }
.role-tag--uploader { background: #e3f2fd; color: #1565c0; }
.role-tag--user { display: none; }

.reply-role-tag {
  font-size: 10px;
  padding: 0 4px;
  border-radius: 2px;
}

.card-time { font-size: 11px; color: #909399; margin-left: auto; }

.card-replies { margin-top: 6px; padding-top: 6px; border-top: 1px solid #f2f3f5; }
.reply-item { padding: 3px 0; font-size: 12px; color: #606266; line-height: 1.5; }
.reply-author { font-weight: 600; color: #303133; max-width: 80px; display: inline-block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; vertical-align: bottom; }
.reply-time { font-size: 11px; color: #c0c4cc; margin-left: 6px; white-space: nowrap; }
.reply-content { word-break: break-word; }

.card-actions { margin-top: 6px; display: flex; justify-content: flex-end; }

/* ======== 响应式 ======== */
@media (max-width: 900px) {
  .comment-card--positioned { position: static; margin-bottom: 6px; }
  .positioned-layer { min-height: auto !important; }
  .focused-group { position: static; max-height: min(70vh, 640px); }
  .annotation-summary-card { position: static; margin-bottom: 6px; }
}
</style>
