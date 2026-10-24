import { useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { COURT_LEVELS, createCourt, updateCourt, type Court, type CourtInput } from './courtsApi';

type Field = keyof CourtInput;
const FIELDS: readonly Field[] = ['code', 'nameAr', 'nameEn', 'level', 'governorate'];

/** The backend names the failed constraint (architecture/CONTRACTS.md §4); the form explains it. */
function fieldMessage(field: Field, code: string): MessageKey {
  switch (code) {
    case 'NotBlank':
    case 'NotNull':
      return 'courts.field.required';
    case 'Size':
      return 'courts.field.tooLong';
    case 'Pattern':
      return field === 'code' ? 'courts.field.codeFormat' : 'courts.field.invalid';
    default:
      return 'courts.field.invalid';
  }
}

function saveError(error: unknown, editing: boolean): MessageKey {
  if (!(error instanceof ApiError)) return 'courts.error.save';
  switch (error.status) {
    case 400:
      return 'courts.error.fields';
    case 401:
      return 'courts.error.signIn';
    case 403:
      return 'courts.error.manageForbidden';
    case 409:
      return editing ? 'courts.error.stale' : 'courts.error.duplicate';
    default:
      return 'courts.error.save';
  }
}

interface Props {
  /** The court being edited; without one the form creates a court. */
  court?: Court | undefined;
  onSaved: (court: Court) => void;
  onCancel: () => void;
}

/** Create or edit one court (LD-018). Validation is the backend's; the form shows its answer per field. */
export function CourtForm({ court, onSaved, onCancel }: Props) {
  const { t } = useI18n();
  const id = useId();
  const [values, setValues] = useState<CourtInput>({
    code: court?.code ?? '',
    nameAr: court?.nameAr ?? '',
    nameEn: court?.nameEn ?? '',
    level: court?.level ?? '',
    governorate: court?.governorate ?? '',
  });
  const [active, setActive] = useState(court?.active ?? true);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<Field, MessageKey>>>({});
  const [formError, setFormError] = useState<MessageKey | null>(null);
  const [saving, setSaving] = useState(false);

  const set = (field: Field) => (value: string) => {
    setValues((previous) => ({ ...previous, [field]: value }));
  };

  const submit = async () => {
    setSaving(true);
    setFormError(null);
    setFieldErrors({});
    const input: CourtInput = { ...values, governorate: values.governorate === '' ? null : values.governorate };
    try {
      const saved =
        court === undefined
          ? await createCourt(input)
          : await updateCourt(court.id, { ...input, active, version: court.version });
      onSaved(saved);
    } catch (error) {
      const errors: Partial<Record<Field, MessageKey>> = {};
      if (error instanceof ApiError) {
        for (const { field, code } of error.problem?.errors ?? []) {
          if ((FIELDS as readonly string[]).includes(field)) {
            const known = field as Field;
            errors[known] ??= fieldMessage(known, code);
          }
        }
      }
      setFieldErrors(errors);
      setFormError(saveError(error, court !== undefined));
      setSaving(false);
    }
  };

  const fieldProps = (field: Field) => {
    const error = fieldErrors[field];
    return {
      id: `${id}-${field}`,
      'aria-invalid': error === undefined ? undefined : true,
      'aria-describedby': error === undefined ? undefined : `${id}-${field}-error`,
    };
  };

  const errorText = (field: Field) => {
    const error = fieldErrors[field];
    return error === undefined ? null : (
      <span id={`${id}-${field}-error`} className="court-field-error">
        {t(error)}
      </span>
    );
  };

  const text = (field: 'code' | 'nameAr' | 'nameEn' | 'governorate', label: MessageKey, dir?: 'rtl' | 'ltr') => (
    <div className="court-field">
      <label htmlFor={`${id}-${field}`}>{t(label)}</label>
      <input
        {...fieldProps(field)}
        type="text"
        dir={dir}
        value={values[field] ?? ''}
        onChange={(event) => {
          set(field)(event.target.value);
        }}
      />
      {errorText(field)}
    </div>
  );

  const title = court === undefined ? t('courts.new') : t('courts.editTitle', { code: court.code });

  return (
    <form
      className="court-form"
      aria-label={title}
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <h2>{title}</h2>
      {formError === null ? null : (
        <p role="alert" className="court-alert">
          {t(formError)}
        </p>
      )}
      {text('code', 'courts.code', 'ltr')}
      {text('nameAr', 'courts.nameAr', 'rtl')}
      {text('nameEn', 'courts.nameEn', 'ltr')}
      <div className="court-field">
        <label htmlFor={`${id}-level`}>{t('courts.level')}</label>
        <select
          {...fieldProps('level')}
          value={values.level}
          onChange={(event) => {
            set('level')(event.target.value);
          }}
        >
          <option value="">{t('courts.chooseLevel')}</option>
          {COURT_LEVELS.map((level) => (
            <option key={level} value={level}>
              {t(`courts.level.${level}`)}
            </option>
          ))}
        </select>
        {errorText('level')}
      </div>
      {text('governorate', 'courts.governorate')}
      {court === undefined ? null : (
        <div className="court-field court-field-inline">
          <input
            id={`${id}-active`}
            type="checkbox"
            checked={active}
            onChange={(event) => {
              setActive(event.target.checked);
            }}
          />
          <label htmlFor={`${id}-active`}>{t('courts.active')}</label>
        </div>
      )}
      <div className="court-form-actions">
        <button type="submit" disabled={saving}>
          {saving ? t('courts.saving') : t('courts.save')}
        </button>
        <button type="button" onClick={onCancel}>
          {t('courts.cancel')}
        </button>
      </div>
    </form>
  );
}
