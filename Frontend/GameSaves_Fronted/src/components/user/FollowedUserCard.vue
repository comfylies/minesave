<template>
  <router-link :to="`/users/${user.id}`" class="followed-user-card">
    <el-avatar :size="56" :src="user.avatarSmallUrl || user.avatarUrl">
      {{ (user.nickname || user.username)?.charAt(0) }}
    </el-avatar>
    <div class="user-copy">
      <h3>{{ user.nickname || user.username }}</h3>
      <p>@{{ user.username }}</p>
      <p v-if="user.bio" class="user-bio">{{ user.bio }}</p>
    </div>
    <el-button plain size="small" @click.prevent.stop="$emit('unfollow', user.id)">已关注</el-button>
  </router-link>
</template>

<script setup>
defineProps({ user: { type: Object, required: true } })
defineEmits(['unfollow'])
</script>

<style scoped>
.followed-user-card { display:flex; align-items:center; gap:var(--spacing-md); padding:var(--spacing-md); border:1px solid var(--color-border-primary); border-radius:var(--radius-md); color:inherit; text-decoration:none; }
.followed-user-card:hover { border-color:var(--color-link); text-decoration:none; }.user-copy { flex:1; min-width:0; }.user-copy h3,.user-copy p { margin:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.user-copy h3 { font-size:var(--font-size-large); }.user-copy p { color:var(--color-secondary-text); font-size:var(--font-size-small); margin-top:2px; }.user-bio { white-space:normal !important; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; }
</style>
