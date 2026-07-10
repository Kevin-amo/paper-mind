package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.FormatSpec;

import java.util.List;
import java.util.Map;

/** 从模板 DOCX 中收集的证据，用于固定规则和 AI 辅助的需求提取。 */
public record DocxTemplateEvidence(
        List<String> bodyParagraphs,
        List<String> textBoxTexts,
        List<String> commentTexts,
        List<String> headerTexts,
        List<String> footerTexts,
        FormatSpec ooxmlSpec,
        Map<String, Object> rawHints
) {
}
