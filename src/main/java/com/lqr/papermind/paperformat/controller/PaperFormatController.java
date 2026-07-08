package com.lqr.papermind.paperformat.controller;

import com.lqr.papermind.auth.security.RoleCodes;
import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.paperformat.dto.CreateFormatCheckRequest;
import com.lqr.papermind.paperformat.dto.PaperFormatCheckJobResponse;
import com.lqr.papermind.paperformat.dto.PaperFormatTemplateResponse;
import com.lqr.papermind.paperformat.dto.PatchFormatSpecRequest;
import com.lqr.papermind.paperformat.service.PaperFormatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

@RestController
@RequestMapping("/paper-format")
@RequiredArgsConstructor
public class PaperFormatController {

    private final PaperFormatService paperFormatService;

    @PostMapping(path = "/templates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PaperFormatTemplateResponse uploadTemplate(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam("name") String name,
                                                      @RequestParam(value = "schoolName", required = false) String schoolName) throws IOException {
        return paperFormatService.uploadTemplate(principal.getId(), isAdmin(principal), file, name, schoolName);
    }

    @GetMapping("/templates")
    public List<PaperFormatTemplateResponse> listTemplates(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        return paperFormatService.listTemplates(principal.getId(), isAdmin(principal));
    }

    @GetMapping("/templates/{templateId}")
    public PaperFormatTemplateResponse getTemplate(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                   @PathVariable UUID templateId) {
        return paperFormatService.getTemplate(principal.getId(), isAdmin(principal), templateId);
    }

    @PatchMapping("/templates/{templateId}/spec")
    public PaperFormatTemplateResponse updateTemplateSpec(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                          @PathVariable UUID templateId,
                                                          @Valid @RequestBody PatchFormatSpecRequest request) {
        return paperFormatService.updateTemplateSpec(principal.getId(), isAdmin(principal), templateId, request);
    }

    @PostMapping("/checks")
    public PaperFormatCheckJobResponse createCheck(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                   @Valid @RequestBody CreateFormatCheckRequest request) {
        return paperFormatService.createCheck(principal.getId(), isAdmin(principal), request, PaperFormatService.CHECK_SCOPE_USER_SELF_CHECK, null);
    }

    @GetMapping("/checks/{checkId}")
    public PaperFormatCheckJobResponse getCheck(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                @PathVariable UUID checkId) {
        return paperFormatService.getCheck(principal.getId(), isAdmin(principal), checkId);
    }

    private boolean isAdmin(SecurityUserPrincipal principal) {
        return principal.getRoles().contains(RoleCodes.ADMIN);
    }
}
