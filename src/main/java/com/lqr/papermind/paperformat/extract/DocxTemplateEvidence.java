package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.FormatSpec;

import java.util.List;
import java.util.Map;

/** Evidence collected from a template DOCX for fixed and AI-assisted requirement extraction. */
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
