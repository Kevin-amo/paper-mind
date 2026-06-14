import type { PageResponse } from './common';
import type { DocumentStatus } from './document';
import type { ReviewTaskStatus } from './review';

export interface ReviewSubmission {
  sourceId: string;
  title: string;
  fileName: string;
  documentStatus: DocumentStatus;
  errorMessage: string | null;
  reviewTaskId: string | null;
  reviewStatus: ReviewTaskStatus | null;
  submittedAt: string;
  updatedAt: string;
}

export interface ListReviewSubmissionsParams {
  page?: number;
  size?: number;
}

export interface UploadReviewSubmissionPayload {
  file: File;
  sourceId?: string;
  title?: string;
}

export type ReviewSubmissionPage = PageResponse<ReviewSubmission>;
