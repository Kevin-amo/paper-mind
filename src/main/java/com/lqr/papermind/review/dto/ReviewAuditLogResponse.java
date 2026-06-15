package com.lqr.papermind.review.dto;

import com.lqr.papermind.review.entity.ReviewAuditLogEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 评审审计日志响应DTO，用于展示评审操作的历史记录。
 */
public record ReviewAuditLogResponse(
        /* 日志记录唯一标识 */
        UUID id,
        /* 关联的评审任务ID */
        UUID taskId,
        /* 执行操作的用户ID */
        UUID operatorUserId,
        /* 操作人用户名 */
        String operatorUsername,
        /* 操作人显示名称 */
        String operatorDisplayName,
        /* 操作类型 */
        String action,
        /* 操作备注说明 */
        String note,
        /* 记录创建时间 */
        OffsetDateTime createdAt
) {
    /**
     * 从审计日志实体构建响应DTO（不含操作人信息）。
     */
    public static ReviewAuditLogResponse from(ReviewAuditLogEntity entity) {
        return from(entity, null, null);
    }

    /**
     * 从审计日志实体和操作人信息构建响应DTO。
     */
    public static ReviewAuditLogResponse from(ReviewAuditLogEntity entity, String username, String displayName) {
        if (entity == null) {
            return null;
        }
        return new ReviewAuditLogResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getOperatorUserId(),
                username,
                displayName,
                entity.getAction(),
                entity.getNote(),
                entity.getCreatedAt()
        );
    }
}
