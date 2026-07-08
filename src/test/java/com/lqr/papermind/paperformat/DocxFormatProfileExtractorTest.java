package com.lqr.papermind.paperformat;

import com.lqr.papermind.paperformat.extract.DocxFormatProfileExtractor;
import com.lqr.papermind.paperformat.model.DocumentFormatProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocxFormatProfileExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractShouldResolveEffectiveBodyAndHeadingStyles() throws Exception {
        Path docx = DocxTestDocuments.studentDocx(tempDir);

        DocumentFormatProfile profile;
        try (var input = Files.newInputStream(docx)) {
            profile = new DocxFormatProfileExtractor().extract(input);
        }

        assertThat(profile.getPageRule().getPageWidthMm()).isCloseTo(210.0, org.assertj.core.data.Offset.offset(0.2));
        assertThat(profile.getHeaderFooterRule().getHeaderText()).isEqualTo("某大学本科毕业论文");
        assertThat(profile.getParagraphs()).anySatisfy(paragraph -> {
            assertThat(paragraph.getText()).contains("正文段落");
            assertThat(paragraph.getFontSizePt()).isEqualTo(12.0);
            assertThat(paragraph.getEastAsiaFont()).isEqualTo("宋体");
        });
        assertThat(profile.getHeadings()).anySatisfy(heading -> {
            assertThat(heading.getLevel()).isEqualTo(1);
            assertThat(heading.getFontSizePt()).isEqualTo(16.0);
            assertThat(heading.getEastAsiaFont()).isEqualTo("黑体");
            assertThat(heading.getAlignment()).isEqualTo("CENTER");
        });
    }
}
