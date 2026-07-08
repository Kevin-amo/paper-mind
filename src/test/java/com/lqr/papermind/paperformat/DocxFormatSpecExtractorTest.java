package com.lqr.papermind.paperformat;

import com.lqr.papermind.paperformat.extract.DocxFormatSpecExtractor;
import com.lqr.papermind.paperformat.model.FormatSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocxFormatSpecExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractShouldReadPageMarginsHeaderAndFooterPageField() throws Exception {
        Path docx = DocxTestDocuments.templateDocx(tempDir, true);

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor().extract(input);
        }

        assertThat(spec.getPageRule().getPageWidthMm()).isCloseTo(210.0, within(0.2));
        assertThat(spec.getPageRule().getPageHeightMm()).isCloseTo(297.0, within(0.2));
        assertThat(spec.getPageRule().getMarginTopMm()).isCloseTo(25.4, within(0.2));
        assertThat(spec.getPageRule().getMarginLeftMm()).isCloseTo(31.8, within(0.2));
        assertThat(spec.getHeaderFooterRule().getHeaderText()).isEqualTo("某大学本科毕业论文");
        assertThat(spec.getHeaderFooterRule().isHeaderCentered()).isTrue();
        assertThat(spec.getHeaderFooterRule().isFooterPageNumber()).isTrue();
        assertThat(spec.getHeaderFooterRule().isFooterCentered()).isTrue();
    }

    @Test
    void extractShouldNotTreatCachedFooterNumberAsPageField() throws Exception {
        Path docx = DocxTestDocuments.templateDocx(tempDir, false);

        FormatSpec spec;
        try (var input = Files.newInputStream(docx)) {
            spec = new DocxFormatSpecExtractor().extract(input);
        }

        assertThat(spec.getHeaderFooterRule().isFooterPageNumber()).isFalse();
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
