package com.lqr.papermind.review.audit;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lqr.papermind.review.entity.ReviewAuditLogEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 评审审计日志服务。
 */
public interface ReviewAuditService {

    /**
     * 追加一条评审审计日志。
     * 记录操作人、操作类型、变更前后快照及差异信息。
     *
     * @param taskId         关联任务唯一标识
     * @param actorUserId    操作人用户唯一标识
     * @param action         操作类型
     * @param message        操作备注信息
     * @param beforeSnapshot 变更前状态快照，可为 null
     * @param afterSnapshot  变更后状态快照，可为 null
     * @param clientInfo     客户端信息，可为 null
     */
    void append(UUID taskId,
                UUID actorUserId,
                String action,
                String message,
                Map<String, Object> beforeSnapshot,
                Map<String, Object> afterSnapshot,
                Map<String, Object> clientInfo);

    /**
     * 查询指定任务的操作审计日志列表，按创建时间倒序排列。
     * 利用索引 idx_review_audit_log_task_created_at(task_id, created_at desc) 加速查询。
     *
     * @param taskId 关联的评审任务ID
     * @return 审计日志列表
     */
    List<ReviewAuditLogEntity> listByTaskId(UUID taskId);

    /**
     * 查询审计日志中出现过的非系统操作人记录。
     *
     * @return 包含非空 operatorUserId 的审计日志列表
     */
    List<ReviewAuditLogEntity> listOperators();

    /**
     * 分页查询全局评审审计日志，支持按操作人、动作类型和时间范围筛选，按创建时间倒序排列。
     *
     * @param operatorUserId 操作人用户ID筛选，可为 null
     * @param action         动作类型筛选，可为 null 或空
     * @param startTime      创建时间下界（含），可为 null
     * @param endTime        创建时间上界（含），可为 null
     * @param page           页码（从0开始）
     * @param size           每页大小
     * @return 分页后的审计日志实体
     */
    IPage<ReviewAuditLogEntity> searchAuditLogs(UUID operatorUserId,
                                                String action,
                                                OffsetDateTime startTime,
                                                OffsetDateTime endTime,
                                                int page,
                                                int size);
}
