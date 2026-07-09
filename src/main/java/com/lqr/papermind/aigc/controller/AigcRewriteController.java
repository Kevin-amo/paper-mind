package com.lqr.papermind.aigc.controller;

import com.lqr.papermind.aigc.dto.AigcRewriteRequest;
import com.lqr.papermind.aigc.dto.AigcRewriteResponse;
import com.lqr.papermind.aigc.service.AigcRewriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 段落学术润色控制器。
 *
 * <p>提供段落文本改写接口，帮助用户优化表达自然度、降低机械化痕迹。</p>
 *
 * <p>Prompt rules adapted from Yezery/aigc-down-skill, MIT License.
 * Repository: https://github.com/Yezery/aigc-down-skill</p>
 */
@RestController
@RequestMapping("/aigc-rewrite")
@RequiredArgsConstructor
public class AigcRewriteController {

    private final AigcRewriteService aigcRewriteService;

    /**
     * 对段落进行学术润色。
     *
     * @param request 润色请求
     * @return 润色结果
     */
    @PostMapping("/text")
    public AigcRewriteResponse rewrite(@Valid @RequestBody AigcRewriteRequest request) {
        return aigcRewriteService.rewrite(request);
    }
}
