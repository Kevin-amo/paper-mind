package com.lqr.papermind.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTableNamingSqlTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();

    @Test
    void initializationSqlShouldUseGenericDocumentTableNames() throws IOException {
        String sql = read("src/main/resources/sql/paper-mind.sql");

        assertThat(sql).doesNotContain(
                "public.paper_document",
                "public.paper_document_chunk",
                "public.paper_document_asset",
                "public.paper_structured_parse"
        );

        assertThat(sql).doesNotContain(
                "fk_paper_document",
                "idx_paper_document",
                "uq_paper_document"
        );

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

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_ROOT.resolve(relativePath));
    }
}
