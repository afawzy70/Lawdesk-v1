import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { formatDate } from '../cases/format';
import { casePath } from '../cases/paths';
import { listCaseMembers, type MemberRef } from '../cases/api';
import { useAuth } from '../auth/AuthContext';
import { CALC_STATUSES, DEADLINE_STATUSES, NO_FILTER, listDeadlines, type Deadline, type DeadlineFilter } from './api';
import { DeadlineBadges } from './DeadlineParts';
import { useSourceName } from './useSourceName';
import { failureKey } from './errors';
import '../followup/followup.css';

/** Section 10: the register of legal deadlines of every case the user may see, with filters by state, owner, window and lateness. */
export function DeadlinesPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('wp4.register.title'));
  const id = useId();
  const { state } = useAuth();
  const canSettings = state.status === 'signedIn' && state.me.permissions.includes('DEADLINE_VIEW');
  const sourceName = useSourceName();
  const [filter, setFilter] = useState<DeadlineFilter>(NO_FILTER);
  const [members, setMembers] = useState<MemberRef[]>([]);
  const [result, setResult] = useState<{ key: string; items: Deadline[]; page: number; totalPages: number; total: number } | null>(null);
  const [failure, setFailure] = useState<{ key: string; message: MessageKey } | null>(null);
  const key = JSON.stringify(filter);

  useEffect(() => {
    const controller = new AbortController();
    listCaseMembers(controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) setMembers(found);
      },
      () => {
        // The owner filter then has no names to choose.
      },
    );
    return () => {
      controller.abort();
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    listDeadlines(filter, 0, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) {
          setResult({ key, items: page.items, page: 0, totalPages: page.totalPages, total: page.totalItems });
          setFailure(null);
        }
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setFailure({ key, message: failureKey(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [filter, key]);

  const more = (current: NonNullable<typeof result>) => {
    listDeadlines(filter, current.page + 1).then(
      (page) => {
        setResult({ ...current, items: [...current.items, ...page.items], page: current.page + 1 });
      },
      (error: unknown) => {
        setFailure({ key, message: failureKey(error) });
      },
    );
  };

  const set = (field: keyof DeadlineFilter) => (value: string) => { setFilter({ ...filter, [field]: value }); };
  const current = result?.key === key ? result : null;

  return (
    <>
      <h1>{t('wp4.register.title')}</h1>
      <p className="case-field-hint">{t('wp4.register.hint')}</p>
      {!canSettings ? null : (
        <p><Link to={withLang('/admin/deadlines', locale)}>{t('wp4.register.settings')}</Link></p>
      )}
      <form className="case-form" aria-label={t('wp4.register.filters')} onSubmit={(e) => { e.preventDefault(); }}>
        <div className="case-field">
          <label htmlFor={`${id}-status`}>{t('wp4.filter.status')}</label>
          <select id={`${id}-status`} value={filter.status} onChange={(e) => { set('status')(e.target.value); }}>
            <option value="">{t('wp4.filter.all')}</option>
            {DEADLINE_STATUSES.map((s) => (<option key={s} value={s}>{t(`wp4.status.${s}`)}</option>))}
          </select>
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-calc`}>{t('wp4.filter.calc')}</label>
          <select id={`${id}-calc`} value={filter.calcStatus} onChange={(e) => { set('calcStatus')(e.target.value); }}>
            <option value="">{t('wp4.filter.all')}</option>
            {CALC_STATUSES.map((s) => (<option key={s} value={s}>{t(`wp4.calc.${s}`)}</option>))}
          </select>
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-owner`}>{t('wp4.filter.owner')}</label>
          <select id={`${id}-owner`} value={filter.ownerId} onChange={(e) => { set('ownerId')(e.target.value); }}>
            <option value="">{t('wp4.filter.all')}</option>
            {members.map((m) => (<option key={m.id} value={m.id}>{m.displayName}</option>))}
          </select>
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-from`}>{t('wp4.filter.from')}</label>
          <input id={`${id}-from`} type="date" value={filter.from} onChange={(e) => { set('from')(e.target.value); }} />
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-to`}>{t('wp4.filter.to')}</label>
          <input id={`${id}-to`} type="date" value={filter.to} onChange={(e) => { set('to')(e.target.value); }} />
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-overdue`}>{t('wp4.filter.overdue')}</label>
          <select id={`${id}-overdue`} value={filter.overdue} onChange={(e) => { set('overdue')(e.target.value); }}>
            <option value="">{t('wp4.filter.all')}</option>
            <option value="true">{t('wp4.filter.overdueOnly')}</option>
            <option value="false">{t('wp4.filter.notOverdue')}</option>
          </select>
        </div>
      </form>
      {failure?.key === key ? <p className="case-alert" role="alert">{t(failure.message)}</p> : null}
      {current === null ? null : current.items.length === 0 ? (
        <p>{t('wp4.register.none')}</p>
      ) : (
        <>
          <p className="case-field-hint">{t('wp4.register.count', { count: String(current.total) })}</p>
          <table className="wp3-table">
            <thead>
              <tr>
                <th scope="col">{t('wp4.col.case')}</th>
                <th scope="col">{t('wp4.field.action')}</th>
                <th scope="col">{t('wp4.field.source')}</th>
                <th scope="col">{t('wp4.field.dueOn')}</th>
                <th scope="col">{t('wp4.field.owner')}</th>
                <th scope="col">{t('wp4.col.state')}</th>
              </tr>
            </thead>
            <tbody>
              {current.items.map((d) => (
                <tr key={d.id} data-overdue={d.overdue}>
                  <td><Link to={`${withLang(casePath(d.caseId), locale)}#wp3-ref-DEADLINE-${d.id}`}>{d.caseReferenceNo}</Link></td>
                  <td>{d.action}</td>
                  <td>{sourceName(d)}</td>
                  <td>{d.dueOn === null ? t('wp3.notSpecified') : formatDate(d.dueOn, locale)}</td>
                  <td>{d.ownerName ?? t('wp3.notSpecified')}</td>
                  <td><DeadlineBadges deadline={d} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          {current.page + 1 >= current.totalPages ? null : (
            <button type="button" onClick={() => { more(current); }}>{t('wp3.timeline.more')}</button>
          )}
        </>
      )}
    </>
  );
}
