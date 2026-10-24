import { type SyntheticEvent, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/http';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { useAuth } from '../auth/AuthContext';
import { AdminTabs } from './AdminTabs';
import { listUsers, type Page, type UserView } from './api';
import { roleLabel, statusKey } from './messages';

/** LD-009: searchable, paged list of users. */
export function UsersPage() {
  const { t, locale } = useI18n();
  const { state } = useAuth();
  usePageTitle(t('admin.users.title'));
  const canManage = state.status === 'signedIn' && state.me.permissions.includes('USER_MANAGE');
  const [draft, setDraft] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<UserView> | null>(null);
  const [failure, setFailure] = useState<'denied' | 'error' | null>(null);

  useEffect(() => {
    let current = true;
    listUsers(query, page).then(
      (users) => {
        if (current) {
          setResult(users);
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
  }, [query, page]);

  if (failure === 'denied') return <AccessDeniedPage />;

  const search = (event: SyntheticEvent) => {
    event.preventDefault();
    setPage(0);
    setQuery(draft.trim());
  };

  return (
    <section>
      <AdminTabs />
      <h1>{t('admin.users.title')}</h1>
      {canManage && <Link to={withLang('/admin/users/new', locale)}>{t('admin.users.new')}</Link>}
      <form role="search" onSubmit={search}>
        <input
          type="search"
          aria-label={t('admin.users.search')}
          value={draft}
          onChange={(event) => {
            setDraft(event.target.value);
          }}
        />
        <button type="submit">{t('admin.users.searchButton')}</button>
      </form>
      {failure === 'error' && <p role="alert">{t('admin.error.unexpected')}</p>}
      {result !== null && result.items.length === 0 && <p>{t('admin.users.none')}</p>}
      {result !== null && result.items.length > 0 && (
        <div className="table-scroll">
          <table>
            <caption>{t('admin.users.title')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('admin.field.username')}</th>
                <th scope="col">{t('admin.field.displayName')}</th>
                <th scope="col">{t('admin.field.email')}</th>
                <th scope="col">{t('admin.field.roles')}</th>
                <th scope="col">{t('admin.users.status')}</th>
              </tr>
            </thead>
            <tbody>
              {result.items.map((user) => (
                <tr key={user.id}>
                  <td>
                    <Link to={withLang(`/admin/users/${user.id}`, locale)}>{user.username}</Link>
                  </td>
                  <td>{user.displayName}</td>
                  <td>{user.email}</td>
                  <td>{user.roles.map((code) => roleLabel(code, t)).join('، ')}</td>
                  <td>{t(statusKey(user))}</td>
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
