import { useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../../i18n/catalog';
import { formatNumber } from '../../../i18n/format';
import { useI18n } from '../../../i18n/I18nContext';
import { usePageTitle } from '../../../i18n/usePageTitle';
import { AccessDeniedPage } from '../../auth/AccessDeniedPage';
import { loadError } from '../formErrors';
import { ExpertForm } from './ExpertForm';
import { listExperts, type Expert, type ExpertFilter, type ExpertPage } from './expertsApi';
import '../operations.css';

type Result = { key: string; page: ExpertPage } | { key: string; error: MessageKey };
type Editing = null | 'new' | Expert;

/**
 * The expert registry (LD-025): reference data like the courts, filtered by status and searched by name or specialty,
 * with create and edit. Experts are deactivated, never deleted. The backend checks EXPERT_VIEW and EXPERT_MANAGE.
 */
export function ExpertsPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('operations.experts.title'));
  const captionId = useId();
  const filterId = useId();
  const [filter, setFilter] = useState<ExpertFilter>({ page: 0 });
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const [editing, setEditing] = useState<Editing>(null);
  const [saved, setSaved] = useState(false);
  const requestKey = `${JSON.stringify(filter)}#${String(reload)}`;

  useEffect(() => {
    const controller = new AbortController();
    listExperts(filter, controller.signal).then(
      (page) => {
        setResult({ key: requestKey, page });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: requestKey, error: loadError(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [filter, requestKey]);

  const current = result?.key === requestKey ? result : null;
  if (current !== null && 'error' in current && current.error === 'operations.error.viewForbidden') {
    return <AccessDeniedPage />;
  }

  const open = (target: Editing) => {
    setSaved(false);
    setEditing(target);
  };

  return (
    <>
      <h1>{t('operations.experts.title')}</h1>
      <p>{t('operations.experts.intro')}</p>
      <div className="ops-toolbar">
        <fieldset className="ops-filters">
          <legend>{t('operations.filters')}</legend>
          <label htmlFor={`${filterId}-status`}>{t('operations.experts.status')}</label>
          <select
            id={`${filterId}-status`}
            value={filter.active === undefined ? '' : String(filter.active)}
            onChange={(event) => {
              const value = event.target.value;
              setFilter((f) => ({ ...f, page: 0, active: value === '' ? undefined : value === 'true' }));
            }}
          >
            <option value="">{t('operations.all')}</option>
            <option value="true">{t('operations.expert.activeStatus')}</option>
            <option value="false">{t('operations.expert.inactiveStatus')}</option>
          </select>
          <label htmlFor={`${filterId}-q`}>{t('operations.experts.search')}</label>
          <input
            id={`${filterId}-q`}
            type="search"
            maxLength={100}
            value={filter.q ?? ''}
            onChange={(event) => {
              const value = event.target.value;
              setFilter((f) => ({ ...f, page: 0, q: value === '' ? undefined : value }));
            }}
          />
        </fieldset>
        <button
          type="button"
          onClick={() => {
            open('new');
          }}
        >
          {t('operations.expert.new')}
        </button>
      </div>

      {saved ? (
        <p role="status" className="ops-saved">
          {t('operations.expert.saved')}
        </p>
      ) : null}
      {editing === null ? null : (
        <ExpertForm
          key={editing === 'new' ? 'new' : editing.id}
          expert={editing === 'new' ? undefined : editing}
          onSaved={() => {
            setEditing(null);
            setSaved(true);
            setReload((n) => n + 1);
          }}
          onCancel={() => {
            setEditing(null);
          }}
        />
      )}

      {current === null ? <p>{t('operations.loading')}</p> : null}
      {current !== null && 'error' in current ? (
        <div role="alert" className="ops-alert">
          <p>{t(current.error)}</p>
          <button
            type="button"
            onClick={() => {
              setReload((n) => n + 1);
            }}
          >
            {t('operations.retry')}
          </button>
        </div>
      ) : null}
      {current !== null && 'page' in current && current.page.items.length === 0 ? (
        <p>{t('operations.experts.empty')}</p>
      ) : null}
      {current !== null && 'page' in current && current.page.items.length > 0 ? (
        <>
          <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
            <table className="ops-table">
              <caption id={captionId} className="visually-hidden">
                {t('operations.experts.title')}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{t('operations.expert.name')}</th>
                  <th scope="col">{t('operations.expert.specialty')}</th>
                  <th scope="col">{t('operations.expert.office')}</th>
                  <th scope="col">{t('operations.expert.phone')}</th>
                  <th scope="col">{t('operations.experts.status')}</th>
                  <th scope="col">{t('operations.task.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {current.page.items.map((expert) => (
                  <tr key={expert.id}>
                    <td className="ops-wrap">{expert.name}</td>
                    <td className="ops-wrap">{expert.specialty}</td>
                    <td className="ops-wrap">{expert.office ?? ''}</td>
                    <td>
                      <bdi dir="ltr">{expert.phone ?? ''}</bdi>
                    </td>
                    <td>
                      {expert.active ? t('operations.expert.activeStatus') : t('operations.expert.inactiveStatus')}
                    </td>
                    <td>
                      <div className="ops-actions">
                        <button
                          type="button"
                          aria-label={t('operations.expert.editLabel', { name: expert.name })}
                          onClick={() => {
                            open(expert);
                          }}
                        >
                          {t('operations.edit')}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {current.page.totalPages > 1 ? (
            <nav className="ops-pager" aria-label={t('operations.experts.pages')}>
              <button
                type="button"
                disabled={current.page.page === 0}
                onClick={() => {
                  setFilter((f) => ({ ...f, page: f.page - 1 }));
                }}
              >
                {t('operations.previous')}
              </button>
              <span>
                {t('operations.pageOf', {
                  page: formatNumber(current.page.page + 1, locale),
                  pages: formatNumber(current.page.totalPages, locale),
                })}
              </span>
              <button
                type="button"
                disabled={current.page.page + 1 >= current.page.totalPages}
                onClick={() => {
                  setFilter((f) => ({ ...f, page: f.page + 1 }));
                }}
              >
                {t('operations.next')}
              </button>
            </nav>
          ) : null}
        </>
      ) : null}
    </>
  );
}
