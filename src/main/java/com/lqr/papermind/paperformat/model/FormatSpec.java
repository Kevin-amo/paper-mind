package com.lqr.papermind.paperformat.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 格式规则规范，定义模板的页面、页眉页脚、正文和标题的格式要求
 */
@Data
public class FormatSpec {
    /** 页面规则 */
    private PageRule pageRule = new PageRule();
    /** 页眉页脚规则 */
    private HeaderFooterRule headerFooterRule = new HeaderFooterRule();
    /** 正文段落样式规则 */
    private ParagraphStyleRule bodyRule = new ParagraphStyleRule();
    /** 标题样式规则（按级别索引） */
    private Map<Integer, HeadingStyleRule> headingRules = new LinkedHashMap<>();
    /** 格式规则提取报告（来源、冲突、回退信息等） */
    private Map<String, Object> extractionReport = new LinkedHashMap<>();
}
