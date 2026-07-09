package com.lqr.papermind.paperformat.model;

import lombok.Data;

/**
 * 段落格式快照，记录单个段落的格式信息（字体、字号、行距、对齐等）
 */
@Data
public class ParagraphFormatSnapshot {
    /** 段落序号 */
    private int index;
    /** 原始段落序号 */
    private int paragraphIndex;
    /** 标题级别（非标题段落为null） */
    private Integer level;
    /** 段落角色 */
    private String role;
    /** 来源OOXML部件 */
    private String partName;
    /** 段落文本内容 */
    private String text;
    /** 样式ID */
    private String styleId;
    /** 样式显示名称 */
    private String styleName;
    /** ASCII字体 */
    private String asciiFont;
    /** hAnsi字体 */
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
    /** 对齐方式 */
    private String alignment;
    /** 是否加粗 */
    private Boolean bold;
}
