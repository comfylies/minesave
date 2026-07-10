<template>
  <div class="admin-announcements">
    <div class="admin-page-header">
      <h2 class="admin-page-title">公告管理</h2>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        新建公告
      </el-button>
    </div>

    <!-- 公告列表 -->
    <el-card shadow="hover">
      <el-table :data="announcements" stripe v-loading="loading" empty-text="暂无公告">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="authorName" label="发布者" width="120" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
              {{ row.isActive ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEditDialog(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button
              link
              :type="row.isActive ? 'warning' : 'success'"
              size="small"
              @click="handleToggle(row)"
            >
              <el-icon><Switch /></el-icon>
              {{ row.isActive ? '禁用' : '启用' }}
            </el-button>
            <el-popconfirm
              title="确定删除此公告？"
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
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑公告' : '新建公告'"
      width="700px"
      draggable
      destroy-on-close
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
        label-position="top"
      >
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="公告标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="内容 (Markdown)" prop="contentRaw">
          <div class="admin-editor-wrapper">
            <div class="admin-editor-toolbar">
              <span class="admin-editor-hint">支持 Markdown 语法</span>
              <div class="admin-editor-actions">
                <input
                  ref="imageInput"
                  type="file"
                  accept="image/*"
                  style="display: none"
                  @change="handleImageUpload"
                />
                <input
                  ref="mdInput"
                  type="file"
                  accept=".md"
                  style="display: none"
                  @change="handleMdUpload"
                />
                <el-button size="small" @click="showPreview = !showPreview">
                  <el-icon><View /></el-icon>
                  {{ showPreview ? '编辑' : '预览' }}
                </el-button>
                <el-button size="small" @click="$refs.imageInput.click()" :loading="uploading">
                  <el-icon><Picture /></el-icon>
                  插入图片
                </el-button>
                <el-button size="small" @click="$refs.mdInput.click()" :loading="uploadingMd">
                  <el-icon><Upload /></el-icon>
                  上传 MD 文件
                </el-button>
              </div>
            </div>
            <div v-if="showPreview" class="admin-markdown-preview" v-html="renderedMarkdown"></div>
            <el-input
              v-else
              v-model="form.contentRaw"
              type="textarea"
              :rows="14"
              placeholder="支持 Markdown 语法：标题、列表、代码块、链接、图片等&#10;&#10;点击「插入图片」按钮上传图片到公告中"
            />
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.isActive"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEditing ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Edit, Delete, Switch, Picture, Upload, View } from '@element-plus/icons-vue'
import { announcementApi } from '../../api/announcementApi'
import { formatDateTime as formatTime } from '@/utils/format'
import { marked } from 'marked'

const announcements = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const uploading = ref(false)
const uploadingMd = ref(false)
const showPreview = ref(false)
const formRef = ref(null)
const imageInput = ref(null)
const mdInput = ref(null)

const renderedMarkdown = computed(() => {
  if (!form.value.contentRaw) return '<p style="color:#909399">暂无内容</p>'
  try {
    return marked.parse(form.value.contentRaw)
  } catch {
    return '<p style="color:#f56c6c">Markdown 渲染错误</p>'
  }
})

const form = ref({
  title: '',
  contentRaw: '',
  isActive: true
})

const rules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 200, message: '标题最长200字', trigger: 'blur' }
  ],
  contentRaw: [
    { required: true, message: '请输入内容', trigger: 'blur' }
  ]
}

async function fetchAnnouncements() {
  loading.value = true
  try {
    announcements.value = await announcementApi.listAll()
  } catch {
    announcements.value = []
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  isEditing.value = false
  editingId.value = null
  form.value = { title: '', contentRaw: '', isActive: true }
  dialogVisible.value = true
}

function openEditDialog(row) {
  isEditing.value = true
  editingId.value = row.id
  form.value = {
    title: row.title,
    contentRaw: row.contentRaw,
    isActive: row.isActive
  }
  dialogVisible.value = true
}

function resetForm() {
  form.value = { title: '', contentRaw: '', isActive: true }
  isEditing.value = false
  editingId.value = null
  formRef.value?.resetFields()
}

async function handleImageUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return

  // 校验类型和大小
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('只支持图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 10MB')
    return
  }

  uploading.value = true
  try {
    const result = await announcementApi.uploadImage(file)
    // 在光标位置插入 Markdown 图片语法
    const md = result.markdown || `![](${result.url})`
    form.value.contentRaw = (form.value.contentRaw || '') + '\n' + md + '\n'
    ElMessage.success('图片上传成功，已插入 Markdown 引用')
  } catch {
    // error handled by interceptor
  } finally {
    uploading.value = false
    // 重置 file input，允许重复上传同一文件
    if (imageInput.value) imageInput.value.value = ''
  }
}

async function handleMdUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return

  uploadingMd.value = true
  try {
    const result = await announcementApi.uploadMd(file)
    // 用文件内容替换编辑器内容
    form.value.contentRaw = result.content || ''
    // 如果标题为空，用文件名填充
    if (!form.value.title) {
      form.value.title = result.filename?.replace(/\.md$/i, '') || ''
    }
    ElMessage.success('MD 文件已加载，原档已保存到服务器')
  } catch {
    // error handled by interceptor
  } finally {
    uploadingMd.value = false
    if (mdInput.value) mdInput.value.value = ''
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEditing.value) {
      await announcementApi.update(editingId.value, form.value)
      ElMessage.success('公告已更新')
    } else {
      await announcementApi.create(form.value)
      ElMessage.success('公告已创建')
    }
    dialogVisible.value = false
    await fetchAnnouncements()
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

async function handleToggle(row) {
  try {
    await announcementApi.toggleActive(row.id)
    ElMessage.success(row.isActive ? '公告已禁用' : '公告已启用')
    await fetchAnnouncements()
  } catch {
    // error handled by interceptor
  }
}

async function handleDelete(id) {
  try {
    await announcementApi.delete(id)
    ElMessage.success('公告已删除')
    await fetchAnnouncements()
  } catch {
    // error handled by interceptor
  }
}

onMounted(() => {
  fetchAnnouncements()
})
</script>

<style scoped>
.admin-announcements {
  max-width: 1100px;
}
</style>
