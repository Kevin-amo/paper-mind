package com.lqr.papermind.paperformat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.document.entity.DocumentIngestionJob;
import com.lqr.papermind.document.mapper.DocumentIngestionJobMapper;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.service.DocumentUploadStorageService;
import com.lqr.papermind.paperformat.dto.AdminPaperFormatTemplateUpdateRequest;
import com.lqr.papermind.paperformat.check.PaperFormatChecker;
import com.lqr.papermind.paperformat.dto.CreateFormatCheckRequest;
import com.lqr.papermind.paperformat.dto.PaperFormatCheckJobResponse;
import com.lqr.papermind.paperformat.dto.PaperFormatTemplateResponse;
import com.lqr.papermind.paperformat.dto.PatchFormatSpecRequest;
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
import com.lqr.papermind.paperformat.service.PaperFormatService;
import com.lqr.papermind.review.entity.ReviewTaskEntity;
import com.lqr.papermind.review.mapper.ReviewTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 论文格式服务实现类，提供模板管理、格式规则解析和格式检查等功能
 */
@Service
@RequiredArgsConstructor
public class PaperFormatServiceImpl implements PaperFormatService {

    private final PaperFormatTemplateMapper templateMapper;
    private final PaperFormatCheckJobMapper checkJobMapper;
    private final DocumentIngestionJobMapper documentIngestionJobMapper;
    private final ReviewTaskMapper reviewTaskMapper;
    private final DocumentPersistenceService documentPersistenceService;
    private final DocumentUploadStorageService documentUploadStorageService;
    private final DocxFormatSpecExtractor specExtractor;
    private final DocxFormatProfileExtractor profileExtractor;
    private final PaperFormatChecker checker;
    private final ObjectMapper objectMapper;

    /** 上传模板文件，解析格式规则并持久化 */
    @Transactional
    @Override
    public PaperFormatTemplateResponse uploadTemplate(UUID currentUserId, boolean admin, MultipartFile file, String name, String schoolName) throws IOException {
        requireDocx(file);
        UUID templateId = UUID.randomUUID();
        DocumentUploadStorageService.StoredUpload upload = documentUploadStorageService.store(
                currentUserId,
                "paper-format-template-" + templateId,
                templateId,
                file,
                file.getOriginalFilename()
        );
        PaperFormatTemplateEntity entity = new PaperFormatTemplateEntity();
        entity.setId(templateId);
        entity.setOwnerUserId(currentUserId);
        entity.setName(blankToDefault(name, upload.fileName()));
        entity.setSchoolName(blankToDefault(schoolName, ""));
        entity.setFileName(upload.fileName());
        entity.setFileType("docx");
        entity.setStorageKey(upload.filePath());
        entity.setPublicTemplate(admin);
        entity.setConfirmed(false);
        entity.setStatus(TEMPLATE_STATUS_PARSING);
        OffsetDateTime now = OffsetDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try (var input = file.getInputStream()) {
            FormatSpec spec = specExtractor.extract(input);
            entity.setFormatSpec(spec);
            entity.setExtractionReport(spec.getExtractionReport());
            entity.setStatus(requiresExtractionConfirmation(spec) ? TEMPLATE_STATUS_NEED_CONFIRM : TEMPLATE_STATUS_READY);
            entity.setConfirmed(TEMPLATE_STATUS_READY.equals(entity.getStatus()));
        } catch (RuntimeException ex) {
            entity.setStatus(TEMPLATE_STATUS_FAILED);
            entity.setExtractionReport(Map.of("error", ex.getMessage() == null ? "模板解析失败" : ex.getMessage()));
        }
        templateMapper.insert(entity);
        return PaperFormatTemplateResponse.from(entity);
    }

    /** 查询用户可见的模板列表 */
    @Override
    public List<PaperFormatTemplateResponse> listTemplates(UUID currentUserId, boolean admin) {
        List<PaperFormatTemplateEntity> templates = admin ? templateMapper.selectAdminTemplates() : templateMapper.selectVisibleTemplates(currentUserId);
        return templates.stream().map(PaperFormatTemplateResponse::from).toList();
    }

    /** 查询所有管理员模板 */
    @Override
    public List<PaperFormatTemplateResponse> listAdminTemplates() {
        List<PaperFormatTemplateEntity> templates = templateMapper.selectAdminTemplates();
        return templates.stream().map(PaperFormatTemplateResponse::from).toList();
    }

    /** 获取单个模板详情 */
    @Override
    public PaperFormatTemplateResponse getTemplate(UUID currentUserId, boolean admin, UUID templateId) {
        return PaperFormatTemplateResponse.from(requireTemplate(currentUserId, admin, templateId));
    }

    /** 管理员更新模板基本信息 */
    @Transactional
    @Override
    public PaperFormatTemplateResponse updateAdminTemplate(UUID templateId, AdminPaperFormatTemplateUpdateRequest request) {
        PaperFormatTemplateEntity entity = requireAdminTemplate(templateId);
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模板名称不能为空");
            }
            entity.setName(request.name().trim());
        }
        if (request.schoolName() != null) {
            entity.setSchoolName(request.schoolName().trim());
        }
        if (request.publicTemplate() != null) {
            entity.setPublicTemplate(request.publicTemplate());
        }
        entity.setUpdatedAt(OffsetDateTime.now());
        templateMapper.updateById(entity);
        return PaperFormatTemplateResponse.from(entity);
    }

    /** 管理员确认模板，标记为就绪状态 */
    @Transactional
    @Override
    public PaperFormatTemplateResponse confirmAdminTemplate(UUID templateId) {
        PaperFormatTemplateEntity entity = requireAdminTemplate(templateId);
        entity.setConfirmed(true);
        entity.setStatus(TEMPLATE_STATUS_READY);
        entity.setUpdatedAt(OffsetDateTime.now());
        templateMapper.updateById(entity);
        return PaperFormatTemplateResponse.from(entity);
    }

    /** 管理员取消模板公开发布 */
    @Transactional
    @Override
    public PaperFormatTemplateResponse unpublishAdminTemplate(UUID templateId) {
        PaperFormatTemplateEntity entity = requireAdminTemplate(templateId);
        entity.setPublicTemplate(false);
        entity.setUpdatedAt(OffsetDateTime.now());
        templateMapper.updateById(entity);
        return PaperFormatTemplateResponse.from(entity);
    }

    /** 更新模板格式规则 */
    @Transactional
    @Override
    public PaperFormatTemplateResponse updateTemplateSpec(UUID currentUserId, boolean admin, UUID templateId, PatchFormatSpecRequest request) {
        PaperFormatTemplateEntity entity = requireTemplate(currentUserId, admin, templateId);
        if (!admin && !currentUserId.equals(entity.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权修改该模板");
        }
        if (request.formatSpec() != null) {
            entity.setFormatSpec(request.formatSpec());
            entity.setStatus(TEMPLATE_STATUS_READY);
        }
        if (request.confirmed() != null) {
            entity.setConfirmed(request.confirmed());
            if (request.confirmed()) {
                entity.setStatus(TEMPLATE_STATUS_READY);
            }
        }
        entity.setUpdatedAt(OffsetDateTime.now());
        templateMapper.updateById(entity);
        return PaperFormatTemplateResponse.from(entity);
    }

    /** 创建格式检查任务 */
    @Transactional
    @Override
    public PaperFormatCheckJobResponse createCheck(UUID currentUserId, boolean admin, CreateFormatCheckRequest request, String scope, UUID reviewTaskId) {
        PaperFormatTemplateEntity template = requireTemplate(currentUserId, admin, request.templateId());
        if (!TEMPLATE_STATUS_READY.equals(template.getStatus()) && !Boolean.TRUE.equals(template.getConfirmed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模板规则尚未确认");
        }
        CheckDocument document = resolveDocument(currentUserId, admin, scope, request.sourceId(), reviewTaskId);
        PaperFormatCheckJobEntity job = new PaperFormatCheckJobEntity();
        job.setId(UUID.randomUUID());
        job.setOwnerUserId(currentUserId);
        job.setTemplateId(template.getId());
        job.setDocumentId(document.documentId());
        job.setSourceId(document.sourceId());
        job.setReviewTaskId(reviewTaskId);
        job.setScope(scope);
        job.setStatus("RUNNING");
        OffsetDateTime now = OffsetDateTime.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        try {
            byte[] bytes = documentUploadStorageService.read(document.filePath());
            DocumentFormatProfile profile = profileExtractor.extract(new ByteArrayInputStream(bytes));
            FormatSpec spec = objectMapper.convertValue(template.getFormatSpec(), FormatSpec.class);
            FormatCheckReport report = checker.check(spec, profile);
            job.setStatus(report.getStatus());
            job.setSummary(report.getSummary());
            job.setViolations(report.getViolations());
        } catch (Exception ex) {
            job.setStatus("ERROR");
            job.setSummary(Map.of("ERROR", 1, "WARNING", 0, "REVIEW", 0));
            FormatViolation violation = formatCheckErrorViolation(ex);
            job.setViolations(List.of(FormatViolation.of(
                    violation.getCode(),
                    violation.getSeverity(),
                    violation.getLocation(),
                    violation.getExpected(),
                    violation.getActual(),
                    violation.getMessage(),
                    violation.getSuggestion()
            )));
        }
        job.setUpdatedAt(OffsetDateTime.now());
        checkJobMapper.insert(job);
        return PaperFormatCheckJobResponse.from(job);
    }

    /** 获取格式检查结果 */
    @Override
    public PaperFormatCheckJobResponse getCheck(UUID currentUserId, boolean admin, UUID checkId) {
        PaperFormatCheckJobEntity entity = checkJobMapper.selectById(checkId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "格式检查任务不存在");
        }
        if (!admin && !currentUserId.equals(entity.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该格式检查任务");
        }
        return PaperFormatCheckJobResponse.from(entity);
    }

    /** 为评审任务创建格式预检 */
    @Transactional
    @Override
    public PaperFormatCheckJobResponse createReviewPrecheck(UUID currentUserId, boolean admin, UUID taskId, UUID templateId) {
        ReviewTaskEntity task = requireReviewTask(currentUserId, admin, taskId);
        return createCheck(currentUserId, admin, new CreateFormatCheckRequest(templateId, task.getSourceId()), CHECK_SCOPE_REVIEW_PRECHECK, taskId);
    }

    /** 获取评审任务最新的预检结果 */
    @Override
    public PaperFormatCheckJobResponse getLatestReviewPrecheck(UUID currentUserId, boolean admin, UUID taskId) {
        requireReviewTask(currentUserId, admin, taskId);
        PaperFormatCheckJobEntity entity = checkJobMapper.selectLatestByReviewTaskId(taskId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "格式检查结果不存在");
        }
        return PaperFormatCheckJobResponse.from(entity);
    }

    /** 获取最新预检中所有错误级别的违规项 */
    @Override
    public List<FormatViolation> latestReviewPrecheckErrors(UUID taskId) {
        PaperFormatCheckJobEntity entity = checkJobMapper.selectLatestByReviewTaskId(taskId);
        if (entity == null || !(entity.getViolations() instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> objectMapper.convertValue(item, FormatViolation.class))
                .filter(item -> "ERROR".equals(item.getSeverity()))
                .toList();
    }

    /** 判断格式规则解析是否存在冲突 */
    private boolean requiresExtractionConfirmation(FormatSpec spec) {
        if (spec == null || spec.getExtractionReport() == null) {
            return false;
        }
        Object conflicts = spec.getExtractionReport().get("conflicts");
        if (conflicts instanceof java.util.Collection<?> collection && !collection.isEmpty()) {
            return true;
        }
        Object lowConfidenceAiRules = spec.getExtractionReport().get("lowConfidenceAiRules");
        return lowConfidenceAiRules instanceof java.util.Collection<?> collection && !collection.isEmpty();
    }
    /** 根据检查范围和来源ID解析文档信息 */
    private CheckDocument resolveDocument(UUID currentUserId, boolean admin, String scope, String sourceId, UUID reviewTaskId) {
        if (CHECK_SCOPE_REVIEW_PRECHECK.equals(scope)) {
            ReviewTaskEntity task = requireReviewTask(currentUserId, admin, reviewTaskId);
            DocumentPersistenceService.DocumentDetail detail = documentPersistenceService.findAnyDocument(task.getSubmitterUserId(), task.getSourceId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "评审论文不存在"));
            return checkDocument(task.getSubmitterUserId(), detail);
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceId 不能为空");
        }
        DocumentPersistenceService.DocumentDetail detail = documentPersistenceService.findAnyDocument(currentUserId, sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
        return checkDocument(currentUserId, detail);
    }

    /** 验证文档为docx格式并获取其存储路径 */
    private CheckDocument checkDocument(UUID ownerUserId, DocumentPersistenceService.DocumentDetail detail) {
        DocumentIngestionJob job = documentIngestionJobMapper.selectLatestByOwnerAndSource(ownerUserId, detail.sourceId());
        if (!isDocx(detail.fileName(), detail.fileType())
                && !isDocx(job == null ? null : job.getFileName(), null)
                && !isDocx(job == null ? null : job.getFilePath(), null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 .docx 文档");
        }
        if (job == null || job.getFilePath() == null || job.getFilePath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到原始上传文件");
        }
        return new CheckDocument(detail.sourceId(), null, job.getFilePath());
    }

    /** 获取评审任务，验证用户有访问权限 */
    private ReviewTaskEntity requireReviewTask(UUID currentUserId, boolean admin, UUID taskId) {
        if (taskId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "评审任务不能为空");
        }
        ReviewTaskEntity task = reviewTaskMapper.selectByIdIncludingDeleted(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "评审任务不存在");
        }
        if (!admin && !currentUserId.equals(task.getReviewerUserId()) && !currentUserId.equals(task.getSubmitterUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该评审任务");
        }
        return task;
    }

    /** 获取模板，验证用户有访问权限（管理员、所有者或公开模板） */
    private PaperFormatTemplateEntity requireTemplate(UUID currentUserId, boolean admin, UUID templateId) {
        PaperFormatTemplateEntity entity = templateMapper.selectById(templateId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "格式模板不存在");
        }
        if (!admin && !currentUserId.equals(entity.getOwnerUserId()) && !Boolean.TRUE.equals(entity.getPublicTemplate())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该模板");
        }
        return entity;
    }

    /** 管理员获取模板，仅验证存在性 */
    private PaperFormatTemplateEntity requireAdminTemplate(UUID templateId) {
        PaperFormatTemplateEntity entity = templateMapper.selectById(templateId);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "格式模板不存在");
        }
        return entity;
    }

    /** 验证上传文件为非空的docx格式 */
    private void requireDocx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }
        if (!isDocx(file.getOriginalFilename(), file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 .docx 文件");
        }
    }

    /** 判断文件名或MIME类型是否为docx格式 */
    private boolean isDocx(String fileName, String fileType) {
        return (fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".docx"))
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(fileType);
    }

    /** 将格式检查异常转换为违规记录 */
    private FormatViolation formatCheckErrorViolation(Exception ex) {
        if (ex instanceof NoSuchFileException) {
            return FormatViolation.of(
                    "FORMAT_CHECK_ERROR",
                    "ERROR",
                    "系统",
                    "读取原始上传文件",
                    "原始上传文件不存在",
                    "原始上传文件不存在，无法执行格式检查",
                    "请重新上传该论文后再执行格式校对；服务端需保留上传原文件"
            );
        }
        return FormatViolation.of(
                "FORMAT_CHECK_ERROR",
                "ERROR",
                "系统",
                "完成格式检查",
                ex.getMessage() == null ? "检查失败" : ex.getMessage(),
                "格式检查执行失败",
                "确认文档为有效 docx 后重试"
        );
    }

    /** 空白字符串返回默认值，否则去除首尾空格 */
    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record CheckDocument(String sourceId, UUID documentId, String filePath) {
    }
}
