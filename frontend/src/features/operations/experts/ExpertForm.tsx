import { useState } from 'react';
import { useI18n } from '../../../i18n/I18nContext';
import { useFormErrors } from '../formErrors';
import { Field, FormShell } from '../FormParts';
import { createExpert, updateExpert, type Expert, type ExpertInput } from './expertsApi';

/** Registers or edits one expert (LD-025). The backend validates; the form shows its answers next to the fields. */

const FIELDS = ['name', 'specialty', 'office', 'phone'] as const;
type ExpertField = (typeof FIELDS)[number];

interface Props {
  expert?: Expert | undefined;
  onSaved: () => void;
  onCancel: () => void;
}

export function ExpertForm({ expert, onSaved, onCancel }: Props) {
  const { t } = useI18n();
  const form = useFormErrors<ExpertField>();
  const [values, setValues] = useState<ExpertInput>({
    name: expert?.name ?? '',
    specialty: expert?.specialty ?? '',
    office: expert?.office ?? '',
    phone: expert?.phone ?? '',
  });
  const [active, setActive] = useState(expert?.active ?? true);
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    setSaving(true);
    try {
      if (expert === undefined) {
        await createExpert(values);
      } else {
        await updateExpert(expert.id, values, active, expert.version);
      }
      onSaved();
    } catch (error) {
      form.fail(error, 'operations.error.stale', FIELDS);
      setSaving(false);
    }
  };

  const text = (field: ExpertField, label: string, maxLength: number, dir?: 'ltr') => (
    <Field id={form.control(field).id} label={label} error={form.errors[field]} errorId={form.errorId(field)}>
      <input
        {...form.control(field)}
        type={field === 'phone' ? 'tel' : 'text'}
        dir={dir}
        maxLength={maxLength}
        value={values[field]}
        onChange={(event) => {
          setValues((previous) => ({ ...previous, [field]: event.target.value }));
        }}
      />
    </Field>
  );

  return (
    <FormShell
      label={expert === undefined ? t('operations.expert.new') : t('operations.expert.editTitle', { name: expert.name })}
      error={form.formError}
      saving={saving}
      submitLabel={t('operations.save')}
      onSubmit={() => {
        void submit();
      }}
      onCancel={onCancel}
    >
      {text('name', t('operations.expert.name'), 200)}
      {text('specialty', t('operations.expert.specialty'), 100)}
      {text('office', t('operations.expert.officeOptional'), 200)}
      {text('phone', t('operations.expert.phoneOptional'), 32, 'ltr')}
      {expert === undefined ? null : (
        <div className="ops-choice">
          <label>
            <input
              type="checkbox"
              checked={active}
              onChange={(event) => {
                setActive(event.target.checked);
              }}
            />
            {t('operations.expert.active')}
          </label>
        </div>
      )}
    </FormShell>
  );
}
