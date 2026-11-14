import { api } from '../api/http';
import type { Locale } from '../i18n/locale';
import { CATALOG_LISTS } from './registry';

/**
 * The administrator-managed labels, order and availability of every dropdown list (LD-DOC-13), loaded once after sign-in
 * from `GET /api/v1/lookups/all`. Until it is loaded, or when it cannot be (offline, an error), every screen keeps its
 * built-in labels and order.
 */

export interface CatalogOption {
  code: string;
  labelAr: string;
  labelEn: string | null;
  active: boolean;
  sortOrder: number;
}

type Snapshot = Record<string, readonly CatalogOption[]>;

let lists: Snapshot | null = null;
/** Message key (`prefix + CODE`) to the option that relabels it. */
let byKey = new Map<string, CatalogOption>();
let version = 0;
const listeners = new Set<() => void>();

function publish(next: Snapshot | null) {
  lists = next;
  byKey = new Map();
  if (next !== null) {
    for (const list of CATALOG_LISTS) {
      for (const option of next[list.code] ?? []) {
        for (const prefix of list.prefixes) byKey.set(prefix + option.code, option);
      }
    }
  }
  version += 1;
  listeners.forEach((listener) => {
    listener();
  });
}

export function setCatalog(next: Snapshot | null): void {
  publish(next);
}

export function resetCatalog(): void {
  publish(null);
}

export function subscribeCatalog(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export const catalogVersion = (): number => version;

/** (Re)loads the catalogue; a failure leaves what is there (the built-in labels when nothing was loaded). */
export async function loadCatalog(signal?: AbortSignal): Promise<void> {
  try {
    const found = await api<unknown>('/api/v1/lookups/all', signal === undefined ? {} : { signal });
    if (found === null || typeof found !== 'object' || Array.isArray(found)) return;
    publish(found as Snapshot);
  } catch {
    // The catalogue is an enhancement: screens keep their built-in labels.
  }
}

/** The administrator's label of a message key of a catalogued list, or undefined to use the built-in one. */
export function catalogLabel(key: string, locale: Locale): string | undefined {
  const option = byKey.get(key);
  if (option === undefined) return undefined;
  return locale === 'ar' ? option.labelAr : (option.labelEn ?? option.labelAr);
}

/**
 * The codes a dropdown offers for a list, in the administrator's order, without the options the administrator
 * deactivated (a record's {@code current} code stays so it can still be shown and kept). A list whose options can be added
 * (class a) offers the catalogue's own active options, including ones the screen's built-in list never had. Without the
 * catalogue the built-in `codes` come back unchanged.
 */
export function catalogCodes<T extends string>(list: string, codes: readonly T[], current?: string | readonly string[]): T[] {
  const options = lists?.[list];
  if (options === undefined || options.length === 0) return [...codes];
  const keep = new Set(current === undefined ? [] : typeof current === 'string' ? [current] : current);
  const known = new Map(options.map((option) => [option.code, option]));
  const definition = CATALOG_LISTS.find((l) => l.code === list);
  const base: string[] =
    definition?.class === 'a'
      ? [...options.map((o) => o.code), ...codes.filter((c) => !known.has(c))]
      : [...codes];
  const shown = base.filter((code) => {
    const option = known.get(code);
    return option === undefined || option.active || keep.has(code);
  });
  const rank = (code: string) => known.get(code)?.sortOrder ?? Number.MAX_SAFE_INTEGER;
  return shown.sort((a, b) => rank(a) - rank(b)) as T[];
}
