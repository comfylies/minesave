<template>
  <div class="admin-contact-messages">
    <div class="admin-page-header">
      <h2 class="admin-page-title">联系留言</h2>
    </div>

    <!-- 状态筛选 -->
    <el-card shadow="hover" class="filter-card">
      <el-radio-group v-model="statusFilter" @change="fetchMessages">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="pending">待处理</el-radio-button>
        <el-radio-button value="resolved">已解决</el-radio-button>
        <el-radio-button value="closed">已关闭</el-radio-button>
      </el-radio-group>
    </el-card>

    <!-- 留言列表 -->
    <el-card shadow="hover">
      <el-table :data="messages" stripe v-loading="loading" empty-text="暂无留言">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="类别" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="categoryTagType(row.category)" size="small">
              {{ categoryLabel(row.category) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" width="120" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />
        <el-table-column prop="subject" label="主题" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">
              <el-icon><View /></el-icon>
              详情
            </el-button>
            <el-button
              v-if="row.status === 'pending'"
              link
              type="success"
              size="small"
              @click="handleResolve(row.id)"
            >
              <el-icon><Check /></el-icon>
              解决
            </el-button>
            <el-button
              v-if="row.status !== 'closed'"
              link
              type="warning"
              size="small"
              @click="handleClose(row.id)"
            >
              <el-icon><CircleClose /></el-icon>
              关闭
            </el-button>
            <el-popconfirm
              title="确定删除此留言？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="handleDelete(row.id)"
            >
              <template #reference>
                <el-button link type="danger" size="small">
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @change="fetchMessages"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="留言详情"
      width="700px"
      :close-on-click-modal="false"
    >
      <template v-if="currentMessage">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="ID">{{ currentMessage.id }}</el-descriptions-item>
          <el-descriptions-item label="类别">
            <el-tag :type="categoryTagType(currentMessage.category)" size="small">
              {{ categoryLabel(currentMessage.category) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="姓名">{{ currentMessage.name }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ currentMessage.email }}</el-descriptions-item>
          <el-descriptions-item label="主题" :span="2">{{ currentMessage.subject }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(currentMessage.status)" size="small">
              {{ statusLabel(currentMessage.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatTime(currentMessage.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="IP 地址" :span="2">{{ currentMessage.ipAddress || '未知' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <h4 style="margin-bottom: var(--spacing-sm)">留言内容</h4>
        <div class="detail-message markdown-body" v-html="renderedDetailMd"></div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { View, Check, CircleClose, Delete } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { adminApi } from '../../api/adminApi'
import { useContactBadge } from '../../composables/useContactBadge'
import { formatTime } from '../../utils/format'

const { decrement } = useContactBadge()

const loading = ref(false)
const messages = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const statusFilter = ref('pending')

const detailVisible = ref(false)
const currentMessage = ref(null)

const renderedDetailMd = computed(() => {
  if (!currentMessage.value?.message) return '<p style="color: #999">暂无内容</p>'
  try {
    return marked.parse(currentMessage.value.message)
  } catch {
    return '<p style="color: #ef4444">Markdown 渲染失败</p>'
  }
})

function categoryLabel(cat) {
  const map = { suggestion: '功能建议', bug: '问题反馈', business: '商务合作', other: '其他' }
  return map[cat] || cat
}

function categoryTagType(cat) {
  const map = { suggestion: '', bug: 'danger', business: 'warning', other: 'info' }
  return map[cat] || 'info'
}

function statusLabel(s) {
  const map = { pending: '待处理', resolved: '已解决', closed: '已关闭' }
  return map[s] || s
}

function statusTagType(s) {
  const map = { pending: 'warning', resolved: 'success', closed: 'info' }
  return map[s] || 'info'
}

async function fetchMessages() {
  loading.value = true
  try {
    const result = await adminApi.listContactMessages(
      currentPage.value - 1,
      pageSize.value,
      statusFilter.value
    )
    messages.value = result.content || []
    total.value = result.totalElements || 0
  } catch {
    // 拦截器处理
  } finally {
    loading.value = false
  }
}

async function handleResolve(id) {
  try {
    await adminApi.resolveContactMessage(id)
    ElMessage.success('已标记为已解决')
    decrement()
    fetchMessages()
  } catch {
    // 拦截器处理
  }
}

async function handleClose(id) {
  try {
    await adminApi.closeContactMessage(id)
    ElMessage.success('留言已关闭')
    decrement()
    fetchMessages()
  } catch {
    // 拦截器处理
  }
}

async function handleDelete(id) {
  try {
    await adminApi.deleteContactMessage(id)
    ElMessage.success('留言已删除')
    decrement()
    fetchMessages()
  } catch {
    // 拦截器处理
  }
}

function openDetail(row) {
  currentMessage.value = row
  detailVisible.value = true
}

onMounted(() => {
  fetchMessages()
})
</script>

<style scoped>
.admin-contact-messages {
  max-width: 1200px;
}

.filter-card {
  margin-bottom: var(--spacing-md);
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: var(--spacing-md);
}

.detail-message {
  padding: var(--spacing-md);
  background: var(--color-bg-secondary);
  border-radius: var(--radius-sm);
  max-height: 400px;
  overflow-y: auto;
}

.detail-message :deep(img) {
  max-width: 100%;
  border-radius: var(--radius-sm);
}
</style>
