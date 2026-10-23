import type { MessageKey } from '../i18n/catalog';
import { useI18n } from '../i18n/I18nContext';
import { usePageTitle } from '../i18n/usePageTitle';

interface Props {
  title: MessageKey;
}

/** Placeholder for areas that are planned but not built: states plainly that the feature is not available. */
export function ComingSoonPage({ title }: Props) {
  const { t } = useI18n();
  const heading = t(title);
  usePageTitle(heading);
  return (
    <>
      <h1>{heading}</h1>
      <p className="coming-soon-badge">{t('comingSoon.badge')}</p>
      <p>{t('comingSoon.body')}</p>
    </>
  );
}
