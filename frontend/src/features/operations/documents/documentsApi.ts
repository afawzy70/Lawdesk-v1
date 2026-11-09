import { api } from '../../../api/http';
import { intlLocale } from '../../../i18n/format';
import type { Locale } from '../../../i18n/locale';

/**
 * Case attachments (LD-026, architecture/CONTRACTS.md §8.3; PDF, Word and images since LD-055). The limits are archive
 * settings read from the backend; these are the defaults it ships with.
 */
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
  /** The archive fields (LD-055); absent in answers of servers older than that. */
  categoryId?: string | null;
  documentDate?: string | null;
  proceedingId?: string | null;
  description?: string | null;
  currentVersionNo?: number;
  withdrawnAt?: string | null;
  withdrawnReason?: string | null;
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

/** A file size in kB or MB (binary units, as the 10 MB limit), in the locale's digits. */
export function formatSize(bytes: number, locale: Locale): string {
  const megabytes = bytes / (1024 * 1024);
  const [value, unit] = megabytes >= 1 ? [megabytes, 'megabyte'] : [bytes / 1024, 'kilobyte'];
  return new Intl.NumberFormat(intlLocale(locale), { style: 'unit', unit, maximumFractionDigits: 1 }).format(value);
}

/** The kinds of file the archive can accept: media types and extensions of the enabled formats. */
export interface Accepted {
  mediaTypes: readonly string[];
  extensions: readonly string[];
  maxBytes: number;
}

/** What the browser can check before sending a file; the backend checks it again (by the file's signature). */
export function fileProblem(file: File | null, accepted: Accepted): 'missing' | 'notAccepted' | 'tooLarge' | null {
  if (file === null) return 'missing';
  const name = file.name.toLowerCase();
  const known = accepted.mediaTypes.includes(file.type) || accepted.extensions.some((e) => name.endsWith(`.${e}`));
  if (!known) return 'notAccepted';
  if (file.size > accepted.maxBytes) return 'tooLarge';
  return null;
}

/** Withdraws a document from the case file (nothing is deleted; the old DELETE call did only this since LD-055). */
export function withdrawDocumentWithReason(document: CaseDocument, reason: string): Promise<void> {
  return api<undefined>(`/api/v1/documents/${encodeURIComponent(document.id)}/withdraw`, {
    method: 'POST',
    body: { reason, version: document.version },
  });
}
