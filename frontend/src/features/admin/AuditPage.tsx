import { type SyntheticEvent, useEffect, useState } from 'react';
import { ApiError } from '../../api/http';
import { formatDateTime } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { AdminTabs } from './AdminTabs';
import { type AuditEvent, type AuditFilter, listAudit, type Page } from './api';
import { codeLabel } from './messages';

/** The actions recorded by LD-011.2, offered as filter choices. */
const ACTIONS = [
  'LOGIN',
  'LOGOUT',
  'PASSWORD_CHANGE',
  'PASSWORD_RESET',
  'USER_CREATE',
  'USER_UPDATE',
  'USER_ENABLE',
  'USER_DISABLE',
  'USER_ROLES_CHANGE',
  'ROLE_CREATE',
  'ROLE_UPDATE',
  'ROLE_PERMISSIONS_CHANGE',
  'ACCESS_DENIED',
  'SESSION_REVOKED',
  'TASK_STATUS_CHANGED',
] as const;
const OUTCOMES = ['SUCCESS', 'FAILURE', 'DENIED'] as const;
const NO_FILTER: AuditFilter = { action: '', actor: '', outcome: '' };

/** LD-011.5: the security audit trail, newest first, filtered and paged. */
export function AuditPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('admin.audit.title'));
  const [draft, setDraft] = useState<AuditFilter>(NO_FILTER);
  const [filter, setFilter] = useState<AuditFilter>(NO_FILTER);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<AuditEvent> | null>(null);
  const [failure, setFailure] = useState<'denied' | 'error' | null>(null);

  useEffect(() => {
    let current = true;
    listAudit(filter, page).then(
      (events) => {
        if (current) {
          setResult(events);
          setFailure(null);
        }
      },
      (error: unknown) => {
        if (current) setFailure(error instanceof ApiError && error.status === 403 ? 'denied' : 'error');
      },
    );
    return () => {
      current = false;
    };
  }, [filter, page]);

  if (failure === 'denied') return <AccessDeniedPage />;

  const apply = (event: SyntheticEvent) => {
    event.preventDefault();
    setPage(0);
    setFilter({ ...draft, actor: draft.actor.trim() });
  };

  return (
    <section>
      <AdminTabs />
      <h1>{t('admin.audit.title')}</h1>
      <form className="admin-filters" aria-label={t('admin.audit.filters')} onSubmit={apply}>
        <label htmlFor="audit-actor">{t('admin.audit.actor')}</label>
        <input
          id="audit-actor"
          autoComplete="off"
          value={draft.actor}
          onChange={(event) => {
            setDraft({ ...draft, actor: event.target.value });
          }}
        />
        <label htmlFor="audit-action">{t('admin.audit.action')}</label>
        <select
          id="audit-action"
          value={draft.action}
          onChange={(event) => {
            setDraft({ ...draft, action: event.target.value });
          }}
        >
          <option value="">{t('admin.audit.any')}</option>
          {ACTIONS.map((action) => (
            <option key={action} value={action}>
              {codeLabel('admin.audit.action', action, t)}
            </option>
          ))}
        </select>
        <label htmlFor="audit-outcome">{t('admin.audit.outcome')}</label>
        <select
          id="audit-outcome"
          value={draft.outcome}
          onChange={(event) => {
            setDraft({ ...draft, outcome: event.target.value });
          }}
        >
          <option value="">{t('admin.audit.any')}</option>
          {OUTCOMES.map((outcome) => (
            <option key={outcome} value={outcome}>
              {codeLabel('admin.audit.outcome', outcome, t)}
            </option>
          ))}
        </select>
        <button type="submit">{t('admin.audit.filter')}</button>
      </form>
      {failure === 'error' && <p role="alert">{t('admin.error.unexpected')}</p>}
      {result !== null && result.items.length === 0 && <p>{t('admin.audit.none')}</p>}
      {result !== null && result.items.length > 0 && (
        <div className="table-scroll">
          <table>
            <caption>{t('admin.audit.title')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('admin.audit.when')}</th>
                <th scope="col">{t('admin.audit.actor')}</th>
                <th scope="col">{t('admin.audit.action')}</th>
                <th scope="col">{t('admin.audit.target')}</th>
                <th scope="col">{t('admin.audit.outcome')}</th>
                <th scope="col">{t('admin.audit.reason')}</th>
                <th scope="col">{t('admin.audit.requestId')}</th>
              </tr>
            </thead>
            <tbody>
              {result.items.map((event) => (
                <tr key={event.id}>
                  <td>
                    <time dateTime={event.occurredAt}>{formatDateTime(event.occurredAt, locale)}</time>
                  </td>
                  <td>{event.actorUsername ?? ''}</td>
                  <td>{codeLabel('admin.audit.action', event.action, t)}</td>
                  <td dir="ltr">{event.targetId ?? event.targetType ?? ''}</td>
                  <td>{codeLabel('admin.audit.outcome', event.outcome, t)}</td>
                  <td>{event.reason === null ? '' : codeLabel('admin.audit.reason', event.reason, t)}</td>
                  <td dir="ltr">{event.requestId ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {result !== null && result.totalPages > 1 && (
        <nav aria-label={t('admin.users.pages')}>
          <button
            type="button"
            disabled={page === 0}
            onClick={() => {
              setPage(page - 1);
            }}
          >
            {t('admin.users.previous')}
          </button>
          <span>
            {t('admin.users.pageOf', { page: String(result.page + 1), pages: String(result.totalPages) })}
          </span>
          <button
            type="button"
            disabled={page + 1 >= result.totalPages}
            onClick={() => {
              setPage(page + 1);
            }}
          >
            {t('admin.users.next')}
          </button>
        </nav>
      )}
    </section>
  );
}
