package com.lqr.papermind.paperformat.model;

import lombok.Data;

/**
 * 段落样式规则，定义字体、字号、行距、间距、缩进、对齐等格式属性
 */
@Data
public class ParagraphStyleRule {
    /** ASCII字体（西文字体） */
    private String asciiFont;
    /** hAnsi字体（Word西文/复杂脚本文本常用字体属性） */
    private String hAnsiFont;
    /** 中文字体 */
    private String eastAsiaFont;
    /** 字号（磅） */
    private Double fontSizePt;
    /** 行距倍数 */
    private Double lineSpacingMultiple;
    /** 行距规则（FIXED/AT_LEAST等） */
    private String lineSpacingRule;
    /** 固定行距值（磅） */
    private Double lineSpacingPt;
    /** 段前间距（磅） */
    private Double spaceBeforePt;
    /** 段后间距（磅） */
    private Double spaceAfterPt;
    /** 首行缩进（毫米） */
    private Double firstLineIndentMm;
    /** 对齐方式（CENTER/RIGHT/BOTH等） */
    private String alignment;
    /** 是否加粗 */
    private Boolean bold;
    /** 拉丁字体 */
    private String latinFont;
    /** 样式ID */
    private String styleId;
    /** 样式显示名称 */
    private String styleName;
    /** 来源OOXML部件 */
    private String sourcePart;
    /** 来源优先级 */
    private String sourcePriority;
    /** 对应证据文本 */
    private String evidenceText;
    /** 规则来源 */
    private FormatRuleSource source = FormatRuleSource.STRUCTURAL_CONFIRMED;
}
