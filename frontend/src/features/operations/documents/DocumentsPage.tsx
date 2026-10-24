import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import type { MessageKey } from '../../../i18n/catalog';
import { formatDateTime, formatNumber } from '../../../i18n/format';
import { useI18n } from '../../../i18n/I18nContext';
import { usePageTitle } from '../../../i18n/usePageTitle';
import { AccessDeniedPage } from '../../auth/AccessDeniedPage';
import { casePath } from '../../cases/paths';
import { loadError } from '../formErrors';
import { contentUrl, formatSize, listLatestDocuments, type DocumentPage } from './documentsApi';
import '../operations.css';

type Result = { key: string; page: DocumentPage } | { key: string; error: MessageKey };

/**
 * The latest PDF attachments of every case (LD-026), newest first. Files are attached and removed on the case page;
 * the backend checks DOCUMENT_VIEW and answers every download as an attachment.
 */
export function DocumentsPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('operations.documents.title'));
  const captionId = useId();
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const requestKey = `${String(page)}#${String(reload)}`;

  useEffect(() => {
    const controller = new AbortController();
    listLatestDocuments(page, controller.signal).then(
      (answer) => {
        setResult({ key: requestKey, page: answer });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: requestKey, error: loadError(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [page, requestKey]);

  const current = result?.key === requestKey ? result : null;
  if (current !== null && 'error' in current && current.error === 'operations.error.viewForbidden') {
    return <AccessDeniedPage />;
  }

  return (
    <>
      <h1>{t('operations.documents.title')}</h1>
      <p>{t('operations.documents.intro')}</p>
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
        <p>{t('operations.documents.empty')}</p>
      ) : null}
      {current !== null && 'page' in current && current.page.items.length > 0 ? (
        <>
          <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
            <table className="ops-table">
              <caption id={captionId} className="visually-hidden">
                {t('operations.documents.title')}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{t('operations.document.title')}</th>
                  <th scope="col">{t('operations.task.case')}</th>
                  <th scope="col">{t('operations.document.fileName')}</th>
                  <th scope="col">{t('operations.document.size')}</th>
                  <th scope="col">{t('operations.document.attachedOn')}</th>
                  <th scope="col">{t('operations.document.file')}</th>
                </tr>
              </thead>
              <tbody>
                {current.page.items.map((d) => (
                  <tr key={d.id}>
                    <td className="ops-wrap">{d.title}</td>
                    <td className="ops-wrap">
                      <Link to={`${casePath(d.caseId)}?lang=${locale}`}>
                        <bdi dir="ltr">{d.caseReferenceNo ?? d.caseId}</bdi>
                      </Link>{' '}
                      {d.caseTitle ?? ''}
                    </td>
                    <td className="ops-wrap">
                      <bdi dir="ltr">{d.fileName}</bdi>
                    </td>
                    <td>{formatSize(d.sizeBytes, locale)}</td>
                    <td>{formatDateTime(d.createdAt, locale)}</td>
                    <td>
                      <a
                        href={contentUrl(d.id)}
                        download
                        aria-label={t('operations.document.downloadLabel', { title: d.title })}
                      >
                        {t('operations.document.download')}
                      </a>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {current.page.totalPages > 1 ? (
            <nav className="ops-pager" aria-label={t('operations.documents.pages')}>
              <button
                type="button"
                disabled={current.page.page === 0}
                onClick={() => {
                  setPage((p) => p - 1);
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
                  setPage((p) => p + 1);
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
