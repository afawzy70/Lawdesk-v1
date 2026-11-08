import type { ReactNode } from 'react';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from './format';
import type { CaseDetails } from './api';

/** The case record beyond its numbers (requirements section 1): only what was recorded is shown, nothing is filled in. */
export function CaseDetailsView({ details }: { details: CaseDetails | undefined }) {
  const { t, locale } = useI18n();
  const rows: [string, ReactNode][] = [];
  const add = (label: string, value: ReactNode | null | undefined) => {
    if (value !== null && value !== undefined && value !== '') rows.push([label, value]);
  };
  add(t('details.chamber'), details?.chamber);
  add(t('details.receivedOn'), details?.receivedOn === undefined || details.receivedOn === null ? null : formatDate(details.receivedOn, locale));
  add(t('details.referredBy'), details?.referredBy);
  add(
    t('details.claimedAmount'),
    details?.claimedAmount === undefined || details.claimedAmount === null
      ? null
      : t('details.view.claimedAmount', {
          amount: formatNumber(Number(details.claimedAmount), locale, { minimumFractionDigits: 3, maximumFractionDigits: 3 }),
        }),
  );
  add(t('details.importance'), details?.importance === undefined || details.importance === null ? null : t(`details.importance.${details.importance}`));
  add(t('details.facts'), details?.facts);
  add(t('details.internalNotes'), details?.internalNotes);
  return (
    <section className="case-record" aria-label={t('details.view.title')}>
      <h2>{t('details.view.title')}</h2>
      {rows.length === 0 ? (
        <p>{t('details.view.none')}</p>
      ) : (
        <dl className="case-facts">
          {rows.map(([label, value]) => (
            <div key={label} className="case-fact">
              <dt>{label}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  );
}
