package com.lqr.papermind.paperformat.model;

import lombok.Data;

@Data
public class PageRule {
    private Double pageWidthMm;
    private Double pageHeightMm;
    private Double marginTopMm;
    private Double marginRightMm;
    private Double marginBottomMm;
    private Double marginLeftMm;
    private Double gutterMm;
    private Double headerDistanceMm;
    private Double footerDistanceMm;
    private FormatRuleSource source = FormatRuleSource.STRUCTURAL_CONFIRMED;
}
