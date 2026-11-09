import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import type { JudgmentDetails } from '../proceedings/proceedingsApi';

/** The recorded extended fields of a judgment; fields never recorded are left out, not shown as empty. */
export function JudgmentDetailsView({ details }: { details: JudgmentDetails | undefined }) {
  const { t, locale } = useI18n();
  if (details === undefined) return null;
  const label = (ar: string | null, en: string | null) => (locale === 'ar' ? ar : (en ?? ar));
  const rows: [string, string | null][] = [
    [t('pl.judgment.chamber'), details.chamber],
    [t('pl.judgment.type'), label(details.judgmentTypeLabelAr, details.judgmentTypeLabelEn)],
    [t('pl.judgment.nature'), label(details.natureLabelAr, details.natureLabelEn)],
    [t('pl.judgment.operative'), details.operativeText],
    [t('pl.judgment.reasons'), details.reasons],
    [t('pl.judgment.costs'), details.costsAmount === null ? null : `${details.costsAmount} KWD`],
    [t('pl.judgment.fees'), details.lawyerFeesAmount === null ? null : `${details.lawyerFeesAmount} KWD`],
    [t('pl.judgment.appealable'), details.appealable === null ? null : t(details.appealable ? 'pl.yes' : 'pl.no')],
    [t('pl.judgment.notifiedOn'), details.notifiedOn === null ? null : formatDate(details.notifiedOn, locale)],
    [t('pl.judgment.status'), details.status === null ? null : t(`pl.judgment.status.${details.status}`)],
  ];
  const shown = rows.filter((row): row is [string, string] => row[1] !== null);
  if (shown.length === 0) return <p className="case-field-hint">{t('pl.judgment.detailsNotRecorded')}</p>;
  return (
    <dl className="case-facts">
      {shown.map(([name, value]) => (
        <div key={name} className="pleadings-fact">
          <dt>{name}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}
