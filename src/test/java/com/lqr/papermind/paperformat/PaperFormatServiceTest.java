package com.lqr.papermind.paperformat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.common.storage.service.ObjectStorageService;
import com.lqr.papermind.document.entity.DocumentIngestionJob;
import com.lqr.papermind.document.mapper.DocumentIngestionJobMapper;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.service.DocumentUploadStorageService;
import com.lqr.papermind.paperformat.check.PaperFormatChecker;
import com.lqr.papermind.paperformat.dto.AdminPaperFormatTemplateUpdateRequest;
import com.lqr.papermind.paperformat.dto.CreateFormatCheckRequest;
import com.lqr.papermind.paperformat.dto.PaperFormatTemplateResponse;
import com.lqr.papermind.paperformat.entity.PaperFormatCheckJobEntity;
import com.lqr.papermind.paperformat.entity.PaperFormatTemplateEntity;
import com.lqr.papermind.paperformat.extract.DocxFormatProfileExtractor;
import com.lqr.papermind.paperformat.extract.DocxFormatSpecExtractor;
import com.lqr.papermind.paperformat.mapper.PaperFormatCheckJobMapper;
import com.lqr.papermind.paperformat.mapper.PaperFormatTemplateMapper;
import com.lqr.papermind.paperformat.model.DocumentFormatProfile;
import com.lqr.papermind.paperformat.model.FormatCheckReport;
import com.lqr.papermind.paperformat.model.FormatSpec;
import com.lqr.papermind.paperformat.model.FormatViolation;
import com.lqr.papermind.paperformat.service.impl.PaperFormatServiceImpl;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.NoSuchFileException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 论文格式服务测试类，验证模板管理、格式检查等功能的正确性
 */
class PaperFormatServiceTest {

    /** 测试模板列表查询应区分管理员和普通用户 */
    @Test
    void listTemplatesShouldUseAdminListAndRestrictiveVisibleList() {
        UUID ownerUserId = UUID.randomUUID();
        PaperFormatTemplateMapper templateMapper = mock(PaperFormatTemplateMapper.class);
        PaperFormatServiceImpl service = service(templateMapper, mock(PaperFormatCheckJobMapper.class), mock(DocumentUploadStorageService.class));
        PaperFormatTemplateEntity adminTemplate = templateEntity(UUID.randomUUID(), ownerUserId);
        PaperFormatTemplateEntity visibleTemplate = templateEntity(UUID.randomUUID(), ownerUserId);
        when(templateMapper.selectAdminTemplates()).thenReturn(List.of(adminTemplate));
        when(templateMapper.selectVisibleTemplates(ownerUserId)).thenReturn(List.of(visibleTemplate));

        assertThat(service.listTemplates(ownerUserId, true)).extracting(PaperFormatTemplateResponse::id).containsExactly(adminTemplate.getId());
        assertThat(service.listTemplates(ownerUserId, false)).extracting(PaperFormatTemplateResponse::id).containsExactly(visibleTemplate.getId());

        verify(templateMapper).selectAdminTemplates();
        verify(templateMapper).selectVisibleTemplates(ownerUserId);
    }

    /** 测试管理员模板更新、确认和取消发布不删除存储文件 */
    @Test
    void adminTemplateManagementShouldUpdateConfirmAndUnpublishWithoutDeletingStorage() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        PaperFormatTemplateMapper templateMapper = mock(PaperFormatTemplateMapper.class);
        DocumentUploadStorageService storageService = mock(DocumentUploadStorageService.class);
        PaperFormatServiceImpl service = service(templateMapper, mock(PaperFormatCheckJobMapper.class), storageService);
        PaperFormatTemplateEntity template = templateEntity(templateId, ownerUserId);
        template.setPublicTemplate(true);
        template.setConfirmed(false);
        template.setStatus("NEED_CONFIRM");
        when(templateMapper.selectById(templateId)).thenReturn(template);

        service.updateAdminTemplate(templateId, new AdminPaperFormatTemplateUpdateRequest("Updated Template", "Updated School", false));
        service.confirmAdminTemplate(templateId);
        service.unpublishAdminTemplate(templateId);

        ArgumentCaptor<PaperFormatTemplateEntity> captor = ArgumentCaptor.forClass(PaperFormatTemplateEntity.class);
        verify(templateMapper, times(3)).updateById(captor.capture());
        List<PaperFormatTemplateEntity> updates = captor.getAllValues();
        assertThat(updates.get(0).getName()).isEqualTo("Updated Template");
        assertThat(updates.get(0).getSchoolName()).isEqualTo("Updated School");
        assertThat(updates.get(0).getPublicTemplate()).isFalse();
        assertThat(updates.get(1).getConfirmed()).isTrue();
        assertThat(updates.get(1).getStatus()).isEqualTo("READY");
        assertThat(updates.get(2).getPublicTemplate()).isFalse();
        verify(storageService, never()).delete(any());
    }

    /** 测试上传非docx文件时应拒绝 */
    @Test
    void uploadTemplateShouldRejectNonDocx() throws Exception {
        PaperFormatTemplateMapper templateMapper = mock(PaperFormatTemplateMapper.class);
        DocumentUploadStorageService storageService = mock(DocumentUploadStorageService.class);
        PaperFormatServiceImpl service = service(templateMapper, mock(PaperFormatCheckJobMapper.class), storageService);
        MockMultipartFile file = new MockMultipartFile("file", "template.pdf", "application/pdf", "pdf".getBytes());

        assertThatThrownBy(() -> service.uploadTemplate(UUID.randomUUID(), false, file, "Template", "School"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("仅支持 .docx");

        verify(storageService, never()).store(any(), any(), any(), any(), any());
        verify(templateMapper, never()).insert(any(PaperFormatTemplateEntity.class));
    }


    /** 测试无冲突时模板状态应为READY并自动确认 */
    @Test
    void uploadTemplateShouldMarkReadyWhenConflictsListIsEmpty() throws Exception {
        PaperFormatTemplateMapper templateMapper = mock(PaperFormatTemplateMapper.class);
        DocumentUploadStorageService storageService = mock(DocumentUploadStorageService.class);
        DocxFormatSpecExtractor specExtractor = mock(DocxFormatSpecExtractor.class);
        PaperFormatServiceImpl service = service(
                templateMapper,
                mock(PaperFormatCheckJobMapper.class),
                storageService,
                mock(DocumentPersistenceService.class),
                mock(DocumentIngestionJobMapper.class),
                mock(DocxFormatProfileExtractor.class),
                mock(PaperFormatChecker.class),
                specExtractor
        );
        MockMultipartFile file = new MockMultipartFile("file", "template.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx".getBytes());
        when(storageService.store(any(), any(), any(), any(), any())).thenReturn(new DocumentUploadStorageService.StoredUpload("template.docx", "storage/template.docx"));
        FormatSpec spec = new FormatSpec();
        spec.getExtractionReport().put("conflicts", List.of());
        when(specExtractor.extract(any())).thenReturn(spec);

        service.uploadTemplate(UUID.randomUUID(), false, file, "Template", "School");

        ArgumentCaptor<PaperFormatTemplateEntity> captor = ArgumentCaptor.forClass(PaperFormatTemplateEntity.class);
        verify(templateMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("READY");
        assertThat(captor.getValue().getConfirmed()).isTrue();
    }

    /** 测试有冲突时模板状态应为NEED_CONFIRM */
    @Test
    void uploadTemplateShouldNeedConfirmWhenConflictsListIsNotEmpty() throws Exception {
        PaperFormatTemplateMapper templateMapper = mock(PaperFormatTemplateMapper.class);
        DocumentUploadStorageService storageService = mock(DocumentUploadStorageService.class);
        DocxFormatSpecExtractor specExtractor = mock(DocxFormatSpecExtractor.class);
        PaperFormatServiceImpl service = service(
                templateMapper,
                mock(PaperFormatCheckJobMapper.class),
                storageService,
                mock(DocumentPersistenceService.class),
                mock(DocumentIngestionJobMapper.class),
                mock(DocxFormatProfileExtractor.class),
                mock(PaperFormatChecker.class),
                specExtractor
        );
        MockMultipartFile file = new MockMultipartFile("file", "template.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx".getBytes());
        when(storageService.store(any(), any(), any(), any(), any())).thenReturn(new DocumentUploadStorageService.StoredUpload("template.docx", "storage/template.docx"));
        FormatSpec spec = new FormatSpec();
        spec.getExtractionReport().put("conflicts", List.of(Map.of("field", "pageRule.marginTopMm")));
        when(specExtractor.extract(any())).thenReturn(spec);

        service.uploadTemplate(UUID.randomUUID(), false, file, "Template", "School");

        ArgumentCaptor<PaperFormatTemplateEntity> captor = ArgumentCaptor.forClass(PaperFormatTemplateEntity.class);
        verify(templateMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("NEED_CONFIRM");
        assertThat(captor.getValue().getConfirmed()).isFalse();
    }

    /** 测试创建检查任务应持久化失败状态和违规记录 */
    @Test
    void createCheckShouldPersistFailedJobWithViolations() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        PaperFormatCheckJobMapper checkJobMapper = mock(PaperFormatCheckJobMapper.class);
        PaperFormatTemplateMapper templateMapper = mock(PaperFormatTemplateMapper.class);
        DocumentUploadStorageService storageService = mock(DocumentUploadStorageService.class);
        DocxFormatProfileExtractor profileExtractor = mock(DocxFormatProfileExtractor.class);
        PaperFormatChecker checker = mock(PaperFormatChecker.class);
        DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
        DocumentIngestionJobMapper jobMapper = mock(DocumentIngestionJobMapper.class);
        PaperFormatServiceImpl service = service(
                templateMapper,
                checkJobMapper,
                storageService,
                documentPersistenceService,
                jobMapper,
                profileExtractor,
                checker
        );
        PaperFormatTemplateEntity template = new PaperFormatTemplateEntity();
        template.setId(templateId);
        template.setOwnerUserId(ownerUserId);
        template.setStatus("READY");
        template.setFormatSpec(new FormatSpec());
        when(templateMapper.selectById(templateId)).thenReturn(template);
        when(documentPersistenceService.findAnyDocument(ownerUserId, "source-a"))
                .thenReturn(Optional.of(document(ownerUserId, "source-a")));
        DocumentIngestionJob job = new DocumentIngestionJob();
        job.setFilePath("storage/paper.docx");
        when(jobMapper.selectLatestByOwnerAndSource(ownerUserId, "source-a")).thenReturn(job);
        when(storageService.read("storage/paper.docx")).thenReturn("docx".getBytes());
        when(profileExtractor.extract(any())).thenReturn(new DocumentFormatProfile());
        FormatCheckReport report = new FormatCheckReport();
        report.setStatus("FAILED");
        report.setSummary(Map.of("ERROR", 1, "WARNING", 0, "REVIEW", 0));
        report.setViolations(List.of(FormatViolation.of("PAGE_SIZE", "ERROR", "页面", "A4", "Letter", "页面错误", "改为A4")));
        when(checker.check(any(), any())).thenReturn(report);

        service.createCheck(ownerUserId, false, new CreateFormatCheckRequest(templateId, "source-a"), "USER_SELF_CHECK", null);

        ArgumentCaptor<PaperFormatCheckJobEntity> captor = ArgumentCaptor.forClass(PaperFormatCheckJobEntity.class);
        verify(checkJobMapper).insert(captor.capture());
        PaperFormatCheckJobEntity entity = captor.getValue();
        assertThat(entity.getTemplateId()).isEqualTo(templateId);
        assertThat(entity.getSourceId()).isEqualTo("source-a");
        assertThat(entity.getStatus()).isEqualTo("FAILED");
        assertThat(entity.getSummary()).isEqualTo(report.getSummary());
        assertThat(entity.getViolations()).isEqualTo(report.getViolations());
    }

    /** 测试当文档元数据为通用类型时应从上传任务中获取docx文件 */
    @Test
    void createCheckShouldAcceptDocxFromUploadJobWhenDocumentMetadataIsGeneric() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        PaperFormatCheckJobMapper checkJobMapper = mock(PaperFormatCheckJobMapper.class);
        PaperFormatTemplateMapper templateMapper = mock(PaperFormatTemplateMapper.class);
        DocumentUploadStorageService storageService = mock(DocumentUploadStorageService.class);
        DocxFormatProfileExtractor profileExtractor = mock(DocxFormatProfileExtractor.class);
        PaperFormatChecker checker = mock(PaperFormatChecker.class);
        DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
        DocumentIngestionJobMapper jobMapper = mock(DocumentIngestionJobMapper.class);
        PaperFormatServiceImpl service = service(
                templateMapper,
                checkJobMapper,
                storageService,
                documentPersistenceService,
                jobMapper,
                profileExtractor,
                checker
        );
        PaperFormatTemplateEntity template = new PaperFormatTemplateEntity();
        template.setId(templateId);
        template.setOwnerUserId(ownerUserId);
        template.setStatus("READY");
        template.setFormatSpec(new FormatSpec());
        when(templateMapper.selectById(templateId)).thenReturn(template);
        when(documentPersistenceService.findAnyDocument(ownerUserId, "source-a"))
                .thenReturn(Optional.of(document(ownerUserId, "source-a", "Paper", "application/octet-stream")));
        DocumentIngestionJob job = new DocumentIngestionJob();
        job.setFileName("paper.docx");
        job.setFilePath("storage/paper.docx");
        when(jobMapper.selectLatestByOwnerAndSource(ownerUserId, "source-a")).thenReturn(job);
        when(storageService.read("storage/paper.docx")).thenReturn("docx".getBytes());
        when(profileExtractor.extract(any())).thenReturn(new DocumentFormatProfile());
        FormatCheckReport report = new FormatCheckReport();
        report.setStatus("PASSED");
        report.setSummary(Map.of("ERROR", 0, "WARNING", 0, "REVIEW", 0));
        report.setViolations(List.of());
        when(checker.check(any(), any())).thenReturn(report);

        service.createCheck(ownerUserId, false, new CreateFormatCheckRequest(templateId, "source-a"), "USER_SELF_CHECK", null);

        ArgumentCaptor<PaperFormatCheckJobEntity> captor = ArgumentCaptor.forClass(PaperFormatCheckJobEntity.class);
        verify(checkJobMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PASSED");
    }

    /** 测试原始上传文件丢失时应清晰报告错误 */
    @Test
    void createCheckShouldReportMissingOriginalUploadClearly() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        PaperFormatCheckJobMapper checkJobMapper = mock(PaperFormatCheckJobMapper.class);
        PaperFormatTemplateMapper templateMapper = mock(PaperFormatTemplateMapper.class);
        DocumentUploadStorageService storageService = mock(DocumentUploadStorageService.class);
        DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
        DocumentIngestionJobMapper jobMapper = mock(DocumentIngestionJobMapper.class);
        PaperFormatServiceImpl service = service(
                templateMapper,
                checkJobMapper,
                storageService,
                documentPersistenceService,
                jobMapper,
                mock(DocxFormatProfileExtractor.class),
                mock(PaperFormatChecker.class)
        );
        PaperFormatTemplateEntity template = new PaperFormatTemplateEntity();
        template.setId(templateId);
        template.setOwnerUserId(ownerUserId);
        template.setStatus("READY");
        template.setFormatSpec(new FormatSpec());
        when(templateMapper.selectById(templateId)).thenReturn(template);
        when(documentPersistenceService.findAnyDocument(ownerUserId, "source-a"))
                .thenReturn(Optional.of(document(ownerUserId, "source-a")));
        DocumentIngestionJob job = new DocumentIngestionJob();
        job.setFileName("paper.docx");
        job.setFilePath("storage/missing.docx");
        when(jobMapper.selectLatestByOwnerAndSource(ownerUserId, "source-a")).thenReturn(job);
        when(storageService.read("storage/missing.docx")).thenThrow(new NoSuchFileException("storage/missing.docx"));

        service.createCheck(ownerUserId, false, new CreateFormatCheckRequest(templateId, "source-a"), "USER_SELF_CHECK", null);

        ArgumentCaptor<PaperFormatCheckJobEntity> captor = ArgumentCaptor.forClass(PaperFormatCheckJobEntity.class);
        verify(checkJobMapper).insert(captor.capture());
        PaperFormatCheckJobEntity entity = captor.getValue();
        assertThat(entity.getStatus()).isEqualTo("ERROR");
        List<?> violations = (List<?>) entity.getViolations();
        FormatViolation violation = (FormatViolation) violations.getFirst();
        assertThat(violation.getActual()).isEqualTo("原始上传文件不存在");
        assertThat(violation.getMessage()).contains("原始上传文件不存在");
        assertThat(violation.getSuggestion()).contains("重新上传");
    }

    private PaperFormatServiceImpl service(PaperFormatTemplateMapper templateMapper,
                                           PaperFormatCheckJobMapper checkJobMapper,
                                           DocumentUploadStorageService storageService) {
        return service(
                templateMapper,
                checkJobMapper,
                storageService,
                mock(DocumentPersistenceService.class),
                mock(DocumentIngestionJobMapper.class),
                mock(DocxFormatProfileExtractor.class),
                mock(PaperFormatChecker.class)
        );
    }

    private PaperFormatServiceImpl service(PaperFormatTemplateMapper templateMapper,
                                           PaperFormatCheckJobMapper checkJobMapper,
                                           DocumentUploadStorageService storageService,
                                           DocumentPersistenceService documentPersistenceService,
                                           DocumentIngestionJobMapper jobMapper,
                                           DocxFormatProfileExtractor profileExtractor,
                                           PaperFormatChecker checker) {
        return service(templateMapper, checkJobMapper, storageService, documentPersistenceService, jobMapper, profileExtractor, checker, mock(DocxFormatSpecExtractor.class));
    }

    private PaperFormatServiceImpl service(PaperFormatTemplateMapper templateMapper,
                                           PaperFormatCheckJobMapper checkJobMapper,
                                           DocumentUploadStorageService storageService,
                                           DocumentPersistenceService documentPersistenceService,
                                           DocumentIngestionJobMapper jobMapper,
                                           DocxFormatProfileExtractor profileExtractor,
                                           PaperFormatChecker checker,
                                           DocxFormatSpecExtractor specExtractor) {
        return new PaperFormatServiceImpl(
                templateMapper,
                checkJobMapper,
                jobMapper,
                mock(ReviewTaskMapper.class),
                documentPersistenceService,
                storageService,
                specExtractor,
                profileExtractor,
                checker,
                new ObjectMapper()
        );
    }

    private PaperFormatTemplateEntity templateEntity(UUID templateId, UUID ownerUserId) {
        PaperFormatTemplateEntity template = new PaperFormatTemplateEntity();
        template.setId(templateId);
        template.setOwnerUserId(ownerUserId);
        template.setName("Template");
        template.setSchoolName("School");
        template.setFileName("template.docx");
        template.setFileType("docx");
        template.setStorageKey("storage/template.docx");
        template.setStatus("READY");
        template.setFormatSpec(new FormatSpec());
        template.setExtractionReport(Map.of());
        template.setConfirmed(true);
        template.setPublicTemplate(true);
        template.setCreatedAt(OffsetDateTime.now());
        template.setUpdatedAt(OffsetDateTime.now());
        return template;
    }

    private DocumentPersistenceService.DocumentDetail document(UUID ownerUserId, String sourceId) {
        return document(ownerUserId, sourceId, "paper.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    private DocumentPersistenceService.DocumentDetail document(UUID ownerUserId, String sourceId, String fileName, String fileType) {
        return new DocumentPersistenceService.DocumentDetail(
                sourceId,
                ownerUserId,
                "Paper",
                null,
                fileName,
                fileType,
                12L,
                List.of(),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                Map.of(),
                "INDEXED",
                1,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null
        );
    }
}
