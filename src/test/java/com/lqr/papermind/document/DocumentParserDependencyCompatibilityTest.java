package com.lqr.papermind.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class DocumentParserDependencyCompatibilityTest {

    @Test
    void commonsIoShouldContainChecksumInputStreamRequiredByTikaZipParser() {
        assertThatCode(() -> Class.forName("org.apache.commons.io.input.ChecksumInputStream"))
                .doesNotThrowAnyException();
    }
}
