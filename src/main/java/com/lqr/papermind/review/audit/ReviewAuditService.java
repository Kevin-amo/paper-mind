package com.lqr.papermind.review.audit;

import com.lqr.papermind.review.entity.ReviewAuditLogEntity;

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
     *
     * @param taskId 关联的评审任务ID
     * @return 审计日志列表
     */
    List<ReviewAuditLogEntity> listByTaskId(UUID taskId);
}
