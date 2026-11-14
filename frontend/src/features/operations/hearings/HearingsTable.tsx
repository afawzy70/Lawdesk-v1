import { useId, type ReactNode } from 'react';
import { Link } from 'react-router';
import { useI18n } from '../../../i18n/I18nContext';
import { casePath } from '../../cases/paths';
import { numberOf } from '../../proceedings/dates';
import { formatDay, formatTime } from '../days';
import type { Hearing } from './hearingsApi';

/**
 * The table of hearings shared by the calendar's schedule view, the dashboard and the follow-up page: date, time, case
 * (a link to it), court, stage number, purpose and status. {@code actions} adds a last column for the caller's buttons.
 */
export function HearingsTable({
  hearings,
  caption,
  actions,
  compact = false,
}: {
  hearings: Hearing[];
  caption: string;
  actions?: { header: string; cell: (hearing: Hearing) => ReactNode };
  /** The dashboard's shorter table leaves out the stage number. */
  compact?: boolean;
}) {
  const { locale, t } = useI18n();
  const captionId = useId();
  return (
    <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
      <table className="ops-table">
        <caption id={captionId} className="visually-hidden">
          {caption}
        </caption>
        <thead>
          <tr>
            <th scope="col">{t('operations.hearing.date')}</th>
            <th scope="col">{t('operations.hearing.time')}</th>
            <th scope="col">{t('operations.hearing.case')}</th>
            <th scope="col">{t('operations.hearing.court')}</th>
            {compact ? null : <th scope="col">{t('operations.hearing.number')}</th>}
            <th scope="col">{t('operations.hearing.purpose')}</th>
            <th scope="col">{t('operations.hearing.status')}</th>
            {actions === undefined ? null : <th scope="col">{actions.header}</th>}
          </tr>
        </thead>
        <tbody>
          {hearings.map((h) => (
            <tr key={h.id}>
              <td>{formatDay(h.hearingDate, locale)}</td>
              <td>{h.hearingTime === null ? t('operations.hearing.noTime') : formatTime(h.hearingTime, locale)}</td>
              <td className="ops-wrap">
                <Link to={`${casePath(h.caseId)}?lang=${locale}`}>
                  <bdi dir="ltr">{h.caseReferenceNo}</bdi>
                </Link>{' '}
                <bdi dir="auto">{h.caseTitle}</bdi>
              </td>
              <td className="ops-wrap">{locale === 'ar' ? h.courtNameAr : h.courtNameEn}</td>
              {compact ? null : (
                <td>
                  <bdi dir="ltr">{numberOf(h)}</bdi>
                </td>
              )}
              <td>{t(`operations.purpose.${h.purpose}`)}</td>
              <td>
                {t(`operations.hearingStatus.${h.status}`)}
                {h.awaitingResult ? <span className="ops-badge ops-badge-warn">{t('operations.hearing.awaiting')}</span> : null}
              </td>
              {actions === undefined ? null : <td>{actions.cell(h)}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
