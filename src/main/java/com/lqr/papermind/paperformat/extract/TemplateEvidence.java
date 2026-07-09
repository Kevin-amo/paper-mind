package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.ParagraphStyleRule;

/** Paragraph-level evidence extracted from a DOCX template or student paper. */
public record TemplateEvidence(
        String text,
        String styleId,
        String styleName,
        ParagraphStyleRule effectiveStyle,
        String partName,
        int paragraphIndex,
        boolean inTable,
        boolean inTextBox,
        String role
) {
}
