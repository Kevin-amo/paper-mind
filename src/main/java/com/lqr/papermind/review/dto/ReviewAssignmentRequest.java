package com.lqr.papermind.review.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Admin override assignment request for assigning reviewers directly.
 */
public record ReviewAssignmentRequest(
        @NotEmpty List<UUID> reviewerUserIds,
        @NotNull UUID leadReviewerUserId,
        UUID groupId,
        OffsetDateTime dueAt
) {
}
