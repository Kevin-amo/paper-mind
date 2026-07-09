package com.lqr.papermind.paperformat;

import com.lqr.papermind.paperformat.extract.DocxPackageReader;
import com.lqr.papermind.paperformat.extract.DocxStyleResolver;
import com.lqr.papermind.paperformat.model.ParagraphStyleRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocxStyleResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveParagraphShouldApplyDocDefaultsStyleInheritanceParagraphAndRunOverrides() throws Exception {
        Path docx = DocxTestDocuments.softwareExampleTemplateDocx(tempDir);

        DocxPackageReader.PackageParts parts;
        try (var input = Files.newInputStream(docx)) {
            parts = DocxPackageReader.read(input);
        }
        DocxStyleResolver resolver = DocxStyleResolver.from(parts);

        Element tocParagraph = firstParagraphWithStyle(parts, "TOC1");
        ParagraphStyleRule toc = resolver.resolveParagraph(tocParagraph, "word/document.xml");

        assertThat(toc.getStyleId()).isEqualTo("TOC1");
        assertThat(toc.getStyleName()).isEqualTo("toc 1");
        assertThat(toc.getEastAsiaFont()).isEqualTo("宋体");
        assertThat(toc.getAsciiFont()).isEqualTo("Times New Roman");
        assertThat(toc.getFontSizePt()).isEqualTo(10.5);
        assertThat(toc.getLineSpacingRule()).isEqualTo("FIXED");
        assertThat(toc.getLineSpacingPt()).isEqualTo(16.0);
        assertThat(toc.getSourcePart()).isEqualTo("word/document.xml");
    }

    @Test
    void resolveParagraphShouldIgnoreThemeFontPlaceholdersWhenNoThemeMappingIsAvailable() throws Exception {
        Path docx = DocxTestDocuments.themePlaceholderFontDocx(tempDir);

        DocxPackageReader.PackageParts parts;
        try (var input = Files.newInputStream(docx)) {
            parts = DocxPackageReader.read(input);
        }
        DocxStyleResolver resolver = DocxStyleResolver.from(parts);

        Element paragraph = firstParagraphWithStyle(parts, "ThemeBody");
        ParagraphStyleRule rule = resolver.resolveParagraph(paragraph, "word/document.xml");

        assertThat(rule.getEastAsiaFont()).isNull();
        assertThat(rule.getAsciiFont()).isNull();
        assertThat(rule.getHAnsiFont()).isNull();
    }

    private Element firstParagraphWithStyle(DocxPackageReader.PackageParts parts, String styleId) {
        var document = parts.parsePart("word/document.xml").orElseThrow();
        var paragraphs = document.getElementsByTagName("w:p");
        for (int i = 0; i < paragraphs.getLength(); i++) {
            Element paragraph = (Element) paragraphs.item(i);
            if (paragraph.getTextContent().contains("<")) {
                continue;
            }
            String current = DocxStyleResolver.paragraphStyleId(paragraph);
            if (styleId.equals(current)) {
                return paragraph;
            }
        }
        throw new AssertionError("Missing paragraph with style " + styleId);
    }
}
