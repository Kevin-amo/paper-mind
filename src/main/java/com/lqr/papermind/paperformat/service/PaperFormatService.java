package com.lqr.papermind.paperformat.service;

import com.lqr.papermind.paperformat.dto.CreateFormatCheckRequest;
import com.lqr.papermind.paperformat.dto.PaperFormatCheckJobResponse;
import com.lqr.papermind.paperformat.dto.PaperFormatTemplateResponse;
import com.lqr.papermind.paperformat.dto.PatchFormatSpecRequest;
import com.lqr.papermind.paperformat.model.FormatViolation;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface PaperFormatService {
    String TEMPLATE_STATUS_READY = "READY";
    String TEMPLATE_STATUS_FAILED = "FAILED";
    String TEMPLATE_STATUS_NEED_CONFIRM = "NEED_CONFIRM";
    String TEMPLATE_STATUS_PARSING = "PARSING";
    String CHECK_SCOPE_USER_SELF_CHECK = "USER_SELF_CHECK";
    String CHECK_SCOPE_REVIEW_PRECHECK = "REVIEW_PRECHECK";

    PaperFormatTemplateResponse uploadTemplate(UUID currentUserId, boolean admin, MultipartFile file, String name, String schoolName) throws IOException;

    List<PaperFormatTemplateResponse> listTemplates(UUID currentUserId, boolean admin);

    PaperFormatTemplateResponse getTemplate(UUID currentUserId, boolean admin, UUID templateId);

    PaperFormatTemplateResponse updateTemplateSpec(UUID currentUserId, boolean admin, UUID templateId, PatchFormatSpecRequest request);

    PaperFormatCheckJobResponse createCheck(UUID currentUserId, boolean admin, CreateFormatCheckRequest request, String scope, UUID reviewTaskId);

    PaperFormatCheckJobResponse getCheck(UUID currentUserId, boolean admin, UUID checkId);

    PaperFormatCheckJobResponse createReviewPrecheck(UUID currentUserId, boolean admin, UUID taskId, UUID templateId);

    PaperFormatCheckJobResponse getLatestReviewPrecheck(UUID currentUserId, boolean admin, UUID taskId);

    List<FormatViolation> latestReviewPrecheckErrors(UUID taskId);
}
