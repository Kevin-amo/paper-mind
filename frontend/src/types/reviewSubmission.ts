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
  reviewReport: ReviewSubmissionReport | null;
}

export interface ReviewSubmissionReport {
  taskId: string;
  finalScore: number | null;
  finalRecommendation: string | null;
  confirmedAt: string | null;
  criteriaScores: ReviewSubmissionCriterionScore[];
}

export interface ReviewSubmissionCriterionScore {
  code: string;
  name: string;
  score: number | null;
  maxScore: number | null;
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
