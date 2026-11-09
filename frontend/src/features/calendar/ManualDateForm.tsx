import { useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { useFormErrors } from '../operations/formErrors';
import { Field, FormShell } from '../operations/FormParts';
import { createManualDate, MANUAL_KINDS, type ManualDate, type ManualDateInput } from './api';

const FIELDS = ['kind', 'title', 'notes', 'date', 'startTime', 'endTime'] as const;
type DateField = (typeof FIELDS)[number];

interface Props {
  /** The case the date belongs to; null for a date of the office (a meeting). */
  caseId: string | null;
  initialDate: string;
  onSaved: (date: ManualDate) => void;
  onCancel: () => void;
}

/**
 * A manual calendar date (requirements section 15): a meeting, an investigation date, the date the lawyer set for a
 * memorandum, an appeal or grievance deadline, an enforcement date. Nothing is calculated from a legal period (D6).
 */
export function ManualDateForm({ caseId, initialDate, onSaved, onCancel }: Props) {
  const { t } = useI18n();
  const form = useFormErrors<DateField>();
  const [saving, setSaving] = useState(false);
  const [values, setValues] = useState<ManualDateInput>({
    kind: caseId === null ? 'MEETING' : 'MEMO_DUE',
    title: '',
    notes: '',
    date: initialDate,
    startTime: '',
    endTime: '',
  });
  const set = (field: keyof ManualDateInput) => (value: string) => {
    setValues((previous) => ({ ...previous, [field]: value }));
  };

  const submit = async () => {
    setSaving(true);
    try {
      onSaved(await createManualDate(values, caseId));
    } catch (error) {
      form.fail(error, 'operations.error.stale', FIELDS);
      setSaving(false);
    }
  };

  return (
    <FormShell
      label={t('wp6.calendar.newDate')}
      error={form.formError}
      saving={saving}
      submitLabel={t('wp6.calendar.addDate')}
      onSubmit={() => {
        void submit();
      }}
      onCancel={onCancel}
    >
      <Field id={form.control('kind').id} label={t('wp6.calendar.kind')} error={form.errors.kind} errorId={form.errorId('kind')}>
        <select {...form.control('kind')} value={values.kind} onChange={(event) => { set('kind')(event.target.value); }}>
          {MANUAL_KINDS.map((kind) => (
            <option key={kind} value={kind}>{t(`wp6.calendar.type.${kind}`)}</option>
          ))}
        </select>
      </Field>
      <Field id={form.control('title').id} label={t('wp6.calendar.dateTitle')} error={form.errors.title}
        errorId={form.errorId('title')}>
        <input {...form.control('title')} type="text" required maxLength={200} value={values.title}
          onChange={(event) => { set('title')(event.target.value); }} />
      </Field>
      <Field id={form.control('date').id} label={t('wp6.calendar.date')} error={form.errors.date} errorId={form.errorId('date')}>
        <input {...form.control('date')} type="date" required value={values.date}
          onChange={(event) => { set('date')(event.target.value); }} />
      </Field>
      <Field id={form.control('startTime').id} label={t('wp6.calendar.startTimeOptional')} error={form.errors.startTime}
        errorId={form.errorId('startTime')} hint={t('wp6.calendar.kuwaitTime')}>
        <input {...form.control('startTime')} type="time" value={values.startTime}
          onChange={(event) => { set('startTime')(event.target.value); }} />
      </Field>
      <Field id={form.control('endTime').id} label={t('wp6.calendar.endTimeOptional')} error={form.errors.endTime}
        errorId={form.errorId('endTime')}>
        <input {...form.control('endTime')} type="time" value={values.endTime}
          onChange={(event) => { set('endTime')(event.target.value); }} />
      </Field>
      <Field id={form.control('notes').id} label={t('wp6.calendar.notesOptional')} error={form.errors.notes}
        errorId={form.errorId('notes')}>
        <textarea {...form.control('notes')} rows={2} maxLength={2000} value={values.notes}
          onChange={(event) => { set('notes')(event.target.value); }} />
      </Field>
    </FormShell>
  );
}
