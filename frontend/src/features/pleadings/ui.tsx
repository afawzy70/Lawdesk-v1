import { useId, type ReactNode } from 'react';
import { useI18n } from '../../i18n/I18nContext';

/** Small building blocks shared by the claims, defences and evidence sections (LD-051). */

export function Notice({ failed, children }: { failed: boolean; children: ReactNode }) {
  return (
    <p className={failed ? 'case-alert' : 'case-notice'} role={failed ? 'alert' : 'status'}>
      {children}
    </p>
  );
}

interface FieldProps {
  label: string;
  /** True when the backend flagged this field. */
  invalid?: boolean;
  hint?: string;
  children: (id: string, invalid: boolean) => ReactNode;
}

export function Field({ label, invalid = false, hint, children }: FieldProps) {
  const id = useId();
  const { t } = useI18n();
  return (
    <div className="case-field">
      <label htmlFor={id}>{label}</label>
      {children(id, invalid)}
      {hint === undefined ? null : <span className="case-field-hint">{hint}</span>}
      {invalid ? <span className="case-field-error">{t('pl.error.fieldInvalid')}</span> : null}
    </div>
  );
}

interface TextProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: 'text' | 'date';
  max?: number;
  invalid?: boolean;
  dir?: 'ltr';
}

export function TextField({ label, value, onChange, type = 'text', max, invalid, dir }: TextProps) {
  return (
    <Field label={label} invalid={invalid ?? false}>
      {(id, bad) => (
        <input
          id={id}
          type={type}
          maxLength={max}
          dir={dir}
          aria-invalid={bad ? true : undefined}
          value={value}
          onChange={(e) => {
            onChange(e.target.value);
          }}
        />
      )}
    </Field>
  );
}

interface AreaProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  invalid?: boolean;
}

export function AreaField({ label, value, onChange, invalid }: AreaProps) {
  return (
    <Field label={label} invalid={invalid ?? false}>
      {(id, bad) => (
        <textarea
          id={id}
          rows={3}
          aria-invalid={bad ? true : undefined}
          value={value}
          onChange={(e) => {
            onChange(e.target.value);
          }}
        />
      )}
    </Field>
  );
}

interface ChoiceProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: readonly (readonly [string, string])[];
  /** The label of the empty choice; omit when a value is required. */
  empty?: string;
  invalid?: boolean;
}

export function ChoiceField({ label, value, onChange, options, empty, invalid }: ChoiceProps) {
  return (
    <Field label={label} invalid={invalid ?? false}>
      {(id, bad) => (
        <select
          id={id}
          aria-invalid={bad ? true : undefined}
          value={value}
          onChange={(e) => {
            onChange(e.target.value);
          }}
        >
          {empty === undefined ? null : <option value="">{empty}</option>}
          {options.map(([code, text]) => (
            <option key={code} value={code}>
              {text}
            </option>
          ))}
        </select>
      )}
    </Field>
  );
}

export function CheckField({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) {
  const id = useId();
  return (
    <div className="case-field">
      <label htmlFor={id}>
        <input
          id={id}
          type="checkbox"
          checked={checked}
          onChange={(e) => {
            onChange(e.target.checked);
          }}
        />{' '}
        {label}
      </label>
    </div>
  );
}
