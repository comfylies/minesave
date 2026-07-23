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
        <el-form-item
          prop="username"
          :error="usernameError"
        >
          <el-input
            v-model="form.username"
            placeholder="用户名（2-20位，字母/数字/下划线/连字符）"
            :prefix-icon="User"
            maxlength="20"
            @blur="checkUsername"
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

        <el-form-item
          prop="phone"
          :error="phoneFieldError"
        >
          <el-input
            v-model="form.phone"
            placeholder="手机号（可选，11位中国大陆手机号）"
            :prefix-icon="Phone"
            maxlength="11"
            @blur="checkPhone"
          />
        </el-form-item>

        <el-form-item
          prop="email"
          :error="emailFieldError"
        >
          <el-input
            v-model="form.email"
            placeholder="邮箱（必填，可用于验证码登录）"
            :prefix-icon="Message"
            maxlength="100"
            @blur="checkEmail"
          />
        </el-form-item>

        <!-- 图形验证码（发送邮箱码后隐藏） -->
        <el-form-item v-if="captchaRequired && !captchaVerified">
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

        <!-- 验证码已发送提示 -->
        <div v-else-if="captchaVerified" class="email-sent-hint">
          📧 验证码已发送至 <strong>{{ form.email }}</strong>
        </div>

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
              @click="handleEmailCodeBtnClick"
            >
              {{ emailCodeBtnText }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item prop="acceptedTerms" class="terms-item">
          <el-checkbox v-model="form.acceptedTerms">
            <span class="terms-prefix">我已阅读并同意</span> <LegalDocuments />
          </el-checkbox>
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
import { ref, reactive, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Avatar, Phone, Message, Key } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/authApi'
import LegalDocuments from '../components/common/LegalDocuments.vue'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const formRef = ref(null)

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  phone: '',
  email: '',
  captchaCode: '',
  emailCode: ''
})

form.acceptedTerms = false

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'change' },
    { min: 2, max: 20, message: '用户名长度须为 2-20 个字符', trigger: 'change' },
    {
      pattern: /^[a-zA-Z0-9][a-zA-Z0-9_-]*[a-zA-Z0-9]$|^[a-zA-Z0-9]$/,
      message: '用户名只允许字母、数字、下划线和连字符，不能以连字符或下划线开头/结尾',
      trigger: 'change'
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
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确，须为11位中国大陆手机号', trigger: 'change' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'change' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'change' },
    { max: 100, message: '邮箱地址最长 100 个字符', trigger: 'change' }
  ],
  emailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '邮箱验证码须为6位数字', trigger: 'blur' }
  ]
}

rules.acceptedTerms = [
  { validator: (_, value, callback) => value ? callback() : callback(new Error('请阅读并同意服务协议与隐私政策')), trigger: 'change' }
]

// 字段实时校验状态：idle | checking | available | taken | error
const fieldState = reactive({
  username: { status: 'idle', message: '' },
  email: { status: 'idle', message: '' },
  phone: { status: 'idle', message: '' }
})

// 限流：每字段每秒最多检查一次
const lastCheckTime = reactive({ username: 0, email: 0, phone: 0 })
const lastCheckedValue = reactive({ username: '', email: '', phone: '' })

async function checkField(field, value) {
  if (!value || value.trim().length === 0) {
    fieldState[field].status = 'idle'
    return
  }
  // 值与上次相同则跳过
  if (value === lastCheckedValue[field]) return
  // 限流：每秒一次
  const now = Date.now()
  if (now - lastCheckTime[field] < 1000) return
  lastCheckTime[field] = now
  lastCheckedValue[field] = value

  fieldState[field].status = 'checking'
  try {
    const result = await authApi.checkField(field, value)
    fieldState[field].status = result.available ? 'available' : 'taken'
    fieldState[field].message = result.message
  } catch (e) {
    // 限流被拒或网络错误，恢复空闲
    fieldState[field].status = 'idle'
  }
}

async function checkUsername() {
  await checkField('username', form.username)
}

async function checkEmail() {
  const value = form.email.trim()
  if (!value) { fieldState.email.status = 'idle'; return }
  // 先本地校验格式
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(value)) {
    fieldState.email.status = 'error'
    return
  }
  await checkField('email', value)
}

async function checkPhone() {
  const value = form.phone.trim()
  if (!value) { fieldState.phone.status = 'idle'; return }
  // 先本地校验格式
  if (!/^1[3-9]\d{9}$/.test(value)) {
    fieldState.phone.status = 'error'
    return
  }
  await checkField('phone', value)
}

// 图形验证码（发送邮箱码后隐藏）
const captchaVerified = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')
const captchaRequired = ref(true)

async function refreshCaptcha() {
  try {
    const data = await authApi.getCaptcha()
    captchaRequired.value = data.captchaRequired !== 'false'
    captchaImage.value = captchaRequired.value ? data.captchaImage : ''
    captchaKey.value = captchaRequired.value ? data.captchaKey : ''
  } catch (e) {
    // error shown by interceptor
  }
}

// 邮箱验证码倒计时
const emailCodeCooldown = ref(0)
let cooldownTimer = null

const emailCodeBtnDisabled = computed(() => {
  if (emailCodeCooldown.value > 0) return true
  if (!form.email) return true
  if (captchaRequired.value && !captchaVerified.value && !form.captchaCode) return true
  return false
})

const emailCodeBtnText = computed(() => {
  if (sendingEmailCode.value) return '发送中...'
  if (emailCodeCooldown.value > 0) return `${emailCodeCooldown.value}s 后重发`
  if (captchaVerified.value) return '重新发送'
  return '发送验证码'
})

const usernameError = computed(() =>
  fieldState.username.status === 'taken' ? '用户名已被注册' : ''
)

const phoneFieldError = computed(() => {
  if (fieldState.phone.status === 'taken') return '手机号已被注册'
  if (fieldState.phone.status === 'error') return '手机号格式不正确'
  return ''
})

const emailFieldError = computed(() => {
  if (fieldState.email.status === 'taken') return '邮箱已被注册'
  if (fieldState.email.status === 'error') return '邮箱格式不正确'
  return ''
})

const sendingEmailCode = ref(false)

async function handleEmailCodeBtnClick() {
  // 重发模式：先展示图形验证码
  if (captchaRequired.value && captchaVerified.value && emailCodeCooldown.value <= 0) {
    captchaVerified.value = false
    form.captchaCode = ''
    await nextTick()
    refreshCaptcha()
    return
  }
  await sendEmailCode()
}

async function sendEmailCode() {
  if (!form.email) {
    ElMessage.warning('请先填写邮箱')
    return
  }
  if (captchaRequired.value && !form.captchaCode) {
    ElMessage.warning('请先填写图形验证码')
    return
  }

  sendingEmailCode.value = true
  try {
    await authApi.sendEmailCode(form.email, captchaKey.value, form.captchaCode)
    ElMessage.success('验证码已发送至邮箱，10分钟内有效')
    // 隐藏图形验证码
    captchaVerified.value = true
    // 启动倒计时
    emailCodeCooldown.value = 90
    cooldownTimer = setInterval(() => {
      emailCodeCooldown.value--
      if (emailCodeCooldown.value <= 0) {
        clearInterval(cooldownTimer)
        cooldownTimer = null
      }
    }, 1000)
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

// 修改邮箱后重置验证码状态
watch(() => form.email, () => {
  if (captchaVerified.value) {
    captchaVerified.value = false
    form.emailCode = ''
  }
  fieldState.email.status = 'idle'
})

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await auth.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined,
      phone: form.phone || undefined,
      email: form.email,
      emailCode: form.emailCode,
      acceptedTerms: form.acceptedTerms
    })
    ElMessage.success('注册成功')
    router.push('/')
  } catch (e) {
    // 错误信息由拦截器统一处理；邮箱码消费失败时需重新获取
    captchaVerified.value = false
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

.terms-item {
  margin-top: -9px;
  margin-bottom: 9px;
}

.terms-item :deep(.el-form-item__content) {
  line-height: 1.5;
}

.terms-prefix {
  color: var(--color-body-text);
}

.auth-footer {
  text-align: center;
  font-size: var(--font-size-normal);
  color: var(--color-secondary-text);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border-secondary);
}

/* 邮箱验证码已发送提示 */
.email-sent-hint {
  font-size: 13px;
  color: var(--color-secondary-text);
  padding: 4px 0 12px;
  text-align: center;
}

.email-sent-hint strong {
  color: var(--el-color-primary);
}
</style>
