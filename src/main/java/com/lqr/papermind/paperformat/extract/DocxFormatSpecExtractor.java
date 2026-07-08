package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.FormatSpec;
import com.lqr.papermind.paperformat.model.HeadingStyleRule;
import com.lqr.papermind.paperformat.model.HeaderFooterRule;
import com.lqr.papermind.paperformat.model.PageRule;
import com.lqr.papermind.paperformat.model.ParagraphStyleRule;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.InputStream;
import java.util.Map;

@Component
public class DocxFormatSpecExtractor {

    public FormatSpec extract(InputStream input) {
        try {
            Map<String, String> parts = DocxPackageReader.readXmlParts(input);
            FormatSpec spec = new FormatSpec();
            spec.setPageRule(readPageRule(parts.get("word/document.xml")));
            spec.setHeaderFooterRule(readHeaderFooter(parts));
            DocxStyleSupport.Styles styles = DocxStyleSupport.parse(parts.get("word/styles.xml"));
            spec.setBodyRule(DocxStyleSupport.merge(styles.docDefaults(), styles.style("Normal")));
            for (int level = 1; level <= 3; level++) {
                ParagraphStyleRule style = DocxStyleSupport.merge(styles.docDefaults(), styles.style("Heading" + level));
                HeadingStyleRule heading = new HeadingStyleRule();
                copy(style, heading);
                heading.setLevel(level);
                spec.getHeadingRules().put(level, heading);
            }
            spec.getExtractionReport().put("source", "OOXML");
            return spec;
        } catch (Exception ex) {
            throw new IllegalArgumentException("DOCX 格式规则解析失败", ex);
        }
    }

    private PageRule readPageRule(String documentXml) {
        PageRule page = new PageRule();
        if (documentXml == null) {
            return page;
        }
        Document document = DocxPackageReader.parse(documentXml);
        Element section = DocxPackageReader.first(document.getDocumentElement(), "w:sectPr");
        Element size = DocxPackageReader.first(section, "w:pgSz");
        Element margins = DocxPackageReader.first(section, "w:pgMar");
        page.setPageWidthMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(size, "w")));
        page.setPageHeightMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(size, "h")));
        page.setMarginTopMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "top")));
        page.setMarginRightMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "right")));
        page.setMarginBottomMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "bottom")));
        page.setMarginLeftMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "left")));
        page.setGutterMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "gutter")));
        page.setHeaderDistanceMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "header")));
        page.setFooterDistanceMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "footer")));
        return page;
    }

    private HeaderFooterRule readHeaderFooter(Map<String, String> parts) {
        HeaderFooterRule rule = new HeaderFooterRule();
        String headerXml = parts.get("word/header1.xml");
        if (headerXml != null) {
            Document header = DocxPackageReader.parse(headerXml);
            Element paragraph = DocxPackageReader.first(header.getDocumentElement(), "w:p");
            rule.setHeaderText(DocxPackageReader.text(header.getDocumentElement()));
            rule.setHeaderCentered("CENTER".equals(DocxPackageReader.paragraphAlignment(paragraph)));
        }
        String footerXml = parts.get("word/footer1.xml");
        if (footerXml != null) {
            Document footer = DocxPackageReader.parse(footerXml);
            Element paragraph = DocxPackageReader.first(footer.getDocumentElement(), "w:p");
            rule.setFooterPageNumber(DocxPackageReader.hasField(footer.getDocumentElement(), "PAGE"));
            rule.setFooterCentered("CENTER".equals(DocxPackageReader.paragraphAlignment(paragraph)));
        }
        return rule;
    }

    private void copy(ParagraphStyleRule source, ParagraphStyleRule target) {
        target.setAsciiFont(source.getAsciiFont());
        target.setEastAsiaFont(source.getEastAsiaFont());
        target.setFontSizePt(source.getFontSizePt());
        target.setLineSpacingMultiple(source.getLineSpacingMultiple());
        target.setFirstLineIndentMm(source.getFirstLineIndentMm());
        target.setAlignment(source.getAlignment());
        target.setBold(source.getBold());
        target.setSource(source.getSource());
    }
}
