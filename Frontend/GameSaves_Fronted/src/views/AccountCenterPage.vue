<template>
  <div class="account-page">
    <header class="account-heading">
      <h1>账户中心</h1>
      <p>管理你的公开资料、头像和登录安全。</p>
    </header>

    <div class="account-layout">
      <nav class="account-nav" aria-label="账户设置">
        <button :class="{ active: section === 'profile' }" @click="section = 'profile'">个人资料</button>
        <button :class="{ active: section === 'avatar' }" @click="section = 'avatar'">头像</button>
        <button :class="{ active: section === 'security' }" @click="section = 'security'">登录与安全</button>
      </nav>

      <section class="account-panel">
        <template v-if="section === 'profile'">
          <div class="panel-heading"><h2>个人资料</h2><p>这些信息会显示在你的公开个人主页。</p></div>
          <el-form :model="profileForm" label-position="top" class="account-form">
            <el-form-item label="昵称"><el-input v-model="profileForm.nickname" maxlength="24" show-word-limit /></el-form-item>
            <el-form-item label="个人简介"><el-input v-model="profileForm.bio" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item>
            <el-form-item label="手机号"><el-input v-model="profileForm.phone" maxlength="11" /></el-form-item>
            <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
          </el-form>
        </template>

        <template v-else-if="section === 'avatar'">
          <div class="panel-heading"><h2>头像</h2><p>支持 JPEG、PNG 和 WebP，文件最大 2 MiB。</p></div>
          <div class="avatar-settings">
            <el-avatar :size="112" :src="accountUser?.avatarUrl" icon="UserFilled" class="account-avatar" />
            <div class="avatar-actions">
              <input ref="avatarInput" class="avatar-file-input" type="file" accept="image/jpeg,image/png,image/webp" @change="handleAvatarChange" />
              <div><el-button type="primary" @click="chooseAvatar">选择新头像</el-button><el-button v-if="accountUser?.avatarUrl" class="secondary-action" @click="removeAvatar">恢复默认</el-button></div>
              <p>上传后会从中心裁剪为正方形，并生成 180×180、90×90 与 45×45 三种尺寸。</p>
            </div>
          </div>
        </template>

        <template v-else>
          <div class="panel-heading"><h2>登录与安全</h2><p>修改密码后，为保障账号安全，你需要重新登录。</p></div>
          <el-form :model="passwordForm" label-position="top" class="account-form password-form">
            <el-form-item label="当前密码"><el-input v-model="passwordForm.currentPassword" type="password" show-password /></el-form-item>
            <el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" show-password /></el-form-item>
            <el-form-item label="确认新密码"><el-input v-model="passwordForm.confirmPassword" type="password" show-password /></el-form-item>
            <el-button type="primary" :loading="savingPassword" @click="changePassword">修改密码</el-button>
          </el-form>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '../api/userApi'
import { authApi } from '../api/authApi'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const section = ref('profile')
const accountUser = ref(auth.currentUser)
const avatarInput = ref(null)
const savingProfile = ref(false)
const savingPassword = ref(false)
const profileForm = reactive({ nickname: '', bio: '', phone: '' })
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

function syncProfileForm(user) {
  Object.assign(profileForm, { nickname: user?.nickname || '', bio: user?.bio || '', phone: user?.phone || '' })
}

async function loadAccount() {
  const user = await userApi.getById(auth.userId)
  accountUser.value = user
  auth.updateCurrentUser(user)
  syncProfileForm(user)
}

async function saveProfile() {
  savingProfile.value = true
  try {
    const user = await userApi.updateProfile({ ...profileForm, phone: profileForm.phone || null })
    accountUser.value = user
    auth.updateCurrentUser(user)
    syncProfileForm(user)
    ElMessage.success('个人资料已更新')
  } finally {
    savingProfile.value = false
  }
}

function chooseAvatar() {
  avatarInput.value?.click()
}

async function handleAvatarChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('头像不能超过 2 MiB')
    event.target.value = ''
    return
  }
  try {
    const user = await userApi.uploadAvatar(file)
    accountUser.value = user
    auth.updateCurrentUser(user)
    ElMessage.success('头像已更新')
  } finally {
    event.target.value = ''
  }
}

async function removeAvatar() {
  await ElMessageBox.confirm('确定恢复为默认头像吗？', '恢复默认头像', { type: 'warning' })
  const user = await userApi.removeAvatar()
  accountUser.value = user
  auth.updateCurrentUser(user)
  ElMessage.success('头像已恢复默认')
}

async function changePassword() {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.error('两次输入的新密码不一致')
    return
  }
  savingPassword.value = true
  try {
    await authApi.changePassword(passwordForm)
    ElMessage.success('密码已修改，请重新登录')
    await auth.logout()
    window.location.assign('/login')
  } finally {
    savingPassword.value = false
  }
}

onMounted(() => {
  syncProfileForm(accountUser.value)
  loadAccount().catch(() => ElMessage.error('账户信息加载失败'))
})
</script>

<style scoped>
.account-page { max-width: 900px; margin: 0 auto; padding: 32px 0 48px; }
.account-heading { margin-bottom: 24px; }
.account-heading h1 { margin: 0; font-size: 24px; line-height: 1.25; font-weight: 600; color: var(--color-body-text); }
.account-heading p, .panel-heading p, .avatar-actions p { margin: 6px 0 0; color: var(--color-secondary-text); font-size: 14px; line-height: 1.5; }
.account-layout { display: grid; grid-template-columns: 180px minmax(0, 1fr); gap: 32px; align-items: start; }
.account-nav { display: grid; gap: 4px; padding-top: 4px; }
.account-nav button { appearance: none; border: 0; border-radius: var(--radius-sm); padding: 8px 10px; text-align: left; background: transparent; color: var(--color-body-text); cursor: pointer; font-size: 14px; line-height: 20px; }
.account-nav button:hover { background: var(--color-bg-secondary); }
.account-nav button.active { background: #ddf4ff; color: var(--color-link); font-weight: 600; }
.account-panel { min-height: 420px; padding: 24px; border: 1px solid var(--color-border-primary); border-radius: var(--radius-md); background: var(--color-bg-canvas); }
.panel-heading { padding-bottom: 20px; border-bottom: 1px solid var(--color-border-secondary); }
.panel-heading h2 { margin: 0; font-size: 16px; line-height: 1.5; font-weight: 600; }
.account-form { max-width: 560px; padding-top: 20px; }
.password-form { max-width: 420px; }
.avatar-settings { display: flex; gap: 20px; align-items: center; padding-top: 24px; }
.account-avatar { flex: none; border: 1px solid var(--color-border-primary); }
.avatar-actions { min-width: 0; }
.secondary-action { margin-left: 8px; }
.avatar-file-input { display: none; }
@media (max-width: 720px) {
  .account-page { padding: 24px 0 36px; }
  .account-layout { grid-template-columns: 1fr; gap: 12px; }
  .account-nav { display: flex; overflow-x: auto; padding: 0 0 2px; }
  .account-nav button { flex: 0 0 auto; }
  .account-panel { min-height: 0; padding: 20px; }
}
@media (max-width: 480px) {
  .avatar-settings { align-items: flex-start; flex-direction: column; }
  .secondary-action { margin-left: 0; margin-top: 8px; }
}
</style>
