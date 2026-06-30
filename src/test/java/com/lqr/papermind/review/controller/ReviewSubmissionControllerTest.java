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
import com.lqr.papermind.review.entity.ReviewConsensusEntity;
import com.lqr.papermind.review.entity.ReviewCriterionEntity;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewConsensusMapper;
import com.lqr.papermind.review.mapper.ReviewCriterionMapper;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import com.lqr.papermind.review.model.ReviewConsensusStatuses;
import com.lqr.papermind.review.model.ReviewTaskStatuses;
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
    private ReviewConsensusMapper reviewConsensusMapper;
    private ReviewCriterionMapper reviewCriterionMapper;
    private ReviewSubmissionController controller;
    private UUID ownerUserId;
    private SecurityUserPrincipal principal;

    @BeforeEach
    void setUp() {
        uploadWorkflowService = mock(DocumentUploadWorkflowService.class);
        documentPersistenceService = mock(DocumentPersistenceService.class);
        reviewTaskMapper = mock(ReviewTaskMapper.class);
        reviewConsensusMapper = mock(ReviewConsensusMapper.class);
        reviewCriterionMapper = mock(ReviewCriterionMapper.class);
        controller = new ReviewSubmissionController(
                uploadWorkflowService,
                documentPersistenceService,
                reviewTaskMapper,
                reviewConsensusMapper,
                reviewCriterionMapper
        );
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
        assertThat(item.reviewReport()).isNull();
        assertThat(item.submittedAt()).isEqualTo(submittedAt);
        assertThat(item.updatedAt()).isEqualTo(updatedAt);
        verify(documentPersistenceService).listReviewDocuments(ownerUserId, 0, 20);
        verify(reviewTaskMapper).selectBySubmitterAndSourceIds(ownerUserId, List.of("source-a"));
    }

    @Test
    void listShouldReturnConfirmedConsensusReportForSubmitter() {
        UUID documentId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        OffsetDateTime submittedAt = OffsetDateTime.now().minusHours(2);
        OffsetDateTime updatedAt = OffsetDateTime.now();
        OffsetDateTime confirmedAt = OffsetDateTime.now().minusMinutes(15);
        DocumentPersistenceService.DocumentDetail document = reviewDocument(submittedAt, updatedAt);
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(taskId);
        task.setDocumentId(documentId);
        task.setSourceId("source-a");
        task.setStatus(ReviewTaskStatuses.CONSENSUS_CONFIRMED);
        ReviewConsensusEntity consensus = new ReviewConsensusEntity();
        consensus.setTaskId(taskId);
        consensus.setStatus(ReviewConsensusStatuses.CONFIRMED);
        consensus.setFinalScore(88);
        consensus.setFinalRecommendation("建议录用，补充实验说明。");
        consensus.setConfirmedAt(confirmedAt);
        consensus.setScoreSummary(Map.of(
                "criteria", List.of(
                        Map.of("criterionCode", "INNOVATION", "average", 18),
                        Map.of("criterionCode", "LOGIC", "average", 16)
                )
        ));
        ReviewCriterionEntity innovation = criterion("INNOVATION", "创新性", 20);
        ReviewCriterionEntity logic = criterion("LOGIC", "逻辑结构", 20);
        when(documentPersistenceService.listReviewDocuments(ownerUserId, 0, 20))
                .thenReturn(new DocumentPersistenceService.PageResult<>(List.of(document), 0, 20, 1));
        when(reviewTaskMapper.selectBySubmitterAndSourceIds(eq(ownerUserId), any()))
                .thenReturn(List.of(task));
        when(reviewConsensusMapper.selectByTaskId(taskId)).thenReturn(consensus);
        when(reviewCriterionMapper.selectList(any())).thenReturn(List.of(innovation, logic));

        PageResponse<ReviewSubmissionResponse> response = controller.list(principal, 0, 20);

        ReviewSubmissionResponse item = response.items().getFirst();
        assertThat(item.reviewReport()).isNotNull();
        assertThat(item.reviewReport().taskId()).isEqualTo(taskId);
        assertThat(item.reviewReport().finalScore()).isEqualTo(88);
        assertThat(item.reviewReport().finalRecommendation()).isEqualTo("建议录用，补充实验说明。");
        assertThat(item.reviewReport().confirmedAt()).isEqualTo(confirmedAt);
        assertThat(item.reviewReport().criteriaScores())
                .extracting("code", "name", "score", "maxScore")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("INNOVATION", "创新性", 18, 20),
                        org.assertj.core.groups.Tuple.tuple("LOGIC", "逻辑结构", 16, 20)
                );
    }

    @Test
    void listShouldNotReturnDraftConsensusReportForSubmitter() {
        UUID documentId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        DocumentPersistenceService.DocumentDetail document = reviewDocument(OffsetDateTime.now().minusHours(2), OffsetDateTime.now());
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(taskId);
        task.setDocumentId(documentId);
        task.setSourceId("source-a");
        task.setStatus(ReviewTaskStatuses.CONSENSUS_CONFIRMED);
        ReviewConsensusEntity consensus = new ReviewConsensusEntity();
        consensus.setTaskId(taskId);
        consensus.setStatus(ReviewConsensusStatuses.DRAFT);
        consensus.setFinalScore(88);
        consensus.setScoreSummary(Map.of("criteria", List.of(Map.of("criterionCode", "LOGIC", "average", 16))));
        when(documentPersistenceService.listReviewDocuments(ownerUserId, 0, 20))
                .thenReturn(new DocumentPersistenceService.PageResult<>(List.of(document), 0, 20, 1));
        when(reviewTaskMapper.selectBySubmitterAndSourceIds(eq(ownerUserId), any()))
                .thenReturn(List.of(task));
        when(reviewConsensusMapper.selectByTaskId(taskId)).thenReturn(consensus);

        PageResponse<ReviewSubmissionResponse> response = controller.list(principal, 0, 20);

        ReviewSubmissionResponse item = response.items().getFirst();
        assertThat(item.reviewReport()).isNull();
    }

    private DocumentPersistenceService.DocumentDetail reviewDocument(OffsetDateTime submittedAt, OffsetDateTime updatedAt) {
        return new DocumentPersistenceService.DocumentDetail(
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
    }

    private ReviewCriterionEntity criterion(String code, String name, Integer maxScore) {
        ReviewCriterionEntity criterion = new ReviewCriterionEntity();
        criterion.setId(UUID.randomUUID());
        criterion.setCode(code);
        criterion.setName(name);
        criterion.setMaxScore(maxScore);
        criterion.setEnabled(true);
        return criterion;
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
