package com.lqr.papermind.paperformat.model;

import lombok.Data;

/**
 * 页眉页脚规则，定义页眉文字、对齐、字体和页脚页码设置
 */
@Data
public class HeaderFooterRule {
    /** 页眉文字内容 */
    private String headerText;
    /** 页眉是否居中 */
    private boolean headerCentered;
    /** 页眉中文字体 */
    private String headerFontEastAsia;
    /** 页眉字号（磅） */
    private Double headerFontSizePt;
    /** 页脚是否包含页码字段 */
    private boolean footerPageNumber;
    /** 页脚是否居中 */
    private boolean footerCentered;
    /** 规则来源 */
    private FormatRuleSource source = FormatRuleSource.STRUCTURAL_CONFIRMED;
}