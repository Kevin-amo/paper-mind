import { longRunningHttp } from './http';
import type { AigcRewriteRequest, AigcRewriteResponse } from '../types';

export async function rewriteParagraph(payload: AigcRewriteRequest) {
  const { data } = await longRunningHttp.post<AigcRewriteResponse>('/aigc-rewrite/text', payload);
  return data;
}
