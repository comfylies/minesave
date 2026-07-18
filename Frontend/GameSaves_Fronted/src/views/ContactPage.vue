<template>
  <div class="contact-page">
    <div class="contact-card">
      <!-- 成功状态 -->
      <template v-if="submitted">
        <div class="success-state">
          <el-icon :size="64" color="#22c55e"><CircleCheckFilled /></el-icon>
          <h2>留言提交成功！</h2>
          <p class="success-desc">感谢您的反馈，我们会尽快处理。</p>
          <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
        </div>
      </template>

      <!-- 表单 -->
      <template v-else>
        <h1 class="page-title">联系我们</h1>
        <p class="page-subtitle">有任何建议、问题反馈或商务合作意向，欢迎留言</p>

        <!-- 联系邮箱区域 -->
        <div class="email-contact">
          <el-icon :size="18"><Message /></el-icon>
          <span class="email-label">也可直接发送邮件联系我们：</span>
          <code class="email-address">{{ contactEmail }}</code>
          <el-button size="small" text type="primary" @click="copyEmail">
            <el-icon><DocumentCopy /></el-icon> 复制
          </el-button>
        </div>

        <el-divider />

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleSubmit"
        >
          <el-row :gutter="16">
            <el-col :sm="12" :xs="24">
              <el-form-item label="姓名" prop="name">
                <el-input v-model="form.name" maxlength="100" show-word-limit />
              </el-form-item>
            </el-col>
            <el-col :sm="12" :xs="24">
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="form.email" maxlength="200" show-word-limit />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="类别" prop="category">
            <el-select v-model="form.category" placeholder="请选择留言类别" style="width: 100%">
              <el-option label="功能建议" value="suggestion" />
              <el-option label="问题反馈" value="bug" />
              <el-option label="商务合作" value="business" />
              <el-option label="其他" value="other" />
            </el-select>
          </el-form-item>

          <el-form-item label="主题" prop="subject">
            <el-input v-model="form.subject" maxlength="200" show-word-limit placeholder="一句话概括你想说的" />
          </el-form-item>

          <!-- MD 编辑器区域 -->
          <el-form-item label="留言内容" prop="message">
            <div class="md-editor">
              <!-- 工具栏 -->
              <div class="md-toolbar">
                <div class="md-toolbar-left">
                  <input
                    ref="imageInput"
                    type="file"
                    accept="image/*"
                    style="display: none"
                    @change="handleImageUpload"
                  />
                  <el-button size="small" @click="$refs.imageInput.click()" :loading="uploading">
                    <el-icon><Picture /></el-icon> 插入图片
                  </el-button>
                  <span class="md-toolbar-hint">支持 Markdown 格式，图片大小不超过 10MB</span>
                </div>
                <el-button-group size="small">
                  <el-button :type="previewMode ? 'default' : 'primary'" @click="previewMode = false">编辑</el-button>
                  <el-button :type="previewMode ? 'primary' : 'default'" @click="previewMode = true">预览</el-button>
                </el-button-group>
              </div>

              <!-- 编辑模式 -->
              <el-input
                v-show="!previewMode"
                v-model="form.message"
                type="textarea"
                :rows="12"
                placeholder="在此输入 Markdown 内容……
支持插入图片、代码块、表格等格式。
点击上方「插入图片」按钮可上传并插入图片。"
              />

              <!-- 预览模式 -->
              <div
                v-show="previewMode"
                class="md-preview markdown-body"
                v-html="renderedMarkdown"
              ></div>
            </div>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="submitting"
              @click="handleSubmit"
              style="width: 100%"
            >
              {{ submitting ? '提交中...' : '提交留言' }}
            </el-button>
          </el-form-item>
        </el-form>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheckFilled, Picture, Message, DocumentCopy } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { useAuthStore } from '../stores/auth'
import { contactApi } from '../api/contactApi'

const router = useRouter()
const auth = useAuthStore()

const contactEmail = import.meta.env.VITE_CONTACT_EMAIL || 'admin@minesave.cn'
const formRef = ref(null)
const imageInput = ref(null)
const submitting = ref(false)
const uploading = ref(false)
const submitted = ref(false)
const previewMode = ref(false)

const form = ref({
  name: '',
  email: '',
  category: '',
  subject: '',
  message: ''
})

const rules = {
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 100, message: '姓名最长100个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }
  ],
  category: [
    { required: true, message: '请选择类别', trigger: 'change' }
  ],
  subject: [
    { required: true, message: '请输入主题', trigger: 'blur' },
    { max: 200, message: '主题最长200个字符', trigger: 'blur' }
  ],
  message: [
    { required: true, message: '请输入留言内容', trigger: 'blur' }
  ]
}

const renderedMarkdown = computed(() => {
  if (!form.value.message) return '<p style="color: #999">暂无内容</p>'
  try {
    return marked.parse(form.value.message)
  } catch {
    return '<p style="color: #ef4444">Markdown 渲染失败</p>'
  }
})

onMounted(() => {
  // 未登录跳转
  if (!auth.isLoggedIn) {
    ElMessage.warning('请先登录后再提交留言')
    router.push('/login?redirect=/contact')
    return
  }

  // 自动填入当前用户信息
  const user = auth.currentUser
  if (user) {
    form.value.name = user.nickname || user.username || ''
    form.value.email = user.email || ''
  }
})

/** 插入图片 */
async function handleImageUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return

  // 客户端校验
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只允许上传图片文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 10MB')
    return
  }

  uploading.value = true
  try {
    const result = await contactApi.uploadImage(file)
    const md = result.markdown || `![](${result.url})`
    form.value.message = (form.value.message || '') + '\n' + md + '\n'
    ElMessage.success('图片已插入')
  } catch (err) {
    ElMessage.error(err?.response?.data?.message || '图片上传失败')
  } finally {
    uploading.value = false
    // 重置 input，允许重复选择同一文件
    if (imageInput.value) {
      imageInput.value.value = ''
    }
  }
}

/** 提交留言 */
async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    await contactApi.submit({
      name: form.value.name,
      email: form.value.email,
      category: form.value.category,
      subject: form.value.subject,
      message: form.value.message
    })
    submitted.value = true
  } catch (err) {
    ElMessage.error(err?.response?.data?.message || '提交失败，请稍后再试')
  } finally {
    submitting.value = false
  }
}

/** 复制邮箱 */
async function copyEmail() {
  try {
    await navigator.clipboard.writeText(contactEmail)
    ElMessage.success('邮箱地址已复制到剪贴板')
  } catch {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = contactEmail
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('邮箱地址已复制到剪贴板')
  }
}
</script>

<style scoped>
.contact-page {
  padding: var(--spacing-xl) 0;
  display: flex;
  justify-content: center;
}

.contact-card {
  width: 100%;
  max-width: 800px;
  background: var(--color-bg-canvas);
  border: 1px solid var(--color-border-primary);
  border-radius: var(--radius-lg);
  padding: var(--spacing-xl);
}

.page-title {
  font-size: var(--font-size-xxlarge);
  font-weight: 700;
  color: var(--color-body-text);
  margin: 0 0 var(--spacing-xs);
}

.page-subtitle {
  color: var(--color-secondary-text);
  margin: 0 0 var(--spacing-xl);
  font-size: var(--font-size-normal);
}

/* MD 编辑器 */
.md-editor {
  border: 1px solid var(--color-border-primary);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.md-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-bg-tertiary);
  border-bottom: 1px solid var(--color-border-primary);
  gap: var(--spacing-md);
}

.md-toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.md-toolbar-hint {
  font-size: var(--font-size-small);
  color: var(--color-tertiary-text);
}

.md-editor :deep(.el-textarea__inner) {
  border: none;
  border-radius: 0;
  resize: vertical;
}

.md-preview {
  padding: var(--spacing-md);
  min-height: 200px;
  max-height: 600px;
  overflow-y: auto;
  background: var(--color-bg-canvas);
}

/* 联系邮箱区域 */
.email-contact {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-small);
  color: var(--color-secondary-text);
}

.email-label {
  color: var(--color-secondary-text);
}

.email-address {
  font-family: var(--font-mono, monospace);
  background: var(--color-bg-tertiary);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-small);
  color: var(--color-body-text);
}

/* 成功状态 */
.success-state {
  text-align: center;
  padding: var(--spacing-xl) 0;
}

.success-state h2 {
  margin: var(--spacing-md) 0 var(--spacing-sm);
  color: var(--color-body-text);
}

.success-desc {
  color: var(--color-secondary-text);
  margin-bottom: var(--spacing-lg);
}
</style>
