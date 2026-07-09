package com.lqr.papermind.paperformat;

import com.lqr.papermind.paperformat.extract.DocxPackageReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DocxPackageReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readShouldExposeDocumentHeadersFootersCommentsFootnotesAndEndnotes() throws Exception {
        Path docx = DocxTestDocuments.richEvidenceTemplateDocx(tempDir);

        DocxPackageReader.PackageParts parts;
        try (var input = Files.newInputStream(docx)) {
            parts = DocxPackageReader.read(input);
        }

        assertThat(parts.partNames()).containsAll(Set.of(
                "word/document.xml",
                "word/styles.xml",
                "word/settings.xml",
                "word/header1.xml",
                "word/footer1.xml",
                "word/comments.xml",
                "word/footnotes.xml",
                "word/endnotes.xml"
        ));
        assertThat(parts.xml("word/missing.xml")).isEmpty();
        assertThat(parts.parsePart("word/document.xml")).isPresent();
    }
}
