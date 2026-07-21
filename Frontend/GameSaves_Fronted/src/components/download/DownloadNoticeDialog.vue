<template>
  <el-dialog v-model="visible" title="下载前提示" width="min(520px, calc(100vw - 32px))" @closed="emit('cancel')">
    <div class="download-notice">
      <div v-if="isWarning" class="security-warning">
        <el-icon><WarningFilled /></el-icon>
        <div><strong>检测到可执行文件或脚本</strong><br />该存档包含如 .exe、.sh、.bat、.py 等文件。请仅在信任来源、完成本地查杀并备份原存档后使用。</div>
      </div>
      <p>本存档由用户上传，平台已进行基础格式与风险检查，但不保证其兼容性、完整性或绝对无风险。</p>
      <p>请先备份原存档，并自行核验来源与适用版本。禁止将下载内容用于侵权、作弊传播或其他违法用途；如发现恶意或侵权内容，请通过“联系我们”投诉。</p>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="continueDownload">我已阅读，继续下载</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
const props = defineProps({ modelValue: Boolean, securityLevel: { type: String, default: 'SAFE' } })
const emit = defineEmits(['update:modelValue', 'confirm', 'cancel'])
const visible = ref(false)
const isWarning = computed(() => props.securityLevel === 'WARNING')
watch(() => props.modelValue, value => { visible.value = value })
watch(visible, value => emit('update:modelValue', value))
function continueDownload() { emit('confirm'); visible.value = false }
</script>

<style scoped>
.download-notice { color: var(--color-body-text); font-size: 14px; line-height: 1.7; }
.download-notice p { margin: 0 0 12px; }
.security-warning { display: flex; gap: 10px; padding: 12px; margin-bottom: 14px; border: 1px solid #d4a72c; border-radius: 6px; background: #fff8c5; color: #7d4e00; }
.security-warning .el-icon { flex: 0 0 auto; margin-top: 4px; font-size: 18px; }
</style>
