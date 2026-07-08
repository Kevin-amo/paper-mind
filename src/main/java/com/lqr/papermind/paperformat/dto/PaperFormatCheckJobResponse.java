package com.lqr.papermind.paperformat.dto;

import com.lqr.papermind.paperformat.entity.PaperFormatCheckJobEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PaperFormatCheckJobResponse(
        UUID id,
        UUID templateId,
        UUID documentId,
        String sourceId,
        UUID reviewTaskId,
        String scope,
        String status,
        Object summary,
        Object violations,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PaperFormatCheckJobResponse from(PaperFormatCheckJobEntity entity) {
        return new PaperFormatCheckJobResponse(
                entity.getId(),
                entity.getTemplateId(),
                entity.getDocumentId(),
                entity.getSourceId(),
                entity.getReviewTaskId(),
                entity.getScope(),
                entity.getStatus(),
                entity.getSummary(),
                entity.getViolations(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
