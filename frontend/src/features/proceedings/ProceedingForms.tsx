import { useEffect, useState, type ReactNode } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import type { Court } from '../courts/courtsApi';
import { formatDate } from './dates';
import { useFormErrors } from './formErrors';
import {
  createJudgment,
  createProceeding,
  createSpecialDecision,
  DECISION_EFFECTS,
  DECISION_TYPES,
  listCourtsFor,
  OUTCOMES,
  STAGES,
  type DecisionEffect,
  type DecisionType,
  type Judgment,
  type Outcome,
  type Proceeding,
  type Stage,
} from './proceedingsApi';

/** Forms of the judicial stages (LD-019…LD-023). The backend validates; the forms show its answer. */

const blankToNull = (value: string) => (value.trim() === '' ? null : value.trim());

export const numberOf = (p: Pick<Proceeding, 'courtCaseNumber' | 'courtCaseYear'>) =>
  `${p.courtCaseNumber}/${String(p.courtCaseYear)}`;

interface FieldProps {
  id: string;
  label: string;
  error: MessageKey | undefined;
  errorId: string;
  inline?: boolean;
  children: ReactNode;
}

function Field({ id, label, error, errorId, inline = false, children }: FieldProps) {
  const { t } = useI18n();
  return (
    <div className={inline ? 'judicial-field judicial-field-inline' : 'judicial-field'}>
      {inline ? children : null}
      <label htmlFor={id}>{label}</label>
      {inline ? null : children}
      {error === undefined ? null : (
        <span id={errorId} className="judicial-field-error">
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
  onSubmit: () => void;
  onCancel: () => void;
  children: ReactNode;
}

function FormShell({ label, error, saving, onSubmit, onCancel, children }: FormShellProps) {
  const { t } = useI18n();
  return (
    <form
      className="judicial-form"
      aria-label={label}
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
    >
      <h3>{label}</h3>
      {error === null ? null : (
        <p role="alert" className="judicial-alert">
          {t(error)}
        </p>
      )}
      {children}
      <div className="judicial-form-actions">
        <button type="submit" disabled={saving}>
          {saving ? t('proceedings.saving') : t('proceedings.save')}
        </button>
        <button type="button" onClick={onCancel}>
          {t('proceedings.cancel')}
        </button>
      </div>
    </form>
  );
}

/** Runs a save: on success hands over, on failure lets the form show why. */
function useSave(onSaved: () => void, fail: (error: unknown) => void, reset: () => void) {
  const [saving, setSaving] = useState(false);
  const run = (save: () => Promise<unknown>) => {
    setSaving(true);
    reset();
    save().then(onSaved, (error: unknown) => {
      fail(error);
      setSaving(false);
    });
  };
  return { saving, run };
}

type CourtList = { stage: Stage; courts: Court[] } | { stage: Stage; failed: true };

const PROCEEDING_FIELDS = ['courtId', 'courtCaseNumber', 'courtCaseYear', 'filingDate', 'parentProceedingId'] as const;

interface ProceedingFormProps {
  caseId: string;
  proceedings: readonly Proceeding[];
  onSaved: () => void;
  onCancel: () => void;
}

/** Records a proceeding; the parent can only be a judged proceeding of the previous stage (LD-020, LD-021). */
export function ProceedingForm({ caseId, proceedings, onSaved, onCancel }: ProceedingFormProps) {
  const { locale, t } = useI18n();
  const form = useFormErrors<(typeof PROCEEDING_FIELDS)[number]>();
  const { saving, run } = useSave(
    onSaved,
    (error) => {
      form.fail(error, 'proceedings.error.conflict', PROCEEDING_FIELDS);
    },
    form.reset,
  );
  const [stage, setStage] = useState<Stage>('FIRST_INSTANCE');
  const [courtId, setCourtId] = useState('');
  const [number, setNumber] = useState('');
  const [year, setYear] = useState('');
  const [filingDate, setFilingDate] = useState('');
  const [parentId, setParentId] = useState('');
  const [courtList, setCourtList] = useState<CourtList | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listCourtsFor(stage, controller.signal).then(
      (courts) => {
        setCourtList({ stage, courts });
      },
      () => {
        if (!controller.signal.aborted) setCourtList({ stage, failed: true });
      },
    );
    return () => {
      controller.abort();
    };
  }, [stage]);

  const courts = courtList?.stage === stage && 'courts' in courtList ? courtList.courts : [];
  const courtsFailed = courtList?.stage === stage && 'failed' in courtList;
  const parentStage: Stage | null = stage === 'APPEAL' ? 'FIRST_INSTANCE' : stage === 'CASSATION' ? 'APPEAL' : null;
  const parents = proceedings.filter((p) => p.stage === parentStage && p.hasJudgment);

  const submit = () => {
    run(() =>
      createProceeding(caseId, {
        stage,
        courtId: blankToNull(courtId),
        courtCaseNumber: number,
        courtCaseYear: year.trim() === '' ? null : Number(year),
        filingDate: blankToNull(filingDate),
        parentProceedingId: blankToNull(parentId),
      }),
    );
  };

  const field = (name: (typeof PROCEEDING_FIELDS)[number], label: MessageKey, control: ReactNode) => (
    <Field id={form.control(name).id} label={t(label)} error={form.errors[name]} errorId={form.errorId(name)}>
      {control}
    </Field>
  );

  return (
    <FormShell label={t('proceedings.record')} error={form.formError} saving={saving} onSubmit={submit} onCancel={onCancel}>
      <div className="judicial-field">
        <label htmlFor={`${form.control('courtId').id}-stage`}>{t('proceedings.field.stage')}</label>
        <select
          id={`${form.control('courtId').id}-stage`}
          value={stage}
          onChange={(event) => {
            setStage(event.target.value as Stage);
            setCourtId('');
            setParentId('');
          }}
        >
          {STAGES.map((s) => (
            <option key={s} value={s}>
              {t(`proceedings.stage.${s}`)}
            </option>
          ))}
        </select>
      </div>
      {parentStage === null
        ? null
        : parents.length === 0
          ? <p className="judicial-hint">{t(stage === 'APPEAL' ? 'proceedings.needsParent.APPEAL' : 'proceedings.needsParent.CASSATION')}</p>
          : field(
              'parentProceedingId',
              'proceedings.field.parent',
              <select
                {...form.control('parentProceedingId')}
                value={parentId}
                onChange={(event) => {
                  setParentId(event.target.value);
                }}
              >
                <option value="">{t('proceedings.choose')}</option>
                {parents.map((p) => (
                  <option key={p.id} value={p.id}>
                    {t('proceedings.number', { stage: t(`proceedings.stage.${p.stage}`), number: numberOf(p) })}
                  </option>
                ))}
              </select>,
            )}
      {field(
        'courtId',
        'proceedings.field.court',
        <select
          {...form.control('courtId')}
          value={courtId}
          onChange={(event) => {
            setCourtId(event.target.value);
          }}
        >
          <option value="">{t('proceedings.choose')}</option>
          {courts.map((court) => (
            <option key={court.id} value={court.id}>
              {locale === 'ar' ? court.nameAr : court.nameEn}
            </option>
          ))}
        </select>,
      )}
      {courtsFailed ? <p className="judicial-hint">{t('proceedings.courtsUnavailable')}</p> : null}
      {field(
        'courtCaseNumber',
        'proceedings.field.number',
        <input
          {...form.control('courtCaseNumber')}
          type="text"
          dir="ltr"
          value={number}
          onChange={(event) => {
            setNumber(event.target.value);
          }}
        />,
      )}
      {field(
        'courtCaseYear',
        'proceedings.field.year',
        <input
          {...form.control('courtCaseYear')}
          type="text"
          inputMode="numeric"
          dir="ltr"
          value={year}
          onChange={(event) => {
            setYear(event.target.value);
          }}
        />,
      )}
      {field(
        'filingDate',
        'proceedings.field.filingDate',
        <input
          {...form.control('filingDate')}
          type="date"
          value={filingDate}
          onChange={(event) => {
            setFilingDate(event.target.value);
          }}
        />,
      )}
    </FormShell>
  );
}

const JUDGMENT_FIELDS = ['judgmentDate', 'outcome', 'summary'] as const;

interface JudgmentFormProps {
  proceeding: Proceeding;
  onSaved: () => void;
  onCancel: () => void;
}

/** Records the judgment that ends a proceeding (LD-022). */
export function JudgmentForm({ proceeding, onSaved, onCancel }: JudgmentFormProps) {
  const { t } = useI18n();
  const form = useFormErrors<(typeof JUDGMENT_FIELDS)[number]>();
  const { saving, run } = useSave(
    onSaved,
    (error) => {
      form.fail(error, 'proceedings.alreadyJudged', JUDGMENT_FIELDS);
    },
    form.reset,
  );
  const [date, setDate] = useState('');
  const [outcome, setOutcome] = useState<Outcome | ''>('');
  const [isFinal, setFinal] = useState(false);
  const [summary, setSummary] = useState('');

  const submit = () => {
    run(() =>
      createJudgment(proceeding.id, {
        judgmentDate: blankToNull(date),
        outcome: outcome === '' ? null : outcome,
        final: isFinal,
        summary: blankToNull(summary),
      }),
    );
  };

  const field = (name: (typeof JUDGMENT_FIELDS)[number], label: MessageKey, control: ReactNode) => (
    <Field id={form.control(name).id} label={t(label)} error={form.errors[name]} errorId={form.errorId(name)}>
      {control}
    </Field>
  );

  return (
    <FormShell
      label={t('proceedings.recordJudgmentFor', { number: numberOf(proceeding) })}
      error={form.formError}
      saving={saving}
      onSubmit={submit}
      onCancel={onCancel}
    >
      {field(
        'judgmentDate',
        'proceedings.field.judgmentDate',
        <input
          {...form.control('judgmentDate')}
          type="date"
          value={date}
          onChange={(event) => {
            setDate(event.target.value);
          }}
        />,
      )}
      {field(
        'outcome',
        'proceedings.field.outcome',
        <select
          {...form.control('outcome')}
          value={outcome}
          onChange={(event) => {
            setOutcome(event.target.value as Outcome | '');
          }}
        >
          <option value="">{t('proceedings.choose')}</option>
          {OUTCOMES.map((o) => (
            <option key={o} value={o}>
              {t(`proceedings.outcome.${o}`)}
            </option>
          ))}
        </select>,
      )}
      <Field id={`${form.control('outcome').id}-final`} label={t('proceedings.field.final')} error={undefined} errorId="" inline>
        <input
          id={`${form.control('outcome').id}-final`}
          type="checkbox"
          checked={isFinal}
          onChange={(event) => {
            setFinal(event.target.checked);
          }}
        />
      </Field>
      {field(
        'summary',
        'proceedings.field.summary',
        <textarea
          {...form.control('summary')}
          value={summary}
          onChange={(event) => {
            setSummary(event.target.value);
          }}
        />,
      )}
    </FormShell>
  );
}

const DECISION_FIELDS = ['decisionType', 'decisionDate', 'effect', 'judgmentId', 'summary'] as const;

interface DecisionFormProps {
  proceeding: Proceeding;
  judgments: readonly Judgment[];
  proceedingsById: ReadonlyMap<string, Proceeding>;
  onSaved: () => void;
  onCancel: () => void;
}

/** Records a special decision, such as an enforcement suspension of a judgment of the case (LD-023). */
export function DecisionForm({ proceeding, judgments, proceedingsById, onSaved, onCancel }: DecisionFormProps) {
  const { locale, t } = useI18n();
  const form = useFormErrors<(typeof DECISION_FIELDS)[number]>();
  const { saving, run } = useSave(
    onSaved,
    (error) => {
      form.fail(error, 'proceedings.error.save', DECISION_FIELDS);
    },
    form.reset,
  );
  const [type, setType] = useState<DecisionType>('ENFORCEMENT_SUSPENSION');
  const [date, setDate] = useState('');
  const [effect, setEffect] = useState<DecisionEffect | ''>('');
  const [judgmentId, setJudgmentId] = useState('');
  const [summary, setSummary] = useState('');

  const submit = () => {
    run(() =>
      createSpecialDecision(proceeding.id, {
        decisionType: type,
        decisionDate: blankToNull(date),
        effect: effect === '' ? null : effect,
        judgmentId: blankToNull(judgmentId),
        summary: blankToNull(summary),
      }),
    );
  };

  const field = (name: (typeof DECISION_FIELDS)[number], label: MessageKey, control: ReactNode) => (
    <Field id={form.control(name).id} label={t(label)} error={form.errors[name]} errorId={form.errorId(name)}>
      {control}
    </Field>
  );

  const judgmentLabel = (j: Judgment) => {
    const judged = proceedingsById.get(j.proceedingId);
    return t('proceedings.judgmentOption', {
      date: formatDate(j.judgmentDate, locale),
      number: judged === undefined ? '' : numberOf(judged),
    });
  };

  return (
    <FormShell
      label={t('proceedings.recordDecisionFor', { number: numberOf(proceeding) })}
      error={form.formError}
      saving={saving}
      onSubmit={submit}
      onCancel={onCancel}
    >
      {field(
        'decisionType',
        'proceedings.field.decisionType',
        <select
          {...form.control('decisionType')}
          value={type}
          onChange={(event) => {
            setType(event.target.value as DecisionType);
          }}
        >
          {DECISION_TYPES.map((d) => (
            <option key={d} value={d}>
              {t(`proceedings.decisionType.${d}`)}
            </option>
          ))}
        </select>,
      )}
      {field(
        'decisionDate',
        'proceedings.field.decisionDate',
        <input
          {...form.control('decisionDate')}
          type="date"
          value={date}
          onChange={(event) => {
            setDate(event.target.value);
          }}
        />,
      )}
      {field(
        'effect',
        'proceedings.field.effect',
        <select
          {...form.control('effect')}
          value={effect}
          onChange={(event) => {
            setEffect(event.target.value as DecisionEffect | '');
          }}
        >
          <option value="">{t('proceedings.choose')}</option>
          {DECISION_EFFECTS.map((e) => (
            <option key={e} value={e}>
              {t(`proceedings.effect.${e}`)}
            </option>
          ))}
        </select>,
      )}
      {field(
        'judgmentId',
        'proceedings.field.judgment',
        <select
          {...form.control('judgmentId')}
          value={judgmentId}
          onChange={(event) => {
            setJudgmentId(event.target.value);
          }}
        >
          <option value="">{t('proceedings.none')}</option>
          {judgments.map((j) => (
            <option key={j.id} value={j.id}>
              {judgmentLabel(j)}
            </option>
          ))}
        </select>,
      )}
      {field(
        'summary',
        'proceedings.field.summary',
        <textarea
          {...form.control('summary')}
          value={summary}
          onChange={(event) => {
            setSummary(event.target.value);
          }}
        />,
      )}
    </FormShell>
  );
}
