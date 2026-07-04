<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import MainLayout from '../../layouts/MainLayout.vue';
import LogoutConfirmDialog from '../../components/common/LogoutConfirmDialog.vue';
import { getErrorMessage } from '../../api/http';
import {
  assignLeaderTask,
  confirmLeaderTaskConsensus,
  getLeaderTaskConsensus,
  joinLeaderTaskReview,
  listLeaderGroupMembers,
  listLeaderGroups,
  listLeaderGroupTasks,
  recalculateLeaderTaskConsensus,
  updateLeaderTaskConsensus,
  listLeaderTaskReports,
} from '../../api/reviewLeader';
import { useAuth } from '../../composables/useAuth';
import type {
  AdminReviewTaskSummary,
  ReviewConsensus,
  ReviewGroup,
  ReviewGroupMember,
  ReviewReport,
  ReviewRiskItem,
  ReviewScoreItem,
} from '../../types';

const router = useRouter();
const auth = useAuth();

const groups = ref<ReviewGroup[]>([]);
const members = ref<ReviewGroupMember[]>([]);
const tasks = ref<AdminReviewTaskSummary[]>([]);
const reports = ref<ReviewReport[]>([]);
const consensus = ref<ReviewConsensus | null>(null);
const selectedGroupId = ref<string>('');
const selectedTaskId = ref<string>('');
const loading = ref(false);
const scopeLoading = ref(false);
const detailLoading = ref(false);
const assigning = ref(false);
const joiningTaskIds = ref<string[]>([]);
const consensusSaving = ref(false);
const consensusRecalculating = ref(false);
const consensusConfirming = ref(false);
const assignDialogVisible = ref(false);
const logoutDialogVisible = ref(false);
const assignmentTask = ref<AdminReviewTaskSummary | null>(null);
const selectedReviewerIds = ref<string[]>([]);
const assignmentDueAt = ref<string | null>(null);
const detailTab = ref<'reports' | 'consensus' | 'risks'>('reports');
const consensusForm = reactive({
  finalScore: null as number | null,
  finalRecommendation: '',
});

const selectedGroup = computed(() => groups.value.find((group) => group.id === selectedGroupId.value) ?? null);
const selectedTask = computed(() => tasks.value.find((task) => task.id === selectedTaskId.value) ?? null);
const reviewerMembers = computed(() => members.value.filter((member) => member.memberRole === 'REVIEWER' && member.status === 'ACTIVE'));
const unassignedCount = computed(() => tasks.value.filter((task) => isUnassignedTask(task)).length);
const submittedCount = computed(() => tasks.value.filter((task) => task.status === 'SUBMITTED').length);
const confirmedCount = computed(() => tasks.value.filter((task) => task.status === 'CONSENSUS_CONFIRMED').length);
const reportRiskItems = computed(() => reports.value.flatMap((report) => riskItems(report)));

const taskStatusLabels: Record<string, string> = {
  PENDING: '待分配',
  PENDING_ASSIGNMENT: '待分配',
  ASSIGNED: '已分配',
  REVIEWING: '评审中',
  IN_REVIEW: '评审中',
  SUBMITTED: '待最终评分',
  COMPLETED: '已完成',
  CONSENSUS_CONFIRMED: '最终已确认',
};

const assignmentStatusLabels: Record<string, string> = {
  ASSIGNED: '待评审',
  REVIEWING: '评审中',
  SUBMITTED: '已提交',
  RETURNED: '已退回',
  CANCELLED: '已取消',
};

const consensusStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  IN_DISCUSSION: '讨论中',
  CONFIRMED: '已确认',
  ARCHIVED: '已归档',
};

watch(selectedGroupId, async (groupId) => {
  if (!groupId) {
    members.value = [];
    tasks.value = [];
    reports.value = [];
    consensus.value = null;
    selectedTaskId.value = '';
    return;
  }
  await loadGroupScope(groupId);
});

watch(consensus, (value) => {
  consensusForm.finalScore = value?.finalScore ?? null;
  consensusForm.finalRecommendation = value?.finalRecommendation ?? '';
});

function statusLabel(status: string | null | undefined) {
  if (!status) return '-';
  return taskStatusLabels[status] ?? assignmentStatusLabels[status] ?? consensusStatusLabels[status] ?? status;
}

function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '-';
}

function reviewerDisplayName(member: ReviewGroupMember) {
  return member.displayName || member.username || member.userId;
}

function reportReviewerName(report: ReviewReport) {
  return report.reviewerDisplayName || report.reviewerUsername || report.reviewerUserId || '未知评审员';
}

function taskLeadName(task: AdminReviewTaskSummary) {
  return task.leadReviewerDisplayName || task.leadReviewerUsername || task.leadReviewerUserId || '-';
}

function isUnassignedTask(task: AdminReviewTaskSummary) {
  return task.assignmentCount === 0 || task.status === 'PENDING_ASSIGNMENT' || task.status === 'PENDING';
}

function hasJoinedReview(task: AdminReviewTaskSummary) {
  return Boolean(task.currentUserAssignmentId);
}

function canJoinReview(task: AdminReviewTaskSummary) {
  return !hasJoinedReview(task) && task.status !== 'SUBMITTED' && task.status !== 'CONSENSUS_CONFIRMED';
}

function isJoiningTask(taskId: string) {
  return joiningTaskIds.value.includes(taskId);
}

function scoreItems(report: ReviewReport) {
  return Array.isArray(report.scores) ? report.scores as ReviewScoreItem[] : [];
}

function riskItems(report: ReviewReport) {
  return Array.isArray(report.risks) ? report.risks as ReviewRiskItem[] : [];
}

function riskLevelLabel(level: string | null | undefined) {
  const labels: Record<string, string> = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险',
    CRITICAL: '严重',
  };
  return level ? labels[level] ?? level : '风险';
}

function riskLevelTagType(level: string | null | undefined) {
  if (level === 'HIGH' || level === 'CRITICAL') return 'danger';
  if (level === 'MEDIUM') return 'warning';
  if (level === 'LOW') return 'success';
  return 'info';
}

async function loadGroups() {
  loading.value = true;
  try {
    groups.value = await listLeaderGroups();
    if (!selectedGroupId.value && groups.value.length) {
      selectedGroupId.value = groups.value[0].id;
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function loadGroupScope(groupId = selectedGroupId.value) {
  if (!groupId) return;
  scopeLoading.value = true;
  try {
    const [nextMembers, nextTasks] = await Promise.all([
      listLeaderGroupMembers(groupId),
      listLeaderGroupTasks(groupId),
    ]);
    members.value = nextMembers;
    tasks.value = nextTasks;
    if (!tasks.value.some((task) => task.id === selectedTaskId.value)) {
      selectedTaskId.value = tasks.value[0]?.id ?? '';
    }
    if (selectedTaskId.value) {
      await loadTaskDetail(selectedTaskId.value);
    } else {
      reports.value = [];
      consensus.value = null;
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    scopeLoading.value = false;
  }
}

async function loadTaskDetail(taskId = selectedTaskId.value) {
  if (!selectedGroupId.value || !taskId) return;
  const isSwitchingTask = taskId !== selectedTaskId.value;
  detailLoading.value = true;
  try {
    selectedTaskId.value = taskId;
    if (isSwitchingTask) {
      detailTab.value = 'reports';
    }
    const [nextReports, nextConsensus] = await Promise.all([
      listLeaderTaskReports(selectedGroupId.value, taskId),
      getLeaderTaskConsensus(selectedGroupId.value, taskId),
    ]);
    reports.value = nextReports;
    consensus.value = nextConsensus;
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    detailLoading.value = false;
  }
}

function openAssignDialog(task: AdminReviewTaskSummary) {
  assignmentTask.value = task;
  selectedReviewerIds.value = [];
  assignmentDueAt.value = task.dueAt;
  assignDialogVisible.value = true;
}

async function submitAssignment() {
  if (!selectedGroupId.value || !assignmentTask.value) return;
  if (!selectedReviewerIds.value.length) {
    ElMessage.warning('请选择本组普通评审员');
    return;
  }
  assigning.value = true;
  try {
    await assignLeaderTask(selectedGroupId.value, assignmentTask.value.id, {
      reviewerUserIds: selectedReviewerIds.value,
      dueAt: assignmentDueAt.value,
    });
    ElMessage.success('分配已完成');
    assignDialogVisible.value = false;
    await loadGroupScope();
    await loadTaskDetail(assignmentTask.value.id);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    assigning.value = false;
  }
}

async function joinReview(task: AdminReviewTaskSummary) {
  if (!selectedGroupId.value || !canJoinReview(task) || isJoiningTask(task.id)) return;
  joiningTaskIds.value = [...joiningTaskIds.value, task.id];
  try {
    await joinLeaderTaskReview(selectedGroupId.value, task.id);
    ElMessage.success('已加入评审任务，可在评审工作台处理');
    selectedTaskId.value = task.id;
    await loadGroupScope();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    joiningTaskIds.value = joiningTaskIds.value.filter((id) => id !== task.id);
  }
}

async function recalculateConsensus() {
  if (!selectedGroupId.value || !selectedTask.value) return;
  consensusRecalculating.value = true;
  try {
    const result = await recalculateLeaderTaskConsensus(selectedGroupId.value, selectedTask.value.id);
    consensus.value = result;
    await loadGroupScope();
    // 检查是否存在评分分歧，若有则弹出提示
    const disagreements = Array.isArray(result.disagreementItems) ? result.disagreementItems : [];
    if (disagreements.length > 0) {
      const scoreSummary = result.scoreSummary ?? {};
      const avg = scoreSummary.overallAverage ?? '-';
      const min = scoreSummary.overallMin ?? '-';
      const max = scoreSummary.overallMax ?? '-';
      const detailHtml = disagreements.map((item) => {
        const typeLabel = item.type === 'OVERALL_SCORE'
          ? '总分分歧'
          : `指标分歧（${item.criterionCode ?? '-'}）`;
        const diff = Number(item.maxScore) - Number(item.minScore);
        return `<li><strong>${typeLabel}</strong>：最低 ${item.minScore} 分 / 最高 ${item.maxScore} 分，分差 ${diff}（阈值 ${item.threshold}）</li>`;
      }).join('');
      await ElMessageBox.alert(
        `<p>检测到 <strong>${disagreements.length}</strong> 项评分分歧。</p>` +
        `<ul style="margin: 12px 0; padding-left: 20px;">${detailHtml}</ul>` +
        `<p style="color: #909399; font-size: 13px;">平均分参考值：${avg}（最低 ${min} / 最高 ${max}）</p>` +
        `<p style="color: #E6A23C;">请组长协调各评审人意见后，手动填写最终分数并保存。</p>`,
        '评分存在分歧',
        { confirmButtonText: '我知道了', dangerouslyUseHTMLString: true },
      );
    } else {
      ElMessage.success('共识汇总已重新计算');
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    consensusRecalculating.value = false;
  }
}

async function saveConsensus() {
  if (!selectedGroupId.value || !selectedTask.value) return;
  consensusSaving.value = true;
  try {
    consensus.value = await updateLeaderTaskConsensus(selectedGroupId.value, selectedTask.value.id, {
      finalScore: consensusForm.finalScore,
      finalRecommendation: consensusForm.finalRecommendation,
    });
    ElMessage.success('最终评分已保存');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    consensusSaving.value = false;
  }
}

async function confirmConsensus() {
  if (!selectedGroupId.value || !selectedTask.value) return;
  try {
    await ElMessageBox.confirm('确认后任务将进入最终已确认状态，是否继续？', '确认最终评分', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  consensusConfirming.value = true;
  try {
    consensus.value = await updateLeaderTaskConsensus(selectedGroupId.value, selectedTask.value.id, {
      finalScore: consensusForm.finalScore,
      finalRecommendation: consensusForm.finalRecommendation,
    });
    consensus.value = await confirmLeaderTaskConsensus(selectedGroupId.value, selectedTask.value.id);
    ElMessage.success('最终评分已确认');
    await loadGroupScope();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    consensusConfirming.value = false;
  }
}

async function handleLogout() {
  await auth.logout();
  await router.replace('/login');
}

onMounted(async () => {
  if (!auth.state.user) {
    await auth.hydrateCurrentUser();
  }
  await loadGroups();
});
</script>

<template>
  <MainLayout class="leader-page paper-mind-leader-shell">
    <header class="leader-top-nav">
      <div class="leader-brand">
        <span class="leader-brand-mark" aria-hidden="true"><span></span></span>
        <span>Paper Mind</span>
      </div>
      <nav class="leader-nav-links" aria-label="评审组长导航">
        <button class="leader-nav-link active" type="button">组长面板</button>
        <button class="leader-nav-link" type="button" @click="router.push('/review')">评审中心</button>
        <button v-if="auth.isAdmin.value" class="leader-nav-link" type="button" @click="router.push('/admin/reviews')">
          管理后台
        </button>
        <button v-if="auth.hasRole('USER')" class="leader-nav-link" type="button" @click="router.push('/user')">用户端</button>
      </nav>

      <div class="leader-nav-actions">
        <el-button text @click="logoutDialogVisible = true">退出</el-button>
      </div>
    </header>

    <LogoutConfirmDialog v-model="logoutDialogVisible" @confirm="handleLogout" />

    <section class="leader-hero">
      <div class="leader-hero-copy">
        <p class="leader-eyebrow">Review Leader</p>
        <h1>评审组长面板</h1>
        <p class="leader-lead">按评审小组处理任务分配、组内评分详情、最终评分与共识确认。</p>
        <div class="leader-hero-meta">
          <span class="leader-badge">负责小组 {{ groups.length }}</span>
          <span class="leader-badge">本组任务 {{ tasks.length }}</span>
          <span class="leader-badge">待最终评分 {{ submittedCount }}</span>
        </div>
      </div>

      <aside class="leader-brief-card" aria-label="当前评审概览">
        <div>
          <div class="brief-title">
            <div>
              <p class="leader-eyebrow">Current Group</p>
              <h2>{{ selectedGroup?.name || '暂无小组' }}</h2>
            </div>
            <span class="current-group-label">当前小组</span>
          </div>
          <p v-if="selectedGroup">
            当前小组由 {{ selectedGroup.leaderDisplayName || selectedGroup.leaderUsername || selectedGroup.leaderUserId }} 负责。
          </p>
          <p v-else>选择评审小组后，这里会显示成员、任务与共识状态。</p>
        </div>
        <div class="brief-grid">
          <div class="brief-metric">
            <strong>{{ selectedGroup?.memberCount ?? 0 }}</strong>
            <span>成员</span>
          </div>
          <div class="brief-metric">
            <strong>{{ unassignedCount }}</strong>
            <span>待分配</span>
          </div>
          <div class="brief-metric">
            <strong>{{ confirmedCount }}</strong>
            <span>已确认</span>
          </div>
        </div>
      </aside>
    </section>

    <section class="leader-stats-grid" aria-label="任务统计">
      <article class="stat-card">
        <span>负责小组</span>
        <strong>{{ groups.length }}</strong>
      </article>
      <article class="stat-card">
        <span>本组任务</span>
        <strong>{{ tasks.length }}</strong>
      </article>
      <article class="stat-card">
        <span>待分配</span>
        <strong>{{ unassignedCount }}</strong>
      </article>
      <article class="stat-card">
        <span>待最终评分</span>
        <strong>{{ submittedCount }}</strong>
      </article>
      <article class="stat-card">
        <span>最终已确认</span>
        <strong>{{ confirmedCount }}</strong>
      </article>
    </section>

    <section class="leader-workbench">
      <aside class="group-panel" v-loading="loading || scopeLoading">
        <div class="panel-header">
          <h2>我的小组</h2>
        </div>

        <div class="group-panel-body">
          <el-select v-model="selectedGroupId" class="full-width" placeholder="选择评审小组">
            <el-option
              v-for="group in groups"
              :key="group.id"
              :label="group.name"
              :value="group.id"
            />
          </el-select>

          <div v-if="selectedGroup" class="group-card">
            <strong>{{ selectedGroup.name }}</strong>
            <span>组长：{{ selectedGroup.leaderDisplayName || selectedGroup.leaderUsername || selectedGroup.leaderUserId }}</span>
            <span>成员：{{ selectedGroup.memberCount }} · 任务：{{ selectedGroup.taskCount }}</span>
          </div>

          <div class="member-list">
            <h3>组内普通评审员</h3>
            <div v-if="!reviewerMembers.length" class="leader-empty-state compact">
              <div class="leader-empty-icon" aria-hidden="true"></div>
              <p class="empty-title">暂无普通评审员成员</p>
              <p class="empty-copy">添加成员后，可直接在本页分配论文与查看个人评分报告。</p>
            </div>
            <div v-for="member in reviewerMembers" :key="member.id" class="member-item">
              <strong>{{ reviewerDisplayName(member) }}</strong>
              <span>{{ member.username || member.userId }}</span>
            </div>
          </div>
        </div>
      </aside>

      <main class="task-panel" v-loading="scopeLoading">
        <div class="panel-header">
          <h2>本组评审任务</h2>
        </div>
        <div class="task-toolbar">
          <p>按论文、状态、提交进度和截止时间快速扫描任务。</p>
          <span class="leader-badge">当前 {{ tasks.length }} 项</span>
        </div>

        <div class="task-table-wrap">
          <el-table :data="tasks" class="task-table" highlight-current-row @row-click="(row: AdminReviewTaskSummary) => loadTaskDetail(row.id)">
            <el-table-column label="论文" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="task-title-cell">
                  <strong>{{ row.title }}</strong>
                  <span>{{ row.sourceId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="分配/提交" width="100">
              <template #default="{ row }">
                {{ row.submittedCount }}/{{ row.assignmentCount }}
              </template>
            </el-table-column>
            <el-table-column label="组长" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ taskLeadName(row) }}</template>
            </el-table-column>
            <el-table-column label="截止时间" width="160">
              <template #default="{ row }">{{ formatDate(row.dueAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <div class="leader-action-group">
                  <el-button size="small" class="leader-action-button detail-button" @click.stop="loadTaskDetail(row.id)">详情</el-button>
                  <el-button size="small" class="leader-action-button assign-button" :disabled="!isUnassignedTask(row)" @click.stop="openAssignDialog(row)">分配</el-button>
                  <el-button
                    v-if="hasJoinedReview(row)"
                    size="small"
                    class="leader-action-button join-button"
                    @click.stop="router.push('/review')"
                  >
                    去评审
                  </el-button>
                  <el-button
                    v-else
                    size="small"
                    class="leader-action-button join-button"
                    :loading="isJoiningTask(row.id)"
                    :disabled="!canJoinReview(row)"
                    @click.stop="joinReview(row)"
                  >
                    加入评审
                  </el-button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <div class="leader-empty-state table-empty">
                <div class="leader-empty-icon" aria-hidden="true"></div>
                <p class="empty-title">暂无本组任务</p>
                <p class="empty-copy">任务分配后将在这里显示论文、评审员、截止时间和最终评分入口。</p>
              </div>
            </template>
          </el-table>
        </div>
      </main>

      <aside class="leader-detail-panel" v-loading="detailLoading">
        <div class="panel-header">
          <h2>所选任务</h2>
          <el-tag :type="consensus?.status === 'CONFIRMED' ? 'success' : 'warning'" size="small" effect="plain">
            {{ statusLabel(consensus?.status || selectedTask?.status) }}
          </el-tag>
        </div>

        <div class="leader-detail-scroll">
          <template v-if="selectedTask">
            <div class="selected-task-card">
              <strong>{{ selectedTask.title }}</strong>
              <span>{{ selectedTask.sourceId }} · {{ statusLabel(selectedTask.status) }}</span>
              <span>提交进度：{{ selectedTask.submittedCount }}/{{ selectedTask.assignmentCount }}</span>
            </div>

            <el-tabs v-model="detailTab" class="leader-detail-tabs">
              <el-tab-pane label="评分报告" name="reports">
                <div v-if="!reports.length" class="leader-empty-state compact">
                  <div class="leader-empty-icon" aria-hidden="true"></div>
                  <p class="empty-title">暂无已生成的个人评分报告</p>
                  <p class="empty-copy">组内评审员提交报告后，可在这里展开查看评分指标、理由与建议。</p>
                </div>
                <div v-else class="report-list">
                  <article v-for="report in reports" :key="report.id" class="report-card">
                    <div class="report-card-header">
                      <div class="report-title">
                        <strong>{{ reportReviewerName(report) }}</strong>
                        <span>{{ report.finalRecommendation || '暂无最终建议' }}</span>
                      </div>
                      <strong class="report-score">{{ report.totalScore ?? '-' }}<span>/100</span></strong>
                    </div>
                    <div class="report-meta">
                      <span>更新时间：{{ formatDate(report.updatedAt) }}</span>
                    </div>
                    <div v-if="scoreItems(report).length" class="score-list">
                      <div v-for="item in scoreItems(report)" :key="`${report.id}-${item.code}`" class="score-row">
                        <span>{{ item.name }}</span>
                        <div class="score-bar">
                          <span :style="{ width: `${Math.min(100, Math.max(0, (Number(item.score) / Number(item.maxScore || 100)) * 100))}%` }"></span>
                        </div>
                        <strong>{{ item.score }}/{{ item.maxScore }}</strong>
                      </div>
                    </div>
                  </article>
                </div>
              </el-tab-pane>

              <el-tab-pane label="最终共识" name="consensus">
                <div class="consensus-summary">
                  <div class="final-score-card">
                    <strong>{{ consensusForm.finalScore ?? '-' }}</strong>
                    <span>最终分数</span>
                  </div>
                  <div class="consensus-copy">
                    <el-tag :type="consensus?.status === 'CONFIRMED' ? 'success' : 'warning'" size="small" effect="plain">
                      {{ statusLabel(consensus?.status) }}
                    </el-tag>
                    <p>{{ consensusForm.finalRecommendation || '暂无最终评审建议。' }}</p>
                  </div>
                </div>

                <div class="consensus-actions">
                  <el-button class="leader-action-button recalculate-button" size="small" :loading="consensusRecalculating" :disabled="consensus?.status === 'CONFIRMED'" @click="recalculateConsensus">
                    重新计算共识
                  </el-button>
                </div>

                <el-form label-position="top" class="consensus-form">
                  <el-form-item label="最终分数">
                    <el-input-number v-model="consensusForm.finalScore" :min="0" :max="100" :precision="0" class="full-width" />
                  </el-form-item>
                  <el-form-item label="最终建议">
                    <el-input v-model="consensusForm.finalRecommendation" type="textarea" :rows="4" placeholder="填写最终评审建议" />
                  </el-form-item>
                </el-form>

                <div class="consensus-footer">
                  <el-button class="leader-action-button subtle-button" :loading="consensusSaving" :disabled="!consensus || consensus.status === 'CONFIRMED'" @click="saveConsensus">
                    保存最终评分
                  </el-button>
                  <el-button class="leader-action-button confirm-button" :loading="consensusConfirming" :disabled="!consensus || consensus.status === 'CONFIRMED'" @click="confirmConsensus">
                    确认最终评分
                  </el-button>
                </div>
              </el-tab-pane>

              <el-tab-pane label="风险提醒" name="risks">
                <div v-if="!reportRiskItems.length" class="leader-empty-state compact">
                  <div class="leader-empty-icon" aria-hidden="true"></div>
                  <p class="empty-title">暂无风险提醒</p>
                  <p class="empty-copy">评审报告中的风险项会在这里汇总，方便组长形成最终建议。</p>
                </div>
                <div v-else class="risk-list">
                  <article v-for="(risk, index) in reportRiskItems" :key="`${risk.type}-${index}`" class="risk-item">
                    <div>
                      <strong>{{ risk.type || '风险提醒' }}</strong>
                      <el-tag :type="riskLevelTagType(risk.level)" size="small" effect="plain">{{ riskLevelLabel(risk.level) }}</el-tag>
                    </div>
                    <p>{{ risk.evidence || '暂无证据说明。' }}</p>
                    <p class="risk-suggestion">{{ risk.suggestion || '暂无处理建议。' }}</p>
                  </article>
                </div>
              </el-tab-pane>
            </el-tabs>
          </template>

          <div v-else class="leader-empty-state compact">
            <div class="leader-empty-icon" aria-hidden="true"></div>
            <p class="empty-title">暂无选中的任务</p>
            <p class="empty-copy">选择左侧小组并点击任务后，可在这里处理评分报告、最终共识和风险提醒。</p>
          </div>
        </div>
      </aside>
    </section>

    <el-dialog v-model="assignDialogVisible" title="分配本组评审任务" width="520px" class="claude-workspace-dialog">
      <div v-if="assignmentTask" class="assign-task-card">
        <strong>{{ assignmentTask.title }}</strong>
        <span>{{ assignmentTask.sourceId }}</span>
      </div>
      <el-form label-position="top">
        <el-form-item label="普通评审员">
          <el-select v-model="selectedReviewerIds" multiple filterable class="full-width" placeholder="选择本组普通评审员">
            <el-option
              v-for="member in reviewerMembers"
              :key="member.userId"
              :label="reviewerDisplayName(member)"
              :value="member.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="assignmentDueAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            class="full-width"
            placeholder="可选"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="submitAssignment">确认分配</el-button>
      </template>
    </el-dialog>
  </MainLayout>
</template>

<style scoped>
.leader-page {
  gap: 22px;
  padding: 0 36px 48px;
  background: var(--claude-canvas);
}

.leader-page :deep([class~="el-button"]) {
  border-radius: var(--app-radius-md);
  cursor: pointer;
}

.leader-page h1,
.leader-page h2,
.leader-page h3 {
  letter-spacing: 0;
}

.leader-top-nav,
.leader-hero,
.leader-stats-grid,
.leader-workbench {
  width: min(100%, 1360px);
  margin-inline: auto;
}

.leader-top-nav {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 24px;
  min-height: 64px;
  border-bottom: 1px solid var(--claude-hairline-soft);
}

.leader-brand,
.leader-nav-links {
  display: flex;
  align-items: center;
}

.leader-brand {
  gap: 10px;
  color: var(--app-text);
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}

.leader-brand-mark {
  position: relative;
  display: inline-flex;
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
}

.leader-brand-mark::before,
.leader-brand-mark::after,
.leader-brand-mark span::before,
.leader-brand-mark span::after {
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

.leader-brand-mark::after {
  transform: translate(-50%, -50%) rotate(90deg);
}

.leader-brand-mark span::before {
  transform: translate(-50%, -50%) rotate(45deg);
}

.leader-brand-mark span::after {
  transform: translate(-50%, -50%) rotate(-45deg);
}

.leader-nav-links {
  gap: 4px;
}

.leader-nav-link {
  min-height: 36px;
  border: 0;
  border-radius: var(--app-radius-md);
  background: transparent;
  color: var(--app-text-muted);
  padding: 8px 14px;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  cursor: pointer;
  transition:
    background-color var(--app-transition-fast),
    color var(--app-transition-fast);
}

.leader-nav-link:hover,
.leader-nav-link.active {
  background: var(--app-surface-soft);
  color: var(--app-text);
}

.leader-nav-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.leader-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 32px;
  align-items: stretch;
  padding: 48px 0 10px;
}

.leader-hero-copy {
  min-width: 0;
}

.leader-eyebrow {
  margin: 0 0 10px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 1.5px;
  line-height: 1.4;
  text-transform: uppercase;
}

.leader-hero h1 {
  max-width: 720px;
  color: var(--app-text);
  font-size: 54px;
  font-weight: 500;
  line-height: 1.06;
}

.leader-lead {
  max-width: 620px;
  margin: 16px 0 0;
  color: var(--app-text-muted);
  font-size: 16px;
  line-height: 1.6;
}

.leader-hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 26px;
}

.leader-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  border-radius: 999px;
  background: var(--app-surface-soft);
  color: var(--app-text);
  padding: 4px 12px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.leader-brief-card {
  display: flex;
  min-height: 216px;
  flex-direction: column;
  justify-content: space-between;
  border: 1px solid var(--app-border);
  border-left: 4px solid var(--app-primary);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-soft);
  color: var(--app-text);
  padding: 24px;
}

.leader-brief-card p,
.leader-brief-card span {
  color: var(--app-text-muted);
}

.leader-brief-card .leader-eyebrow {
  color: var(--app-primary);
}

.brief-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 22px;
}

.brief-title h2 {
  color: var(--app-text);
  font-size: 28px;
  font-weight: 500;
  line-height: 1.15;
}

.current-group-label {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  background: var(--app-surface);
  color: var(--app-text-muted);
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.brief-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.brief-metric {
  border-radius: 10px;
  background: var(--app-surface);
  padding: 12px;
}

.brief-metric strong {
  display: block;
  color: var(--app-text);
  font-family: var(--claude-serif);
  font-size: 28px;
  font-weight: 500;
  line-height: 1;
}

.brief-metric span {
  display: block;
  margin-top: 6px;
  font-size: 12px;
}

.leader-stats-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  min-height: 88px;
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-soft);
  padding: 18px;
}

.stat-card span {
  display: block;
  color: var(--app-text-muted);
  font-size: 13px;
  font-weight: 500;
}

.stat-card strong {
  display: block;
  margin-top: 8px;
  color: var(--app-text);
  font-family: var(--claude-serif);
  font-size: 30px;
  font-weight: 500;
  line-height: 1;
}

.leader-action-group {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.leader-action-button {
  min-width: 48px;
  height: 30px;
  padding: 0 9px;
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
  font-weight: 500;
}

.assign-button {
  --el-button-bg-color: var(--app-primary-soft);
  --el-button-border-color: rgba(204, 120, 92, 0.26);
  --el-button-text-color: var(--app-primary);
  --el-button-hover-bg-color: rgba(204, 120, 92, 0.16);
  --el-button-hover-border-color: rgba(204, 120, 92, 0.4);
  --el-button-hover-text-color: var(--app-primary-active);
}

.join-button {
  --el-button-bg-color: var(--app-surface-soft);
  --el-button-border-color: var(--app-border);
  --el-button-text-color: var(--app-text);
  --el-button-hover-bg-color: var(--app-surface-muted);
  --el-button-hover-border-color: var(--app-primary);
  --el-button-hover-text-color: var(--app-primary-active);
}

.recalculate-button {
  min-width: 112px;
  --el-button-bg-color: var(--app-primary-soft);
  --el-button-border-color: rgba(204, 120, 92, 0.26);
  --el-button-text-color: var(--app-primary);
  --el-button-hover-bg-color: var(--app-primary-soft-hover);
  --el-button-hover-border-color: rgba(204, 120, 92, 0.4);
  --el-button-hover-text-color: var(--app-primary-active);
}

.leader-page :deep(.recalculate-button.is-disabled),
.leader-page :deep(.recalculate-button.is-disabled:hover) {
  --el-button-disabled-bg-color: var(--app-surface-muted);
  --el-button-disabled-border-color: var(--app-border);
  --el-button-disabled-text-color: var(--app-text-subtle);
}

.confirm-button {
  --el-button-bg-color: var(--app-primary);
  --el-button-border-color: var(--app-primary);
  --el-button-text-color: var(--app-text-on-primary);
  --el-button-hover-bg-color: var(--app-primary-hover);
  --el-button-hover-border-color: var(--app-primary-hover);
  --el-button-active-bg-color: var(--app-primary-active);
  --el-button-active-border-color: var(--app-primary-active);
}

.leader-page :deep(.confirm-button.is-disabled),
.leader-page :deep(.confirm-button.is-disabled:hover) {
  --el-button-bg-color: var(--app-surface-muted);
  --el-button-border-color: var(--app-border);
  --el-button-text-color: var(--app-text-subtle);
}

.task-panel :deep(.el-table__cell:last-child) {
  min-width: 258px;
}

.task-panel :deep(.el-button + .el-button) {
  margin-left: 0;
}

.leader-workbench {
  display: grid;
  grid-template-columns: 300px minmax(520px, 1fr) 420px;
  gap: 18px;
  align-items: start;
}

.group-panel,
.task-panel,
.leader-detail-panel {
  display: flex;
  height: calc(100vh - 250px);
  min-height: 560px;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 64px;
  margin: 0;
  padding: 16px 18px 14px;
  border-bottom: 1px solid var(--app-border);
}

.panel-header h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 28px;
  font-weight: 500;
  line-height: 1.15;
}

.full-width {
  width: 100%;
}

.group-panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 18px 18px;
}

.leader-detail-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 18px 18px;
}

.group-card,
.assign-task-card,
.selected-task-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 12px;
  padding: 16px;
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-soft);
}

.group-card strong,
.assign-task-card strong,
.selected-task-card strong {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.group-card span,
.assign-task-card span,
.selected-task-card span,
.member-item span,
.task-title-cell span,
.report-title span,
.report-meta span {
  color: var(--app-text-muted);
  font-size: 13px;
}

.member-list {
  margin-top: 26px;
}

.member-list h3 {
  margin: 0 0 12px;
  color: var(--app-text);
  font-family: var(--claude-sans);
  font-size: 15px;
  font-weight: 600;
}

.member-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 0;
  border-bottom: 1px solid var(--app-border);
}

.member-item:last-child {
  border-bottom: none;
}

.member-item strong {
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
}

.task-title-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.task-title-cell strong {
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
}

.task-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 0;
  padding: 14px 18px;
  border-bottom: 1px solid var(--app-border);
}

.task-toolbar p {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.task-table {
  width: 100%;
}

.task-table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.task-table :deep(.el-table__empty-block) {
  min-height: 256px;
}

.task-table :deep(.el-table__empty-text) {
  width: 100%;
}

.leader-empty-state {
  display: grid;
  justify-items: center;
  align-content: center;
  min-height: 190px;
  border: 1px dashed var(--app-border);
  border-radius: var(--app-radius-lg);
  background: linear-gradient(180deg, rgba(245, 240, 232, 0.55), rgba(250, 249, 245, 0));
  padding: 24px;
  text-align: center;
}

.leader-empty-state.compact {
  min-height: 190px;
}

.leader-empty-state.table-empty {
  min-height: 256px;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.leader-empty-icon {
  position: relative;
  width: 38px;
  height: 48px;
  margin-bottom: 14px;
  border: 1.5px solid var(--app-border);
  border-radius: 7px;
  background: var(--app-surface);
}

.leader-empty-icon::before {
  position: absolute;
  top: -1.5px;
  right: -1.5px;
  width: 15px;
  height: 15px;
  border-bottom: 1.5px solid var(--app-border);
  border-left: 1.5px solid var(--app-border);
  border-radius: 0 7px 0 4px;
  background: var(--app-surface-strong);
  content: "";
}

.leader-empty-icon::after {
  position: absolute;
  top: 22px;
  right: 10px;
  left: 10px;
  height: 1.5px;
  background: var(--app-border);
  box-shadow: 0 8px 0 var(--app-border);
  content: "";
}

.empty-title {
  margin: 0;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}

.empty-copy {
  max-width: 320px;
  margin: 6px auto 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.leader-empty-state .el-button {
  margin-top: 18px;
}

.report-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.report-title strong {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.35;
}

.report-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.leader-detail-tabs {
  margin-top: 14px;
}

.leader-detail-tabs :deep(.el-tabs__header) {
  margin: 0 0 14px;
}

.leader-detail-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.leader-detail-tabs :deep(.el-tabs__nav) {
  width: 100%;
  gap: 6px;
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
  padding: 5px;
}

.leader-detail-tabs :deep(.el-tabs__item) {
  flex: 1;
  justify-content: center;
  min-height: 34px;
  height: 34px;
  border-radius: var(--app-radius-sm);
  padding: 0 8px;
  font-size: 13px;
}

.leader-detail-tabs :deep(.el-tabs__active-bar) {
  display: none;
}

.report-list,
.risk-list {
  display: grid;
  gap: 10px;
}

.report-card,
.risk-item,
.consensus-copy {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface);
  padding: 14px;
}

.report-card-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 12px;
}

.report-score {
  color: var(--app-text);
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.report-score span {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.score-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.score-row {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) 48px;
  align-items: center;
  gap: 8px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.score-row strong {
  color: var(--app-text);
  font-weight: 600;
  text-align: right;
  white-space: nowrap;
}

.score-bar {
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--app-surface-muted);
}

.score-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--app-primary);
}

.consensus-summary {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 12px;
}

.final-score-card {
  display: grid;
  place-items: center;
  min-height: 120px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-soft);
  text-align: center;
}

.final-score-card strong {
  display: block;
  color: var(--app-text);
  font-size: 34px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.final-score-card span {
  display: block;
  margin-top: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.consensus-copy p {
  margin: 10px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.consensus-actions,
.consensus-form,
.consensus-footer {
  margin-top: 14px;
}

.consensus-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.consensus-footer :deep(.el-button) {
  min-width: 84px;
  height: 32px;
  padding: 0 10px;
  font-size: 12px;
  line-height: 1;
}

.risk-item > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.risk-item strong {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.risk-item p {
  margin: 10px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.65;
}

.risk-suggestion {
  color: var(--app-text) !important;
}

@media (max-width: 1180px) {
  .leader-page {
    padding-inline: 24px;
  }

  .leader-hero {
    grid-template-columns: 1fr;
  }

  .leader-stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .leader-workbench {
    grid-template-columns: 1fr;
  }

  .group-panel,
  .task-panel,
  .leader-detail-panel {
    height: 560px;
  }
}

@media (max-width: 820px) {
  .leader-page {
    padding-inline: 18px;
  }

  .leader-top-nav {
    align-items: flex-start;
    flex-direction: column;
    padding: 14px 0;
  }

  .leader-nav-links {
    width: 100%;
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .leader-hero {
    padding-top: 32px;
  }

  .leader-hero h1 {
    font-size: 40px;
  }

  .leader-stats-grid {
    grid-template-columns: 1fr;
  }

  .task-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .panel-header,
  .consensus-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .consensus-summary {
    grid-template-columns: 1fr;
  }
}
</style>
