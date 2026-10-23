import { useI18n } from '../i18n/I18nContext';

/** Switches between English and Arabic. The label is the other language's own name, marked with its `lang`. */
export function LocaleToggle() {
  const { locale, t, setLocale } = useI18n();
  const other = locale === 'en' ? 'ar' : 'en';
  return (
    <button
      type="button"
      className="locale-toggle"
      lang={other}
      onClick={() => {
        setLocale(other);
      }}
    >
      {t('shell.switchLanguage')}
    </button>
  );
}
