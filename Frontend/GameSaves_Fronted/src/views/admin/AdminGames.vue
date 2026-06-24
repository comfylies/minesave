<template>
  <div class="admin-games">
    <div class="page-header">
      <h2 class="page-title">游戏管理</h2>
    </div>

    <el-card shadow="never">
      <template #header>
        <span>游戏列表</span>
      </template>

      <el-table :data="games" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="游戏名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="标准结构" width="160" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.hasStructure" type="success" size="small">
              {{ row.pathCount }} 条路径
            </el-tag>
            <el-tag v-else type="info" size="small">未上传</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <input
              type="file"
              accept=".zip"
              :ref="el => setFileInputRef(row.id, el)"
              style="display: none"
              @change="(e) => handleFileChange(row, e)"
            />
            <el-button
              type="primary"
              size="small"
              :loading="uploading === row.id"
              @click="triggerUpload(row.id)"
            >
              {{ row.hasStructure ? '更新标准结构' : '上传标准结构' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 上传说明 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>标准结构说明</span>
      </template>
      <div class="structure-info">
        <p>上传游戏的标准文件夹结构 ZIP 后，系统会提取所有文件路径作为<b>安全白名单基线</b>：</p>
        <ul>
          <li><el-tag type="danger" size="small">红色</el-tag> — 高危文件：<code>.exe .bat .cmd .vbs .ps1 .scr .msi</code>，无论在何处都标红</li>
          <li>
            <el-tag type="warning" size="small">黄色</el-tag> — 可疑文件：
            标准结构内的 <code>.sh .py .rb</code>（正常脚本）；
            标准结构外的 <code>.dll .so .jar</code>（异常位置）
          </li>
          <li><el-tag type="info" size="small">默认</el-tag> — 安全文件：其余所有扩展名</li>
        </ul>
        <p class="tip">上传新 ZIP 会<b>替换</b>该游戏已有的白名单，请确保 ZIP 包含完整的标准目录结构。</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { gameApi } from '../../api/gameApi'
import { adminApi } from '../../api/adminApi'

const games = ref([])
const loading = ref(false)
const uploading = ref(null)
const fileInputRefs = {}

function setFileInputRef(gameId, el) {
  if (el) fileInputRefs[gameId] = el
}

onMounted(async () => {
  loading.value = true
  try {
    const gameList = await gameApi.getAll()
    // 查询每个游戏的标准结构状态
    const enriched = await Promise.all(
      gameList.map(async (game) => {
        try {
          const structInfo = await adminApi.getSafeStructure(game.id)
          return { ...game, ...structInfo }
        } catch {
          return { ...game, hasStructure: false, pathCount: 0 }
        }
      })
    )
    games.value = enriched
  } catch {
    ElMessage.error('加载游戏列表失败')
  } finally {
    loading.value = false
  }
})

function triggerUpload(gameId) {
  fileInputRefs[gameId]?.click()
}

async function handleFileChange(game, event) {
  const file = event.target.files?.[0]
  if (!file) return

  if (!file.name.toLowerCase().endsWith('.zip')) {
    ElMessage.error('只允许上传 .zip 文件')
    return
  }

  uploading.value = game.id
  try {
    const result = await adminApi.uploadSafeStructure(game.id, file)
    ElMessage.success(result.message || `标准结构已更新，共 ${result.pathCount} 条路径`)

    // 刷新当前游戏的状态
    const idx = games.value.findIndex(g => g.id === game.id)
    if (idx >= 0) {
      games.value[idx] = { ...games.value[idx], hasStructure: true, pathCount: result.pathCount }
    }
  } catch {
    // Axios interceptor already shows error
  } finally {
    uploading.value = null
    // 清空 file input 以允许重复上传同一文件
    event.target.value = ''
  }
}
</script>

<style scoped>
.admin-games {
  max-width: 1000px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.structure-info p {
  margin-bottom: 8px;
  color: #606266;
}

.structure-info ul {
  margin: 8px 0;
  padding-left: 20px;
}

.structure-info li {
  margin-bottom: 6px;
  color: #606266;
  line-height: 1.8;
}

.structure-info code {
  padding: 1px 4px;
  background: #f5f5f5;
  border-radius: 3px;
  font-size: 12px;
}

.structure-info .tip {
  margin-top: 12px;
  padding: 8px 12px;
  background: #fdf6ec;
  border-left: 3px solid #e6a23c;
  color: #b88230;
  font-size: 13px;
}
</style>
