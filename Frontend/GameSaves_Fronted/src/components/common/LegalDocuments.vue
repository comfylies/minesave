<template>
  <span :class="['legal-links', { 'legal-links--footer': footer }]">
    <button type="button" @click.stop="open('terms')">服务协议</button>
    <span>和</span>
    <button type="button" @click.stop="open('privacy')">隐私政策</button>
  </span>
  <el-dialog v-model="visible" :title="active === 'privacy' ? '隐私政策' : '服务协议与社区规则'" width="min(720px, calc(100vw - 32px))">
    <section v-if="active === 'privacy'" class="legal-document">
      <p><strong>生效日期：2026 年 7 月 21 日</strong></p>
      <p>个人信息处理者：MineSave 平台运营者；个人信息保护相关请求请通过登录后的“联系我们”提交。</p>
      <h3>我们处理的信息</h3>
      <p>为注册、登录、找回与保护账号，我们处理用户名、加密后的密码、邮箱、可选手机号、验证码和登录状态。为提供存档分享服务，我们处理你主动提交的头像、简介、文章、评论、上传文件和联系留言。</p>
      <h3>安全与运行日志</h3>
      <p>为防止滥用、保障下载安全、排查故障和履行法定义务，我们会记录 IP 地址、用户代理、登录失败、下载、管理操作及必要的安全检测结果；浏览器本地存储用于保存登录令牌、当前用户和界面偏好。</p>
      <h3>使用、保存与共享</h3>
      <p>信息仅用于提供、维护和改进本服务，防范安全风险及处理投诉争议。账号资料通常保存至注销或删除申请完成；安全日志、下载记录和投诉材料在实现相应目的所必需的期限内保存，法律另有要求的从其规定。我们不会出售个人信息；如使用邮件服务商或对象存储服务商，其仅按我们的指示处理提供服务所必需的信息。</p>
      <h3>你的权利与联系</h3>
      <p>你可查询、更正、删除个人资料，撤回非必要同意，或申请注销账号。请通过登录后的“联系我们”提交请求；我们将依法处理。政策发生实质变化时，会以站内公告或显著方式提示。</p>
    </section>
    <section v-else class="legal-document">
      <p><strong>生效日期：2026 年 7 月 21 日</strong></p>
      <h3>上传与内容责任</h3>
      <p>上传者应确认拥有分享存档、截图、说明和其他内容所需的权利，并确保不包含未经授权的个人信息、侵权内容、恶意程序或违法信息。不得借助平台传播作弊工具、恶意软件或其他违法内容。</p>
      <h3>平台管理与投诉</h3>
      <p>平台可基于安全检测、投诉处理或法律要求限制展示、下架、删除内容或采取账号管理措施。发现侵权、恶意文件或隐私泄露时，请通过“联系我们”提交存档链接、权利说明和必要证明材料。</p>
      <h3>下载提示</h3>
      <p>存档由用户上传。平台会进行基础格式与风险检查，但不保证兼容性、完整性或绝对无风险。下载前请备份原存档、核验来源和适用版本；含脚本或可执行文件的存档会以黄色提示标识，需谨慎审查后使用。本协议不排除或限制平台依法应承担的责任。</p>
    </section>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
defineProps({ footer: { type: Boolean, default: false } })
const visible = ref(false)
const active = ref('terms')
function open(document) { active.value = document; visible.value = true }
</script>

<style scoped>
.legal-links { color: var(--color-secondary-text); }
.legal-links button { appearance: none; border: 0; padding: 0; background: transparent; color: var(--color-link); cursor: pointer; font: inherit; }
.legal-links--footer { font-size: var(--font-size-small); color: var(--color-tertiary-text, #999); }
.legal-links--footer button { color: inherit; }
.legal-links button:hover { text-decoration: underline; }
.legal-document { color: var(--color-body-text); font-size: 14px; line-height: 1.7; }
.legal-document h3 { margin: 18px 0 4px; font-size: 15px; }
.legal-document p { margin: 0 0 10px; text-align: justify; text-justify: inter-ideograph; }
</style>
