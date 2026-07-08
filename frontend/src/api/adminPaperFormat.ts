import { http, uploadHttp } from './http';
import type { PaperFormatTemplate, UpdateFormatTemplatePayload } from '../types/paperFormat';

const baseUrl = '/admin/paper-format/templates';

export async function listAdminFormatTemplates() {
  const { data } = await http.get<PaperFormatTemplate[]>(baseUrl);
  return data;
}

export async function getAdminFormatTemplate(templateId: string) {
  const { data } = await http.get<PaperFormatTemplate>(`${baseUrl}/${templateId}`);
  return data;
}

export async function uploadAdminFormatTemplate(payload: { file: File; name: string; schoolName?: string | null }) {
  const formData = new FormData();
  formData.append('file', payload.file);
  formData.append('name', payload.name);
  if (payload.schoolName?.trim()) {
    formData.append('schoolName', payload.schoolName.trim());
  }

  const { data } = await uploadHttp.post<PaperFormatTemplate>(baseUrl, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
  return data;
}

export async function updateAdminFormatTemplate(templateId: string, payload: UpdateFormatTemplatePayload) {
  const { data } = await http.patch<PaperFormatTemplate>(`${baseUrl}/${templateId}`, payload);
  return data;
}

export async function confirmAdminFormatTemplate(templateId: string) {
  const { data } = await http.post<PaperFormatTemplate>(`${baseUrl}/${templateId}/confirm`);
  return data;
}

export async function unpublishAdminFormatTemplate(templateId: string) {
  const { data } = await http.delete<PaperFormatTemplate>(`${baseUrl}/${templateId}`);
  return data;
}
