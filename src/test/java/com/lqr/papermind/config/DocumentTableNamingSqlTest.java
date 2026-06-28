package com.lqr.papermind.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTableNamingSqlTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();
    private static final String LEGACY_PREFIX = "paper" + "_document";
    private static final List<String> TABLE_NAMING_FILES = List.of(
            "README.md",
            "src/main/resources/sql/paper-mind.sql",
            "src/main/java",
            "src/test/java"
    );
    private static final String LEGACY_MIGRATION_PATH = "migration/" + "2026-06-26-" + "rename-document-tables.sql";

    @Test
    void initializationSqlShouldUseGenericDocumentTableNames() throws IOException {
        String sql = read("src/main/resources/sql/paper-mind.sql");

        assertThat(sql).contains(
                "public.document",
                "public.document_chunk",
                "public.document_asset",
                "public.document_structured_parse"
        );
    }

    @Test
    void documentChunkShouldKeepVectorStoreReference() throws IOException {
        String sql = read("src/main/resources/sql/paper-mind.sql");
        String normalizedSql = sql.toLowerCase();

        assertThat(normalizedSql).contains("create table if not exists public.vector_store");
        assertThat(sql).contains("vector_store_id uuid");
        assertThat(sql).contains("references public.vector_store (id)");
    }

    @Test
    void sourceFilesShouldNotKeepLegacyDocumentTableNames() throws IOException {
        List<String> offendingFiles = findOffendingFiles(TABLE_NAMING_FILES, List.of(
                "public." + LEGACY_PREFIX,
                "public." + LEGACY_PREFIX + "_chunk",
                "public." + LEGACY_PREFIX + "_asset",
                "public." + "paper" + "_structured_parse",
                "fk_" + LEGACY_PREFIX,
                "idx_" + LEGACY_PREFIX,
                "uq_" + LEGACY_PREFIX,
                LEGACY_MIGRATION_PATH
        ));

        assertThat(offendingFiles).isEmpty();
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }

    private List<String> findOffendingFiles(List<String> relativePaths, List<String> forbiddenTexts) throws IOException {
        List<String> offendingFiles = new ArrayList<>();
        for (String relativePath : relativePaths) {
            Path path = PROJECT_ROOT.resolve(relativePath);
            if (Files.isDirectory(path)) {
                try (var files = Files.walk(path)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        collectIfOffending(file, forbiddenTexts, offendingFiles);
                    }
                }
            } else {
                collectIfOffending(path, forbiddenTexts, offendingFiles);
            }
        }
        return offendingFiles;
    }

    private void collectIfOffending(Path file, List<String> forbiddenTexts, List<String> offendingFiles) throws IOException {
        String content = Files.readString(file);
        if (forbiddenTexts.stream().anyMatch(content::contains)) {
            offendingFiles.add(PROJECT_ROOT.relativize(file).toString());
        }
    }
}
