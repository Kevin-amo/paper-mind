<script setup lang="ts">
defineProps<{
  variant?: 'primary' | 'dark';
}>();
</script>

<template>
  <main class="main-layout" :class="variant">
    <slot />
  </main>
</template>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: clamp(16px, 2.8vw, 28px);
  background: var(--app-bg);
}
</style>
