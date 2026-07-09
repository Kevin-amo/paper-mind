package com.lqr.papermind.paperformat.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 标题样式规则，继承段落样式规则，增加标题级别和编号模式
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HeadingStyleRule extends ParagraphStyleRule {
    /** 标题级别（1/2/3） */
    private int level;
    /** 编号模式（如"1"、"1.1"、"1.1.1"） */
    private String numberingPattern;
}