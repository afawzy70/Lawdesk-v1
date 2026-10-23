import { createContext, useContext } from 'react';
import { translate, type MessageKey, type MessageVars } from './catalog';
import { DEFAULT_LOCALE, type Locale } from './locale';

export interface I18n {
  locale: Locale;
  t: (key: MessageKey, vars?: MessageVars) => string;
  setLocale: (locale: Locale) => void;
}

export function i18nFor(locale: Locale, setLocale: (locale: Locale) => void): I18n {
  return { locale, t: (key, vars) => translate(locale, key, vars), setLocale };
}

/** Outside a `LocaleProvider` (e.g. isolated component tests) components render in the default locale. */
export const I18nContext = createContext<I18n>(
  i18nFor(DEFAULT_LOCALE, () => {
    /* no provider: language cannot change */
  }),
);

export function useI18n(): I18n {
  return useContext(I18nContext);
}
