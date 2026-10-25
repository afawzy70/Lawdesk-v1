import { api } from '../../api/http';
import type { PageResponse } from '../cases/api';
import { OUTCOMES, STAGES, type Outcome, type Stage } from '../proceedings/proceedingsApi';
import type { CourtRef, Direction } from './inventoryApi';

/** `GET /api/v1/reports/judgments` (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-032); every call goes through `api()`. */

export const JUDGMENT_SORTS = ['judgmentDate', 'outcome', 'stage'] as const;
export type JudgmentSort = (typeof JUDGMENT_SORTS)[number];

export interface JudgmentRow {
  id: string;
  judgmentDate: string;
  outcome: Outcome;
  final: boolean;
  stage: Stage;
  court: CourtRef;
  courtCaseNumber: string;
  courtCaseYear: number;
  caseId: string;
  caseReferenceNo: string | null;
  caseTitle: string | null;
}

export interface JudgmentReport extends PageResponse<JudgmentRow> {
  from: string;
  to: string;
  summary: {
    total: number;
    final: number;
    byOutcome: Record<Outcome, number>;
    byStage: Record<Stage, number>;
  };
}

/** The filters as the address and the request carry them; absent means "any" (the period: the current month). */
export interface JudgmentFilter {
  from?: string;
  to?: string;
  outcome?: Outcome;
  stage?: Stage;
  courtId?: string;
  final?: 'true' | 'false';
  sort?: JudgmentSort;
  direction?: Direction;
  page?: number;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function oneOf<T extends string>(values: readonly T[], value: string | null | undefined): T | undefined {
  return values.find((candidate) => candidate === value);
}

function matching(pattern: RegExp, value: string | null): string | undefined {
  const trimmed = value?.trim() ?? '';
  return pattern.test(trimmed) ? trimmed : undefined;
}

/** Reads the filters from an address or a submitted form; unknown values are dropped rather than sent. */
export function judgmentFilterFrom(read: (name: string) => string | null): JudgmentFilter {
  const filter: JudgmentFilter = {};
  const from = matching(ISO_DATE, read('from'));
  if (from !== undefined) filter.from = from;
  const to = matching(ISO_DATE, read('to'));
  if (to !== undefined) filter.to = to;
  const outcome = oneOf(OUTCOMES, read('outcome'));
  if (outcome !== undefined) filter.outcome = outcome;
  const stage = oneOf(STAGES, read('stage'));
  if (stage !== undefined) filter.stage = stage;
  const courtId = matching(UUID, read('courtId'));
  if (courtId !== undefined) filter.courtId = courtId;
  const isFinal = oneOf(['true', 'false'] as const, read('final'));
  if (isFinal !== undefined) filter.final = isFinal;
  const [field, direction] = (read('sort') ?? '').split(',');
  const sort = oneOf(JUDGMENT_SORTS, field);
  if (sort !== undefined) {
    filter.sort = sort;
    filter.direction = direction === 'desc' ? 'desc' : 'asc';
  }
  const page = Number.parseInt(read('page') ?? '', 10);
  if (page > 0) filter.page = page;
  return filter;
}

/** The filters as query parameters, in a stable order; the default page and sort are left out. */
export function judgmentQuery(filter: JudgmentFilter): URLSearchParams {
  const params = new URLSearchParams();
  for (const name of ['from', 'to', 'outcome', 'stage', 'courtId', 'final'] as const) {
    const value = filter[name];
    if (value !== undefined) params.set(name, value);
  }
  if (filter.sort !== undefined) params.set('sort', `${filter.sort},${filter.direction ?? 'asc'}`);
  if (filter.page !== undefined && filter.page > 0) params.set('page', String(filter.page));
  return params;
}

export function getJudgmentReport(filter: JudgmentFilter, signal?: AbortSignal): Promise<JudgmentReport> {
  const query = judgmentQuery(filter).toString();
  return api(`/api/v1/reports/judgments${query === '' ? '' : `?${query}`}`, signal === undefined ? {} : { signal });
}
