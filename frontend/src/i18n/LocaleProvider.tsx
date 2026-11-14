import { useCallback, useLayoutEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react';
import { useSearchParams } from 'react-router';
import { catalogVersion, subscribeCatalog } from '../catalog/catalogStore';
import { I18nContext, i18nFor } from './I18nContext';
import { applyDocumentLocale, resolveLocale, type Locale } from './locale';

export const LANG_PARAM = 'lang';

interface Props {
  /** Language used when the URL has no `?lang=`; unsupported values fall back to English. */
  defaultLocale?: string | undefined;
  children: ReactNode;
}

/**
 * Provides the UI language. The URL query parameter `?lang=` is the single source of truth: switching language
 * rewrites it (replacing the history entry), so nothing is written to browser storage. Must be rendered inside a
 * router.
 */
export function LocaleProvider({ defaultLocale, children }: Props) {
  const [params, setParams] = useSearchParams();
  const locale = resolveLocale(params.get(LANG_PARAM) ?? defaultLocale);

  useLayoutEffect(() => {
    applyDocumentLocale(locale);
  }, [locale]);

  const setLocale = useCallback(
    (next: Locale) => {
      setParams(
        (prev) => {
          const updated = new URLSearchParams(prev);
          updated.set(LANG_PARAM, next);
          return updated;
        },
        { replace: true },
      );
    },
    [setParams],
  );

  // A changed catalogue gives every consumer a new `t`, so screens show the administrator's labels at once.
  const catalogue = useSyncExternalStore(subscribeCatalog, catalogVersion);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const value = useMemo(() => i18nFor(locale, setLocale), [locale, setLocale, catalogue]);
  return <I18nContext value={value}>{children}</I18nContext>;
}
