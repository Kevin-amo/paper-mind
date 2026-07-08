package com.lqr.papermind.paperformat.model;

import lombok.Data;

/**
 * 页面规则，定义纸张尺寸、页边距、装订线、页眉页脚距离等页面设置
 */
@Data
public class PageRule {
    /** 页面宽度（毫米） */
    private Double pageWidthMm;
    /** 页面高度（毫米） */
    private Double pageHeightMm;
    /** 上边距（毫米） */
    private Double marginTopMm;
    /** 右边距（毫米） */
    private Double marginRightMm;
    /** 下边距（毫米） */
    private Double marginBottomMm;
    /** 左边距（毫米） */
    private Double marginLeftMm;
    /** 是否对称页边距 */
    private Boolean mirrorMargins;
    /** 是否双面打印 */
    private Boolean duplexPrint;
    /** 内侧边距（毫米） */
    private Double insideMarginMm;
    /** 外侧边距（毫米） */
    private Double outsideMarginMm;
    /** 装订线（毫米） */
    private Double gutterMm;
    /** 页眉距边界（毫米） */
    private Double headerDistanceMm;
    /** 页脚距边界（毫米） */
    private Double footerDistanceMm;
    /** 规则来源 */
    private FormatRuleSource source = FormatRuleSource.STRUCTURAL_CONFIRMED;
}