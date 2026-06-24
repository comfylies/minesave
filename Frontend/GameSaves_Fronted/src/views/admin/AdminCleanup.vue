<template>
  <div class="admin-cleanup">
    <h2 class="page-title">失败存档清理</h2>
    <p class="page-desc">清理因网络中断、解压失败等原因产生的无效存档文件和数据库记录，释放磁盘空间。</p>

    <!-- 状态卡片 -->
    <el-row :gutter="20" style="margin-bottom: 24px">
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #f56c6c">
              <el-icon :size="24"><Delete /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ status.totalDeleted }}</div>
              <div class="stat-label">已删除总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409eff">
              <el-icon :size="24"><InfoFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ status.batchesCompleted }}</div>
              <div class="stat-label">已完成批次</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 操作区 -->
    <el-card shadow="hover" style="margin-bottom: 24px">
      <template #header>
        <span class="card-header-title">手动清理</span>
      </template>

      <el-form :inline="true">
        <el-form-item label="清理模式">
          <el-radio-group v-model="cleanupMode" :disabled="cleaning">
            <el-radio value="all">
              全部清理
              <el-tooltip content="清理所有 FAILED + 过期 UPLOADING 存档" placement="top">
                <el-icon style="margin-left: 4px; vertical-align: middle"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-radio>
            <el-radio value="failed-only">
              仅清理失败存档
              <el-tooltip content="仅清理 FAILED 存档，保留 UPLOADING" placement="top">
                <el-icon style="margin-left: 4px; vertical-align: middle"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button
            type="danger"
            :icon="Delete"
            :loading="cleaning"
            :disabled="cleaning"
            @click="triggerCleanup"
          >
            {{ cleaning ? '清理中...' : '一键清理' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 清理结果 -->
      <el-alert
        v-if="lastResult"
        :title="lastResult.message"
        :type="lastResult.success ? 'success' : 'error'"
        closable
        show-icon
        style="margin-top: 16px"
        @close="lastResult = null"
      />
    </el-card>

    <!-- 配置说明 -->
    <el-card shadow="hover">
      <template #header>
        <span class="card-header-title">当前配置</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="保留窗口">
          {{ cleanupConfig.retentionHours }} 小时
          <el-tooltip content="距上次更新时间超过此窗口的存档才会被清理" placement="top">
            <el-icon style="margin-left: 4px; vertical-align: middle"><InfoFilled /></el-icon>
          </el-tooltip>
        </el-descriptions-item>
        <el-descriptions-item label="每批数量">{{ cleanupConfig.batchSize }} 条</el-descriptions-item>
        <el-descriptions-item label="清理对象">
          <el-tag size="small" type="danger">FAILED</el-tag>
          <el-tag size="small" type="warning" style="margin-left: 4px">UPLOADING（过期）</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="安全保护">
          <el-tag size="small" type="success">READY 永不触碰</el-tag>
          <el-tag size="small" style="margin-left: 4px">EXTRACTING 永不触碰</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { adminApi } from '../../api/adminApi'
import { ElMessage } from 'element-plus'

const cleanupMode = ref('all')
const cleaning = ref(false)
const lastResult = ref(null)
const status = reactive({
  completed: true,
  batchesCompleted: 0,
  totalDeleted: 0,
  estimatedRemaining: 0,
  durationMs: 0
})

// 配置说明（与后端 application.yaml 保持一致）
const cleanupConfig = {
  retentionHours: 3,
  batchSize: 50
}

onMounted(() => {
  loadStatus()
})

async function loadStatus() {
  try {
    const result = await adminApi.getCleanupStatus()
    // axios interceptor already unwraps ApiResponse.data
    Object.assign(status, result)
  } catch {
    // ignore
  }
}

async function triggerCleanup() {
  if (cleaning.value) return

  cleaning.value = true
  lastResult.value = null

  try {
    // axios interceptor already unwraps ApiResponse.data
    const result = await adminApi.triggerCleanup(cleanupMode.value)
    lastResult.value = {
      success: true,
      message: `清理完成：删除 ${result.totalDeleted} 条记录，耗时 ${(result.durationMs / 1000).toFixed(1)} 秒`
    }
    ElMessage.success(lastResult.value.message)
    await loadStatus()
  } catch (err) {
    const msg = err?.response?.data?.message || err?.message || '清理失败'
    lastResult.value = { success: false, message: msg }
    ElMessage.error(msg)
  } finally {
    cleaning.value = false
  }
}
</script>

<style scoped>
.admin-cleanup {
  max-width: 900px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.page-desc {
  color: #909399;
  margin: 0 0 24px 0;
  font-size: 14px;
}

.card-header-title {
  font-weight: 600;
  color: #303133;
}

.stat-card {
  margin-bottom: 0;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 2px;
}
</style>
