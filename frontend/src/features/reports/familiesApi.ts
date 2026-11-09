import { api } from '../../api/http';

/** The report families of requirements section 27 (LD-058): `GET /api/v1/reports/families[/{code}]`. */

export const FAMILY_CODES = [
  'CASES_BY_TYPE_COURT_STAGE',
  'CASES_BY_ORG_UNIT',
  'CASES_BY_MEMBER',
  'OPEN_CLOSED_CASES',
  'JUDGMENT_OUTCOMES',
  'CLAIM_RESULTS',
  'DEFENSE_POSITIONS',
  'LINKED_CASES',
  'ADJOURNMENTS',
  'EXPERT_SESSIONS',
  'DEADLINES_APPEALS',
  'ENFORCEMENT',
  'MISSING_EVIDENCE',
  'INFO_REQUESTS',
  'RISK_ASSESSMENT',
  'PERFORMANCE',
  'LIBRARY_PRECEDENTS',
] as const;
export type FamilyCode = (typeof FAMILY_CODES)[number];

export function isFamilyCode(value: string | undefined): value is FamilyCode {
  return FAMILY_CODES.some((code) => code === value);
}

export interface FamilyColumn {
  key: string;
  label: string;
  type: 'TEXT' | 'COUNT' | 'MONEY' | 'DECIMAL' | 'DATE';
}

export type Cell = string | number | null;

/** A family's answer: labels are in the language asked for; money is a three-decimal string, counts are numbers. */
export interface FamilyTable {
  family: string;
  title: string;
  available: boolean;
  unavailableReason: string | null;
  groupings: string[];
  groupBy: string | null;
  columns: FamilyColumn[];
  rows: Cell[][];
  hasTotal: boolean;
  note: string | null;
}

export function getFamily(code: FamilyCode, params: URLSearchParams, signal?: AbortSignal): Promise<FamilyTable> {
  const query = params.toString();
  return api(`/api/v1/reports/families/${code}${query === '' ? '' : `?${query}`}`,
    signal === undefined ? {} : { signal });
}

export function isFamilyTable(value: unknown): value is FamilyTable {
  if (typeof value !== 'object' || value === null) return false;
  const table = value as Partial<FamilyTable>;
  return Array.isArray(table.columns) && Array.isArray(table.rows) && typeof table.title === 'string';
}
