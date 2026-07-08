package com.lqr.papermind.review.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.review.entity.ReviewAuditLogEntity;
import com.lqr.papermind.review.entity.ReviewReportEntity;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void reportResponseShouldNotExposeTechnicalArchiveFields() throws Exception {
        ReviewReportEntity entity = new ReviewReportEntity();
        entity.setId(UUID.randomUUID());
        entity.setTaskId(UUID.randomUUID());
        entity.setDocumentId(UUID.randomUUID());
        entity.setCriterionVersion(2);
        entity.setModelVersion("qwen3.6-27b");
        entity.setPromptVersion("1.0");
        entity.setManualDelta(Map.of("scoreChanged", true));

        Map<String, Object> json = toMap(ReviewReportResponse.from(entity));

        assertThat(json).containsEntry("modelVersion", "qwen3.6-27b");
        assertThat(json).doesNotContainKeys("criterionVersion", "promptVersion", "manualDelta");
    }

    @Test
    void auditLogResponseShouldNotExposeSnapshotsOrDiff() throws Exception {
        ReviewAuditLogEntity entity = new ReviewAuditLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setTaskId(UUID.randomUUID());
        entity.setOperatorUserId(UUID.randomUUID());
        entity.setAction("ADJUST_REPORT");
        entity.setNote("人工调整评审报告");
        entity.setBeforeSnapshot(Map.of("score", 80));
        entity.setAfterSnapshot(Map.of("score", 90));
        entity.setDiff(Map.of("score", Map.of("before", 80, "after", 90)));
        entity.setCreatedAt(OffsetDateTime.now());

        Map<String, Object> json = toMap(ReviewAuditLogResponse.from(entity, "reviewer", "Reviewer"));

        assertThat(json).containsEntry("action", "ADJUST_REPORT");
        assertThat(json).containsEntry("operatorUsername", "reviewer");
        assertThat(json).doesNotContainKeys("beforeSnapshot", "afterSnapshot", "diff");
    }

    private Map<String, Object> toMap(Object response) throws Exception {
        return objectMapper.readValue(objectMapper.writeValueAsString(response), new TypeReference<>() {
        });
    }
}
