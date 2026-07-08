package com.lqr.papermind.paperformat.model;

import lombok.Data;

@Data
public class ParagraphFormatSnapshot {
    private int index;
    private Integer level;
    private String text;
    private String styleId;
    private String asciiFont;
    private String eastAsiaFont;
    private Double fontSizePt;
    private Double lineSpacingMultiple;
    private Double firstLineIndentMm;
    private String alignment;
    private Boolean bold;
}
