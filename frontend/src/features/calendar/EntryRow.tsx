import { Link } from 'react-router';
import { useI18n } from '../../i18n/I18nContext';
import { casePath } from '../cases/paths';
import type { CalendarEntry } from './api';

/** The entry's time, or "all day". Times are Kuwait wall-clock times. */
function timeLabel(entry: CalendarEntry, allDay: string): string {
  if (entry.startTime === null) return allDay;
  return entry.endTime === null ? entry.startTime : `${entry.startTime}–${entry.endTime}`;
}

/** One dated entry of the calendar: time, kind, what it is, its case, its owner and what needs attention. */
export function EntryRow({ entry }: { entry: CalendarEntry }) {
  const { locale, t } = useI18n();
  const heading = entry.type === 'TASK' || entry.type === 'INFO_REQUEST_DUE' || entry.refType === 'CALENDAR_DATE'
    ? entry.title
    : (entry.caseTitle ?? entry.title);
  return (
    <li className="wp6-entry" data-type={entry.type} data-conflict={entry.conflict}>
      <span className="wp6-entry-time" dir="ltr">{timeLabel(entry, t('wp6.calendar.allDay'))}</span>
      <span className="wp6-entry-body">
        <strong>{t(`wp6.calendar.type.${entry.type}`)}</strong>
        {' · '}
        <span className="ops-wrap">{heading}</span>
        {entry.caseId === null ? null : (
          <>
            {' · '}
            <Link to={`${casePath(entry.caseId)}?lang=${locale}`}>
              <bdi dir="ltr">{entry.caseReferenceNo ?? entry.caseId}</bdi>
            </Link>
          </>
        )}
        {entry.ownerName === null ? null : <span className="wp6-entry-owner"> · {entry.ownerName}</span>}
        {entry.conflict ? <span className="ops-badge ops-badge-danger">{t('wp6.calendar.conflict')}</span> : null}
        {entry.closedCaseFuture ? <span className="ops-badge ops-badge-warn">{t('wp6.calendar.closedCase')}</span> : null}
        {entry.status === 'CANCELLED' || entry.status === 'LATE' || entry.status === 'DONE' ? (
          <span className="ops-badge">{t(`wp6.calendar.status.${entry.status}`)}</span>
        ) : null}
      </span>
    </li>
  );
}

