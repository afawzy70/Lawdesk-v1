import { api } from '../../api/http';
import { CASE_STATUSES, CASE_TYPES, type CaseStatus, type CaseType, type PageResponse } from '../cases/api';
import { STAGES, type Stage } from '../proceedings/proceedingsApi';

/** `GET /api/v1/reports/cases` (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-031); every call goes through `api()`. */

export const STATUS_GROUPS = ['OPEN', 'CLOSED'] as const;
export type StatusGroup = (typeof STATUS_GROUPS)[number];

/** The sort fields the backend accepts (anything else is a 400). */
export const INVENTORY_SORTS = ['openedOn', 'referenceNo', 'title', 'caseType', 'status'] as const;
export type InventorySort = (typeof INVENTORY_SORTS)[number];
export type Direction = 'asc' | 'desc';

export interface CourtRef {
  id: string;
  code: string;
  nameAr: string;
  nameEn: string;
}

export interface InventoryRow {
  id: string;
  referenceNo: string;
  title: string;
  caseType: CaseType;
  status: CaseStatus;
  statusGroup: StatusGroup;
  openedOn: string;
  currentStage: Stage | null;
  currentCourt: CourtRef | null;
  proceedingCount: number;
}

export interface InventoryReport extends PageResponse<InventoryRow> {
  summary: {
    total: number;
    open: number;
    closed: number;
    byStatus: Record<CaseStatus, number>;
    byType: Record<CaseType, number>;
    byStage: Record<Stage | 'NONE', number>;
  };
}

/** The filters as the address and the request carry them; absent means "any". */
export interface InventoryFilter {
  q?: string;
  status?: CaseStatus;
  statusGroup?: StatusGroup;
  caseType?: CaseType;
  openedFrom?: string;
  openedTo?: string;
  stage?: Stage;
  courtId?: string;
  sort?: InventorySort;
  direction?: Direction;
  page?: number;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function oneOf<T extends string>(values: readonly T[], value: string | null): T | undefined {
  return values.find((candidate) => candidate === value);
}

function matching(pattern: RegExp, value: string | null): string | undefined {
  const trimmed = value?.trim() ?? '';
  return pattern.test(trimmed) ? trimmed : undefined;
}

/** Reads the filters from an address or a submitted form; unknown values are dropped rather than sent. */
export function inventoryFilterFrom(read: (name: string) => string | null): InventoryFilter {
  const filter: InventoryFilter = {};
  const q = read('q')?.trim() ?? '';
  if (q !== '') filter.q = q;
  const status = oneOf(CASE_STATUSES, read('status'));
  if (status !== undefined) filter.status = status;
  const statusGroup = oneOf(STATUS_GROUPS, read('statusGroup'));
  if (statusGroup !== undefined) filter.statusGroup = statusGroup;
  const caseType = oneOf(CASE_TYPES, read('caseType'));
  if (caseType !== undefined) filter.caseType = caseType;
  const openedFrom = matching(ISO_DATE, read('openedFrom'));
  if (openedFrom !== undefined) filter.openedFrom = openedFrom;
  const openedTo = matching(ISO_DATE, read('openedTo'));
  if (openedTo !== undefined) filter.openedTo = openedTo;
  const stage = oneOf(STAGES, read('stage'));
  if (stage !== undefined) filter.stage = stage;
  const courtId = matching(UUID, read('courtId'));
  if (courtId !== undefined) filter.courtId = courtId;
  const [field, direction] = (read('sort') ?? '').split(',');
  const sort = oneOf(INVENTORY_SORTS, field ?? null);
  if (sort !== undefined) {
    filter.sort = sort;
    filter.direction = direction === 'desc' ? 'desc' : 'asc';
  }
  const page = Number.parseInt(read('page') ?? '', 10);
  if (page > 0) filter.page = page;
  return filter;
}

/** The filters as query parameters, in a stable order; the default page and sort are left out. */
export function inventoryQuery(filter: InventoryFilter): URLSearchParams {
  const params = new URLSearchParams();
  const set = (name: string, value: string | undefined) => {
    if (value !== undefined && value !== '') params.set(name, value);
  };
  set('q', filter.q);
  set('status', filter.status);
  set('statusGroup', filter.statusGroup);
  set('caseType', filter.caseType);
  set('openedFrom', filter.openedFrom);
  set('openedTo', filter.openedTo);
  set('stage', filter.stage);
  set('courtId', filter.courtId);
  if (filter.sort !== undefined) params.set('sort', `${filter.sort},${filter.direction ?? 'asc'}`);
  if (filter.page !== undefined && filter.page > 0) params.set('page', String(filter.page));
  return params;
}

export function getInventory(filter: InventoryFilter, signal?: AbortSignal): Promise<InventoryReport> {
  const query = inventoryQuery(filter).toString();
  return api(`/api/v1/reports/cases${query === '' ? '' : `?${query}`}`, signal === undefined ? {} : { signal });
}
