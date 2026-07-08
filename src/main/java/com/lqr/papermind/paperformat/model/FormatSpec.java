package com.lqr.papermind.paperformat.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class FormatSpec {
    private PageRule pageRule = new PageRule();
    private HeaderFooterRule headerFooterRule = new HeaderFooterRule();
    private ParagraphStyleRule bodyRule = new ParagraphStyleRule();
    private Map<Integer, HeadingStyleRule> headingRules = new LinkedHashMap<>();
    private Map<String, Object> extractionReport = new LinkedHashMap<>();
}
