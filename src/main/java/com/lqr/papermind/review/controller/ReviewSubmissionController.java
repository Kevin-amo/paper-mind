package com.lqr.papermind.review.controller;

import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.common.constant.MetadataKeys;
import com.lqr.papermind.document.dto.DocumentUploadAcceptedResponse;
import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.document.entity.DocumentIngestionJob;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.service.DocumentUploadWorkflowService;
import com.lqr.papermind.review.dto.ReviewSubmissionResponse;
import com.lqr.papermind.review.entity.ReviewConsensusEntity;
import com.lqr.papermind.review.entity.ReviewCriterionEntity;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewConsensusMapper;
import com.lqr.papermind.review.mapper.ReviewCriterionMapper;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import com.lqr.papermind.review.model.ReviewConsensusStatuses;
import com.lqr.papermind.review.model.ReviewTaskStatuses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/review-submissions")
@RequiredArgsConstructor
public class ReviewSubmissionController {

    private final DocumentUploadWorkflowService documentUploadWorkflowService;
    private final DocumentPersistenceService documentPersistenceService;
    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewConsensusMapper reviewConsensusMapper;
    private final ReviewCriterionMapper reviewCriterionMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadAcceptedResponse> submit(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                                 @RequestParam("file") MultipartFile file,
                                                                 @RequestParam(value = "sourceId", required = false) String sourceId,
                                                                 @RequestParam(value = "title", required = false) String title) throws IOException {
        DocumentIngestionJob job = documentUploadWorkflowService.createAndPublishJob(
                principal.getId(),
                file,
                sourceId,
                title,
                null,
                MetadataKeys.SOURCE_TYPE_REVIEW
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(DocumentUploadAcceptedResponse.from(job));
    }

    @GetMapping
    public PageResponse<ReviewSubmissionResponse> list(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                       @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
                                                       @RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        DocumentPersistenceService.PageResult<DocumentPersistenceService.DocumentDetail> documents =
                documentPersistenceService.listReviewDocuments(principal.getId(), page, size);
        Map<String, ReviewTaskEntity> tasksBySourceId = tasksBySourceId(principal.getId(), documents.items());
        Map<String, ReviewCriterionEntity> criteriaByCode = criteriaByCode();
        List<ReviewSubmissionResponse> items = documents.items().stream()
                .map(document -> {
                    ReviewTaskEntity task = tasksBySourceId.get(document.sourceId());
                    return ReviewSubmissionResponse.from(document, task, publicReport(task, criteriaByCode));
                })
                .toList();
        return new PageResponse<>(items, documents.page(), documents.size(), documents.total());
    }

    private Map<String, ReviewTaskEntity> tasksBySourceId(UUID submitterUserId,
                                                          List<DocumentPersistenceService.DocumentDetail> documents) {
        List<String> sourceIds = documents.stream()
                .map(DocumentPersistenceService.DocumentDetail::sourceId)
                .filter(sourceId -> sourceId != null && !sourceId.isBlank())
                .toList();
        if (sourceIds.isEmpty()) {
            return Map.of();
        }
        Map<String, ReviewTaskEntity> result = new LinkedHashMap<>();
        for (ReviewTaskEntity task : reviewTaskMapper.selectBySubmitterAndSourceIds(submitterUserId, sourceIds)) {
            result.putIfAbsent(task.getSourceId(), task);
        }
        return result;
    }

    private ReviewSubmissionResponse.ReviewSubmissionReportResponse publicReport(
            ReviewTaskEntity task,
            Map<String, ReviewCriterionEntity> criteriaByCode
    ) {
        if (task == null || !ReviewTaskStatuses.CONSENSUS_CONFIRMED.equals(task.getStatus())) {
            return null;
        }
        ReviewConsensusEntity consensus = reviewConsensusMapper.selectByTaskId(task.getId());
        if (consensus == null || !ReviewConsensusStatuses.CONFIRMED.equals(consensus.getStatus())) {
            return null;
        }
        return new ReviewSubmissionResponse.ReviewSubmissionReportResponse(
                task.getId(),
                consensus.getFinalScore(),
                consensus.getFinalRecommendation(),
                consensus.getConfirmedAt(),
                criterionScores(consensus.getScoreSummary(), criteriaByCode)
        );
    }

    private Map<String, ReviewCriterionEntity> criteriaByCode() {
        List<ReviewCriterionEntity> criteria = reviewCriterionMapper.selectList(null);
        if (criteria == null || criteria.isEmpty()) {
            return Map.of();
        }
        return criteria.stream()
                .filter(Objects::nonNull)
                .filter(criterion -> criterion.getCode() != null && !criterion.getCode().isBlank())
                .collect(Collectors.toMap(
                        ReviewCriterionEntity::getCode,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    private List<ReviewSubmissionResponse.ReviewSubmissionCriterionScoreResponse> criterionScores(
            Object scoreSummary,
            Map<String, ReviewCriterionEntity> criteriaByCode
    ) {
        if (!(scoreSummary instanceof Map<?, ?> summary)) {
            return List.of();
        }
        Object criteria = summary.get("criteria");
        if (!(criteria instanceof List<?> items)) {
            return List.of();
        }
        return items.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(item -> criterionScore(item, criteriaByCode))
                .filter(Objects::nonNull)
                .toList();
    }

    private ReviewSubmissionResponse.ReviewSubmissionCriterionScoreResponse criterionScore(
            Map<?, ?> item,
            Map<String, ReviewCriterionEntity> criteriaByCode
    ) {
        Object codeValue = item.get("criterionCode");
        if (codeValue == null || String.valueOf(codeValue).isBlank()) {
            return null;
        }
        String code = String.valueOf(codeValue);
        ReviewCriterionEntity criterion = criteriaByCode.get(code);
        return new ReviewSubmissionResponse.ReviewSubmissionCriterionScoreResponse(
                code,
                criterion == null || criterion.getName() == null || criterion.getName().isBlank()
                        ? code
                        : criterion.getName(),
                integerValue(item.get("average")),
                criterion == null ? null : criterion.getMaxScore()
        );
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return (int) Math.round(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return (int) Math.round(Double.parseDouble(text.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
