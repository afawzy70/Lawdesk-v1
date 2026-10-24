import { useId, useState, type ReactNode, type SyntheticEvent } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { CASE_TYPES, type NewCase } from './api';
import type { CaseField, FieldErrors } from './problems';
import './cases.css';

/** How a page explains a failed submission: a summary, errors per field, and whether to offer a reload. */
export interface Failure {
  message: MessageKey;
  fields: FieldErrors;
  conflict?: boolean;
}

interface Props {
  initial: NewCase;
  /** Only a new case has an (optional) reference number; it never changes afterwards. */
  withReference: boolean;
  submitLabel: MessageKey;
  onSubmit: (values: NewCase) => Promise<void>;
  explain: (error: unknown) => Failure;
  onReload?: () => void;
}

/**
 * The case fields (LD-012, LD-013). Validation is the server's: its field errors are shown next to each field and
 * announced in a summary. Nothing the server sends is displayed, only the matching translated messages.
 */
export function CaseForm({ initial, withReference, submitLabel, onSubmit, explain, onReload }: Props) {
  const { t } = useI18n();
  const formId = useId();
  const [values, setValues] = useState(initial);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [saving, setSaving] = useState(false);

  const set = (field: keyof NewCase) => (value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
  };

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    if (saving) return;
    setSaving(true);
    setFailure(null);
    try {
      await onSubmit(values);
    } catch (error) {
      setFailure(explain(error));
    } finally {
      setSaving(false);
    }
  };

  const ids = (field: CaseField, hint = false) => {
    const error = failure?.fields[field] === undefined ? [] : [`${formId}-${field}-error`];
    const described = [...error, ...(hint ? [`${formId}-${field}-hint`] : [])].join(' ');
    return {
      id: `${formId}-${field}`,
      'aria-invalid': error.length > 0 ? true : undefined,
      'aria-describedby': described === '' ? undefined : described,
    };
  };

  const field = (name: CaseField, label: MessageKey, control: ReactNode, hint?: MessageKey) => {
    const error = failure?.fields[name];
    return (
      <div className="case-field">
        <label htmlFor={`${formId}-${name}`}>{t(label)}</label>
        {control}
        {error === undefined ? null : (
          <p id={`${formId}-${name}-error`} className="case-field-error">
            {t(error)}
          </p>
        )}
        {hint === undefined ? null : (
          <p id={`${formId}-${name}-hint`} className="case-field-hint">
            {t(hint)}
          </p>
        )}
      </div>
    );
  };

  return (
    <form className="case-form" noValidate onSubmit={(event) => void submit(event)}>
      {failure === null ? null : (
        <div className="case-alert" role="alert">
          <p>{t(failure.message)}</p>
          {failure.conflict === true && onReload !== undefined ? (
            <button type="button" onClick={onReload}>
              {t('cases.edit.reload')}
            </button>
          ) : null}
        </div>
      )}
      {withReference
        ? field(
            'referenceNo',
            'cases.field.referenceNo',
            <input
              type="text"
              maxLength={40}
              value={values.referenceNo}
              onChange={(e) => {
                set('referenceNo')(e.target.value);
              }}
              {...ids('referenceNo', true)}
            />,
            'cases.field.referenceNoHint',
          )
        : null}
      {field(
        'title',
        'cases.field.title',
        <input
          type="text"
          maxLength={200}
          aria-required="true"
          value={values.title}
          onChange={(e) => {
            set('title')(e.target.value);
          }}
          {...ids('title')}
        />,
      )}
      {field(
        'caseType',
        'cases.field.caseType',
        <select
          aria-required="true"
          value={values.caseType}
          onChange={(e) => {
            set('caseType')(e.target.value);
          }}
          {...ids('caseType')}
        >
          <option value="">{t('cases.form.chooseType')}</option>
          {CASE_TYPES.map((type) => (
            <option key={type} value={type}>
              {t(`cases.type.${type}`)}
            </option>
          ))}
        </select>,
      )}
      {field(
        'subject',
        'cases.field.subject',
        <textarea
          rows={4}
          maxLength={4000}
          value={values.subject}
          onChange={(e) => {
            set('subject')(e.target.value);
          }}
          {...ids('subject')}
        />,
      )}
      {field(
        'openedOn',
        'cases.field.openedOn',
        <input
          type="date"
          aria-required="true"
          value={values.openedOn}
          onChange={(e) => {
            set('openedOn')(e.target.value);
          }}
          {...ids('openedOn')}
        />,
      )}
      <div className="case-actions">
        <button type="submit" className="primary" disabled={saving}>
          {t(saving ? 'cases.form.saving' : submitLabel)}
        </button>
      </div>
    </form>
  );
}
