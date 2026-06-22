package com.lqr.papermind.review.controller;

import com.lqr.papermind.auth.entity.SysUser;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewSubmissionControllerTest {

    private DocumentUploadWorkflowService uploadWorkflowService;
    private DocumentPersistenceService documentPersistenceService;
    private ReviewTaskMapper reviewTaskMapper;
    private ReviewSubmissionController controller;
    private UUID ownerUserId;
    private SecurityUserPrincipal principal;

    @BeforeEach
    void setUp() {
        uploadWorkflowService = mock(DocumentUploadWorkflowService.class);
        documentPersistenceService = mock(DocumentPersistenceService.class);
        reviewTaskMapper = mock(ReviewTaskMapper.class);
        controller = new ReviewSubmissionController(uploadWorkflowService, documentPersistenceService, reviewTaskMapper);
        ownerUserId = UUID.randomUUID();
        principal = principal(ownerUserId);
    }

    @Test
    void submitShouldCreateReviewSourceUploadJob() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "paper-a.pdf", "application/pdf", "a".getBytes());
        DocumentIngestionJob job = new DocumentIngestionJob();
        job.setId(UUID.randomUUID());
        job.setOwnerUserId(ownerUserId);
        job.setSourceId("source-a");
        job.setFileName("paper-a.pdf");
        job.setTitle("Paper A");
        job.setStatus("QUEUED");
        when(uploadWorkflowService.createAndPublishJob(
                eq(ownerUserId),
                eq(file),
                eq("source-a"),
                eq("Paper A"),
                eq(null),
                eq(MetadataKeys.SOURCE_TYPE_REVIEW)
        )).thenReturn(job);

        ResponseEntity<DocumentUploadAcceptedResponse> response = controller.submit(principal, file, "source-a", "Paper A");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().jobId()).isEqualTo(job.getId());
        assertThat(response.getBody().sourceId()).isEqualTo("source-a");
        assertThat(response.getBody().status()).isEqualTo("QUEUED");
        verify(uploadWorkflowService).createAndPublishJob(ownerUserId, file, "source-a", "Paper A", null, MetadataKeys.SOURCE_TYPE_REVIEW);
    }

    @Test
    void listShouldReturnOnlyCurrentUserReviewDocumentsWithTaskStatus() {
        UUID documentId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        OffsetDateTime submittedAt = OffsetDateTime.now().minusHours(2);
        OffsetDateTime updatedAt = OffsetDateTime.now();
        DocumentPersistenceService.DocumentDetail document = new DocumentPersistenceService.DocumentDetail(
                "source-a",
                ownerUserId,
                "Paper A",
                "upload",
                "paper-a.pdf",
                "application/pdf",
                12L,
                List.of(),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                Map.of(MetadataKeys.SOURCE_TYPE, MetadataKeys.SOURCE_TYPE_REVIEW),
                "INDEXED",
                3,
                null,
                submittedAt,
                updatedAt,
                null
        );
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(taskId);
        task.setDocumentId(documentId);
        task.setSourceId("source-a");
        task.setStatus("PENDING_ASSIGNMENT");
        when(documentPersistenceService.listReviewDocuments(ownerUserId, 0, 20))
                .thenReturn(new DocumentPersistenceService.PageResult<>(List.of(document), 0, 20, 1));
        when(reviewTaskMapper.selectBySubmitterAndSourceIds(eq(ownerUserId), any()))
                .thenReturn(List.of(task));

        PageResponse<ReviewSubmissionResponse> response = controller.list(principal, 0, 20);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        ReviewSubmissionResponse item = response.items().getFirst();
        assertThat(item.sourceId()).isEqualTo("source-a");
        assertThat(item.title()).isEqualTo("Paper A");
        assertThat(item.fileName()).isEqualTo("paper-a.pdf");
        assertThat(item.documentStatus()).isEqualTo("INDEXED");
        assertThat(item.reviewTaskId()).isEqualTo(taskId);
        assertThat(item.reviewStatus()).isEqualTo("PENDING_ASSIGNMENT");
        assertThat(item.submittedAt()).isEqualTo(submittedAt);
        assertThat(item.updatedAt()).isEqualTo(updatedAt);
        verify(documentPersistenceService).listReviewDocuments(ownerUserId, 0, 20);
        verify(reviewTaskMapper).selectBySubmitterAndSourceIds(ownerUserId, List.of("source-a"));
    }

    private SecurityUserPrincipal principal(UUID userId) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setUsername("author");
        user.setPasswordHash("{noop}password");
        user.setDisplayName("Author");
        user.setStatus("ACTIVE");
        return new SecurityUserPrincipal(user, List.of("USER"));
    }
}
