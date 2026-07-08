package com.lqr.papermind.paperformat.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档格式画像，表示从docx文件中提取的实际格式信息
 */
@Data
public class DocumentFormatProfile {
    /** 页面规则（尺寸、边距等） */
    private PageRule pageRule = new PageRule();
    /** 页眉页脚规则 */
    private HeaderFooterRule headerFooterRule = new HeaderFooterRule();
    /** 正文段落格式快照列表 */
    private List<ParagraphFormatSnapshot> paragraphs = new ArrayList<>();
    /** 标题段落格式快照列表 */
    private List<ParagraphFormatSnapshot> headings = new ArrayList<>();
}
