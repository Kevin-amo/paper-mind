package com.lqr.papermind.paperformat.model;

import lombok.Data;

/**
 * 段落格式快照，记录单个段落的格式信息（字体、字号、行距、对齐等）
 */
@Data
public class ParagraphFormatSnapshot {
    /** 段落序号 */
    private int index;
    /** 标题级别（非标题段落为null） */
    private Integer level;
    /** 段落文本内容 */
    private String text;
    /** 样式ID */
    private String styleId;
    /** ASCII字体 */
    private String asciiFont;
    /** 中文字体 */
    private String eastAsiaFont;
    /** 字号（磅） */
    private Double fontSizePt;
    /** 行距倍数 */
    private Double lineSpacingMultiple;
    /** 首行缩进（毫米） */
    private Double firstLineIndentMm;
    /** 对齐方式 */
    private String alignment;
    /** 是否加粗 */
    private Boolean bold;
}
