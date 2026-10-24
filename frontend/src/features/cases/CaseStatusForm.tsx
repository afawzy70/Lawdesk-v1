import { useId, useState, type SyntheticEvent } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { changeStatus, NEXT_STATUSES, type CaseStatus, type LegalCase } from './api';
import { isStatus, problemMessage } from './problems';

interface Props {
  legalCase: LegalCase;
  onChanged: () => void;
  onReload: () => void;
}

function explain(error: unknown): MessageKey {
  if (isStatus(error, 422, 'REASON_REQUIRED')) return 'cases.status.reasonRequired';
  if (isStatus(error, 422, 'STATUS_TRANSITION_NOT_ALLOWED')) return 'cases.status.notAllowed';
  if (isStatus(error, 409)) return 'cases.edit.conflict';
  if (isStatus(error, 400)) return 'cases.status.missing';
  return problemMessage(error, 'cases.status.failed');
}

/** LD-017: moves the case to one of the statuses its current status allows. */
export function CaseStatusForm({ legalCase, onChanged, onReload }: Props) {
  const { t } = useI18n();
  const id = useId();
  const [status, setStatus] = useState<CaseStatus | ''>('');
  const [reason, setReason] = useState('');
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setSaving(true);
    setFailure(null);
    try {
      await changeStatus(legalCase.id, status, reason, legalCase.version);
      onChanged();
    } catch (error) {
      setFailure(explain(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="case-form" aria-label={t('cases.status.change')} noValidate onSubmit={(e) => void submit(e)}>
      {failure === null ? null : (
        <div className="case-alert" role="alert">
          <p>{t(failure)}</p>
          {failure === 'cases.edit.conflict' ? (
            <button type="button" onClick={onReload}>
              {t('cases.edit.reload')}
            </button>
          ) : null}
        </div>
      )}
      <div className="case-field">
        <label htmlFor={`${id}-status`}>{t('cases.status.new')}</label>
        <select
          id={`${id}-status`}
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as CaseStatus | '');
          }}
        >
          <option value="">{t('cases.status.choose')}</option>
          {NEXT_STATUSES[legalCase.status].map((next) => (
            <option key={next} value={next}>
              {t(`cases.status.${next}`)}
            </option>
          ))}
        </select>
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-reason`}>{t('cases.status.reason')}</label>
        <textarea
          id={`${id}-reason`}
          rows={2}
          maxLength={500}
          value={reason}
          onChange={(e) => {
            setReason(e.target.value);
          }}
        />
      </div>
      <div className="case-actions">
        <button type="submit" className="primary" disabled={saving}>
          {t(saving ? 'cases.form.saving' : 'cases.status.change')}
        </button>
      </div>
    </form>
  );
}
