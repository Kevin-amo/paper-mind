package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 管理员将待分配评审任务派发给评审小组的请求。
 */
public record AdminTaskDispatchRequest(
        @NotNull UUID groupId,
        OffsetDateTime dueAt
) {
}
