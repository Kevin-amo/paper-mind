import { http } from './http';
import { compactParams } from '../utils/params';
import type {
  AdminReviewTaskDetail,
  AdminReviewTaskSummary,
  DispatchReviewTaskPayload,
  PageResponse,
  ReviewAuditLog,
  ReviewAuditOperator,
  ReviewBatch,
  ReviewBatchPayload,
  ReviewGroup,
  ReviewGroupMember,
  ReviewGroupMemberUpdatePayload,
  ReviewGroupPayload,
  ReviewerLoad,
} from '../types';

export async function listReviewBatches(params: { page?: number; size?: number } = {}) {
  const { data } = await http.get<PageResponse<ReviewBatch>>('/admin/reviews/batches', {
    params: compactParams({
      page: params.page ?? 0,
      size: params.size ?? 100,
    }),
  });
  return data;
}

export async function createReviewBatch(payload: ReviewBatchPayload) {
  const { data } = await http.post<ReviewBatch>('/admin/reviews/batches', payload);
  return data;
}

export async function updateReviewBatch(batchId: string, payload: ReviewBatchPayload) {
  const { data } = await http.patch<ReviewBatch>(`/admin/reviews/batches/${batchId}`, payload);
  return data;
}

export async function listReviewGroups(batchId?: string) {
  const { data } = await http.get<ReviewGroup[]>('/admin/reviews/groups', {
    params: compactParams({ batchId }),
  });
  return data;
}

export async function createReviewGroup(payload: ReviewGroupPayload) {
  const { data } = await http.post<ReviewGroup>('/admin/reviews/groups', payload);
  return data;
}

export async function updateReviewGroup(groupId: string, payload: ReviewGroupPayload) {
  const { data } = await http.patch<ReviewGroup>(`/admin/reviews/groups/${groupId}`, payload);
  return data;
}

export async function listReviewGroupMembers(groupId: string) {
  const { data } = await http.get<ReviewGroupMember[]>(`/admin/reviews/groups/${groupId}/members`);
  return data;
}

export async function replaceReviewGroupMembers(groupId: string, payload: ReviewGroupMemberUpdatePayload) {
  const { data } = await http.put<ReviewGroupMember[]>(`/admin/reviews/groups/${groupId}/members`, payload);
  return data;
}

export async function listAdminReviewTasks(params: {
  keyword?: string;
  status?: string;
  page?: number;
  size?: number;
} = {}) {
  const { data } = await http.get<PageResponse<AdminReviewTaskSummary>>('/admin/reviews/tasks', {
    params: compactParams({
      keyword: params.keyword,
      status: params.status,
      page: params.page ?? 0,
      size: params.size ?? 20,
    }),
  });
  return data;
}

export async function getAdminReviewTask(taskId: string) {
  const { data } = await http.get<AdminReviewTaskDetail>(`/admin/reviews/tasks/${taskId}`);
  return data;
}

export async function dispatchReviewTask(taskId: string, payload: DispatchReviewTaskPayload) {
  const { data } = await http.post<AdminReviewTaskSummary>(`/admin/reviews/tasks/${taskId}/dispatch`, payload);
  return data;
}

export async function listReviewerLoads() {
  const { data } = await http.get<ReviewerLoad[]>('/admin/reviews/reviewer-loads');
  return data;
}

export interface ListAdminAuditLogsParams {
  operatorUserId?: string;
  action?: string;
  startTime?: string;
  endTime?: string;
  page?: number;
  size?: number;
}

export async function listAdminAuditLogs(params: ListAdminAuditLogsParams = {}) {
  const { data } = await http.get<PageResponse<ReviewAuditLog>>('/admin/reviews/audit-logs', {
    params: compactParams({
      operatorUserId: params.operatorUserId,
      action: params.action,
      startTime: params.startTime,
      endTime: params.endTime,
      page: params.page ?? 0,
      size: params.size ?? 20,
    }),
  });
  return data;
}

export async function listAdminAuditOperators() {
  const { data } = await http.get<ReviewAuditOperator[]>('/admin/reviews/audit-logs/operators');
  return data;
}

export async function listAdminTaskAuditLogs(taskId: string) {
  const { data } = await http.get<ReviewAuditLog[]>(`/admin/reviews/tasks/${taskId}/audit-logs`);
  return data;
}
