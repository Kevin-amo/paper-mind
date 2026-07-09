package com.lqr.papermind.paperformat.controller;

import com.lqr.papermind.auth.security.RoleCodes;
import com.lqr.papermind.auth.security.SecurityUserPrincipal;
import com.lqr.papermind.paperformat.dto.CreateFormatCheckRequest;
import com.lqr.papermind.paperformat.dto.PaperFormatCheckJobResponse;
import com.lqr.papermind.paperformat.service.PaperFormatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 评审论文格式预检控制器，为评审任务提供格式预检的创建和查询接口
 */
@RestController
@RequestMapping("/reviews/tasks/{taskId}/format-check")
@RequiredArgsConstructor
public class ReviewPaperFormatController {

    private final PaperFormatService paperFormatService;

    /** 创建评审任务的格式预检 */
    @PostMapping
    public PaperFormatCheckJobResponse createReviewPrecheck(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                            @PathVariable UUID taskId,
                                                            @Valid @RequestBody CreateFormatCheckRequest request) {
        return paperFormatService.createReviewPrecheck(principal.getId(), isAdmin(principal), taskId, request.templateId());
    }

    /** 获取评审任务最新的格式预检结果 */
    @GetMapping
    public PaperFormatCheckJobResponse getReviewPrecheck(@AuthenticationPrincipal SecurityUserPrincipal principal,
                                                         @PathVariable UUID taskId) {
        return paperFormatService.getLatestReviewPrecheck(principal.getId(), isAdmin(principal), taskId);
    }

    /** 判断当前用户是否为管理员 */
    private boolean isAdmin(SecurityUserPrincipal principal) {
        return principal.getRoles().contains(RoleCodes.ADMIN);
    }
}
