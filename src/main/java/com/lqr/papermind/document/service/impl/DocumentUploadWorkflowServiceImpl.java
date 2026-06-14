package com.lqr.papermind.document.service.impl;

import com.lqr.papermind.common.constant.MetadataKeys;
import com.lqr.papermind.document.entity.DocumentIngestionJob;
import com.lqr.papermind.document.model.DocumentIngestionMessage;
import com.lqr.papermind.document.service.DocumentIngestionJobService;
import com.lqr.papermind.document.service.DocumentIngestionProducer;
import com.lqr.papermind.document.service.DocumentUploadStorageService;
import com.lqr.papermind.document.service.DocumentUploadWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentUploadWorkflowServiceImpl implements DocumentUploadWorkflowService {

    private final DocumentUploadStorageService documentUploadStorageService;
    private final DocumentIngestionJobService documentIngestionJobService;
    private final DocumentIngestionProducer documentIngestionProducer;

    @Override
    public DocumentIngestionJob createAndPublishJob(UUID ownerUserId,
                                                    MultipartFile file,
                                                    String sourceId,
                                                    String title,
                                                    String fallbackFileName,
                                                    String sourceType) throws IOException {
        UUID jobId = UUID.randomUUID();
        String resolvedSourceId = sourceId == null || sourceId.isBlank() ? UUID.randomUUID().toString() : sourceId.trim();
        String fileName = fallbackFileName == null || fallbackFileName.isBlank() ? file.getOriginalFilename() : fallbackFileName;
        DocumentUploadStorageService.StoredUpload upload = documentUploadStorageService.store(
                ownerUserId,
                resolvedSourceId,
                jobId,
                file,
                fileName
        );
        DocumentIngestionJob job = documentIngestionJobService.createJob(
                jobId,
                ownerUserId,
                resolvedSourceId,
                upload.fileName(),
                upload.filePath(),
                title,
                Map.of(MetadataKeys.SOURCE_TYPE, sourceType == null || sourceType.isBlank() ? MetadataKeys.SOURCE_TYPE_USER : sourceType)
        );
        documentIngestionProducer.publish(new DocumentIngestionMessage(job.getId(), ownerUserId, resolvedSourceId));
        return documentIngestionJobService.findJob(ownerUserId, job.getId()).orElse(job);
    }
}
