/**
 * 评审审计日志动作类型标签映射。
 * 用于在审计日志时间线和全局查询视图中统一展示动作的可读名称。
 */
export const reviewAuditActionLabels: Record<string, string> = {
  CREATE_TASK: '创建任务',
  AI_REVIEW: '生成 AI 评审',
  ADJUST_REPORT: '人工调整报告',
  ASSIGN: '分配评审人',
  ASSIGN_BY_ADMIN_OVERRIDE: '管理员兜底分配',
  ASSIGN_BY_LEADER: '组长分配本组评审任务',
  JOIN_REVIEW_BY_LEADER: '组长加入本组评审任务',
  DISPATCH_TO_GROUP: '管理员派发评审任务到小组',
  RETURN: '退回评审',
  CANCEL_ASSIGNMENT: '取消分配',
  SUBMIT: '提交评审',
  SUBMIT_ASSIGNMENT: '提交个人评审任务',
  UPDATE_CONSENSUS: '更新共识',
  CONFIRM_CONSENSUS: '确认共识',
  RECALCULATE_CONSENSUS: '重新计算共识',
};

/**
 * 获取审计动作的可读标签，未知动作回退为原始动作编码。
 */
export function reviewAuditActionLabel(action: string): string {
  return reviewAuditActionLabels[action] ?? action;
}

/**
 * 全局审计日志查询视图可选的动作筛选选项列表。
 */
export const reviewAuditActionOptions: Array<{ value: string; label: string }> = Object.entries(
  reviewAuditActionLabels,
).map(([value, label]) => ({ value, label }));
