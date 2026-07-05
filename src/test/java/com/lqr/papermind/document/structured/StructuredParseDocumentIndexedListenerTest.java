package com.lqr.papermind.document.structured;

import com.lqr.papermind.document.event.DocumentIndexedEvent;
import com.lqr.papermind.document.service.DocumentPersistenceService;
import com.lqr.papermind.document.structured.service.PaperStructuredParseService;
import com.lqr.papermind.document.structured.service.impl.StructuredParseDocumentIndexedListener;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StructuredParseDocumentIndexedListenerTest {

    private final PaperStructuredParseService paperStructuredParseService = mock(PaperStructuredParseService.class);
    private final DocumentPersistenceService documentPersistenceService = mock(DocumentPersistenceService.class);
    private final StructuredParseDocumentIndexedListener listener = new StructuredParseDocumentIndexedListener(
            paperStructuredParseService,
            documentPersistenceService
    );

    @Test
    void onDocumentIndexedShouldSkipUserDocument() {
        UUID ownerUserId = UUID.randomUUID();
        DocumentIndexedEvent event = new DocumentIndexedEvent(ownerUserId, UUID.randomUUID(), "source-user");
        when(documentPersistenceService.findReviewDocument(ownerUserId, "source-user")).thenReturn(Optional.empty());

        listener.onDocumentIndexed(event);

        verify(paperStructuredParseService, never()).generate(ownerUserId, "source-user");
    }

    @Test
    void onDocumentIndexedShouldGenerateForReviewDocument() {
        UUID ownerUserId = UUID.randomUUID();
        DocumentIndexedEvent event = new DocumentIndexedEvent(ownerUserId, UUID.randomUUID(), "source-review");
        when(documentPersistenceService.findReviewDocument(ownerUserId, "source-review"))
                .thenReturn(Optional.of(document(ownerUserId, "source-review")));

        listener.onDocumentIndexed(event);

        verify(paperStructuredParseService).generate(ownerUserId, "source-review");
    }

    private DocumentPersistenceService.DocumentDetail document(UUID ownerUserId, String sourceId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new DocumentPersistenceService.DocumentDetail(
                sourceId,
                ownerUserId,
                "Review Paper",
                null,
                "review.pdf",
                "application/pdf",
                100L,
                List.of(),
                null,
                null,
                null,
                null,
                List.of(),
                "content",
                Map.of(),
                "INDEXED",
                1,
                null,
                now,
                now,
                null
        );
    }
}
