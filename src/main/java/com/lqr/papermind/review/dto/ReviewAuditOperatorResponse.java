package com.lqr.papermind.review.dto;

import java.util.UUID;

/**
 * 评审审计日志操作人筛选选项。
 */
public record ReviewAuditOperatorResponse(
        UUID userId,
        String username,
        String displayName
) {
}
