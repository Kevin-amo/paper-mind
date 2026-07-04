<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import MainLayout from '../../layouts/MainLayout.vue';
import LogoutConfirmDialog from '../../components/common/LogoutConfirmDialog.vue';
import ReviewTaskList from './components/ReviewTaskList.vue';
import ReviewParseTab from './components/ReviewParseTab.vue';
import ReviewScoresTab from './components/ReviewScoresTab.vue';
import ReviewRisksTab from './components/ReviewRisksTab.vue';
import ReviewCommentsTab from './components/ReviewCommentsTab.vue';
import ReviewAuditTab from './components/ReviewAuditTab.vue';
import { formatDate } from '../../utils/format';
import { getErrorMessage } from '../../api/http';
import { useAuth } from '../../composables/useAuth';
import { useReviews } from '../../composables/useReviews';
import { useReviewLeaderAccess } from '../../composables/useReviewLeaderAccess';
import type { PaperStructuredContent, ReviewAssignmentStatus, ReviewScoreItem } from '../../types';

type ReviewTabKey = 'parse' | 'risks' | 'scores' | 'comments' | 'audit';

const router = useRouter();
const auth = useAuth();
const reviews = useReviews();
const { canAccessLeaderWorkspace, refreshLeaderWorkspaceAccess } = useReviewLeaderAccess();
const activeReviewTab = ref<ReviewTabKey>('parse');
const logoutDialogVisible = ref(false);

const reviewSteps: Array<{ key: ReviewTabKey; label: string; caption: string }> = [
  { key: 'parse', label: '论文概览', caption: '结构解析' },
  { key: 'risks', label: 'AI 辅助', caption: '风险提示' },
  { key: 'scores', label: '评分表', caption: '多维调整' },
  { key: 'comments', label: '评语', caption: '意见整理' },
  { key: 'audit', label: '提交', caption: '留档记录' },
];

const selectedTask = computed(() => reviews.selectedTask.value);
const selectedReport = computed(() => reviews.selectedReport.value);
const assignmentSubmitted = computed(() => selectedTask.value?.currentAssignment?.status === 'SUBMITTED');
const showLeaderEmptyState = computed(() => canAccessLeaderWorkspace.value && !reviews.loading.value && reviews.tasks.value.length === 0);
const structuredParse = computed(() => reviews.structuredParse.value);
const structuredContent = computed(() => {
  const merged = structuredParse.value?.mergedResult;
  if (merged && typeof merged === 'object') {
    return merged as PaperStructuredContent;
  }
  return selectedReport.value?.paperSections as Partial<PaperStructuredContent> ?? {};
});
const missingFields = computed(() => structuredParse.value?.missingFields ?? []);
const lowConfidenceFields = computed(() => structuredParse.value?.lowConfidenceFields ?? []);
const scoreItems = computed(() => (Array.isArray(selectedReport.value?.scores) ? selectedReport.value?.scores as ReviewScoreItem[] : []));
const riskRecords = computed(() => reviews.riskRecords.value);
const comments = computed(() => selectedReport.value?.comments && typeof selectedReport.value.comments === 'object'
  ? selectedReport.value.comments as Record<string, unknown>
  : {});

const reviewSummary = computed(() => {
  const summary = comments.value.summary || comments.value.finalAdvice || selectedReport.value?.finalRecommendation;
  if (typeof summary === 'string' && summary.trim()) {
    return summary.trim();
  }
  if (selectedReport.value) {
    return '辅助评审已生成，建议结合评分表、风险提示和人工判断完成最终意见。';
  }
  return '尚未生成辅助评审。先运行 AI 辅助评审，再进行评分与评语调整。';
});

const reportScoreLabel = computed(() => {
  if (!selectedReport.value) {
    return '--';
  }
  const score = selectedReport.value.totalScore ?? reviews.reportForm.totalScore;
  return Number.isFinite(Number(score)) ? `${Math.round(Number(score))}` : '--';
});

const bottomStatusText = computed(() => {
  if (assignmentSubmitted.value) {
    return '本次个人评审已提交，报告将进入只读留档状态。';
  }
  if (!selectedReport.value) {
    return '生成辅助评审后，可保存草稿并提交个人评审。';
  }
  return '草稿会保存当前评分与最终意见；提交后将锁定本次个人评审报告。';
});

function handlePageChange(page: number) {
  reviews.loadTasks(page - 1);
}

function handleScoreInput(code: string, value: number) {
  reviews.updateScore(code, value);
}

function updateTaskKeyword(value: string) {
  reviews.keyword.value = value;
}

function applyStatusFilter(value: ReviewAssignmentStatus | '') {
  reviews.statusFilter.value = value;
  reviews.loadTasks(0);
}

function focusCriteria() {
  activeReviewTab.value = 'scores';
}

function setActiveReviewTab(key: ReviewTabKey) {
  activeReviewTab.value = key;
}

function isReviewStepDone(key: ReviewTabKey) {
  if (key === 'parse') {
    return Boolean(structuredParse.value || selectedTask.value?.document);
  }
  if (key === 'risks') {
    return Boolean(selectedReport.value);
  }
  if (key === 'scores') {
    return scoreItems.value.length > 0;
  }
  if (key === 'comments') {
    return Boolean(selectedReport.value?.finalRecommendation || comments.value.summary || comments.value.finalAdvice);
  }
  return assignmentSubmitted.value;
}

async function handleLogout() {
  await auth.logout();
  await router.replace('/login');
}

onMounted(async () => {
  try {
    if (!auth.state.user) {
      await auth.hydrateCurrentUser();
    }
    await Promise.all([
      reviews.loadCriteria(),
      reviews.loadTasks(0),
      refreshLeaderWorkspaceAccess(),
    ]);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
    await router.replace('/login');
  }
});
</script>

<template>
  <MainLayout class="review-page paper-mind-review-shell">
    <header class="review-top-nav">
      <button class="review-brand" type="button" @click="router.push('/review')">
        <span class="review-brand-mark" aria-hidden="true"><span></span></span>
        <span>Paper Mind</span>
      </button>

      <nav class="review-nav-links" aria-label="主导航">
        <button v-if="canAccessLeaderWorkspace" class="review-nav-link" type="button" @click="router.push('/review-leader')">
          组长面板
        </button>
        <button class="review-nav-link active" type="button">评审中心</button>
        <button v-if="auth.hasRole('USER')" class="review-nav-link" type="button" @click="router.push('/user')">用户端</button>
        <button v-if="auth.isAdmin.value" class="review-nav-link" type="button" @click="router.push('/admin')">管理后台</button>
      </nav>

      <div class="review-nav-actions">
        <el-button text @click="logoutDialogVisible = true">退出</el-button>
      </div>
    </header>

    <LogoutConfirmDialog v-model="logoutDialogVisible" @confirm="handleLogout" />

    <main class="review-workspace">
      <section class="review-hero">
        <div class="review-hero-copy">
          <p class="review-eyebrow">Review Workspace</p>
          <h1>论文评审中心</h1>
          <p>
            像收件箱一样处理评审任务：左侧选择论文，右侧完成解析、AI 辅助、评分、评语和提交。
          </p>
        </div>

        <aside class="review-hero-summary" aria-label="评审概览">
          <div>
            <span>待评审</span>
            <strong>{{ reviews.pendingCount.value }}</strong>
          </div>
          <div>
            <span>评审中</span>
            <strong>{{ reviews.reviewingCount.value }}</strong>
          </div>
          <div>
            <span>已提交</span>
            <strong>{{ reviews.completedCount.value }}</strong>
          </div>
        </aside>
      </section>

      <section class="review-filter-chips" aria-label="任务筛选">
        <button class="review-filter-chip" :class="{ active: reviews.statusFilter.value === '' }" type="button" @click="applyStatusFilter('')">
          全部 {{ reviews.pagination.total || reviews.tasks.value.length }}
        </button>
        <button class="review-filter-chip" :class="{ active: reviews.statusFilter.value === 'ASSIGNED' }" type="button" @click="applyStatusFilter('ASSIGNED')">
          待评审 {{ reviews.pendingCount.value }}
        </button>
        <button class="review-filter-chip" :class="{ active: reviews.statusFilter.value === 'REVIEWING' }" type="button" @click="applyStatusFilter('REVIEWING')">
          评审中 {{ reviews.reviewingCount.value }}
        </button>
        <button class="review-filter-chip" :class="{ active: reviews.statusFilter.value === 'SUBMITTED' }" type="button" @click="applyStatusFilter('SUBMITTED')">
          已提交 {{ reviews.completedCount.value }}
        </button>
      </section>

      <section class="review-layout">
        <ReviewTaskList
          :tasks="reviews.tasks.value"
          :selected-task-id="selectedTask?.id ?? null"
          :loading="reviews.loading.value"
          :keyword="reviews.keyword.value"
          :pagination="reviews.pagination"
          @update:keyword="updateTaskKeyword"
          @select="reviews.selectTask"
          @search="reviews.loadTasks(0)"
          @page-change="handlePageChange"
        />

        <main class="review-detail" v-loading="reviews.detailLoading.value">
          <template v-if="selectedTask">
            <article class="review-paper-header">
              <div class="review-paper-header-top">
                <div class="review-paper-title">
                  <p class="review-eyebrow">Selected Paper</p>
                  <h2>{{ selectedTask.title }}</h2>
                  <div class="review-paper-meta">
                    <span>{{ selectedTask.sourceId }}</span>
                    <span>{{ formatDate(selectedTask.createdAt) }}</span>
                    <span v-if="selectedTask.dueAt">截止 {{ formatDate(selectedTask.dueAt) }}</span>
                  </div>
                </div>
                <div class="review-paper-actions">
                  <el-button
                    type="primary"
                    class="regenerate-review-button"
                    :disabled="assignmentSubmitted"
                    :loading="reviews.generating.value"
                    @click="reviews.runAiReview"
                  >
                    {{ selectedReport ? '重新生成辅助评审' : '生成辅助评审' }}
                  </el-button>
                </div>
              </div>

              <div class="review-progress-strip" aria-label="评审流程">
                <button
                  v-for="step in reviewSteps"
                  :key="step.key"
                  class="review-step"
                  :class="{ active: activeReviewTab === step.key, done: activeReviewTab !== step.key && isReviewStepDone(step.key) }"
                  type="button"
                  @click="setActiveReviewTab(step.key)"
                >
                  <span>{{ step.label }}</span>
                  <small>{{ step.caption }}</small>
                </button>
              </div>

              <div class="review-summary-strip" aria-label="当前论文评审摘要">
                <article class="review-summary-item">
                  <span>当前总分</span>
                  <strong>{{ reportScoreLabel }}</strong>
                  <p>评分表调整后，保存草稿会重新计算总分。</p>
                </article>
                <article class="review-summary-item review-summary-ai">
                  <span>AI 辅助摘要</span>
                  <p>{{ reviewSummary }}</p>
                </article>
                <article class="review-summary-item">
                  <span>风险提示</span>
                  <strong>{{ riskRecords.length }}</strong>
                  <p>高风险项建议在提交前逐条确认或忽略。</p>
                </article>
                <article class="review-summary-item">
                  <span>工作状态</span>
                  <strong>{{ assignmentSubmitted ? '已提交' : selectedReport ? '可编辑' : '待生成' }}</strong>
                  <p>{{ assignmentSubmitted ? '本次评审已进入只读留档。' : '右侧工作台内完成评分、评语和提交。' }}</p>
                </article>
              </div>
            </article>

            <section class="review-work-area">
              <div class="review-tab-surface">
                <ReviewParseTab
                  v-if="activeReviewTab === 'parse'"
                  :structured-parse="structuredParse"
                  :structured-content="structuredContent"
                  :selected-task="selectedTask"
                  :missing-fields="missingFields"
                  :low-confidence-fields="lowConfidenceFields"
                  :assignment-submitted="assignmentSubmitted"
                  :structured-parse-loading="reviews.structuredParseLoading.value"
                  :regenerating-structured-parse="reviews.regeneratingStructuredParse.value"
                  @rerun-structured-parse="reviews.rerunStructuredParse"
                />

                <ReviewRisksTab
                  v-else-if="activeReviewTab === 'risks'"
                  :risk-records="riskRecords"
                  :risk-loading="reviews.riskLoading.value"
                  :risk-status-updating-ids="reviews.riskStatusUpdatingIds.value"
                  @set-risk-status="reviews.setRiskStatus"
                />

                <ReviewScoresTab
                  v-else-if="activeReviewTab === 'scores'"
                  :score-items="scoreItems"
                  :selected-report="selectedReport"
                  :assignment-submitted="assignmentSubmitted"
                  :saving="reviews.saving.value"
                  :submitting-assignment="reviews.submittingAssignment.value"
                  :report-form="reviews.reportForm"
                  :criteria="reviews.criteria.value"
                  @update-score="handleScoreInput"
                  @save-report="reviews.saveReport"
                  @submit-assignment="reviews.submitCurrentAssignment"
                />

                <ReviewCommentsTab
                  v-else-if="activeReviewTab === 'comments'"
                  :comments="comments"
                  :selected-report="selectedReport"
                />

                <ReviewAuditTab v-else :selected-report="selectedReport" :task-id="selectedTask?.id ?? null" />
              </div>
            </section>
          </template>

          <section v-else class="review-empty-state">
            <div class="review-doc-icon" aria-hidden="true"></div>
            <strong>{{ showLeaderEmptyState ? '当前没有个人评审任务' : '请选择一篇论文开始评审' }}</strong>
            <p>
              {{ showLeaderEmptyState ? '作为组长，你可以前往组长工作台分配任务或主动加入评审。' : '从左侧任务收件箱选择论文后，这里会展示解析、评分和提交流程。' }}
            </p>
            <el-button v-if="showLeaderEmptyState" type="primary" @click="router.push('/review-leader')">
              前往组长工作台
            </el-button>
          </section>
        </main>
      </section>
    </main>

    <footer v-if="selectedTask" class="review-bottom-bar">
      <p>{{ bottomStatusText }}</p>
      <div class="review-bottom-actions">
        <el-button :disabled="assignmentSubmitted || !selectedReport" :loading="reviews.saving.value" @click="reviews.saveReport">
          保存草稿
        </el-button>
        <el-popconfirm title="提交后个人评审将只读，是否继续？" @confirm="reviews.submitCurrentAssignment">
          <template #reference>
            <el-button
              type="primary"
              class="review-submit-button"
              :disabled="assignmentSubmitted || reviews.submittingAssignment.value || !selectedReport"
              :loading="reviews.submittingAssignment.value"
            >
              提交评审
            </el-button>
          </template>
        </el-popconfirm>
      </div>
    </footer>
  </MainLayout>
</template>

<style scoped>
.review-page {
  --review-page-gutter: 36px;
  --review-list-width: 360px;
  --review-layout-gap: 26px;
  --review-shell-width: min(calc(100vw - (var(--review-page-gutter) * 2)), 1360px);
  --review-shell-left: calc((100vw - var(--review-shell-width)) / 2);
  --review-workbench-left: calc(var(--review-shell-left) + var(--review-list-width) + var(--review-layout-gap));
  --review-workbench-width: calc(var(--review-shell-width) - var(--review-list-width) - var(--review-layout-gap));
  gap: 0;
  min-height: 100vh;
  padding: 0 var(--review-page-gutter) 104px;
  background: var(--claude-canvas);
}

.review-page :deep(.el-button) {
  border-radius: var(--app-radius-md);
}

.review-top-nav,
.review-workspace {
  width: var(--review-shell-width);
  margin-inline: auto;
}

.review-top-nav {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 24px;
  min-height: 64px;
  border-bottom: 1px solid var(--app-border-light);
}

.review-brand,
.review-nav-links {
  display: flex;
  align-items: center;
}

.review-brand {
  gap: 10px;
  border: 0;
  background: transparent;
  color: var(--app-text);
  padding: 0;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.review-brand-mark {
  position: relative;
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
}

.review-brand-mark::before,
.review-brand-mark::after,
.review-brand-mark span::before,
.review-brand-mark span::after {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 18px;
  height: 2px;
  border-radius: 999px;
  background: var(--app-text);
  content: "";
  transform: translate(-50%, -50%);
}

.review-brand-mark::after {
  transform: translate(-50%, -50%) rotate(90deg);
}

.review-brand-mark span::before {
  transform: translate(-50%, -50%) rotate(45deg);
}

.review-brand-mark span::after {
  transform: translate(-50%, -50%) rotate(-45deg);
}

.review-nav-links {
  gap: 4px;
}

.review-nav-link {
  min-height: 36px;
  border: 0;
  border-radius: var(--app-radius-md);
  background: transparent;
  color: var(--app-text-muted);
  padding: 8px 14px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.review-nav-link.active,
.review-nav-link:hover {
  background: var(--app-surface-soft);
  color: var(--app-text);
}

.review-nav-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.review-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 424px;
  gap: 28px;
  align-items: end;
  padding: 38px 0 22px;
}

.review-eyebrow {
  margin: 0 0 8px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1.4;
  text-transform: uppercase;
}

.review-hero h1 {
  margin: 0;
  color: var(--app-text);
  font-family: var(--claude-serif);
  font-size: clamp(38px, 4vw, 58px);
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1.08;
}

.review-hero-copy p:last-child {
  max-width: 840px;
  margin: 14px 0 0;
  color: var(--app-text-muted);
  font-size: 15px;
  line-height: 1.7;
}

.review-hero-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-border);
}

.review-hero-summary div {
  min-height: 96px;
  background: var(--app-surface-soft);
  padding: 18px;
}

.review-hero-summary span {
  display: block;
  color: var(--app-text-muted);
  font-size: 13px;
  font-weight: 500;
}

.review-hero-summary strong {
  display: block;
  margin-top: 8px;
  color: var(--app-text);
  font-family: var(--claude-serif);
  font-size: 32px;
  font-weight: 500;
  line-height: 1;
}

.review-layout {
  display: grid;
  grid-template-columns: var(--review-list-width) minmax(0, 1fr);
  gap: var(--review-layout-gap);
  align-items: start;
}

.review-filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.review-filter-chip {
  min-height: 34px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: var(--app-surface);
  color: var(--app-text-muted);
  padding: 7px 13px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.review-filter-chip.active,
.review-filter-chip:hover {
  border-color: rgba(204, 120, 92, 0.34);
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.review-detail {
  min-width: 0;
  display: grid;
  gap: 16px;
  height: calc(100vh - 250px);
  min-height: 650px;
  overflow-y: auto;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
  padding: 22px;
  scrollbar-color: var(--app-border-strong) transparent;
}

.review-paper-header,
.review-tab-surface {
  border: 0;
  border-radius: 0;
  background: transparent;
}

.review-paper-header {
  padding: 0 0 18px;
  border-bottom: 1px solid var(--app-border);
}

.review-paper-header-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--app-border);
}

.review-paper-title {
  min-width: 0;
}

.review-paper-title h2 {
  margin: 0;
  color: var(--app-text);
  font-family: var(--claude-serif);
  font-size: 30px;
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1.24;
}

.review-paper-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.review-paper-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}

.regenerate-review-button {
  --el-button-bg-color: var(--app-primary-soft);
  --el-button-border-color: rgba(204, 120, 92, 0.26);
  --el-button-text-color: var(--app-primary);
  --el-button-hover-bg-color: var(--app-primary-soft);
  --el-button-hover-border-color: rgba(204, 120, 92, 0.4);
  --el-button-hover-text-color: var(--app-primary);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
  transform-origin: center;
}

.review-page :deep(.regenerate-review-button:not(.is-disabled):not(.is-loading):hover) {
  transform: scale(1.02);
}

.review-page :deep(.regenerate-review-button.is-disabled),
.review-page :deep(.regenerate-review-button.is-disabled:hover) {
  --el-button-disabled-bg-color: var(--app-surface-muted);
  --el-button-disabled-border-color: var(--app-border);
  --el-button-disabled-text-color: var(--app-text-subtle);
}

.review-progress-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  margin-top: 18px;
}

.review-summary-strip {
  display: grid;
  grid-template-columns: 160px minmax(0, 1.3fr) minmax(180px, 1fr) 150px;
  gap: 1px;
  overflow: hidden;
  margin-top: 18px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-border);
}

.review-summary-item {
  min-width: 0;
  background: var(--app-surface-strong);
  padding: 14px 16px;
}

.review-summary-item span {
  display: block;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.review-summary-item strong {
  display: block;
  margin-top: 7px;
  color: var(--app-text);
  font-family: var(--claude-serif);
  font-size: 31px;
  font-weight: 500;
  line-height: 1;
}

.review-summary-item p {
  display: -webkit-box;
  overflow: hidden;
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.review-summary-ai p {
  display: block;
  max-height: 72px;
  overflow-y: auto;
  padding-right: 8px;
  scrollbar-color: var(--app-border-strong) transparent;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  -webkit-line-clamp: unset;
}

.review-summary-ai p::-webkit-scrollbar {
  width: 6px;
}

.review-summary-ai p::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: var(--app-border-strong);
}

.review-step {
  min-width: 0;
  min-height: 58px;
  border: 0;
  border-radius: var(--app-radius-md);
  background: var(--app-surface-strong);
  color: var(--app-text-muted);
  padding: 9px 10px;
  cursor: pointer;
  text-align: center;
}

.review-step span,
.review-step small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-step span {
  color: inherit;
  font-size: 13px;
  font-weight: 600;
}

.review-step small {
  margin-top: 3px;
  font-size: 11px;
  font-weight: 500;
}

.review-step.done {
  background: var(--app-success-soft);
  color: var(--app-success-hover);
}

.review-step.active {
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.review-work-area {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  align-items: start;
}

.review-tab-surface {
  min-width: 0;
  padding: 4px 0 0;
}

.review-empty-state {
  display: grid;
  justify-items: center;
  min-height: 520px;
  border: 1px dashed var(--app-border);
  border-radius: var(--app-radius-md);
  background: linear-gradient(180deg, rgba(245, 240, 232, 0.58), rgba(250, 249, 245, 0));
  padding: 72px 24px;
  text-align: center;
}

.review-doc-icon {
  position: relative;
  width: 36px;
  height: 46px;
  margin-bottom: 14px;
  border: 1.5px solid var(--app-border);
  border-radius: 7px;
  background: var(--app-surface);
}

.review-doc-icon::before {
  position: absolute;
  top: -1.5px;
  right: -1.5px;
  width: 14px;
  height: 14px;
  border-bottom: 1.5px solid var(--app-border);
  border-left: 1.5px solid var(--app-border);
  border-radius: 0 7px 0 4px;
  background: var(--app-surface-strong);
  content: "";
}

.review-doc-icon::after {
  position: absolute;
  top: 21px;
  left: 9px;
  right: 9px;
  height: 1.5px;
  background: var(--app-border);
  box-shadow: 0 7px 0 var(--app-border);
  content: "";
}

.review-empty-state strong {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 600;
}

.review-empty-state p {
  max-width: 420px;
  margin: 8px 0 18px;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.review-bottom-bar {
  position: fixed;
  right: auto;
  bottom: 20px;
  left: var(--review-shell-left);
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: var(--review-shell-width);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: rgba(250, 249, 245, 0.94);
  padding: 12px 14px;
  box-shadow: var(--app-shadow-lg);
  backdrop-filter: blur(12px);
}

.review-bottom-bar p {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.review-bottom-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.review-submit-button {
  --el-button-bg-color: var(--app-surface-muted);
  --el-button-border-color: var(--app-border-strong);
  --el-button-text-color: var(--app-text);
  --el-button-hover-bg-color: var(--app-surface-soft);
  --el-button-hover-border-color: var(--app-primary);
  --el-button-hover-text-color: var(--app-primary-active);
  --el-button-active-bg-color: var(--app-surface-muted);
  --el-button-active-border-color: var(--app-primary-active);
  --el-button-active-text-color: var(--app-primary-active);
  --el-button-disabled-bg-color: var(--app-surface-muted);
  --el-button-disabled-border-color: var(--app-border);
  --el-button-disabled-text-color: var(--app-text-muted);
  min-width: 108px;
}

@media (max-width: 1180px) {
  .review-page {
    --review-page-gutter: 18px;
    padding: 0 var(--review-page-gutter) 104px;
  }

  .review-top-nav {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    padding: 14px 0;
  }

  .review-nav-links,
  .review-nav-actions {
    flex-wrap: wrap;
  }

  .review-hero,
  .review-layout,
  .review-work-area {
    grid-template-columns: 1fr;
  }

  .review-detail {
    height: auto;
    min-height: 0;
  }

  .review-paper-header-top,
  .review-bottom-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .review-paper-actions {
    justify-content: flex-start;
  }

  .review-bottom-bar {
    right: 18px;
    left: 18px;
    width: auto;
  }
}

@media (max-width: 720px) {
  .review-hero h1 {
    font-size: 34px;
  }

  .review-hero-summary {
    grid-template-columns: 1fr;
  }

  .review-summary-strip {
    grid-template-columns: 1fr;
  }

  .review-progress-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .review-bottom-actions {
    width: 100%;
    justify-content: flex-end;
    flex-wrap: wrap;
  }
}
</style>
