package com.lqr.papermind.paperformat;

import com.lqr.papermind.paperformat.extract.DocxPackageReader;
import com.lqr.papermind.paperformat.extract.DocxStyleResolver;
import com.lqr.papermind.paperformat.extract.TemplateEvidenceExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateEvidenceExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractShouldCollectBodyTablesTextBoxesHeadersFootersCommentsFootnotesAndEndnotes() throws Exception {
        Path docx = DocxTestDocuments.richEvidenceTemplateDocx(tempDir);

        DocxPackageReader.PackageParts parts;
        try (var input = Files.newInputStream(docx)) {
            parts = DocxPackageReader.read(input);
        }
        var evidence = new TemplateEvidenceExtractor(DocxStyleResolver.from(parts)).extract(parts);

        assertThat(evidence).anySatisfy(item -> {
            assertThat(item.text()).contains("表格中的格式说明");
            assertThat(item.inTable()).isTrue();
            assertThat(item.effectiveStyle().getEastAsiaFont()).isEqualTo("宋体");
        });
        assertThat(evidence).anySatisfy(item -> {
            assertThat(item.text()).contains("文本框里的格式说明");
            assertThat(item.inTextBox()).isTrue();
        });
        assertThat(evidence).anySatisfy(item -> {
            assertThat(item.partName()).isEqualTo("word/header1.xml");
            assertThat(item.role()).isEqualTo("header");
        });
        assertThat(evidence).anySatisfy(item -> assertThat(item.partName()).isEqualTo("word/footer1.xml"));
        assertThat(evidence).anySatisfy(item -> assertThat(item.partName()).isEqualTo("word/comments.xml"));
        assertThat(evidence).anySatisfy(item -> assertThat(item.partName()).isEqualTo("word/footnotes.xml"));
        assertThat(evidence).anySatisfy(item -> assertThat(item.partName()).isEqualTo("word/endnotes.xml"));
    }

    @Test
    void extractShouldClassifyTocStylesByStyleIdBeforeFallingBackToBody() throws Exception {
        Path docx = DocxTestDocuments.toc10BodyCandidateTemplateDocx(tempDir);

        DocxPackageReader.PackageParts parts;
        try (var input = Files.newInputStream(docx)) {
            parts = DocxPackageReader.read(input);
        }
        var evidence = new TemplateEvidenceExtractor(DocxStyleResolver.from(parts)).extract(parts);

        assertThat(evidence).anySatisfy(item -> {
            assertThat(item.styleId()).isEqualTo("TOC10");
            assertThat(item.role()).isEqualTo("tocTitle");
        });
        assertThat(evidence).anySatisfy(item -> {
            assertThat(item.styleId()).isEqualTo("TOC1");
            assertThat(item.role()).isEqualTo("tocEntry1");
        });
        assertThat(evidence).anySatisfy(item -> {
            assertThat(item.styleId()).isEqualTo("TOC2");
            assertThat(item.role()).isEqualTo("tocEntry2");
        });
        assertThat(evidence).anySatisfy(item -> {
            assertThat(item.styleId()).isEqualTo("TOC3");
            assertThat(item.role()).isEqualTo("tocEntry3");
        });
        assertThat(evidence).anySatisfy(item -> {
            assertThat(item.styleId()).isEqualTo("aff2");
            assertThat(item.role()).isEqualTo("body");
        });
    }
}
