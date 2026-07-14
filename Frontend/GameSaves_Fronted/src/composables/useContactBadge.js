import { ref } from 'vue'
import { contactApi } from '@/api/contactApi'

// 全局单例 — 管理待处理联系留言计数（跨组件共享）
const pendingCount = ref(0)

export function useContactBadge() {
  async function refresh() {
    try {
      pendingCount.value = await contactApi.getPendingCount()
    } catch {
      // 静默失败 — 非管理员或网络问题
      pendingCount.value = 0
    }
  }

  /** 减少计数（管理员操作后调用） */
  function decrement() {
    if (pendingCount.value > 0) {
      pendingCount.value--
    }
  }

  return { pendingCount, refresh, decrement }
}
