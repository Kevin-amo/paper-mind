package com.lqr.papermind.review.controller;

import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.common.constant.MetadataKeys;
import com.lqr.papermind.document.dto.DocumentUploadAcceptedResponse;
import com.lqr.papermind.document.dto.PageResponse;
import com.lqr.papermind.document.entity.DocumentIngestionJob;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.service.DocumentUploadWorkflowService;
import com.lqr.papermind.review.dto.ReviewSubmissionResponse;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
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
import java.util.UUID;

@RestController
@RequestMapping("/review-submissions")
@RequiredArgsConstructor
public class ReviewSubmissionController {

    private final DocumentUploadWorkflowService documentUploadWorkflowService;
    private final DocumentPersistenceService documentPersistenceService;
    private final ReviewTaskMapper reviewTaskMapper;

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
        List<ReviewSubmissionResponse> items = documents.items().stream()
                .map(document -> ReviewSubmissionResponse.from(document, tasksBySourceId.get(document.sourceId())))
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
}
