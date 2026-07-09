package com.lqr.papermind.paperformat.controller;

import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.paperformat.dto.AdminPaperFormatTemplateUpdateRequest;
import com.lqr.papermind.paperformat.dto.PaperFormatTemplateResponse;
import com.lqr.papermind.paperformat.service.PaperFormatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 管理员论文格式模板管理控制器，提供模板的增删改查接口
 */
@RestController
@RequestMapping("/admin/paper-format/templates")
@RequiredArgsConstructor
public class AdminPaperFormatTemplateController {

    private final PaperFormatService paperFormatService;

    /** 获取所有管理员模板列表 */
    @GetMapping
    public List<PaperFormatTemplateResponse> listTemplates() {
        return paperFormatService.listAdminTemplates();
    }

    /** 根据ID获取模板详情 */
    @GetMapping("/{templateId}")
    public PaperFormatTemplateResponse getTemplate(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                   @PathVariable UUID templateId) {
        return paperFormatService.getTemplate(principal.getId(), true, templateId);
    }

    /** 上传新的格式模板文件 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PaperFormatTemplateResponse uploadTemplate(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam("name") String name,
                                                      @RequestParam(value = "schoolName", required = false) String schoolName) throws IOException {
        return paperFormatService.uploadTemplate(principal.getId(), true, file, name, schoolName);
    }

    /** 更新模板基本信息（名称、学校、公开状态） */
    @PatchMapping("/{templateId}")
    public PaperFormatTemplateResponse updateTemplate(@PathVariable UUID templateId,
                                                      @Valid @RequestBody AdminPaperFormatTemplateUpdateRequest request) {
        return paperFormatService.updateAdminTemplate(templateId, request);
    }

    /** 确认模板，标记为就绪状态 */
    @PostMapping("/{templateId}/confirm")
    public PaperFormatTemplateResponse confirmTemplate(@PathVariable UUID templateId) {
        return paperFormatService.confirmAdminTemplate(templateId);
    }

    /** 取消模板公开发布（软删除） */
    @DeleteMapping("/{templateId}")
    public PaperFormatTemplateResponse deleteTemplate(@PathVariable UUID templateId) {
        return paperFormatService.unpublishAdminTemplate(templateId);
    }
}
