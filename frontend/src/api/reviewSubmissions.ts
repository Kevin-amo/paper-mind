import { http, uploadHttp } from './http';
import { compactParams } from '../utils/params';
import type {
  DocumentJobResponse,
  DocumentUploadAcceptedResponse,
  ListReviewSubmissionsParams,
  ReviewSubmission,
  ReviewSubmissionPage,
  UploadReviewSubmissionPayload,
} from '../types';

export async function uploadReviewSubmission(payload: UploadReviewSubmissionPayload) {
  const formData = new FormData();
  formData.append('file', payload.file);

  if (payload.sourceId) {
    formData.append('sourceId', payload.sourceId);
  }

  if (payload.title) {
    formData.append('title', payload.title);
  }

  const { data } = await uploadHttp.post<DocumentUploadAcceptedResponse>('/review-submissions', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });

  return data;
}

export async function listReviewSubmissions(params: ListReviewSubmissionsParams = {}) {
  const { data } = await http.get<ReviewSubmissionPage>('/review-submissions', {
    params: compactParams({
      page: params.page ?? 0,
      size: params.size ?? 20,
    }),
  });
  return data;
}

export async function getReviewSubmissionUploadJob(jobId: string) {
  const { data } = await http.get<DocumentJobResponse>(`/documents/jobs/${jobId}`);
  return data;
}

export type { ReviewSubmission };
