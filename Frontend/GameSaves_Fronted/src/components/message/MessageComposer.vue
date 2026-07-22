<template><form class="composer" @submit.prevent="submit"><el-input v-model="text" type="textarea" :autosize="{ minRows: 3, maxRows: 10 }" maxlength="4000" placeholder="输入消息，Enter 发送，Shift+Enter 换行" @keydown.enter.exact.prevent="submit"/><div class="controls"><input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp" @change="choose"/><el-button type="primary" :loading="sending" native-type="submit">发送</el-button></div></form></template>
<script setup>
import { ref } from 'vue'; import { ElMessage } from 'element-plus'
const emit = defineEmits(['send']); const text = ref(''); const image = ref(null); const sending = ref(false)
function choose(event){ const file=event.target.files?.[0]; if(file && file.size>10*1024*1024){ElMessage.error('图片不能超过 10 MB');event.target.value='';return} image.value=file||null }
async function submit(){ if(!text.value.trim()&&!image.value)return; sending.value=true; try{await emit('send',{text:text.value,image:image.value});text.value='';image.value=null}catch{}finally{sending.value=false} }
</script>
<style scoped>.composer{flex:0 0 auto;max-height:45vh;max-height:45dvh;overflow-y:auto;padding:12px;border-top:1px solid var(--color-border-primary);background:var(--color-bg-primary)}.composer :deep(.el-textarea__inner){resize:vertical;max-height:calc(45vh - 76px);max-height:calc(45dvh - 76px);overflow-y:auto}.controls{display:flex;justify-content:space-between;align-items:center;margin-top:8px}.controls input{max-width:70%}</style>
