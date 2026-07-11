<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <router-link to="/" class="auth-logo">💾 MineSave</router-link>
        <h1 class="auth-title">注册账号</h1>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @submit.prevent="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名（2-20位，字母/数字/下划线/连字符）"
            :prefix-icon="User"
            maxlength="20"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码（至少 6 位）"
            :prefix-icon="Lock"
            show-password
            maxlength="100"
          />
        </el-form-item>

        <el-form-item prop="nickname">
          <el-input
            v-model="form.nickname"
            placeholder="昵称（1-24位，中文/字母/数字/空格/_-/·）"
            :prefix-icon="Avatar"
            maxlength="24"
            show-word-limit
          />
        </el-form-item>

        <el-form-item prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="手机号（可选，11位中国大陆手机号）"
            :prefix-icon="Phone"
            maxlength="11"
          />
        </el-form-item>

        <el-form-item prop="email">
          <el-input
            v-model="form.email"
            placeholder="邮箱（必填，可用于验证码登录）"
            :prefix-icon="Message"
            maxlength="100"
          />
        </el-form-item>

        <!-- 图形验证码 -->
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaCode"
              placeholder="图形验证码"
              :prefix-icon="Key"
              maxlength="4"
              class="captcha-input"
            />
            <img
              :src="captchaImage"
              alt="验证码"
              class="captcha-img"
              title="点击刷新验证码"
              @click="refreshCaptcha"
            />
          </div>
        </el-form-item>

        <!-- 邮箱验证码 -->
        <el-form-item prop="emailCode">
          <div class="captcha-row">
            <el-input
              v-model="form.emailCode"
              placeholder="邮箱验证码（6位数字）"
              :prefix-icon="Message"
              maxlength="6"
              class="captcha-input"
            />
            <el-button
              class="email-code-btn"
              :disabled="emailCodeBtnDisabled"
              :loading="sendingEmailCode"
              @click="sendEmailCode"
            >
              {{ emailCodeBtnText }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="auth-submit-btn"
            @click="handleRegister"
          >
            {{ loading ? '注册中...' : '注册' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        已有账号？<router-link to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Avatar, Phone, Message, Key } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/authApi'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const formRef = ref(null)

// 图形验证码
const captchaImage = ref('')
const captchaKey = ref('')

async function refreshCaptcha() {
  try {
    const data = await authApi.getCaptcha()
    captchaImage.value = data.captchaImage
    captchaKey.value = data.captchaKey
  } catch (e) {
    // error shown by interceptor
  }
}

// 邮箱验证码倒计时
const emailCodeCooldown = ref(0)
let cooldownTimer = null

const emailCodeBtnDisabled = computed(() => {
  return emailCodeCooldown.value > 0 || !form.email || !form.captchaCode
})

const emailCodeBtnText = computed(() => {
  if (sendingEmailCode.value) return '发送中...'
  if (emailCodeCooldown.value > 0) return `${emailCodeCooldown.value}s 后重发`
  return '发送验证码'
})

const sendingEmailCode = ref(false)

async function sendEmailCode() {
  // 前置校验：邮箱格式和图形验证码
  if (!form.email) {
    ElMessage.warning('请先填写邮箱')
    return
  }
  if (!form.captchaCode) {
    ElMessage.warning('请先填写图形验证码')
    return
  }

  sendingEmailCode.value = true
  try {
    await authApi.sendEmailCode(form.email, captchaKey.value, form.captchaCode)
    ElMessage.success('验证码已发送至邮箱，5分钟内有效（2分钟后可使用）')
    // 启动倒计时
    emailCodeCooldown.value = 60
    cooldownTimer = setInterval(() => {
      emailCodeCooldown.value--
      if (emailCodeCooldown.value <= 0) {
        clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }, 1000)
    // 刷新图形验证码（防止复用）
    refreshCaptcha()
    form.captchaCode = ''
  } catch (e) {
    refreshCaptcha()
    form.captchaCode = ''
    // 错误信息由拦截器统一处理
  } finally {
    sendingEmailCode.value = false
  }
}

onUnmounted(() => {
  if (cooldownTimer) clearInterval(cooldownTimer)
})

onMounted(() => {
  refreshCaptcha()
})

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  phone: '',
  email: '',
  captchaCode: '',
  emailCode: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度须为 2-20 个字符', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9][a-zA-Z0-9_-]*[a-zA-Z0-9]$|^[a-zA-Z0-9]$/,
      message: '用户名只允许字母、数字、下划线和连字符，不能以连字符或下划线开头/结尾',
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度须为 6-100 个字符', trigger: 'blur' }
  ],
  nickname: [
    { min: 1, max: 24, message: '昵称长度须为 1-24 个字符', trigger: 'blur' },
    {
      pattern: /^[一-龥a-zA-Z0-9_\-\s·]+$/,
      message: '昵称只允许中文、字母、数字、空格、下划线、连字符和中间点',
      trigger: 'blur'
    }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确，须为11位中国大陆手机号', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 100, message: '邮箱地址最长 100 个字符', trigger: 'blur' }
  ],
  captchaCode: [
    { required: true, message: '请输入图形验证码', trigger: 'blur' }
  ],
  emailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '邮箱验证码须为6位数字', trigger: 'blur' }
  ]
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await auth.register(
      {
        username: form.username,
        password: form.password,
        nickname: form.nickname || undefined,
        phone: form.phone || undefined,
        email: form.email,
        emailCode: form.emailCode
      },
      captchaKey.value,
      form.captchaCode
    )
    ElMessage.success('注册成功')
    router.push('/')
  } catch (e) {
    refreshCaptcha()
    form.captchaCode = ''
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-tertiary);
  padding: var(--spacing-lg);
}

.auth-card {
  width: 100%;
  max-width: 420px;
  padding: var(--spacing-xl);
  background: var(--color-bg-canvas);
  border: 1px solid var(--color-border-primary);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
}

.auth-header {
  text-align: center;
  margin-bottom: var(--spacing-lg);
}

.auth-logo {
  font-size: var(--font-size-title);
  font-weight: 700;
  color: var(--color-body-text);
  text-decoration: none;
}

.auth-title {
  font-size: var(--font-size-xlarge);
  font-weight: 400;
  color: var(--color-body-text);
  margin-top: var(--spacing-md);
}

.captcha-row {
  display: flex;
  gap: var(--spacing-sm);
  width: 100%;
}

.captcha-input {
  flex: 1;
}

.captcha-img {
  height: 40px;
  width: 110px;
  border: 1px solid var(--color-border-primary);
  border-radius: var(--radius-sm);
  cursor: pointer;
  object-fit: cover;
  flex-shrink: 0;
}

.email-code-btn {
  flex-shrink: 0;
  min-width: 110px;
  white-space: nowrap;
}

.auth-submit-btn {
  width: 100%;
}

.auth-footer {
  text-align: center;
  font-size: var(--font-size-normal);
  color: var(--color-secondary-text);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border-secondary);
}
</style>
