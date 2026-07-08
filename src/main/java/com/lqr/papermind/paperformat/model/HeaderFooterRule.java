package com.lqr.papermind.paperformat.model;

import lombok.Data;

@Data
public class HeaderFooterRule {
    private String headerText;
    private boolean headerCentered;
    private boolean footerPageNumber;
    private boolean footerCentered;
    private FormatRuleSource source = FormatRuleSource.STRUCTURAL_CONFIRMED;
}
