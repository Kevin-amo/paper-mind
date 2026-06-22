package com.lqr.papermind.review.service;

import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.review.dto.AdminTaskDispatchRequest;
import com.lqr.papermind.review.dto.AdminReviewTaskDetailResponse;
import com.lqr.papermind.review.dto.AdminReviewTaskSummaryResponse;
import com.lqr.papermind.review.dto.ReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentResponse;
import com.lqr.papermind.review.dto.ReviewAuditLogResponse;
import com.lqr.papermind.review.dto.ReviewAuditOperatorResponse;
import com.lqr.papermind.review.dto.ReviewerLoadResponse;
import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AdminReviewService {

    /**
     * 管理员查询评审任务列表
     *
     * @param keyword 搜索关键词
     * @param status  任务状态过滤
     * @param page    页码
     * @param size    每页大小
     * @return 评审任务分页列表
     */
    PageResponse<AdminReviewTaskSummaryResponse> listTasks(String keyword, String status, int page, int size);

    /**
     * 管理员获取评审任务详情
     *
     * @param taskId 任务ID
     * @return 评审任务详情
    */
    AdminReviewTaskDetailResponse getTask(UUID taskId);

    AdminReviewTaskSummaryResponse dispatchTaskToGroup(UUID taskId, UUID operatorUserId, AdminTaskDispatchRequest request);

    /**
     * 分配评审人
     *
     * @param taskId         任务ID
     * @param operatorUserId 操作人用户ID
     * @param request        分配请求参数
     * @return 分配结果列表
     */
    List<ReviewAssignmentResponse> assignReviewers(UUID taskId, UUID operatorUserId, ReviewAssignmentRequest request);

    /**
     * 查询评审人工作负载列表
     *
     * @return 评审人工作负载列表
     */
    List<ReviewerLoadResponse> listReviewerLoads();

    /**
     * 重新计算评审共识
     *
     * @param taskId         任务ID
     * @param operatorUserId 操作人用户ID
     * @return 重新计算后的评审共识
     */
    ReviewConsensusResponse recalculateConsensus(UUID taskId, UUID operatorUserId);

    /**
     * 更新评审共识
     *
     * @param taskId         任务ID
     * @param operatorUserId 操作人用户ID
     * @param request        更新请求参数
     * @return 更新后的评审共识
     */
    ReviewConsensusResponse updateConsensus(UUID taskId, UUID operatorUserId, ReviewConsensusUpdateRequest request);

    /**
     * 确认评审共识
     *
     * @param taskId         任务ID
     * @param operatorUserId 操作人用户ID
     * @return 确认后的评审共识
     */
    ReviewConsensusResponse confirmConsensus(UUID taskId, UUID operatorUserId);

    /**
     * 分页查询全局评审审计日志，支持按操作人、动作类型和时间范围筛选。
     *
     * @param operatorUserId 操作人用户ID筛选，可为 null
     * @param action         动作类型筛选，可为 null 或空
     * @param startTime      创建时间下界（含），可为 null
     * @param endTime        创建时间上界（含），可为 null
     * @param page           页码（从0开始）
     * @param size           每页大小
     * @return 分页后的审计日志响应列表
     */
    PageResponse<ReviewAuditLogResponse> listAuditLogs(UUID operatorUserId,
                                                       String action,
                                                       OffsetDateTime startTime,
                                                       OffsetDateTime endTime,
                                                       int page,
                                                       int size);

    /**
     * 查询全局审计日志筛选用的实际操作人列表，不包含系统/自动事件。
     *
     * @return 审计日志中出现过的操作人列表
     */
    List<ReviewAuditOperatorResponse> listAuditOperators();

    /**
     * 查询指定评审任务的审计日志列表，按创建时间倒序排列。
     *
     * @param taskId 评审任务ID
     * @return 审计日志响应列表
     */
    List<ReviewAuditLogResponse> listTaskAuditLogs(UUID taskId);
}
