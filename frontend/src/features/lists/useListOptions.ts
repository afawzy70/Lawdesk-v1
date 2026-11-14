import { useEffect, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { screenOptions, type ListOption } from './listsApi';

/**
 * The options of an administrator-managed list, for a screen's dropdown (LD-DOC-r5). While the catalogue is not
 * available (loading, no permission, offline) `options` is null and the screen keeps the labels it always had; a
 * `label()` falls back the same way for a code the catalogue does not know.
 */
export function useListOptions(list: string): {
  options: ListOption[] | null;
  label: (code: string, fallback: string) => string;
  /** The codes in the administrator's order; codes the catalogue does not know keep their place at the end. */
  order: <T extends string>(codes: readonly T[]) => T[];
} {
  const { locale } = useI18n();
  const [options, setOptions] = useState<ListOption[] | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    screenOptions(list, controller.signal).then(
      (found) => {
        if (!controller.signal.aborted && Array.isArray(found) && found.length > 0) setOptions(found);
      },
      () => {
        // The catalogue is an enhancement: the screen's own labels stay in use.
      },
    );
    return () => {
      controller.abort();
    };
  }, [list]);
  const label = (code: string, fallback: string): string => {
    const found = options?.find((option) => option.code === code);
    if (found === undefined) return fallback;
    return locale === 'ar' ? found.labelAr : (found.labelEn ?? found.labelAr);
  };
  const order = <T extends string>(codes: readonly T[]): T[] => {
    const rank = (code: T) => options?.find((option) => option.code === code)?.sortOrder ?? Number.MAX_SAFE_INTEGER;
    return [...codes].sort((a, b) => rank(a) - rank(b));
  };
  return { options, label, order };
}
