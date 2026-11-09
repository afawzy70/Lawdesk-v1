import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { useI18n } from '../../../i18n/I18nContext';
import { withLang } from '../../../i18n/locale';
import { casePath } from '../../cases/paths';
import { formatDay } from '../days';
import { listFollowUp, type Hearing } from './hearingsApi';

/** Requirements section 7: cases whose last hearing was adjourned with no next date, kept in view until one is scheduled. */
export function HearingFollowUp({ refresh }: { refresh: number }) {
  const { t, locale } = useI18n();
  const id = useId();
  const [items, setItems] = useState<Hearing[] | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listFollowUp(0, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) setItems(page.items);
      },
      () => {
        // The calendar below explains its own failures; this panel just stays empty.
        if (!controller.signal.aborted) setItems(null);
      },
    );
    return () => {
      controller.abort();
    };
  }, [refresh]);

  if (items === null || items.length === 0) return null;
  return (
    <section className="ops-section" aria-labelledby={`${id}-h`}>
      <h2 id={`${id}-h`}>{t('wp3.hearing.followUpTitle')}</h2>
      <p className="ops-hint">{t('wp3.hearing.followUpHint')}</p>
      <ul className="ops-list">
        {items.map((h) => (
          <li key={h.id} className="ops-item">
            <p>
              <Link to={withLang(casePath(h.caseId), locale)}>{h.caseReferenceNo}</Link> — {h.caseTitle}
            </p>
            <p className="ops-wrap">
              {t('wp3.hearing.followUpItem', { date: formatDay(h.hearingDate, locale), reason: h.noNextDateReason ?? '' })}
            </p>
          </li>
        ))}
      </ul>
    </section>
  );
}
