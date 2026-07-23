<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <router-link to="/" class="auth-logo">💾 MineSave</router-link>
        <h1 class="auth-title">登录</h1>
      </div>

      <!-- 登录方式切换 -->
      <div class="login-tabs">
        <button
          :class="['tab-btn', { active: loginMode === 'password' }]"
          @click="loginMode = 'password'"
        >
          密码登录
        </button>
        <button
          :class="['tab-btn', { active: loginMode === 'email' }]"
          @click="loginMode = 'email'"
        >
          验证码登录
        </button>
      </div>

      <!-- ==================== 密码登录表单 ==================== -->
      <el-form
        v-if="loginMode === 'password'"
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        size="large"
        @submit.prevent="handlePasswordLogin"
      >
        <el-form-item prop="login">
          <el-input
            v-model="passwordForm.login"
            placeholder="用户名或手机号"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="passwordForm.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handlePasswordLogin"
          />
        </el-form-item>

        <!-- 图形验证码 -->
        <el-form-item v-if="captchaRequired" prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="passwordForm.captchaCode"
              placeholder="验证码"
              :prefix-icon="Key"
              maxlength="4"
              class="captcha-input"
              @keyup.enter="handlePasswordLogin"
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

        <el-form-item class="terms-item">
          <el-checkbox v-model="loginAgreed">
            <span class="terms-prefix">我已阅读并同意</span> <LegalDocuments />
          </el-checkbox>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="auth-submit-btn"
            @click="handlePasswordLogin"
          >
            {{ loading ? '登录中...' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- ==================== 邮箱验证码登录表单 ==================== -->
      <el-form
        v-if="loginMode === 'email'"
        ref="emailFormRef"
        :model="emailForm"
        :rules="emailRules"
        size="large"
        @submit.prevent="handleEmailLogin"
      >
        <el-form-item prop="email">
          <el-input
            v-model="emailForm.email"
            placeholder="邮箱地址"
            :prefix-icon="Message"
          />
        </el-form-item>

        <!-- 图形验证码 -->
        <el-form-item v-if="captchaRequired" prop="emailCaptchaCode">
          <div class="captcha-row">
            <el-input
              v-model="emailForm.emailCaptchaCode"
              placeholder="验证码"
              :prefix-icon="Key"
              maxlength="4"
              class="captcha-input"
            />
            <img
              :src="emailCaptchaImage"
              alt="验证码"
              class="captcha-img"
              title="点击刷新验证码"
              @click="refreshEmailCaptcha"
            />
          </div>
        </el-form-item>

        <el-form-item prop="code">
          <div class="captcha-row">
            <el-input
              v-model="emailForm.code"
              placeholder="6位邮箱验证码"
              :prefix-icon="Key"
              maxlength="6"
              class="captcha-input"
              @keyup.enter="handleEmailLogin"
            />
            <el-button
              :disabled="emailCooldown > 0"
              class="email-code-btn"
              @click="handleSendEmailCode"
            >
              {{ emailCooldown > 0 ? `${emailCooldown}s` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item class="terms-item">
          <el-checkbox v-model="loginAgreed">
            <span class="terms-prefix">我已阅读并同意</span> <LegalDocuments />
          </el-checkbox>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="auth-submit-btn"
            @click="handleEmailLogin"
          >
            {{ loading ? '登录中...' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key, Message } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/authApi'
import LegalDocuments from '../components/common/LegalDocuments.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const loading = ref(false)
const loginMode = ref('password')
const loginAgreed = ref(true)

// ---- 图形验证码 ----
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

onMounted(() => {
  refreshCaptcha()
  refreshEmailCaptcha()
})

// ---- 密码登录 ----
const passwordFormRef = ref(null)
const passwordForm = reactive({
  login: '',
  password: '',
  captchaCode: ''
})

const passwordRules = {
  login: [{ required: true, message: '请输入用户名或手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function handlePasswordLogin() {
  if (!loginAgreed.value) {
    ElMessage.warning('请阅读并同意服务协议与隐私政策')
    return
  }
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await auth.login(
      passwordForm.login,
      passwordForm.password,
      captchaKey.value,
      passwordForm.captchaCode
    )
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (e) {
    // 验证码错误时刷新
    refreshCaptcha()
    passwordForm.captchaCode = ''
  } finally {
    loading.value = false
  }
}

// ---- 邮箱登录 ----
const emailFormRef = ref(null)
const emailCaptchaImage = ref('')
const emailCaptchaKey = ref('')
const emailForm = reactive({
  email: '',
  emailCaptchaCode: '',
  code: ''
})
const emailCooldown = ref(0)
let cooldownTimer = null

async function refreshEmailCaptcha() {
  try {
    const data = await authApi.getCaptcha()
    captchaRequired.value = data.captchaRequired !== 'false'
    emailCaptchaImage.value = captchaRequired.value ? data.captchaImage : ''
    emailCaptchaKey.value = captchaRequired.value ? data.captchaKey : ''
  } catch (e) {
    // error shown by interceptor
  }
}

const emailRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  emailCaptchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入邮箱验证码', trigger: 'blur' }]
}

async function handleSendEmailCode() {
  if (!emailForm.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  if (captchaRequired.value && !emailForm.emailCaptchaCode) {
    ElMessage.warning('请先填写图形验证码')
    return
  }
  try {
    await authApi.sendEmailCode(emailForm.email, emailCaptchaKey.value, emailForm.emailCaptchaCode)
    ElMessage.success('验证码已发送，10分钟内有效')
    // 开始倒计时
    emailCooldown.value = 90
    cooldownTimer = setInterval(() => {
      emailCooldown.value--
      if (emailCooldown.value <= 0) {
        clearInterval(cooldownTimer)
      }
    }, 1000)
    // 刷新图形验证码
    refreshEmailCaptcha()
    emailForm.emailCaptchaCode = ''
  } catch (e) {
    refreshEmailCaptcha()
    emailForm.emailCaptchaCode = ''
    // error shown by interceptor
  }
}

async function handleEmailLogin() {
  if (!loginAgreed.value) {
    ElMessage.warning('请阅读并同意服务协议与隐私政策')
    return
  }
  const valid = await emailFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await auth.loginWithEmail(emailForm.email, emailForm.code)
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/'
    router.push(redirect)
  } catch (e) {
    // error shown by interceptor
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

/* 登录方式切换 */
.login-tabs {
  display: flex;
  margin-bottom: var(--spacing-lg);
  border-bottom: 2px solid var(--color-border-secondary);
}

.tab-btn {
  flex: 1;
  padding: var(--spacing-sm) 0;
  border: none;
  background: none;
  font-size: var(--font-size-normal);
  color: var(--color-secondary-text);
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.tab-btn.active {
  color: var(--el-color-primary);
  font-weight: 600;
}

.tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--el-color-primary);
}

/* 验证码 */
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
}

.email-code-btn {
  width: 120px;
  flex-shrink: 0;
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
</style>
