import { useI18n } from '../../i18n/I18nContext';
import type { DocumentUse } from './api';

/** Where a stored document is used in the case file: what it proves, which memo or judgment it belongs to. */
export function DocumentUsageList({ uses }: { uses: readonly DocumentUse[] }) {
  const { t } = useI18n();
  if (uses.length === 0) return null;
  return (
    <ul className="pleadings-list" aria-label={t('pl.usage.title')}>
      {uses.map((u) => (
        <li key={`${u.type}-${u.refId}`}>
          {t(`pl.usage.${u.type}`)}: {u.label}
          {u.links.length === 0 ? null : <> — {u.links.map((l) => l.label).join('؛ ')}</>}
        </li>
      ))}
    </ul>
  );
}
