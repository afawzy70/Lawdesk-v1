import { useEffect, useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { usePermissions } from '../archive/usePermissions';
import { type CaseRequests, getCaseRequests, saveCaseRequests } from './api';
import { RequestItemsEditor } from './RequestItemsEditor';

interface Props {
  caseId: string;
  /** False for a closed or archived case: its requests are shown but cannot change. */
  editable: boolean;
}

/** «الطلبات» of the case on its page: the items of each side, editable by those who may edit the case. */
export function CaseRequestsSection({ caseId, editable }: Props) {
  const { t } = useI18n();
  const id = useId();
  const granted = usePermissions();
  const [saved, setSaved] = useState<CaseRequests | null>(null);
  const [draft, setDraft] = useState<CaseRequests | null>(null);
  const [editing, setEditing] = useState(false);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    getCaseRequests(caseId, controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) setSaved(found);
      },
      () => {
        if (!controller.signal.aborted) setMessage({ key: 'requests.loadFailed', failed: true });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId]);

  const canEdit = editable && granted.has('CASE_EDIT');
  const save = async () => {
    if (draft === null) return;
    setSaving(true);
    setMessage(null);
    try {
      setSaved(await saveCaseRequests(caseId, draft));
      setEditing(false);
      setMessage({ key: 'requests.saved', failed: false });
    } catch (error) {
      const status = error instanceof ApiError ? error.status : 0;
      setMessage({
        key: status === 400 ? 'requests.invalid' : status === 403 ? 'requests.denied' : status === 422 ? 'requests.readOnly' : 'requests.failed',
        failed: true,
      });
    } finally {
      setSaving(false);
    }
  };

  const list = (items: string[], label: string) => (
    <div className="intake-side">
      <h3>{label}</h3>
      {items.length === 0 ? <p className="case-field-hint">{t('requests.none')}</p> : <ol>{items.map((item, index) => <li key={`${String(index)}-${item}`}>{item}</li>)}</ol>}
    </div>
  );

  return (
    <section className="case-requests" aria-labelledby={`${id}-h`}>
      <h2 id={`${id}-h`}>{t('requests.view.title')}</h2>
      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>
          {t(message.key)}
        </p>
      )}
      {saved === null ? null : editing && draft !== null ? (
        <>
          <RequestItemsEditor value={draft} onChange={setDraft} />
          <div className="case-actions">
            <button type="button" className="primary" disabled={saving} onClick={() => void save()}>
              {t(saving ? 'requests.saving' : 'requests.save')}
            </button>
          </div>
        </>
      ) : (
        <>
          <div className="intake-sides">
            {list(saved.plaintiff, t('requests.sideTitle', { side: t('intake.side.PLAINTIFF') }))}
            {list(saved.defendant, t('requests.sideTitle', { side: t('intake.side.DEFENDANT') }))}
          </div>
          {!canEdit ? null : (
            <button type="button" onClick={() => { setDraft(saved); setEditing(true); setMessage(null); }}>
              {t('requests.edit')}
            </button>
          )}
        </>
      )}
    </section>
  );
}
