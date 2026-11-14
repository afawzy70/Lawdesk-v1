import { api } from '../../api/http';

/** LD-DOC-r5 (stream A): the administrator's dropdown lists. `/api/v1/admin/lists` needs LIST_ADMIN. */

export type ListSource = 'LOOKUP' | 'MANAGED' | 'COURT';

export interface ListSummary {
  code: string;
  source: ListSource;
  addable: boolean;
  titleAr: string;
  titleEn: string;
  noteAr: string;
  noteEn: string;
  options: number;
}

export interface ListOption {
  id: string;
  listCode: string;
  code: string;
  labelAr: string;
  labelEn: string | null;
  active: boolean;
  sortOrder: number;
  version: number;
  /** Only the catalogue's lists carry it; system logic depends on the value. */
  system?: boolean;
}

export interface OptionInput {
  labelAr: string;
  labelEn: string | null;
  sortOrder: number | null;
}

const enc = encodeURIComponent;

export const listCatalogue = (signal?: AbortSignal) =>
  api<ListSummary[]>('/api/v1/admin/lists', signal === undefined ? {} : { signal });

/** The options of a list, active and inactive, in the order the screens show them. */
export function listOptions(list: ListSummary, signal?: AbortSignal): Promise<ListOption[]> {
  const options = signal === undefined ? {} : { signal };
  return list.source === 'MANAGED'
    ? api<ListOption[]>(`/api/v1/lists/${enc(list.code)}?includeInactive=true`, options)
    : api<ListOption[]>(`/api/v1/admin/lists/${enc(list.code)}/options`, options);
}

export function addOption(list: ListSummary, input: OptionInput & { code: string }): Promise<ListOption> {
  const url = list.source === 'MANAGED' ? `/api/v1/lists/${enc(list.code)}` : `/api/v1/admin/lists/${enc(list.code)}/options`;
  return api<ListOption>(url, { method: 'POST', body: input });
}

export function saveOption(list: ListSummary, option: ListOption, input: OptionInput & { active: boolean }): Promise<ListOption> {
  const url =
    list.source === 'MANAGED'
      ? `/api/v1/lists/${enc(list.code)}/${enc(option.id)}`
      : `/api/v1/admin/lists/options/${enc(option.id)}`;
  return api<ListOption>(url, { method: 'PUT', body: { ...input, version: option.version } });
}

/** The active options a screen offers, in order (any signed-in user who sees cases). */
export const screenOptions = (list: string, signal?: AbortSignal) =>
  api<ListOption[]>(`/api/v1/lookups/${enc(list)}`, signal === undefined ? {} : { signal });
