export type FormatTemplateStatus = 'PARSING' | 'READY' | 'FAILED' | 'NEED_CONFIRM' | string;
export type FormatCheckScope = 'USER_SELF_CHECK' | 'REVIEW_PRECHECK' | string;
export type FormatCheckStatus = 'PENDING' | 'RUNNING' | 'PASSED' | 'FAILED' | 'ERROR' | string;
export type FormatViolationSeverity = 'ERROR' | 'WARNING' | 'REVIEW' | string;

export interface FormatViolation {
  code: string;
  severity: FormatViolationSeverity;
  location: string | null;
  expected: string | null;
  actual: string | null;
  message: string;
  suggestion: string | null;
}

export interface FormatCheckSummary {
  total: number;
  errorCount: number;
  warningCount: number;
  reviewCount: number;
  passed?: boolean;
  [key: string]: unknown;
}

export interface PaperFormatTemplate {
  id: string;
  ownerUserId: string;
  name: string;
  schoolName: string | null;
  fileName: string;
  fileType: string;
  storageKey: string;
  status: FormatTemplateStatus;
  formatSpec: Record<string, unknown> | null;
  extractionReport: Record<string, unknown> | null;
  confirmed: boolean;
  publicTemplate: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PaperFormatCheckJob {
  id: string;
  templateId: string;
  documentId: string | null;
  sourceId: string;
  reviewTaskId: string | null;
  scope: FormatCheckScope;
  status: FormatCheckStatus;
  summary: FormatCheckSummary | Record<string, unknown> | null;
  violations: FormatViolation[] | unknown;
  createdAt: string;
  updatedAt: string;
}

export interface CreateFormatCheckPayload {
  templateId: string;
  sourceId?: string | null;
}

export interface PatchFormatSpecPayload {
  formatSpec?: Record<string, unknown> | null;
  confirmed?: boolean;
}
