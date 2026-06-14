package com.lqr.papermind.document.service;

import com.lqr.papermind.document.entity.DocumentIngestionJob;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

public interface DocumentUploadWorkflowService {

    DocumentIngestionJob createAndPublishJob(UUID ownerUserId,
                                             MultipartFile file,
                                             String sourceId,
                                             String title,
                                             String fallbackFileName,
                                             String sourceType) throws IOException;
}
