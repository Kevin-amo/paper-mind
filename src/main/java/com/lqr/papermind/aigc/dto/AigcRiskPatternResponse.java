package com.lqr.papermind.aigc.dto;

/**
 * 原文中识别到的 AI 写作风险模式。
 */
public record AigcRiskPatternResponse(
        String type,
        String evidence,
        String suggestion
) {
}
