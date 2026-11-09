import { api } from '../../api/http';

/** The dashboard indicators of requirements section 26 (LD-058): `GET /api/v1/reports/indicators[/{code}]`. */

export const INDICATOR_CODES = [
  'TOTAL_CASES',
  'CASES_NEW',
  'CASES_ONGOING',
  'CASES_CONCLUDED',
  'RESERVED_FOR_JUDGMENT',
  'JUDGMENTS_ISSUED',
  'JUDGMENTS_IN_ENFORCEMENT',
  'UPCOMING_HEARINGS',
  'EXPERT_HEARINGS',
  'APPEAL_DEADLINES',
  'OVERDUE_TASKS',
  'CASES_BY_UNIT',
  'CASES_BY_MEMBER',
  'HIGH_RISK_CASES',
  'OVERDUE_INFO_REQUESTS',
  'LINKED_CASES',
] as const;
export type IndicatorCode = (typeof INDICATOR_CODES)[number];

export const ENTITIES = ['CASE', 'JUDGMENT', 'HEARING', 'TASK', 'INFO_REQUEST', 'DEADLINE'] as const;
export type Entity = (typeof ENTITIES)[number];

/** The group key the API uses for "no value recorded". */
export const NONE_KEY = '_none';

export interface IndicatorGroup {
  level: string | null;
  key: string;
  label: string | null;
  count: number;
}

export interface IndicatorFigure {
  code: IndicatorCode;
  number: number;
  entity: Entity;
  available: boolean;
  unavailableReason: string | null;
  count: number | null;
  groups: IndicatorGroup[] | null;
}

export interface IndicatorItem {
  entity: Entity;
  id: string;
  caseId: string | null;
  caseReferenceNo: string | null;
  caseTitle: string | null;
  title: string | null;
  date: string | null;
  status: string | null;
  detail: string | null;
  statusLabel: string | null;
  detailLabel: string | null;
}

export interface IndicatorPage {
  code: IndicatorCode;
  available: boolean;
  unavailableReason: string | null;
  items: IndicatorItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  from: string;
  to: string;
}

export function isIndicatorCode(value: string | undefined): value is IndicatorCode {
  return INDICATOR_CODES.some((code) => code === value);
}

/** The address of the list an indicator (or one of its groups) opens: the click-through of the dashboard. */
export function indicatorPath(code: IndicatorCode, group?: IndicatorGroup): string {
  if (group === undefined) return `/reports/indicators/${code}`;
  const query = new URLSearchParams({ group: group.key });
  if (group.level !== null) query.set('level', group.level);
  return `/reports/indicators/${code}?${query.toString()}`;
}

/** An in-app address of the indicator lists carrying the UI language: the group already put a query on it. */
export function indicatorHref(path: string, locale: string): string {
  return `${path}${path.includes('?') ? '&' : '?'}lang=${locale}`;
}

export function getIndicatorPage(code: IndicatorCode, params: URLSearchParams, signal?: AbortSignal): Promise<IndicatorPage> {
  const query = params.toString();
  const suffix = query === '' ? '' : `?${query}`;
  return api(`/api/v1/reports/indicators/${code}${suffix}`, signal === undefined ? {} : { signal });
}

export function isIndicatorPage(value: unknown): value is IndicatorPage {
  if (typeof value !== 'object' || value === null) return false;
  const page = value as Partial<IndicatorPage>;
  return Array.isArray(page.items) && typeof page.totalItems === 'number' && typeof page.totalPages === 'number';
}
