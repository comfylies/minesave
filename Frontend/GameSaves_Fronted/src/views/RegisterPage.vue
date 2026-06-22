<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-header">
        <router-link to="/" class="auth-logo">💾 GameSaves</router-link>
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
            placeholder="用户名"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码（至少 6 位）"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item prop="nickname">
          <el-input
            v-model="form.nickname"
            placeholder="昵称（可选，默认使用用户名）"
            :prefix-icon="Avatar"
          />
        </el-form-item>

        <el-form-item prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="手机号（可选）"
            :prefix-icon="Phone"
          />
        </el-form-item>

        <el-form-item prop="email">
          <el-input
            v-model="form.email"
            placeholder="邮箱（可选，可用于验证码登录）"
            :prefix-icon="Message"
          />
        </el-form-item>

        <!-- 图形验证码 -->
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaCode"
              placeholder="验证码"
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
import { ref, reactive, onMounted } from 'vue'
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

onMounted(() => {
  refreshCaptcha()
})

const form = reactive({
  username: '',
  password: '',
  nickname: '',
  phone: '',
  email: '',
  captchaCode: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 50, message: '用户名长度 2-50 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度 6-100 个字符', trigger: 'blur' }
  ],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
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
        email: form.email || undefined
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
