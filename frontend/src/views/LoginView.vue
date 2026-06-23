<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import AuthLayout from '../layouts/AuthLayout.vue';
import { getErrorMessage } from '../api/http';
import { useAuth } from '../composables/useAuth';

const router = useRouter();
const route = useRoute();
const auth = useAuth();
const activeMode = ref<'login' | 'register'>('login');
const authTransitionName = ref('auth-pane-forward');
const loginLoading = ref(false);
const registerLoading = ref(false);
const codeLoading = ref(false);
const codeCountdown = ref(0);
let codeTimer: number | undefined;

const loginForm = reactive({
  username: '',
  password: '',
});

const registerForm = reactive({
  username: '',
  email: '',
  emailCode: '',
  password: '',
  confirmPassword: '',
});

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function getRedirectPath() {
  return typeof route.query.redirect === 'string' ? route.query.redirect : '/';
}

function handleAuthModeChange(mode: string | number) {
  if (mode !== 'login' && mode !== 'register') {
    return;
  }
  if (mode === activeMode.value) {
    return;
  }

  authTransitionName.value = activeMode.value === 'login' ? 'auth-pane-forward' : 'auth-pane-back';
  activeMode.value = mode;
}

function startCodeCountdown() {
  codeCountdown.value = 60;
  if (codeTimer !== undefined) {
    window.clearInterval(codeTimer);
  }
  codeTimer = window.setInterval(() => {
    codeCountdown.value -= 1;
    if (codeCountdown.value <= 0 && codeTimer !== undefined) {
      window.clearInterval(codeTimer);
      codeTimer = undefined;
    }
  }, 1000);
}

function validateRegisterForm() {
  const username = registerForm.username.trim();
  const email = registerForm.email.trim();
  const emailCode = registerForm.emailCode.trim();

  if (username.length < 3) {
    ElMessage.warning('用户名至少需要 3 个字符');
    return false;
  }
  if (!emailPattern.test(email)) {
    ElMessage.warning('请输入有效邮箱');
    return false;
  }
  if (registerForm.password.length < 6) {
    ElMessage.warning('密码至少需要 6 位');
    return false;
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致');
    return false;
  }
  if (!/^\d{6}$/.test(emailCode)) {
    ElMessage.warning('请输入 6 位邮箱验证码');
    return false;
  }
  return true;
}

async function handleLogin() {
  if (!loginForm.username.trim() || !loginForm.password) {
    ElMessage.warning('请输入用户名或密码');
    return;
  }
  loginLoading.value = true;
  try {
    await auth.login(loginForm.username.trim(), loginForm.password);
    ElMessage.success('登录成功');
    await router.replace(getRedirectPath());
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loginLoading.value = false;
  }
}

async function handleSendRegisterCode() {
  const email = registerForm.email.trim();
  if (!emailPattern.test(email)) {
    ElMessage.warning('请输入有效邮箱');
    return;
  }
  codeLoading.value = true;
  try {
    await auth.requestRegisterEmailCode(email);
    ElMessage.success('验证码已发送，请查收邮箱');
    startCodeCountdown();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    codeLoading.value = false;
  }
}

async function handleRegister() {
  if (!validateRegisterForm()) {
    return;
  }
  registerLoading.value = true;
  try {
    await auth.register(
      registerForm.username.trim(),
      registerForm.password,
      registerForm.email.trim(),
      registerForm.emailCode.trim(),
    );
    ElMessage.success('注册成功，已自动登录');
    await router.replace(getRedirectPath());
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    registerLoading.value = false;
  }
}

onUnmounted(() => {
  if (codeTimer !== undefined) {
    window.clearInterval(codeTimer);
  }
});
</script>

<template>
  <AuthLayout>
    <section class="login-shell">
      <aside class="brand-panel">
        <div>
          <p class="eyebrow">Paper Mind</p>
          <h1>论文知识库与智能评审系统</h1>
          <p class="brand-desc">
            面向论文资料管理、文档解析、RAG 问答与学术评审的一体化工作台。
          </p>
        </div>

        <div class="feature-grid">
          <div class="feature-card">
            <strong>文档管理</strong>
            <span>批量上传、解析状态、详情与分块查看</span>
          </div>
          <div class="feature-card">
            <strong>RAG 问答</strong>
            <span>会话持久化、上下文追踪、引用来源展示</span>
          </div>
          <div class="feature-card">
            <strong>论文评审</strong>
            <span>智能评审、质量评估与反馈生成</span>
          </div>
        </div>
      </aside>

      <el-card
        class="form-card animate zoom-in"
        shadow="never"
        v-animate="{ type: 'zoom-in', trigger: 'load', duration: '0.7s' }"
      >
        <div class="form-heading">
          <p>{{ activeMode === 'login' ? '账号登录' : '邮箱注册' }}</p>
          <h2>{{ activeMode === 'login' ? '欢迎回来' : '创建账号' }}</h2>
          <span>{{ activeMode === 'login' ? '登录后将根据角色进入对应工作台。' : '验证码将发送到你的邮箱，用于完成注册。' }}</span>
        </div>

        <el-tabs
          :model-value="activeMode"
          stretch
          class="auth-tabs"
          :class="`mode-${activeMode}`"
          @tab-change="handleAuthModeChange"
        >
          <el-tab-pane label="登录" name="login" />
          <el-tab-pane label="注册" name="register" />
        </el-tabs>

        <div class="auth-form-frame" :class="`mode-${activeMode}`">
          <Transition :name="authTransitionName" mode="out-in">
          <el-form v-if="activeMode === 'login'" key="login" label-position="top" @submit.prevent>
            <el-form-item label="用户名" required>
              <el-input
                v-model="loginForm.username"
                size="large"
                autocomplete="username"
                placeholder="请输入用户名"
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-form-item label="密码" required>
              <el-input
                v-model="loginForm.password"
                size="large"
                type="password"
                show-password
                autocomplete="current-password"
                placeholder="请输入密码"
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-button class="primary-button" type="primary" size="large" :loading="loginLoading" @click="handleLogin">
              登录
            </el-button>
          </el-form>

          <el-form v-else key="register" label-position="top" @submit.prevent>
            <el-form-item label="用户名" required>
              <el-input
                v-model="registerForm.username"
                size="large"
                autocomplete="username"
                placeholder="至少 3 个字符"
                @keyup.enter="handleRegister"
              />
            </el-form-item>
            <el-form-item label="邮箱" required>
              <el-input v-model="registerForm.email" size="large" autocomplete="email" placeholder="name@example.com">
                <template #append>
                  <el-button :loading="codeLoading" :disabled="codeCountdown > 0" @click="handleSendRegisterCode">
                    {{ codeCountdown > 0 ? `${codeCountdown}s` : '发送验证码' }}
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="邮箱验证码" required>
              <el-input
                v-model="registerForm.emailCode"
                size="large"
                maxlength="6"
                autocomplete="one-time-code"
                placeholder="6 位数字验证码"
                @keyup.enter="handleRegister"
              />
            </el-form-item>
            <el-form-item label="密码" required>
              <el-input
                v-model="registerForm.password"
                size="large"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="至少 6 位"
                @keyup.enter="handleRegister"
              />
            </el-form-item>
            <el-form-item label="确认密码" required>
              <el-input
                v-model="registerForm.confirmPassword"
                size="large"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="再次输入密码"
                @keyup.enter="handleRegister"
              />
            </el-form-item>
            <el-button class="primary-button" type="primary" size="large" :loading="registerLoading" @click="handleRegister">
              注册并登录
            </el-button>
          </el-form>
          </Transition>
        </div>
      </el-card>
    </section>
  </AuthLayout>
</template>

<style scoped>
.login-shell {
  --auth-accent: var(--app-primary);
  --auth-accent-strong: var(--app-primary-active);
  --auth-ink: var(--app-text);
  --auth-muted: var(--app-text-muted);
  --auth-left-bg: #24211d;
  --auth-left-border: rgba(250, 249, 245, 0.09);
  --auth-left-surface: rgba(250, 249, 245, 0.055);
  --auth-right-bg: #f7f3ec;
  width: min(1080px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(390px, 0.98fr);
  overflow: hidden;
  border: 1px solid #e8ded2;
  border-radius: var(--app-radius-xl);
  background: var(--auth-right-bg);
  box-shadow: var(--app-shadow-xl);
}

.brand-panel {
  position: relative;
  min-height: 640px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 38px;
  overflow: hidden;
  padding: clamp(38px, 5vw, 62px);
  color: var(--app-on-dark);
  background: var(--auth-left-bg);
}

.brand-panel::before {
  position: absolute;
  top: 34px;
  left: 38px;
  width: 34px;
  height: 34px;
  border-radius: 999px;
  background:
    linear-gradient(var(--app-text), var(--app-text)) center / 2px 24px no-repeat,
    linear-gradient(90deg, var(--app-text), var(--app-text)) center / 24px 2px no-repeat,
    linear-gradient(45deg, transparent 47%, var(--app-text) 48%, var(--app-text) 52%, transparent 53%) center / 24px 24px no-repeat,
    linear-gradient(-45deg, transparent 47%, var(--app-text) 48%, var(--app-text) 52%, transparent 53%) center / 24px 24px no-repeat,
    var(--app-surface);
  content: '';
}

.brand-panel > * {
  position: relative;
  z-index: 1;
}

.eyebrow {
  margin: 18px 0 12px;
  color: var(--auth-accent);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 1.5px;
  text-transform: uppercase;
}

.brand-panel h1 {
  max-width: 540px;
  margin: 0;
  color: var(--app-on-dark);
  font-size: clamp(38px, 5vw, 64px);
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 1.05;
}

.brand-desc {
  max-width: 520px;
  margin: 22px 0 0;
  color: var(--app-on-dark-soft);
  font-size: 16px;
  line-height: 1.85;
}

.feature-grid {
  display: grid;
  gap: 14px;
}

.feature-card {
  padding: 17px 18px;
  border: 1px solid var(--auth-left-border);
  border-radius: var(--app-radius-lg);
  background: var(--auth-left-surface);
  box-shadow: none;
}

.feature-card strong {
  display: block;
  margin-bottom: 6px;
  color: var(--app-on-dark);
  font-size: 15px;
  font-weight: 500;
}

.feature-card span {
  color: var(--app-on-dark-soft);
  font-size: 13px;
  line-height: 1.65;
}

.form-card {
  display: flex;
  align-items: center;
  padding: clamp(34px, 4.5vw, 52px);
  border: none;
  border-left: 1px solid var(--app-border);
  border-radius: 0;
  background: var(--auth-right-bg);
  box-shadow: none;
}

.form-card :deep([class~="el-card__body"]) {
  width: 100%;
  padding-top: 24px;
}

.form-heading {
  margin-bottom: 22px;
}

.form-heading p {
  margin: 0 0 8px;
  color: var(--auth-accent);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 1.5px;
  text-transform: uppercase;
}

.form-heading h2 {
  margin: 0;
  color: var(--auth-ink);
  font-size: 36px;
  font-weight: 500;
  letter-spacing: -0.02em;
}

.form-heading span {
  display: block;
  margin-top: 9px;
  color: var(--auth-muted);
  line-height: 1.65;
}

.auth-tabs {
  --auth-tab-offset: 0%;
  margin-top: 12px;
}

.auth-tabs[class~="mode-register"] {
  --auth-tab-offset: 100%;
}

.auth-tabs :deep([class~="el-tabs__header"]) {
  margin-bottom: 24px;
}

.auth-tabs :deep([class~="el-tabs__content"]) {
  display: none;
}

.auth-tabs :deep([class~="el-tabs__nav-wrap"]::after),
.auth-tabs :deep([class~="el-tabs__active-bar"]) {
  display: none;
}

.auth-tabs :deep([class~="el-tabs__nav-scroll"]) {
  overflow: hidden;
  padding: 4px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: var(--app-surface-soft);
}

.auth-tabs :deep([class~="el-tabs__nav"]) {
  position: relative;
  width: 100%;
}

.auth-tabs :deep([class~="el-tabs__nav"]::before) {
  position: absolute;
  inset: 0 auto 0 0;
  z-index: 0;
  width: 50%;
  border-radius: 999px;
  background: var(--app-surface);
  box-shadow: 0 1px 3px rgba(20, 20, 19, 0.08);
  content: '';
  transform: translateX(var(--auth-tab-offset));
  transition: transform 0.34s cubic-bezier(0.22, 1, 0.36, 1), box-shadow 0.2s ease;
}

.auth-tabs :deep([class~="el-tabs__item"]) {
  z-index: 1;
  height: 38px;
  border-radius: 999px;
  color: var(--auth-muted);
  font-weight: 500;
  transition: color 0.18s ease;
}

.auth-tabs :deep([class~="el-tabs__item"][class~="is-active"]) {
  background: transparent;
  box-shadow: none;
  color: var(--auth-ink);
  transform: none;
}

.auth-form-frame {
  overflow: visible;
  height: 238px;
  transition: height 0.46s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: height;
}

.auth-form-frame[class~="mode-register"] {
  height: 510px;
}

.auth-form-frame :deep([class~="el-form"]) {
  padding-bottom: 28px;
}

[class~="auth-pane-forward-enter-active"],
[class~="auth-pane-forward-leave-active"],
[class~="auth-pane-back-enter-active"],
[class~="auth-pane-back-leave-active"] {
  transition: opacity 0.2s ease, transform 0.26s cubic-bezier(0.22, 1, 0.36, 1);
}

[class~="auth-pane-forward-enter-from"],
[class~="auth-pane-back-leave-to"] {
  opacity: 0;
  transform: translateX(18px) scale(0.985);
}

[class~="auth-pane-forward-leave-to"],
[class~="auth-pane-back-enter-from"] {
  opacity: 0;
  transform: translateX(-18px) scale(0.985);
}

.form-card :deep([class~="el-form-item"]) {
  margin-bottom: 17px;
}

.form-card :deep([class~="el-form-item__label"]) {
  padding-bottom: 7px;
  color: var(--auth-ink);
  font-size: 13px;
  font-weight: 500;
}

.form-card :deep([class~="el-input__wrapper"]) {
  min-height: 48px;
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
  box-shadow: 0 0 0 1px var(--app-border) inset;
  transition: box-shadow 0.18s ease;
}

.form-card :deep([class~="el-input__wrapper"]:hover) {
  background: var(--app-surface);
  box-shadow: 0 0 0 1px var(--app-border-strong) inset;
}

.form-card :deep([class~="el-input__wrapper"][class~="is-focus"]) {
  background: var(--app-surface);
  box-shadow:
    0 0 0 1px var(--app-primary) inset,
    var(--app-shadow-focus);
}

.form-card :deep([class~="el-input__inner"]) {
  color: var(--auth-ink);
  font-weight: 500;
}

.form-card :deep([class~="el-input__inner"]::placeholder) {
  color: var(--app-text-subtle);
  font-weight: 500;
}

.form-card :deep([class~="el-input-group__append"]) {
  border-radius: 0 var(--app-radius-md) var(--app-radius-md) 0;
  background: var(--app-surface-soft);
  box-shadow: inset 1px 0 0 var(--app-border);
}

.form-card :deep([class~="el-input-group__append"] [class~="el-button"]) {
  border: none;
  color: var(--auth-accent);
  font-weight: 800;
}

.primary-button {
  width: 100%;
  height: 48px;
  margin-top: 10px;
  border: none;
  border-radius: var(--app-radius-md);
  background: var(--app-primary);
  box-shadow: none;
  color: #ffffff;
  font-weight: 500;
  letter-spacing: 0;
}

.primary-button:hover,
.primary-button:focus {
  border: none;
  background: var(--app-primary-hover);
  box-shadow: none;
  color: #ffffff;
}

.primary-button[class~="is-disabled"],
.primary-button[class~="is-loading"] {
  box-shadow: none;
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    min-height: auto;
  }

  .form-card {
    border-top: 1px solid rgba(255, 255, 255, 0.58);
    border-left: none;
  }

  .form-card :deep([class~="el-card__body"]) {
    padding-top: 0;
  }
}

@media (max-width: 520px) {
  .login-shell {
    border-radius: 28px;
  }

  .brand-panel,
  .form-card {
    padding: 28px;
  }

  .brand-panel h1 {
    font-size: 34px;
  }
}
</style>
