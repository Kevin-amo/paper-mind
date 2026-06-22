<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Clock,
  DataAnalysis,
  Files,
  Setting,
  SwitchButton,
  User,
} from '@element-plus/icons-vue';
import { useAuth } from '../../composables/useAuth';
import LogoutConfirmDialog from '../common/LogoutConfirmDialog.vue';

type AdminSection = 'users' | 'config' | 'tasks' | 'criteria' | 'audit-logs';

const props = defineProps<{
  active: AdminSection;
  loading?: boolean;
  title?: string;
}>();

const router = useRouter();
const route = useRoute();
const auth = useAuth();
const logoutDialogVisible = ref(false);

const navItems: Array<{
  key: AdminSection;
  title: string;
  description: string;
  path: string;
  query?: Record<string, string>;
  icon: unknown;
}> = [
  {
    key: 'users',
    title: '用户管理',
    description: '账号、角色、状态',
    path: '/admin',
    icon: User,
  },
  {
    key: 'config',
    title: '批次与小组',
    description: '批次、组长、成员',
    path: '/admin/reviews',
    query: { tab: 'config' },
    icon: Setting,
  },
  {
    key: 'tasks',
    title: '全局进度',
    description: '任务、进度',
    path: '/admin/reviews',
    query: { tab: 'tasks' },
    icon: Files,
  },
  {
    key: 'criteria',
    title: '评审指标',
    description: '评分标准维护',
    path: '/admin/reviews',
    query: { tab: 'criteria' },
    icon: DataAnalysis,
  },
  {
    key: 'audit-logs',
    title: '审计日志',
    description: '操作历史追溯',
    path: '/admin/audit-logs',
    icon: Clock,
  },
];

const currentUserName = computed(() => auth.state.user?.displayName || auth.state.user?.username || '管理员');
const pageTitle = computed(() => props.title || navItems.find((item) => item.key === props.active)?.title || '管理后台');

function isCurrentItem(item: (typeof navItems)[number]) {
  if (route.path !== item.path) return false;
  if (!item.query?.tab) return props.active === item.key;
  return route.query.tab === item.query.tab || props.active === item.key;
}

async function navigate(item: (typeof navItems)[number]) {
  if (isCurrentItem(item)) return;
  await router.push({ path: item.path, query: item.query });
}

async function handleLogout() {
  await auth.logout();
  await router.replace('/login');
}
</script>

<template>
  <div class="admin-layout" v-loading="props.loading">
    <aside class="admin-sidebar" aria-label="管理后台导航">
      <div class="sidebar-brand">
        <div class="brand-logo">
          <svg width="28" height="28" viewBox="0 0 28 28" aria-hidden="true" fill="none">
            <circle cx="14" cy="14" r="13" fill="#faf9f5"/>
            <path d="M14 4.5v19M4.5 14h19M7.3 7.3l13.4 13.4M20.7 7.3 7.3 20.7" stroke="#141413" stroke-width="1.8" stroke-linecap="round"/>
            <circle cx="14" cy="14" r="2.8" fill="#cc785c"/>
          </svg>
        </div>
        <div class="brand-text">
          <strong>PaperMind</strong>
          <span>管理控制台</span>
        </div>
      </div>

      <nav class="sidebar-menu">
        <button
          v-for="item in navItems"
          :key="item.key"
          class="sidebar-menu-item"
          :class="{ active: props.active === item.key }"
          type="button"
          :aria-current="props.active === item.key ? 'page' : undefined"
          @click="navigate(item)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <div class="menu-item-text">
            <span>{{ item.title }}</span>
            <small>{{ item.description }}</small>
          </div>
        </button>
      </nav>

      <div class="sidebar-footer">
        <div class="sidebar-user">
          <div class="user-avatar">{{ currentUserName.charAt(0) }}</div>
          <div class="user-info">
            <span class="user-name">{{ currentUserName }}</span>
            <span class="user-role">管理员</span>
          </div>
        </div>
      </div>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <div class="topbar-left">
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="topbar-right">
          <el-button v-if="auth.hasRole('USER')" size="small" @click="router.push('/user')">用户端</el-button>
          <el-divider v-if="auth.hasRole('USER')" direction="vertical" />
          <el-button :icon="SwitchButton" text @click="logoutDialogVisible = true">退出</el-button>
        </div>
      </header>

      <LogoutConfirmDialog v-model="logoutDialogVisible" @confirm="handleLogout" />

      <main class="admin-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  background: var(--app-bg);
  color: var(--app-text);
  overflow-x: hidden;
}

.admin-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--app-dark);
  border-right: 1px solid var(--app-dark-elevated);
  overflow-y: auto;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 18px;
  border-bottom: 1px solid var(--app-dark-elevated);
}

.brand-logo {
  flex-shrink: 0;
}

.brand-text strong {
  display: block;
  color: var(--app-on-dark);
  font-family: "Cormorant Garamond", "EB Garamond", Georgia, serif;
  font-size: 22px;
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.brand-text span {
  display: block;
  color: var(--app-on-dark-soft);
  font-size: 12px;
  font-weight: 500;
}

.sidebar-menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 14px 10px;
}

.sidebar-menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 0;
  border-radius: var(--app-radius-md);
  padding: 11px 12px;
  background: transparent;
  color: var(--app-on-dark-soft);
  cursor: pointer;
  text-align: left;
  transition: color 0.25s ease-in-out, background-color 0.25s ease-in-out;
}

.sidebar-menu-item:hover {
  background: var(--app-dark-elevated);
  color: var(--app-on-dark);
}

.sidebar-menu-item.active {
  background: var(--app-primary);
  color: var(--app-text-on-primary);
}

.sidebar-menu-item .el-icon {
  flex-shrink: 0;
  font-size: 18px;
}

.menu-item-text span {
  display: block;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}

.menu-item-text small {
  display: block;
  color: var(--app-on-dark-soft);
  font-size: 11px;
  font-weight: 400;
  margin-top: 2px;
}

.sidebar-menu-item.active .menu-item-text small {
  color: rgba(255, 255, 255, 0.78);
  opacity: 1;
}

.sidebar-footer {
  padding: 14px 10px;
  border-top: 1px solid var(--app-dark-elevated);
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--app-radius-md);
  background: var(--app-dark-elevated);
}

.user-avatar {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--app-radius-full, 9999px);
  background: var(--app-surface);
  color: var(--app-text);
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-info {
  min-width: 0;
}

.user-name {
  display: block;
  color: var(--app-on-dark);
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  display: block;
  color: var(--app-on-dark-soft);
  font-size: 11px;
}

.admin-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.admin-topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 64px;
  border-bottom: 1px solid var(--app-border);
  background: rgba(250, 249, 245, 0.92);
  padding: 0 28px;
  backdrop-filter: blur(14px);
}

.topbar-left h1 {
  margin: 0;
  color: var(--app-text);
  font-size: 28px;
  font-weight: 500;
  white-space: nowrap;
}

.topbar-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.topbar-right :deep(.el-divider--vertical) {
  height: 20px;
  margin: 0 4px;
}

.admin-content {
  display: grid;
  gap: 20px;
  min-width: 0;
  padding: 28px;
  overflow-x: auto;
}

.admin-content > :deep(*) {
  min-width: 0;
}

@media (max-width: 1120px) {
  .admin-topbar {
    flex-wrap: wrap;
    padding: 12px 24px;
  }

}

@media (max-width: 860px) {
  .admin-layout {
    grid-template-columns: 1fr;
  }

  .admin-sidebar {
    position: static;
    height: auto;
    border-right: 0;
    border-bottom: 1px solid var(--app-border);
  }

  .sidebar-menu {
    flex-direction: row;
    overflow-x: auto;
    padding: 8px;
  }

  .sidebar-footer {
    display: none;
  }

  .admin-topbar {
    position: static;
  }
}

@media (max-width: 560px) {
  .sidebar-menu {
    flex-direction: column;
  }

  .admin-content {
    padding: 16px;
  }

  .topbar-right {
    flex-wrap: wrap;
  }
}
</style>
