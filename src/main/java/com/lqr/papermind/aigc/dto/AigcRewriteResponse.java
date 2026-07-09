package com.lqr.papermind.aigc.dto;

import java.util.List;

/**
 * 段落学术润色响应。
 *
 * <p>Repository: https://github.com/Yezery/aigc-down-skill</p>
 */
public record AigcRewriteResponse(
        List<AigcRiskPatternResponse> riskPatterns,
        String rewrittenText,
        List<String> changeNotes,
        List<String> warnings,
        AigcQualityScoreResponse qualityScore
) {
}
