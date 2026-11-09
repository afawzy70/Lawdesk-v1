import { useState } from 'react';
import { formatDateTime } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import type { MessageKey } from '../../i18n/catalog';
import type { CaseDocument } from '../operations/documents/documentsApi';
import { api } from '../../api/http';
import { Notice } from '../pleadings/ui';
import { useLoad } from '../pleadings/useLoad';
import { asList, purgeDocument, restoreDocument } from './api';
import { archiveError } from './errors';

interface Props {
  caseId: string;
  canAdminister: boolean;
  /** Called after a document came back into the case file. */
  onRestored: () => void;
  /** Bumped by the parent whenever it withdrew something, so this list reloads. */
  generation: number;
}

/**
 * Documents taken out of the case file are kept. Those who manage documents may restore them; only a holder of the archive
 * administration right can delete one for good, after stating why (the approved deletion procedure, requirement 13).
 */
export function WithdrawnDocuments({ caseId, canAdminister, onRestored, generation }: Props) {
  const { t, locale } = useI18n();
  const withdrawn = useLoad(
    (signal) => api<CaseDocument[]>(`/api/v1/cases/${encodeURIComponent(caseId)}/documents/withdrawn`, { signal }),
    [caseId, generation],
  );
  const [purging, setPurging] = useState<CaseDocument | null>(null);
  const [reason, setReason] = useState('');
  const [message, setMessage] = useState<{ failed: boolean; key: MessageKey } | null>(null);

  const restore = async (d: CaseDocument) => {
    setMessage(null);
    try {
      await restoreDocument(d.id, d.version);
      setMessage({ failed: false, key: 'wp5.doc.restored' });
      withdrawn.reload();
      onRestored();
    } catch (error) {
      setMessage({ failed: true, key: archiveError(error) });
    }
  };

  const purge = async (d: CaseDocument) => {
    setMessage(null);
    try {
      await purgeDocument(d.id, reason);
      setPurging(null);
      setReason('');
      setMessage({ failed: false, key: 'wp5.doc.purged' });
      withdrawn.reload();
    } catch (error) {
      setMessage({ failed: true, key: archiveError(error) });
    }
  };

  const list = asList(withdrawn.data);
  if (list.length === 0 && message === null) return null;
  return (
    <section aria-label={t('wp5.doc.withdrawnList')}>
      <h4>{t('wp5.doc.withdrawnList')}</h4>
      {message === null ? null : <Notice failed={message.failed}>{t(message.key)}</Notice>}
      <ul className="archive-list">
        {list.map((d) => (
          <li key={d.id} className="archive-item">
            <p>
              <strong>{d.title}</strong>
            </p>
            <p className="archive-meta">
              {d.withdrawnAt === null || d.withdrawnAt === undefined ? '' : `${formatDateTime(d.withdrawnAt, locale)} · `}
              {t('wp5.doc.withdrawnReason', { reason: d.withdrawnReason ?? '' })}
            </p>
            <div className="archive-actions">
              <button type="button" aria-label={t('wp5.doc.restoreLabel', { title: d.title })} onClick={() => void restore(d)}>
                {t('wp5.doc.restore')}
              </button>
              {canAdminister ? (
                <button
                  type="button"
                  aria-label={t('wp5.doc.purgeLabel', { title: d.title })}
                  onClick={() => {
                    setPurging(d);
                    setReason('');
                    setMessage(null);
                  }}
                >
                  {t('wp5.doc.purge')}
                </button>
              ) : null}
            </div>
            {purging?.id === d.id ? (
              <form
                className="case-form"
                aria-label={t('wp5.doc.purgeLabel', { title: d.title })}
                onSubmit={(event) => {
                  event.preventDefault();
                  void purge(d);
                }}
              >
                <p className="case-alert">{t('wp5.doc.purgeWarning')}</p>
                <div className="case-field">
                  <label htmlFor={`purge-${d.id}`}>{t('wp5.doc.withdrawReason')}</label>
                  <input
                    id={`purge-${d.id}`}
                    type="text"
                    maxLength={500}
                    value={reason}
                    onChange={(event) => {
                      setReason(event.target.value);
                    }}
                  />
                </div>
                <div className="archive-actions">
                  <button type="submit" disabled={reason.trim() === ''}>
                    {t('wp5.doc.purgeConfirm')}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setPurging(null);
                    }}
                  >
                    {t('wp5.cancel')}
                  </button>
                </div>
              </form>
            ) : null}
          </li>
        ))}
      </ul>
    </section>
  );
}
