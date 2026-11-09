import { type SyntheticEvent, useId, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate, todayIso } from '../cases/format';
import type { MemberRef } from '../cases/api';
import type { CaseDocument } from '../operations/documents/documentsApi';
import {
  EMPTY_DEADLINE,
  SOURCE_TYPES,
  type Deadline,
  type DeadlineEdit,
  type DeadlineRule,
  type NewDeadline,
  type SourceOption,
  type SourceType,
} from './api';

interface FieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: 'text' | 'date' | 'number';
  max?: number;
}

function Field({ label, value, onChange, type = 'text', max }: FieldProps) {
  const id = useId();
  return (
    <div className="case-field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type={type}
        value={value}
        {...(max === undefined ? {} : { maxLength: max })}
        {...(type === 'number' ? { min: 1, max: 3650 } : {})}
        onChange={(e) => { onChange(e.target.value); }}
      />
    </div>
  );
}

function MemberField({ label, value, members, onChange }: { label: string; value: string; members: MemberRef[]; onChange: (value: string) => void }) {
  const { t } = useI18n();
  const id = useId();
  return (
    <div className="case-field">
      <label htmlFor={id}>{label}</label>
      <select id={id} value={value} onChange={(e) => { onChange(e.target.value); }}>
        <option value="">{t('wp3.notSpecified')}</option>
        {members.map((m) => (
          <option key={m.id} value={m.id}>{m.displayName}</option>
        ))}
      </select>
    </div>
  );
}

interface NewProps {
  members: MemberRef[];
  rules: DeadlineRule[];
  sources: (type: SourceType) => SourceOption[] | null;
  onChooseType: (type: SourceType) => void;
  /** Resolve to true when the server accepted the deadline: only then is the form emptied. */
  onManual: (input: NewDeadline) => Promise<boolean>;
  onPropose: (input: NewDeadline, ruleId: string) => Promise<boolean>;
}

/**
 * A new deadline: typed by the user (manual, awaiting approval) or, only when an approved rule exists, proposed by the system
 * from that rule and a start date. Nothing is computed here; the answer of the server is shown.
 */
export function NewDeadlineForm({ members, rules, sources, onChooseType, onManual, onPropose }: NewProps) {
  const { t, locale } = useI18n();
  const id = useId();
  const [input, setInput] = useState<NewDeadline>(EMPTY_DEADLINE);
  const [mode, setMode] = useState<'manual' | 'propose'>('manual');
  const [ruleId, setRuleId] = useState('');
  const [invalid, setInvalid] = useState(false);
  const set = (field: keyof NewDeadline) => (value: string) => { setInput({ ...input, [field]: value }); };
  const options = input.sourceType === 'TEXT' ? [] : sources(input.sourceType);

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    const sourceMissing = input.sourceType === 'TEXT' ? input.sourceText.trim() === '' : input.sourceId === '';
    const timing =
      mode === 'propose' ? input.startsOn === '' || ruleId === '' : input.dueOn === '' && input.periodDays.trim() === '';
    if (input.action.trim() === '' || sourceMissing || timing) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    const saved = mode === 'propose' ? await onPropose(input, ruleId) : await onManual(input);
    if (saved) setInput(EMPTY_DEADLINE);
  };

  return (
    <form className="case-form" aria-label={t('wp4.new')} noValidate onSubmit={(e) => void submit(e)}>
      <h3>{t('wp4.new')}</h3>
      {rules.length === 0 ? (
        <p className="case-field-hint">{t('wp4.noRules')}</p>
      ) : (
        <fieldset className="case-fieldset">
          <legend>{t('wp4.mode')}</legend>
          <label>
            <input type="radio" name={`${id}-mode`} checked={mode === 'manual'} onChange={() => { setMode('manual'); }} />{' '}
            {t('wp4.mode.manual')}
          </label>{' '}
          <label>
            <input type="radio" name={`${id}-mode`} checked={mode === 'propose'} onChange={() => { setMode('propose'); }} />{' '}
            {t('wp4.mode.propose')}
          </label>
        </fieldset>
      )}
      <div className="case-field">
        <label htmlFor={`${id}-type`}>{t('wp4.field.sourceType')}</label>
        <select
          id={`${id}-type`}
          value={input.sourceType}
          onChange={(e) => {
            const type = SOURCE_TYPES.find((s) => s === e.target.value) ?? 'TEXT';
            setInput({ ...input, sourceType: type, sourceId: '' });
            onChooseType(type);
          }}
        >
          {SOURCE_TYPES.map((s) => (
            <option key={s} value={s}>{t(`wp4.source.${s}`)}</option>
          ))}
        </select>
      </div>
      {input.sourceType === 'TEXT' ? (
        <Field label={t('wp4.field.sourceText')} value={input.sourceText} onChange={set('sourceText')} max={500} />
      ) : (
        <div className="case-field">
          <label htmlFor={`${id}-source`}>{t('wp4.field.source')}</label>
          <select id={`${id}-source`} value={input.sourceId} onChange={(e) => { set('sourceId')(e.target.value); }}>
            <option value="">{t('wp3.choose')}</option>
            {(options ?? []).map((o) => (
              <option key={o.id} value={o.id}>{o.isDate ? formatDate(o.label, locale) : o.label}</option>
            ))}
          </select>
        </div>
      )}
      <Field label={t('wp4.field.action')} value={input.action} onChange={set('action')} max={500} />
      <Field label={t('wp4.field.startsOn')} value={input.startsOn} onChange={set('startsOn')} type="date" />
      {mode === 'propose' ? (
        <div className="case-field">
          <label htmlFor={`${id}-rule`}>{t('wp4.field.rule')}</label>
          <select id={`${id}-rule`} value={ruleId} onChange={(e) => { setRuleId(e.target.value); }}>
            <option value="">{t('wp3.choose')}</option>
            {rules.map((r) => (
              <option key={r.id} value={r.id}>{r.code} — {r.label}</option>
            ))}
          </select>
          <p className="case-field-hint">{t('wp4.proposeHint')}</p>
        </div>
      ) : (
        <>
          <Field label={t('wp4.field.periodDays')} value={input.periodDays} onChange={set('periodDays')} type="number" />
          <Field label={t('wp4.field.dueOn')} value={input.dueOn} onChange={set('dueOn')} type="date" />
          <p className="case-field-hint">{t('wp4.manualHint')}</p>
        </>
      )}
      <MemberField label={t('wp4.field.owner')} value={input.ownerId} members={members} onChange={set('ownerId')} />
      <Field label={t('wp4.field.notes')} value={input.notes} onChange={set('notes')} max={2000} />
      {invalid ? <p className="case-alert" role="alert">{t('wp3.error.fields')}</p> : null}
      <div className="case-actions">
        <button type="submit" className="primary">{mode === 'propose' ? t('wp4.propose') : t('wp4.record')}</button>
      </div>
    </form>
  );
}

interface EditProps {
  deadline: Deadline;
  members: MemberRef[];
  onSave: (edit: DeadlineEdit) => Promise<void>;
  onCancel: () => void;
}

/** Edits an open deadline; changing the dates of a proposed or approved one needs a reason and sends it back for approval. */
export function EditDeadlineForm({ deadline, members, onSave, onCancel }: EditProps) {
  const { t } = useI18n();
  const [edit, setEdit] = useState<DeadlineEdit>({
    startsOn: deadline.startsOn ?? '',
    periodDays: deadline.periodDays === null ? '' : String(deadline.periodDays),
    dueOn: deadline.dueOn ?? '',
    action: deadline.action,
    ownerId: deadline.ownerId ?? '',
    notes: deadline.notes ?? '',
    changeReason: '',
  });
  const [invalid, setInvalid] = useState(false);
  const set = (field: keyof DeadlineEdit) => (value: string) => { setEdit({ ...edit, [field]: value }); };
  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    if (edit.action.trim() === '' || (edit.dueOn === '' && edit.periodDays.trim() === '')) {
      setInvalid(true);
      return;
    }
    await onSave(edit);
  };
  return (
    <form className="case-form" aria-label={t('wp4.edit')} noValidate onSubmit={(e) => void submit(e)}>
      <Field label={t('wp4.field.action')} value={edit.action} onChange={set('action')} max={500} />
      <Field label={t('wp4.field.startsOn')} value={edit.startsOn} onChange={set('startsOn')} type="date" />
      <Field label={t('wp4.field.periodDays')} value={edit.periodDays} onChange={set('periodDays')} type="number" />
      <Field label={t('wp4.field.dueOn')} value={edit.dueOn} onChange={set('dueOn')} type="date" />
      <MemberField label={t('wp4.field.owner')} value={edit.ownerId} members={members} onChange={set('ownerId')} />
      <Field label={t('wp4.field.notes')} value={edit.notes} onChange={set('notes')} max={2000} />
      {deadline.calcStatus === 'MANUAL' ? null : (
        <Field label={t('wp4.field.changeReason')} value={edit.changeReason} onChange={set('changeReason')} max={1000} />
      )}
      {invalid ? <p className="case-alert" role="alert">{t('wp3.error.fields')}</p> : null}
      <div className="case-actions">
        <button type="submit" className="primary">{t('wp4.save')}</button>
        <button type="button" onClick={onCancel}>{t('wp4.cancel')}</button>
      </div>
    </form>
  );
}

interface CompleteProps {
  documents: CaseDocument[];
  onComplete: (completedOn: string, proofDocumentId: string) => Promise<void>;
  onCancel: () => void;
}

/** Completion is recorded by a user, with the date and (optionally) the proof document of the case. */
export function CompleteDeadlineForm({ documents, onComplete, onCancel }: CompleteProps) {
  const { t } = useI18n();
  const id = useId();
  const [completedOn, setCompletedOn] = useState(todayIso());
  const [proof, setProof] = useState('');
  return (
    <form
      className="case-form"
      aria-label={t('wp4.complete')}
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        void onComplete(completedOn, proof);
      }}
    >
      <Field label={t('wp4.field.completedOn')} value={completedOn} onChange={setCompletedOn} type="date" />
      <div className="case-field">
        <label htmlFor={`${id}-proof`}>{t('wp4.field.proof')}</label>
        <select id={`${id}-proof`} value={proof} onChange={(e) => { setProof(e.target.value); }}>
          <option value="">{t('wp3.notSpecified')}</option>
          {documents.map((d) => (
            <option key={d.id} value={d.id}>{d.title}</option>
          ))}
        </select>
      </div>
      <div className="case-actions">
        <button type="submit" className="primary">{t('wp4.complete')}</button>
        <button type="button" onClick={onCancel}>{t('wp4.cancel')}</button>
      </div>
    </form>
  );
}
