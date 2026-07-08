package com.lqr.papermind.paperformat.model;

import lombok.Data;

@Data
public class ParagraphStyleRule {
    private String asciiFont;
    private String eastAsiaFont;
    private Double fontSizePt;
    private Double lineSpacingMultiple;
    private Double firstLineIndentMm;
    private String alignment;
    private Boolean bold;
    private FormatRuleSource source = FormatRuleSource.STRUCTURAL_CONFIRMED;
}
