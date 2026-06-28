<template>
  <div class="admin-games">
    <div class="page-header">
      <h2 class="page-title">游戏管理</h2>
      <span class="page-subtitle">{{ games.length }} 个游戏</span>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-input
        v-model="searchQuery"
        placeholder="搜索游戏名..."
        clearable
        :prefix-icon="Search"
        style="width: 280px"
      />
      <el-checkbox v-model="onlyConflicts" border size="small">
        只看冲突
      </el-checkbox>
    </div>

    <!-- 游戏表格 -->
    <el-card shadow="never">
      <el-table :data="filteredGames" v-loading="loading" stripe>
        <!-- 封面 -->
        <el-table-column label="封面" width="120" align="center">
          <template #default="{ row }">
            <div class="cover-cell">
              <img
                v-if="row.thumbnailUrl"
                :src="row.thumbnailUrl"
                class="cover-thumb"
                @error="onCoverError($event)"
                alt=""
              />
              <div v-else class="cover-placeholder">
                <el-icon :size="32"><PictureFilled /></el-icon>
              </div>
            </div>
          </template>
        </el-table-column>

        <!-- 游戏名 -->
        <el-table-column label="游戏名" min-width="180">
          <template #default="{ row }">
            <router-link :to="`/games/${row.id}`" class="game-name-link">
              {{ row.name }}
            </router-link>
          </template>
        </el-table-column>

        <!-- 存档数 -->
        <el-table-column label="存档" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.articleCount > 0 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.articleCount }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 别名数 -->
        <el-table-column label="别名" width="80" align="center">
          <template #default="{ row }">
            <span :class="{ 'alias-count': row.aliasCount > 0 }">
              {{ row.aliasCount }}
            </span>
          </template>
        </el-table-column>

        <!-- 冲突 -->
        <el-table-column label="冲突" width="100" align="center">
          <template #default="{ row }">
            <el-popover
              v-if="row.hasConflict"
              placement="top"
              :width="240"
              trigger="hover"
            >
              <template #reference>
                <el-tag type="danger" size="small" effect="dark">
                  ⚠ {{ row.conflictGameNames?.length || 0 }}
                </el-tag>
              </template>
              <div class="conflict-popover">
                <p class="conflict-hint">别名冲突（出现在多个游戏中）：</p>
                <el-tag
                  v-for="name in row.conflictGameNames"
                  :key="name"
                  size="small"
                  type="danger"
                  effect="plain"
                  style="margin: 2px"
                >
                  {{ name }}
                </el-tag>
              </div>
            </el-popover>
            <span v-else class="no-conflict">-</span>
          </template>
        </el-table-column>

        <!-- 操作 -->
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              @click="openMergeDialog(row)"
            >
              合并
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 合并弹窗 -->
    <el-dialog
      v-model="mergeDialogVisible"
      title="合并游戏"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="mergeSource" class="merge-dialog">
        <p class="merge-desc">
          将 <strong>{{ mergeSource.name }}</strong> 的所有存档和别名迁移到目标游戏，合并后<strong>{{ mergeSource.name }}</strong>将被删除。
        </p>

        <div class="merge-cards">
          <!-- 源游戏（将被删除） -->
          <div class="merge-card source-card">
            <div class="merge-card-badge">源（将被删除）</div>
            <div class="merge-card-cover">
              <img
                v-if="mergeSource.thumbnailUrl"
                :src="mergeSource.thumbnailUrl"
                class="merge-thumb"
                @error="onCoverError($event)"
                alt=""
              />
              <div v-else class="cover-placeholder small">
                <el-icon :size="24"><PictureFilled /></el-icon>
              </div>
            </div>
            <div class="merge-card-name">{{ mergeSource.name }}</div>
            <div class="merge-card-meta">
              <span>{{ mergeSource.articleCount }} 个存档</span>
              <span v-if="mergeSource.aliasCount > 0">{{ mergeSource.aliasCount }} 个别名</span>
            </div>
          </div>

          <!-- 箭头 -->
          <div class="merge-arrow">
            <el-icon :size="32"><ArrowRightBold /></el-icon>
          </div>

          <!-- 目标游戏（保留） -->
          <div class="merge-card target-card">
            <div class="merge-card-badge target">目标（保留）</div>
            <div class="merge-card-cover">
              <img
                v-if="mergeTarget && mergeTarget.thumbnailUrl"
                :src="mergeTarget.thumbnailUrl"
                class="merge-thumb"
                @error="onCoverError($event)"
                alt=""
              />
              <div v-else class="cover-placeholder small">
                <el-icon :size="24"><PictureFilled /></el-icon>
              </div>
            </div>
            <div class="merge-card-select">
              <el-select
                v-model="selectedTargetId"
                placeholder="选择目标游戏"
                filterable
                :filter-method="filterTargetGames"
                style="width: 100%"
              >
                <el-option
                  v-for="g in targetOptions"
                  :key="g.id"
                  :label="`${g.name} (${g.articleCount} 个存档)`"
                  :value="g.id"
                >
                  <span>{{ g.name }}</span>
                  <span class="target-option-meta">{{ g.articleCount }} 个存档</span>
                </el-option>
              </el-select>
            </div>
          </div>
        </div>

        <!-- 合并结果摘要 -->
        <div v-if="mergeTarget" class="merge-summary">
          <p><strong>合并后：</strong></p>
          <ul>
            <li>「{{ mergeSource.name }}」→ 「{{ mergeTarget.name }}」的别名</li>
            <li>{{ mergeSource.articleCount }} 篇文章迁移到「{{ mergeTarget.name }}」</li>
            <li v-if="mergeSource.aliasCount > 0">{{ mergeSource.aliasCount }} 个别名一并迁移</li>
            <li>「{{ mergeSource.name }}」游戏记录将被删除</li>
          </ul>
        </div>
      </div>

      <template #footer>
        <el-button @click="mergeDialogVisible = false">取消</el-button>
        <el-button
          type="danger"
          :loading="merging"
          :disabled="!selectedTargetId || selectedTargetId === mergeSource?.id"
          @click="confirmMerge"
        >
          确认合并
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, PictureFilled, ArrowRightBold } from '@element-plus/icons-vue'
import { adminApi } from '../../api/adminApi'

const games = ref([])
const loading = ref(false)
const searchQuery = ref('')
const onlyConflicts = ref(false)

// ── 合并弹窗状态 ──
const mergeDialogVisible = ref(false)
const mergeSource = ref(null)
const selectedTargetId = ref(null)
const merging = ref(false)
const allTargetGames = ref([])

// ── 计算属性 ──
const filteredGames = computed(() => {
  let list = games.value

  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    list = list.filter(g => g.name.toLowerCase().includes(q))
  }

  if (onlyConflicts.value) {
    list = list.filter(g => g.hasConflict)
  }

  return list
})

const targetOptions = computed(() => {
  return allTargetGames.value.filter(g => g.id !== mergeSource.value?.id)
})

const mergeTarget = computed(() => {
  if (!selectedTargetId.value) return null
  return allTargetGames.value.find(g => g.id === selectedTargetId.value) || null
})

// ── 数据加载 ──
onMounted(async () => {
  await loadGames()
})

async function loadGames() {
  loading.value = true
  try {
    const data = await adminApi.getGames()
    games.value = Array.isArray(data) ? data : []
  } catch {
    ElMessage.error('加载游戏列表失败')
  } finally {
    loading.value = false
  }
}

// ── 封面加载失败 ──
function onCoverError(e) {
  e.target.style.display = 'none'
}

// ── 合并弹窗 ──
function openMergeDialog(game) {
  mergeSource.value = game
  allTargetGames.value = [...games.value]
  // 默认选择存档数最多的游戏（排除自己）
  const candidates = allTargetGames.value
    .filter(g => g.id !== game.id)
    .sort((a, b) => b.articleCount - a.articleCount)
  selectedTargetId.value = candidates.length > 0 ? candidates[0].id : null
  mergeDialogVisible.value = true
}

function filterTargetGames(query) {
  // el-select 的 filter-method，前端过滤即可（数据量小）
  allTargetGames.value = games.value.filter(g =>
    g.id !== mergeSource.value?.id &&
    g.name.toLowerCase().includes(query.toLowerCase())
  )
}

async function confirmMerge() {
  if (!selectedTargetId.value || selectedTargetId.value === mergeSource.value?.id) {
    ElMessage.warning('请选择与源不同的目标游戏')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定将「${mergeSource.value.name}」合并到「${mergeTarget.value?.name}」吗？此操作不可撤销。`,
      '确认合并',
      {
        confirmButtonText: '确认合并',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return // 用户取消
  }

  merging.value = true
  try {
    const result = await adminApi.mergeGames(mergeSource.value.id, selectedTargetId.value)
    ElMessage.success(result.message || `已合并：${result.articlesMoved} 篇文章已迁移`)
    mergeDialogVisible.value = false
    await loadGames()
  } catch {
    // Axios interceptor already shows error
  } finally {
    merging.value = false
  }
}
</script>

<style scoped>
.admin-games {
  max-width: 1100px;
}

.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.page-subtitle {
  font-size: 13px;
  color: #909399;
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

/* 封面缩略图 */
.cover-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 60px;
}

.cover-thumb {
  max-height: 56px;
  max-width: 100px;
  border-radius: 4px;
  object-fit: cover;
}

.cover-placeholder {
  width: 100px;
  height: 56px;
  background: #f5f7fa;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}

.cover-placeholder.small {
  width: 80px;
  height: 48px;
}

/* 游戏名 */
.game-name-link {
  font-weight: 600;
  color: #409eff;
  text-decoration: none;
}

.game-name-link:hover {
  text-decoration: underline;
}

/* 别名数 */
.alias-count {
  font-weight: 500;
  color: #606266;
}

/* 冲突 */
.no-conflict {
  color: #c0c4cc;
}

.conflict-popover {
  font-size: 13px;
}

.conflict-hint {
  margin: 0 0 8px;
  color: #606266;
}

/* ── 合并弹窗 ── */
.merge-dialog {
  font-size: 14px;
}

.merge-desc {
  margin: 0 0 20px;
  color: #606266;
  line-height: 1.6;
}

.merge-cards {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  margin-bottom: 20px;
}

.merge-card {
  width: 240px;
  min-height: 290px;
  border: 2px solid #e4e7ed;
  border-radius: 10px;
  padding: 20px 16px 16px;
  text-align: center;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.merge-card.source-card {
  border-color: #f56c6c;
  background: #fef0f0;
}

.merge-card.target-card {
  border-color: #67c23a;
  background: #f0f9eb;
}

.merge-card-badge {
  position: absolute;
  top: -13px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  padding: 3px 14px;
  border-radius: 12px;
  background: #f56c6c;
  color: #fff;
  white-space: nowrap;
}

.merge-card-badge.target {
  background: #67c23a;
}

.merge-card-cover {
  width: 200px;
  height: 140px;
  margin: 10px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f7fa;
}

.merge-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 6px;
}

.merge-card-name {
  font-weight: 600;
  font-size: 16px;
  margin: 10px 0 6px;
  color: #303133;
  line-height: 1.3;
  word-break: break-word;
}

.merge-card-meta {
  font-size: 13px;
  color: #909399;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.merge-arrow {
  color: #c0c4cc;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.merge-card-select {
  margin-top: 12px;
  width: 100%;
}

.target-option-meta {
  float: right;
  color: #909399;
  font-size: 12px;
}

.merge-summary {
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
}

.merge-summary p {
  margin: 0 0 6px;
}

.merge-summary ul {
  margin: 0;
  padding-left: 18px;
}

.merge-summary li {
  margin-bottom: 4px;
}
</style>
