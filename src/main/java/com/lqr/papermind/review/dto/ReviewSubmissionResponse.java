package com.lqr.papermind.review.dto;

import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.review.entity.ReviewTaskEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReviewSubmissionResponse(
        String sourceId,
        String title,
        String fileName,
        String documentStatus,
        String errorMessage,
        UUID reviewTaskId,
        String reviewStatus,
        OffsetDateTime submittedAt,
        OffsetDateTime updatedAt,
        ReviewSubmissionReportResponse reviewReport
) {
    public static ReviewSubmissionResponse from(DocumentPersistenceService.DocumentDetail document, ReviewTaskEntity task) {
        return from(document, task, null);
    }

    public static ReviewSubmissionResponse from(DocumentPersistenceService.DocumentDetail document,
                                                ReviewTaskEntity task,
                                                ReviewSubmissionReportResponse reviewReport) {
        return new ReviewSubmissionResponse(
                document.sourceId(),
                document.title(),
                document.fileName(),
                document.status(),
                document.errorMessage(),
                task == null ? null : task.getId(),
                task == null ? null : task.getStatus(),
                document.createdAt(),
                document.updatedAt(),
                reviewReport
        );
    }

    public record ReviewSubmissionReportResponse(
            UUID taskId,
            Integer finalScore,
            String finalRecommendation,
            OffsetDateTime confirmedAt,
            List<ReviewSubmissionCriterionScoreResponse> criteriaScores
    ) {
    }

    public record ReviewSubmissionCriterionScoreResponse(
            String code,
            String name,
            Integer score,
            Integer maxScore
    ) {
    }
}
