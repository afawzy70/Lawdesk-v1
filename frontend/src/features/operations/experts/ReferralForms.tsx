import { useEffect, useState } from 'react';
import { useI18n } from '../../../i18n/I18nContext';
import { numberOf } from '../../proceedings/dates';
import type { Proceeding } from '../../proceedings/proceedingsApi';
import { useFormErrors } from '../formErrors';
import { Field, FormShell } from '../FormParts';
import {
  listExperts,
  recordSession,
  referToExpert,
  submitReport,
  type Expert,
  type Referral,
  type ReferralInput,
  type SessionInput,
} from './expertsApi';

/** The forms of the expert referrals (LD-025). The backend validates and applies the rules; the forms show its answers. */

const REFERRAL_FIELDS = ['proceedingId', 'expertId', 'referralDate', 'reportDueDate', 'mission'] as const;
type ReferralField = (typeof REFERRAL_FIELDS)[number];

interface ReferProps {
  proceedings: Proceeding[];
  onSaved: () => void;
  onCancel: () => void;
}

/** Refers one of the case's proceedings to an active expert of the registry. */
export function ReferralForm({ proceedings, onSaved, onCancel }: ReferProps) {
  const { t } = useI18n();
  const form = useFormErrors<ReferralField>();
  const [proceedingId, setProceedingId] = useState(proceedings[0]?.id ?? '');
  const [values, setValues] = useState<ReferralInput>({ expertId: '', referralDate: '', reportDueDate: '', mission: '' });
  const [experts, setExperts] = useState<Expert[]>([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    listExperts({ active: true, page: 0, size: 100 }, controller.signal).then(
      (page) => {
        setExperts(page.items);
      },
      () => {
        // Without the registry the form still opens; the backend then names the missing expert.
      },
    );
    return () => {
      controller.abort();
    };
  }, []);

  const set = (field: keyof ReferralInput) => (value: string) => {
    setValues((previous) => ({ ...previous, [field]: value }));
  };
  const submit = async () => {
    setSaving(true);
    try {
      await referToExpert(proceedingId, values);
      onSaved();
    } catch (error) {
      form.fail(error, 'operations.error.stale', REFERRAL_FIELDS);
      setSaving(false);
    }
  };
  const date = (field: 'referralDate' | 'reportDueDate', label: string) => (
    <Field id={form.control(field).id} label={label} error={form.errors[field]} errorId={form.errorId(field)}>
      <input
        {...form.control(field)}
        type="date"
        value={values[field]}
        onChange={(event) => {
          set(field)(event.target.value);
        }}
      />
    </Field>
  );

  return (
    <FormShell
      label={t('operations.referral.new')}
      error={form.formError}
      saving={saving}
      submitLabel={t('operations.referral.refer')}
      onSubmit={() => {
        void submit();
      }}
      onCancel={onCancel}
    >
      <Field id={form.control('proceedingId').id} label={t('operations.hearing.proceeding')} error={undefined}
        errorId={form.errorId('proceedingId')}>
        <select
          {...form.control('proceedingId')}
          value={proceedingId}
          onChange={(event) => {
            setProceedingId(event.target.value);
          }}
        >
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
      <Field id={form.control('expertId').id} label={t('operations.referral.expert')} error={form.errors.expertId}
        errorId={form.errorId('expertId')}>
        <select
          {...form.control('expertId')}
          value={values.expertId}
          onChange={(event) => {
            set('expertId')(event.target.value);
          }}
        >
          <option value="">{t('operations.choose')}</option>
          {experts.map((e) => (
            <option key={e.id} value={e.id}>
              {t('operations.referral.expertOption', { name: e.name, specialty: e.specialty })}
            </option>
          ))}
        </select>
      </Field>
      {date('referralDate', t('operations.referral.date'))}
      {date('reportDueDate', t('operations.referral.dueOptional'))}
      <Field id={form.control('mission').id} label={t('operations.referral.missionOptional')}
        error={form.errors.mission} errorId={form.errorId('mission')}>
        <textarea
          {...form.control('mission')}
          rows={3}
          maxLength={4000}
          value={values.mission}
          onChange={(event) => {
            set('mission')(event.target.value);
          }}
        />
      </Field>
    </FormShell>
  );
}

const SESSION_FIELDS = ['sessionDate', 'sessionTime', 'location', 'notes'] as const;
type SessionField = (typeof SESSION_FIELDS)[number];

interface ReferralProps {
  referral: Referral;
  onSaved: () => void;
  onCancel: () => void;
}

/** Records one session of the expert (محضر أعمال الخبير). */
export function SessionForm({ referral, onSaved, onCancel }: ReferralProps) {
  const { t } = useI18n();
  const form = useFormErrors<SessionField>();
  const [values, setValues] = useState<SessionInput>({ sessionDate: '', sessionTime: '', location: '', notes: '' });
  const [saving, setSaving] = useState(false);
  const field = (name: SessionField, label: string, type: 'date' | 'time' | 'text', maxLength?: number) => (
    <Field id={form.control(name).id} label={label} error={form.errors[name]} errorId={form.errorId(name)}>
      <input
        {...form.control(name)}
        type={type}
        maxLength={maxLength}
        value={values[name]}
        onChange={(event) => {
          setValues((previous) => ({ ...previous, [name]: event.target.value }));
        }}
      />
    </Field>
  );
  const submit = async () => {
    setSaving(true);
    try {
      await recordSession(referral.id, values);
      onSaved();
    } catch (error) {
      form.fail(error, 'operations.error.stale', SESSION_FIELDS);
      setSaving(false);
    }
  };
  return (
    <FormShell
      label={t('operations.referral.sessionTitle')}
      error={form.formError}
      saving={saving}
      submitLabel={t('operations.save')}
      onSubmit={() => {
        void submit();
      }}
      onCancel={onCancel}
    >
      {field('sessionDate', t('operations.referral.sessionDate'), 'date')}
      {field('sessionTime', t('operations.hearing.timeOptional'), 'time')}
      {field('location', t('operations.referral.placeOptional'), 'text', 200)}
      <Field id={form.control('notes').id} label={t('operations.referral.notesOptional')} error={form.errors.notes}
        errorId={form.errorId('notes')}>
        <textarea
          {...form.control('notes')}
          rows={3}
          maxLength={2000}
          value={values.notes}
          onChange={(event) => {
            setValues((previous) => ({ ...previous, notes: event.target.value }));
          }}
        />
      </Field>
    </FormShell>
  );
}

/** Records the day the expert's report was filed with the court. */
export function ReportForm({ referral, onSaved, onCancel }: ReferralProps) {
  const { t } = useI18n();
  const form = useFormErrors<'reportSubmittedDate'>();
  const [date, setDate] = useState('');
  const [saving, setSaving] = useState(false);
  const submit = async () => {
    setSaving(true);
    try {
      await submitReport(referral, date);
      onSaved();
    } catch (error) {
      form.fail(error, 'operations.error.stale', ['reportSubmittedDate']);
      setSaving(false);
    }
  };
  return (
    <FormShell
      label={t('operations.referral.reportTitle')}
      error={form.formError}
      saving={saving}
      submitLabel={t('operations.save')}
      onSubmit={() => {
        void submit();
      }}
      onCancel={onCancel}
    >
      <Field id={form.control('reportSubmittedDate').id} label={t('operations.referral.reportDate')}
        error={form.errors.reportSubmittedDate} errorId={form.errorId('reportSubmittedDate')}>
        <input
          {...form.control('reportSubmittedDate')}
          type="date"
          value={date}
          onChange={(event) => {
            setDate(event.target.value);
          }}
        />
      </Field>
    </FormShell>
  );
}
