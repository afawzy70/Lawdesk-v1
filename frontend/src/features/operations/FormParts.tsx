import type { ReactNode } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';

/** The field and form frame shared by the legal operations forms. */

interface FieldProps {
  id: string;
  label: string;
  error: MessageKey | undefined;
  errorId: string;
  hint?: string | undefined;
  children: ReactNode;
}

export function Field({ id, label, error, errorId, hint, children }: FieldProps) {
  const { t } = useI18n();
  return (
    <div className="ops-field">
      <label htmlFor={id}>{label}</label>
      {children}
      {hint === undefined ? null : <span className="ops-hint">{hint}</span>}
      {error === undefined ? null : (
        <span id={errorId} className="ops-field-error">
          {t(error)}
        </span>
      )}
    </div>
  );
}

interface FormShellProps {
  label: string;
  error: MessageKey | null;
  saving: boolean;
  submitLabel: string;
  onSubmit: () => void;
  onCancel: () => void;
  children: ReactNode;
}

export function FormShell({ label, error, saving, submitLabel, onSubmit, onCancel, children }: FormShellProps) {
  const { t } = useI18n();
  return (
    <form
      className="ops-form"
      aria-label={label}
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
    >
      <h3>{label}</h3>
      {error === null ? null : (
        <p role="alert" className="ops-alert">
          {t(error)}
        </p>
      )}
      {children}
      <div className="ops-form-actions">
        <button type="submit" disabled={saving}>
          {saving ? t('operations.saving') : submitLabel}
        </button>
        <button type="button" onClick={onCancel}>
          {t('operations.cancel')}
        </button>
      </div>
    </form>
  );
}
