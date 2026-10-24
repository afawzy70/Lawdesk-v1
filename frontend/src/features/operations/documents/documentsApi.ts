import { api } from '../../../api/http';
import { intlLocale } from '../../../i18n/format';
import type { Locale } from '../../../i18n/locale';

/** Legal PDF attachments, up to ten per case (LD-026, architecture/CONTRACTS.md §8.3). */
export const MAX_DOCUMENTS = 10;
export const MAX_BYTES = 10 * 1024 * 1024;
export const PDF = 'application/pdf';
export const DOCUMENTS_PAGE_SIZE = 20;

export interface CaseDocument {
  id: string;
  caseId: string;
  caseReferenceNo: string | null;
  caseTitle: string | null;
  title: string;
  fileName: string;
  mediaType: string;
  sizeBytes: number;
  sha256: string;
  version: number;
  createdAt: string;
  createdBy: string;
}

export interface DocumentPage {
  items: CaseDocument[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

const casePath = (caseId: string) => `/api/v1/cases/${encodeURIComponent(caseId)}/documents`;

/** The file itself: a same-origin link the browser downloads with the session cookie (attachment, nosniff). */
export function contentUrl(id: string): string {
  return `/api/v1/documents/${encodeURIComponent(id)}/content`;
}

export async function listCaseDocuments(caseId: string, signal: AbortSignal): Promise<CaseDocument[]> {
  return (await api<DocumentPage>(casePath(caseId), { signal })).items;
}

export function listLatestDocuments(page: number, signal: AbortSignal): Promise<DocumentPage> {
  const query = new URLSearchParams({ page: String(page), size: String(DOCUMENTS_PAGE_SIZE) });
  return api<DocumentPage>(`/api/v1/documents?${query.toString()}`, { signal });
}

/** Sent as multipart form data: `file` and, when given, `title`. */
export function attachDocument(caseId: string, file: File, title: string): Promise<CaseDocument> {
  const form = new FormData();
  form.append('file', file);
  if (title.trim() !== '') form.append('title', title.trim());
  return api<CaseDocument>(casePath(caseId), { method: 'POST', body: form });
}

export function removeDocument(document: CaseDocument): Promise<void> {
  const query = new URLSearchParams({ version: String(document.version) });
  return api<undefined>(`/api/v1/documents/${encodeURIComponent(document.id)}?${query.toString()}`, {
    method: 'DELETE',
  });
}

/** A file size in kB or MB (binary units, as the 10 MB limit), in the locale's digits. */
export function formatSize(bytes: number, locale: Locale): string {
  const megabytes = bytes / (1024 * 1024);
  const [value, unit] = megabytes >= 1 ? [megabytes, 'megabyte'] : [bytes / 1024, 'kilobyte'];
  return new Intl.NumberFormat(intlLocale(locale), { style: 'unit', unit, maximumFractionDigits: 1 }).format(value);
}

/** What the browser can check before sending a file; the backend checks it again. */
export function fileProblem(file: File | null): 'missing' | 'notPdf' | 'tooLarge' | null {
  if (file === null) return 'missing';
  if (file.type !== PDF && !file.name.toLowerCase().endsWith('.pdf')) return 'notPdf';
  if (file.size > MAX_BYTES) return 'tooLarge';
  return null;
}
