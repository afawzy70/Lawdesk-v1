import { api } from '../../api/http';

/** Court reference data, LD-018 (`/api/v1/courts`, docs/delivery/PHASE-4-ACCEPTANCE.md). */
export const COURT_LEVELS = ['FIRST_INSTANCE', 'APPEAL', 'CASSATION'] as const;
export type CourtLevel = (typeof COURT_LEVELS)[number];

export interface Court {
  id: string;
  code: string;
  nameAr: string;
  nameEn: string;
  level: CourtLevel;
  governorate: string | null;
  active: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface CourtPage {
  items: Court[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface CourtFilter {
  level?: CourtLevel | undefined;
  active?: boolean | undefined;
  page: number;
}

export interface CourtInput {
  code: string;
  nameAr: string;
  nameEn: string;
  level: CourtLevel | '';
  governorate: string | null;
}

export const COURTS_PATH = '/api/v1/courts';
export const COURT_PAGE_SIZE = 20;

export function listCourts(filter: CourtFilter, signal: AbortSignal): Promise<CourtPage> {
  const query = new URLSearchParams({ page: String(filter.page), size: String(COURT_PAGE_SIZE), sort: 'code,asc' });
  if (filter.level !== undefined) query.set('level', filter.level);
  if (filter.active !== undefined) query.set('active', String(filter.active));
  return api<CourtPage>(`${COURTS_PATH}?${query.toString()}`, { signal });
}

/** An empty level is sent as `null`, so the backend reports it as a missing field. */
function body(input: CourtInput) {
  return { ...input, level: input.level === '' ? null : input.level };
}

export function createCourt(input: CourtInput): Promise<Court> {
  return api<Court>(COURTS_PATH, { method: 'POST', body: body(input) });
}

export function updateCourt(id: string, input: CourtInput & { active: boolean; version: number }): Promise<Court> {
  return api<Court>(`${COURTS_PATH}/${encodeURIComponent(id)}`, { method: 'PUT', body: body(input) });
}
