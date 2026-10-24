import { useState } from 'react';
import { useI18n } from '../../../i18n/I18nContext';
import { numberOf } from '../../proceedings/dates';
import type { Proceeding } from '../../proceedings/proceedingsApi';
import { formatDay } from '../days';
import { useFormErrors } from '../formErrors';
import { Field, FormShell } from '../FormParts';
import {
  HEARING_PURPOSES,
  RESULT_STATUSES,
  recordResult,
  rescheduleHearing,
  scheduleHearing,
  type Hearing,
  type HearingInput,
  type ResultInput,
  type ResultStatus,
} from './hearingsApi';

/** Forms of the hearings (LD-024). The backend validates and applies the rules; the forms show its answers. */

const HEARING_FIELDS = ['proceedingId', 'hearingDate', 'hearingTime', 'purpose', 'notes'] as const;
type HearingField = (typeof HEARING_FIELDS)[number];

interface HearingFieldsProps {
  values: HearingInput;
  onChange: (values: HearingInput) => void;
  form: ReturnType<typeof useFormErrors<HearingField>>;
}

/** Date, time, purpose and notes, shared by scheduling and rescheduling. */
function HearingFields({ values, onChange, form }: HearingFieldsProps) {
  const { t } = useI18n();
  const set = (field: keyof HearingInput) => (value: string) => {
    onChange({ ...values, [field]: value });
  };
  const field = (name: HearingField) => ({ ...form.control(name) });
  return (
    <>
      <Field id={field('hearingDate').id} label={t('operations.hearing.date')} error={form.errors.hearingDate}
        errorId={form.errorId('hearingDate')}>
        <input {...field('hearingDate')} type="date" required value={values.hearingDate}
          onChange={(event) => { set('hearingDate')(event.target.value); }} />
      </Field>
      <Field id={field('hearingTime').id} label={t('operations.hearing.timeOptional')} error={form.errors.hearingTime}
        errorId={form.errorId('hearingTime')}>
        <input {...field('hearingTime')} type="time" value={values.hearingTime}
          onChange={(event) => { set('hearingTime')(event.target.value); }} />
      </Field>
      <Field id={field('purpose').id} label={t('operations.hearing.purpose')} error={form.errors.purpose}
        errorId={form.errorId('purpose')}>
        <select {...field('purpose')} required value={values.purpose}
          onChange={(event) => { set('purpose')(event.target.value); }}>
          <option value="">{t('operations.choose')}</option>
          {HEARING_PURPOSES.map((purpose) => (
            <option key={purpose} value={purpose}>{t(`operations.purpose.${purpose}`)}</option>
          ))}
        </select>
      </Field>
      <Field id={field('notes').id} label={t('operations.hearing.notesOptional')} error={form.errors.notes}
        errorId={form.errorId('notes')}>
        <textarea {...field('notes')} rows={3} maxLength={2000} value={values.notes}
          onChange={(event) => { set('notes')(event.target.value); }} />
      </Field>
    </>
  );
}

interface ScheduleProps {
  proceedings: Proceeding[];
  onSaved: (hearing: Hearing) => void;
  onCancel: () => void;
}

/** Schedules a hearing on one of the case's proceedings. */
export function ScheduleHearingForm({ proceedings, onSaved, onCancel }: ScheduleProps) {
  const { t } = useI18n();
  const form = useFormErrors<HearingField>();
  const [proceedingId, setProceedingId] = useState(proceedings.length === 1 ? (proceedings[0]?.id ?? '') : '');
  const [values, setValues] = useState<HearingInput>({ hearingDate: '', hearingTime: '', purpose: '', notes: '' });
  const [saving, setSaving] = useState(false);
  const [missing, setMissing] = useState(false);

  const submit = async () => {
    if (proceedingId === '') {
      setMissing(true);
      return;
    }
    setSaving(true);
    try {
      onSaved(await scheduleHearing(proceedingId, values));
    } catch (error) {
      form.fail(error, 'operations.hearing.error.dayTaken', HEARING_FIELDS);
      setSaving(false);
    }
  };

  const proceedingControl = form.control('proceedingId');
  return (
    <FormShell label={t('operations.hearing.schedule')} error={form.formError} saving={saving}
      submitLabel={t('operations.hearing.scheduleSubmit')} onSubmit={() => { void submit(); }} onCancel={onCancel}>
      <Field id={proceedingControl.id} label={t('operations.hearing.proceeding')}
        error={missing ? 'operations.fieldError.required' : undefined} errorId={form.errorId('proceedingId')}>
        <select id={proceedingControl.id} required value={proceedingId}
          aria-invalid={missing ? true : undefined}
          aria-describedby={missing ? form.errorId('proceedingId') : undefined}
          onChange={(event) => { setMissing(false); setProceedingId(event.target.value); }}>
          <option value="">{t('operations.choose')}</option>
          {proceedings.map((p) => (
            <option key={p.id} value={p.id}>
              {t('operations.hearing.proceedingOption', {
                stage: t(`proceedings.stage.${p.stage}`),
                number: numberOf(p),
                court: p.courtCode,
              })}
            </option>
          ))}
        </select>
      </Field>
      <HearingFields values={values} onChange={setValues} form={form} />
    </FormShell>
  );
}

interface HearingProps {
  hearing: Hearing;
  onSaved: (hearing: Hearing) => void;
  onCancel: () => void;
}

/** Moves a scheduled hearing or changes its purpose and notes. */
export function RescheduleHearingForm({ hearing, onSaved, onCancel }: HearingProps) {
  const { locale, t } = useI18n();
  const form = useFormErrors<HearingField>();
  const [values, setValues] = useState<HearingInput>({
    hearingDate: hearing.hearingDate,
    hearingTime: hearing.hearingTime ?? '',
    purpose: hearing.purpose,
    notes: hearing.notes ?? '',
  });
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    setSaving(true);
    try {
      onSaved(await rescheduleHearing(hearing.id, values, hearing.version));
    } catch (error) {
      form.fail(error, 'operations.error.stale', HEARING_FIELDS);
      setSaving(false);
    }
  };

  return (
    <FormShell
      label={t('operations.hearing.rescheduleTitle', {
        date: formatDay(hearing.hearingDate, locale),
        reference: hearing.caseReferenceNo,
      })}
      error={form.formError} saving={saving} submitLabel={t('operations.save')}
      onSubmit={() => { void submit(); }} onCancel={onCancel}>
      <HearingFields values={values} onChange={setValues} form={form} />
    </FormShell>
  );
}

const RESULT_FIELDS = ['status', 'decision', 'nextHearingDate', 'nextHearingTime', 'nextHearingPurpose'] as const;
type ResultField = (typeof RESULT_FIELDS)[number];

/** Records what happened at a hearing; an adjournment asks for the next hearing. */
export function HearingResultForm({ hearing, onSaved, onCancel }: HearingProps) {
  const { locale, t } = useI18n();
  const form = useFormErrors<ResultField>();
  const [values, setValues] = useState<ResultInput>({
    status: 'HELD',
    decision: '',
    nextHearingDate: '',
    nextHearingTime: '',
    nextHearingPurpose: '',
    version: hearing.version,
  });
  const [saving, setSaving] = useState(false);
  const set = (field: keyof ResultInput) => (value: string) => {
    setValues((previous) => ({ ...previous, [field]: value }));
  };

  const submit = async () => {
    setSaving(true);
    try {
      onSaved(await recordResult(hearing.id, values));
    } catch (error) {
      form.fail(error, 'operations.hearing.error.resultConflict', RESULT_FIELDS);
      setSaving(false);
    }
  };

  const statusControl = form.control('status');
  return (
    <FormShell
      label={t('operations.hearing.resultTitle', {
        date: formatDay(hearing.hearingDate, locale),
        reference: hearing.caseReferenceNo,
      })}
      error={form.formError} saving={saving} submitLabel={t('operations.hearing.resultSubmit')}
      onSubmit={() => { void submit(); }} onCancel={onCancel}>
      <fieldset className="ops-choice" aria-describedby={statusControl['aria-describedby']}>
        <legend>{t('operations.hearing.result')}</legend>
        {RESULT_STATUSES.map((status: ResultStatus) => (
          <label key={status}>
            <input type="radio" name={statusControl.id} value={status} checked={values.status === status}
              onChange={() => { set('status')(status); }} />
            {t(`operations.hearingStatus.${status}`)}
          </label>
        ))}
      </fieldset>
      <Field id={form.control('decision').id} label={t('operations.hearing.decisionOptional')}
        error={form.errors.decision} errorId={form.errorId('decision')}>
        <textarea {...form.control('decision')} rows={3} maxLength={2000} value={values.decision}
          onChange={(event) => { set('decision')(event.target.value); }} />
      </Field>
      {values.status === 'ADJOURNED' ? (
        <>
          <Field id={form.control('nextHearingDate').id} label={t('operations.hearing.nextDate')}
            error={form.errors.nextHearingDate} errorId={form.errorId('nextHearingDate')}>
            <input {...form.control('nextHearingDate')} type="date" required value={values.nextHearingDate}
              onChange={(event) => { set('nextHearingDate')(event.target.value); }} />
          </Field>
          <Field id={form.control('nextHearingTime').id} label={t('operations.hearing.nextTimeOptional')}
            error={form.errors.nextHearingTime} errorId={form.errorId('nextHearingTime')}>
            <input {...form.control('nextHearingTime')} type="time" value={values.nextHearingTime}
              onChange={(event) => { set('nextHearingTime')(event.target.value); }} />
          </Field>
          <Field id={form.control('nextHearingPurpose').id} label={t('operations.hearing.nextPurpose')}
            error={form.errors.nextHearingPurpose} errorId={form.errorId('nextHearingPurpose')}>
            <select {...form.control('nextHearingPurpose')} value={values.nextHearingPurpose}
              onChange={(event) => { set('nextHearingPurpose')(event.target.value); }}>
              <option value="">{t('operations.hearing.samePurpose')}</option>
              {HEARING_PURPOSES.map((purpose) => (
                <option key={purpose} value={purpose}>{t(`operations.purpose.${purpose}`)}</option>
              ))}
            </select>
          </Field>
        </>
      ) : null}
    </FormShell>
  );
}
