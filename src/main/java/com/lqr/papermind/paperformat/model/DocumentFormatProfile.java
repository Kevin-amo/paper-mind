package com.lqr.papermind.paperformat.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentFormatProfile {
    private PageRule pageRule = new PageRule();
    private HeaderFooterRule headerFooterRule = new HeaderFooterRule();
    private List<ParagraphFormatSnapshot> paragraphs = new ArrayList<>();
    private List<ParagraphFormatSnapshot> headings = new ArrayList<>();
}
