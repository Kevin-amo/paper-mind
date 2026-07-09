import { http, longRunningHttp, uploadHttp } from './http';
import type {
  CreateFormatCheckPayload,
  PaperFormatCheckJob,
  PaperFormatTemplate,
  PatchFormatSpecPayload,
} from '../types/paperFormat';

export async function uploadFormatTemplate(payload: { file: File; name: string; schoolName?: string }) {
  const formData = new FormData();
  formData.append('file', payload.file);
  formData.append('name', payload.name);
  if (payload.schoolName?.trim()) {
    formData.append('schoolName', payload.schoolName.trim());
  }

  const { data } = await uploadHttp.post<PaperFormatTemplate>('/paper-format/templates', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return data;
}

export async function listFormatTemplates() {
  const { data } = await http.get<PaperFormatTemplate[]>('/paper-format/templates');
  return data;
}

export async function getFormatTemplate(templateId: string) {
  const { data } = await http.get<PaperFormatTemplate>(`/paper-format/templates/${templateId}`);
  return data;
}

export async function updateFormatTemplateSpec(templateId: string, payload: PatchFormatSpecPayload) {
  const { data } = await http.patch<PaperFormatTemplate>(`/paper-format/templates/${templateId}/spec`, payload);
  return data;
}

export async function createFormatCheck(payload: CreateFormatCheckPayload) {
  const { data } = await longRunningHttp.post<PaperFormatCheckJob>('/paper-format/checks', payload);
  return data;
}

export async function getFormatCheck(checkId: string) {
  const { data } = await http.get<PaperFormatCheckJob>(`/paper-format/checks/${checkId}`);
  return data;
}
