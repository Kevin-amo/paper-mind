package com.lqr.papermind.review.service.impl;

import com.lqr.papermind.auth.entity.SysUser;
import com.lqr.papermind.auth.mapper.SysUserMapper;
import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.review.audit.AuditContext;
import com.lqr.papermind.review.audit.ReviewAudit;
import com.lqr.papermind.review.audit.ReviewAuditAction;
import com.lqr.papermind.review.audit.ReviewAuditService;
import com.lqr.papermind.review.dto.AdminTaskDispatchRequest;
import com.lqr.papermind.review.dto.AdminReviewTaskDetailResponse;
import com.lqr.papermind.review.dto.AdminReviewTaskSummaryResponse;
import com.lqr.papermind.review.dto.ReviewAssignmentRequest;
import com.lqr.papermind.review.dto.ReviewAssignmentResponse;
import com.lqr.papermind.review.dto.ReviewAuditLogResponse;
import com.lqr.papermind.review.dto.ReviewAuditOperatorResponse;
import com.lqr.papermind.review.dto.ReviewConsensusResponse;
import com.lqr.papermind.review.dto.ReviewConsensusUpdateRequest;
import com.lqr.papermind.review.dto.ReviewerLoadResponse;
import com.lqr.papermind.review.entity.ReviewAssignmentEntity;
import com.lqr.papermind.review.entity.ReviewAuditLogEntity;
import com.lqr.papermind.review.entity.ReviewConsensusEntity;
import com.lqr.papermind.review.entity.ReviewGroupEntity;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewAssignmentMapper;
import com.lqr.papermind.review.mapper.ReviewConsensusMapper;
import com.lqr.papermind.review.mapper.ReviewGroupMapper;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import com.lqr.papermind.review.model.ReviewAssignmentRoles;
import com.lqr.papermind.review.model.ReviewAssignmentStatuses;
import com.lqr.papermind.review.model.ReviewTaskStatuses;
import com.lqr.papermind.review.service.AdminReviewService;
import com.lqr.papermind.review.service.ReviewAssignmentService;
import com.lqr.papermind.review.service.ReviewConsensusService;
import com.lqr.papermind.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminReviewServiceImpl implements AdminReviewService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewTaskMapper taskMapper;
    private final ReviewAssignmentMapper assignmentMapper;
    private final ReviewConsensusMapper consensusMapper;
    private final ReviewGroupMapper groupMapper;
    private final ReviewService reviewService;
    private final ReviewAssignmentService assignmentService;
    private final ReviewConsensusService consensusService;
    private final SysUserMapper userMapper;
    private final ReviewAuditService reviewAuditService;

    /**
     * 分页查询评审任务列表
     *
     * @param keyword 关键字，用于按标题模糊搜索，可为null
     * @param status  任务状态过滤条件，可为null
     * @param page    页码，从0开始
     * @param size    每页大小，最大100
     * @return 包含评审任务摘要信息的分页响应
     */
    @Override
    public PageResponse<AdminReviewTaskSummaryResponse> listTasks(String keyword, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<AdminReviewTaskSummaryResponse> allItems = taskMapper.selectAdminTasks(blankToNull(keyword), statusFilter(status)).stream()
                .map(this::toSummaryResponse)
                .toList();
        long offset = (long) safePage * safeSize;
        if (offset >= allItems.size()) {
            return new PageResponse<>(List.of(), safePage, safeSize, allItems.size());
        }
        int fromIndex = (int) offset;
        int toIndex = Math.min(fromIndex + safeSize, allItems.size());
        return new PageResponse<>(allItems.subList(fromIndex, toIndex), safePage, safeSize, allItems.size());
    }

    /**
     * 获取评审任务详情
     *
     * @param taskId 评审任务ID
     * @return 包含任务信息、分配记录、提交报告及共识详情的响应对象
     */
    @Override
    public AdminReviewTaskDetailResponse getTask(UUID taskId) {
        ReviewConsensusResponse consensus = consensusService.getForTask(taskId);
        return new AdminReviewTaskDetailResponse(
                reviewService.getTask(null, true, taskId),
                assignmentService.listAssignments(taskId),
                consensus == null ? List.of() : consensus.submittedReports(),
                consensus
        );
    }

    @Override
    @Transactional
    @ReviewAudit(action = ReviewAuditAction.DISPATCH_TO_GROUP)
    public AdminReviewTaskSummaryResponse dispatchTaskToGroup(UUID taskId, UUID operatorUserId, AdminTaskDispatchRequest request) {
        ReviewTaskEntity task = requireTask(taskId);
        ReviewGroupEntity group = requireActiveGroup(request == null ? null : request.groupId());
        if (!ReviewTaskStatuses.PENDING_ASSIGNMENT.equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有待分配任务可以派发到小组");
        }
        if (assignmentMapper.countActiveByTaskId(taskId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "评审任务已存在有效分配，不能派发到小组");
        }
        OffsetDateTime dueAt = request == null ? null : request.dueAt();
        Map<String, Object> beforeSnapshot = taskScopeSnapshot(task);
        taskMapper.dispatchToGroup(taskId, group.getBatchId(), group.getId(), operatorUserId, group.getLeaderUserId(), dueAt);
        task.setBatchId(group.getBatchId());
        task.setGroupId(group.getId());
        task.setAssignedByUserId(operatorUserId);
        task.setLeaderUserId(group.getLeaderUserId());
        task.setDueAt(dueAt);
        task.setAssignedAt(task.getAssignedAt() == null ? OffsetDateTime.now() : task.getAssignedAt());
        task.setUpdatedAt(OffsetDateTime.now());
        AuditContext.set(new AuditContext()
                .taskId(taskId)
                .operatorUserId(operatorUserId)
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(taskScopeSnapshot(task))
                .clientInfo(Map.of("scope", "admin-dispatch")));
        return toSummaryResponse(task);
    }

    /**
     * 为评审任务分配评审人员
     *
     * @param taskId         评审任务ID
     * @param operatorUserId 操作用户ID
     * @param request        评审分配请求，包含分配详情
     * @return 更新后的评审分配列表
     */
    @Override
    public List<ReviewAssignmentResponse> assignReviewers(UUID taskId, UUID operatorUserId, ReviewAssignmentRequest request) {
        return assignmentService.assignReviewers(taskId, operatorUserId, request);
    }

    /**
     * 查询所有评审人员的工作负载情况
     *
     * @return 评审人员负载信息列表
     */
    @Override
    public List<ReviewerLoadResponse> listReviewerLoads() {
        return assignmentService.listReviewerLoads();
    }

    /**
     * 重新计算评审共识
     *
     * @param taskId         评审任务ID
     * @param operatorUserId 操作用户ID
     * @return 重新计算后的共识详情
     */
    @Override
    public ReviewConsensusResponse recalculateConsensus(UUID taskId, UUID operatorUserId) {
        return consensusService.recalculate(taskId, operatorUserId);
    }

    /**
     * 更新评审共识信息
     *
     * @param taskId         评审任务ID
     * @param operatorUserId 操作用户ID
     * @param request        共识更新请求
     * @return 更新后的共识详情
     */
    @Override
    public ReviewConsensusResponse updateConsensus(UUID taskId, UUID operatorUserId, ReviewConsensusUpdateRequest request) {
        return consensusService.update(taskId, operatorUserId, request);
    }

    /**
     * 确认评审共识
     *
     * @param taskId         评审任务ID
     * @param operatorUserId 操作用户ID
     * @return 确认后的共识详情
     */
    @Override
    public ReviewConsensusResponse confirmConsensus(UUID taskId, UUID operatorUserId) {
        return consensusService.confirm(taskId, operatorUserId);
    }

    /**
     * 分页查询全局评审审计日志，支持按操作人、动作类型和时间范围筛选。
     * 通过批量加载操作人信息避免 N+1 查询。
     *
     * @param operatorUserId 操作人用户ID筛选，可为 null
     * @param action         动作类型筛选，可为 null 或空
     * @param startTime      创建时间下界（含），可为 null
     * @param endTime        创建时间上界（含），可为 null
     * @param page           页码（从0开始）
     * @param size           每页大小
     * @return 分页后的审计日志响应列表
     */
    @Override
    public PageResponse<ReviewAuditLogResponse> listAuditLogs(UUID operatorUserId,
                                                              String action,
                                                              OffsetDateTime startTime,
                                                              OffsetDateTime endTime,
                                                              int page,
                                                              int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        IPage<ReviewAuditLogEntity> result = reviewAuditService.searchAuditLogs(
                operatorUserId, action, startTime, endTime, safePage, safeSize);
        Map<UUID, SysUser> userMap = loadOperatorUsers(result.getRecords());
        List<ReviewAuditLogResponse> items = result.getRecords().stream()
                .map(log -> toAuditResponse(log, userMap))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, result.getTotal());
    }

    @Override
    public List<ReviewAuditOperatorResponse> listAuditOperators() {
        List<ReviewAuditLogEntity> logs = reviewAuditService.listOperators();
        Map<UUID, SysUser> userMap = loadOperatorUsers(logs);
        return logs.stream()
                .map(ReviewAuditLogEntity::getOperatorUserId)
                .filter(Objects::nonNull)
                .distinct()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(user -> new ReviewAuditOperatorResponse(user.getId(), user.getUsername(), user.getDisplayName()))
                .toList();
    }

    /**
     * 查询指定评审任务的审计日志列表，按创建时间倒序排列。
     * 利用 idx_review_audit_log_task_created_at 索引加速按任务查询。
     *
     * @param taskId 评审任务ID
     * @return 审计日志响应列表
     */
    @Override
    public List<ReviewAuditLogResponse> listTaskAuditLogs(UUID taskId) {
        List<ReviewAuditLogEntity> logs = reviewAuditService.listByTaskId(taskId);
        Map<UUID, SysUser> userMap = loadOperatorUsers(logs);
        return logs.stream()
                .map(log -> toAuditResponse(log, userMap))
                .toList();
    }

    /**
     * 批量加载审计日志涉及的操作人用户信息，构建用户ID到用户的映射。
     *
     * @param logs 审计日志列表
     * @return 用户ID到用户实体的映射，无操作人时返回空 Map
     */
    private Map<UUID, SysUser> loadOperatorUsers(List<ReviewAuditLogEntity> logs) {
        List<UUID> userIds = logs.stream()
                .map(ReviewAuditLogEntity::getOperatorUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * 将审计日志实体转换为响应DTO，补充操作人用户名和昵称。
     *
     * @param log     审计日志实体
     * @param userMap 操作人用户映射
     * @return 审计日志响应DTO
     */
    private ReviewAuditLogResponse toAuditResponse(ReviewAuditLogEntity log, Map<UUID, SysUser> userMap) {
        SysUser user = log.getOperatorUserId() == null ? null : userMap.get(log.getOperatorUserId());
        String username = user == null ? null : user.getUsername();
        String displayName = user == null ? null : user.getDisplayName();
        return ReviewAuditLogResponse.from(log, username, displayName);
    }

    private ReviewTaskEntity requireTask(UUID taskId) {
        ReviewTaskEntity task = taskMapper.selectByIdIncludingDeleted(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审任务不存在");
        }
        return task;
    }

    private ReviewGroupEntity requireActiveGroup(UUID groupId) {
        if (groupId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择评审小组");
        }
        ReviewGroupEntity group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审小组不存在");
        }
        if (!"ACTIVE".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "评审小组不可用");
        }
        return group;
    }

    private Map<String, Object> taskScopeSnapshot(ReviewTaskEntity task) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskStatus", task.getStatus());
        snapshot.put("batchId", task.getBatchId());
        snapshot.put("groupId", task.getGroupId());
        snapshot.put("assignedByUserId", task.getAssignedByUserId());
        snapshot.put("leaderUserId", task.getLeaderUserId());
        snapshot.put("dueAt", task.getDueAt());
        return snapshot;
    }

    /**
     * 将评审任务实体转换为管理员评审任务摘要响应对象
     *
     * @param task 评审任务实体
     * @return 评审任务摘要响应对象
     */
    private AdminReviewTaskSummaryResponse toSummaryResponse(ReviewTaskEntity task) {
        List<ReviewAssignmentEntity> assignments = assignmentMapper.selectByTaskId(task.getId());
        ReviewConsensusEntity consensus = consensusMapper.selectByTaskId(task.getId());
        UUID leadReviewerUserId = task.getLeaderUserId();
        if (leadReviewerUserId == null) {
            leadReviewerUserId = assignments.stream()
                    .filter(assignment -> !ReviewAssignmentStatuses.CANCELLED.equals(assignment.getStatus()))
                    .filter(assignment -> ReviewAssignmentRoles.LEAD.equals(assignment.getRole()))
                    .map(ReviewAssignmentEntity::getReviewerUserId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        SysUser leadReviewer = leadReviewerUserId == null ? null : userMapper.selectById(leadReviewerUserId);
        OffsetDateTime dueAt = assignmentMapper.maxDueAtByTaskId(task.getId());
        if (dueAt == null) {
            dueAt = task.getDueAt();
        }
        return new AdminReviewTaskSummaryResponse(
                task.getId(),
                task.getDocumentId(),
                task.getSubmitterUserId(),
                task.getSourceId(),
                task.getTitle(),
                task.getStatus(),
                assignments.stream()
                        .filter(assignment -> !ReviewAssignmentStatuses.CANCELLED.equals(assignment.getStatus()))
                        .count(),
                assignments.stream()
                        .filter(assignment -> ReviewAssignmentStatuses.SUBMITTED.equals(assignment.getStatus()))
                        .count(),
                leadReviewerUserId,
                leadReviewer == null ? null : leadReviewer.getUsername(),
                leadReviewer == null ? null : leadReviewer.getDisplayName(),
                null,
                null,
                dueAt,
                consensus == null ? null : consensus.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    /**
     * 将空字符串或空白字符串转换为null
     *
     * @param value 输入字符串
     * @return 处理后的字符串，若输入为空或空白则返回null
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 标准化状态过滤条件
     *
     * @param status 状态字符串
     * @return 处理后的状态字符串，转为大写格式，若输入为空或空白则返回null
     */
    private String statusFilter(String status) {
        return status == null || status.isBlank() ? null : status.trim().toUpperCase();
    }
}
