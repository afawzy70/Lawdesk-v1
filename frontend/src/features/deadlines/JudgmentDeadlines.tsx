import { useContext } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import { AuthContext } from '../auth/AuthContext';
import { useLoad } from '../pleadings/useLoad';
import { listJudgmentDeadlines } from './api';
import { DeadlineBadges } from './DeadlineParts';

/**
 * The deadlines that run from one judgment (its appeal, cassation or grievance deadline): the per-judgment view of section
 * 10, replacing the empty "appeal deadline" field. Shown only to a user holding DEADLINE_VIEW; recorded in the case's
 * deadlines section.
 */
export function JudgmentDeadlines({ judgmentId }: { judgmentId: string }) {
  const { t, locale } = useI18n();
  const state = useContext(AuthContext)?.state;
  const allowed = state?.status === 'signedIn' && state.me.permissions.includes('DEADLINE_VIEW');
  const loaded = useLoad((signal) => (allowed ? listJudgmentDeadlines(judgmentId, signal) : Promise.resolve([])), [judgmentId, allowed]);
  if (!allowed || loaded.status !== null) return null;
  const items = loaded.data ?? [];
  return (
    <div className="wp4-judgment-deadlines">
      <h4>{t('wp4.judgment.title')}</h4>
      {items.length === 0 ? (
        <p className="case-field-hint">{t('wp4.judgment.none')}</p>
      ) : (
        <ul className="wp3-list">
          {items.map((d) => (
            <li key={d.id}>
              <strong>{d.action}</strong> — {d.dueOn === null ? t('wp3.notSpecified') : formatDate(d.dueOn, locale)}{' '}
              <DeadlineBadges deadline={d} />
              {d.status === 'OPEN' && d.calcStatus !== 'APPROVED' ? (
                <span className="case-field-hint"> {t(`wp4.awaiting.${d.calcStatus === 'PROPOSED' ? 'PROPOSED' : 'MANUAL'}`)}</span>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
