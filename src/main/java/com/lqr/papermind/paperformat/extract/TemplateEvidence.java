package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.ParagraphStyleRule;

/** 从 DOCX 模板或学生论文中提取的段落级格式证据。 */
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
