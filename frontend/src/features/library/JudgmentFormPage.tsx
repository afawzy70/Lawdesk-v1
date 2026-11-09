import { type SyntheticEvent, useEffect, useId, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { todayIso } from '../cases/format';
import {
  CASE_TYPES,
  CONFIDENTIALITIES,
  COURT_LEVELS,
  DIMENSIONS,
  DIRECTIONS,
  FINALITIES,
  getJudgment,
  listClassifications,
  registerJudgment,
  replaceJudgment,
  type Classification,
  type Judgment,
  type JudgmentInput,
} from './api';
import { classificationLabel, courtLevelKey, dimensionKey, directionKey, finalityKey } from './labels';
import './library.css';

type Draft = {
  courtName: string;
  courtLevel: string;
  chamber: string;
  caseNumber: string;
  caseYear: string;
  judgmentDate: string;
  caseType: string;
  subject: string;
  partiesDescription: string;
  summary: string;
  operativeText: string;
  reasons: string;
  legalTexts: string;
  requestsAddressed: string;
  requestResults: string;
  direction: string;
  keywords: string;
  finality: string;
  confidentiality: string;
  sourceReference: string;
  verificationNote: string;
  copyReference: string;
  classificationIds: string[];
};

const EMPTY: Draft = {
  courtName: '',
  courtLevel: '',
  chamber: '',
  caseNumber: '',
  caseYear: '',
  judgmentDate: '',
  caseType: '',
  subject: '',
  partiesDescription: '',
  summary: '',
  operativeText: '',
  reasons: '',
  legalTexts: '',
  requestsAddressed: '',
  requestResults: '',
  direction: '',
  keywords: '',
  finality: 'NOT_SPECIFIED',
  confidentiality: 'NORMAL',
  sourceReference: '',
  verificationNote: '',
  copyReference: '',
  classificationIds: [],
};

const orNull = (value: string): string | null => (value.trim() === '' ? null : value.trim());

function fromJudgment(j: Judgment): Draft {
  return {
    courtName: j.courtName,
    courtLevel: j.courtLevel ?? '',
    chamber: j.chamber ?? '',
    caseNumber: j.caseNumber ?? '',
    caseYear: j.caseYear === null ? '' : String(j.caseYear),
    judgmentDate: j.judgmentDate ?? '',
    caseType: j.caseType ?? '',
    subject: j.subject,
    partiesDescription: j.partiesDescription ?? '',
    summary: j.summary ?? '',
    operativeText: j.operativeText ?? '',
    reasons: j.reasons ?? '',
    legalTexts: j.legalTexts ?? '',
    requestsAddressed: j.requestsAddressed ?? '',
    requestResults: j.requestResults ?? '',
    direction: j.direction ?? '',
    keywords: j.keywords.join('\n'),
    finality: j.finality,
    confidentiality: j.confidentiality,
    sourceReference: j.sourceReference,
    verificationNote: j.verificationNote,
    copyReference: j.copyReference ?? '',
    classificationIds: j.classifications.map((c) => c.id),
  };
}

function toInput(d: Draft, version?: number, allowDuplicate?: boolean): JudgmentInput {
  return {
    courtName: d.courtName.trim(),
    courtLevel: orNull(d.courtLevel) as JudgmentInput['courtLevel'],
    chamber: orNull(d.chamber),
    caseNumber: orNull(d.caseNumber),
    caseYear: d.caseYear.trim() === '' ? null : Number(d.caseYear),
    judgmentDate: orNull(d.judgmentDate),
    caseType: orNull(d.caseType),
    subject: d.subject.trim(),
    partiesDescription: orNull(d.partiesDescription),
    summary: orNull(d.summary),
    operativeText: orNull(d.operativeText),
    reasons: orNull(d.reasons),
    legalTexts: orNull(d.legalTexts),
    requestsAddressed: orNull(d.requestsAddressed),
    requestResults: orNull(d.requestResults),
    direction: orNull(d.direction) as JudgmentInput['direction'],
    keywords: d.keywords.split('\n').map((k) => k.trim()).filter((k) => k !== ''),
    finality: d.finality as JudgmentInput['finality'],
    confidentiality: d.confidentiality as JudgmentInput['confidentiality'],
    sourceReference: d.sourceReference.trim(),
    verificationNote: d.verificationNote.trim(),
    copyReference: orNull(d.copyReference),
    classificationIds: d.classificationIds,
    ...(version === undefined ? {} : { version }),
    ...(allowDuplicate === true ? { allowDuplicate: true } : {}),
  };
}

/** Add (`/library/new`) or edit (`/library/:id/edit`) a judgment (LD-052, requirements section 16.1). */
export function JudgmentFormPage() {
  const { id: judgmentId } = useParams();
  const editing = judgmentId !== undefined;
  const { t, locale } = useI18n();
  usePageTitle(t(editing ? 'library.form.titleEdit' : 'library.form.titleNew'));
  const id = useId();
  const navigate = useNavigate();
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [version, setVersion] = useState<number | undefined>(undefined);
  const [ready, setReady] = useState(!editing);
  const [lists, setLists] = useState<Classification[]>([]);
  const [problem, setProblem] = useState<MessageKey | null>(null);
  const [duplicate, setDuplicate] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    listClassifications().then(setLists, () => {
      setLists([]);
    });
  }, []);

  useEffect(() => {
    if (judgmentId === undefined) return;
    const controller = new AbortController();
    getJudgment(judgmentId, controller.signal).then(
      (found) => {
        if (controller.signal.aborted) return;
        setDraft(fromJudgment(found));
        setVersion(found.version);
        setReady(true);
      },
      () => {
        if (!controller.signal.aborted) setProblem('library.failed');
      },
    );
    return () => {
      controller.abort();
    };
  }, [judgmentId]);

  const set = (field: keyof Omit<Draft, 'classificationIds'>, value: string) => {
    setDraft((current) => ({ ...current, [field]: value }));
    setDuplicate(false);
  };
  const toggle = (classificationId: string) => {
    setDraft((current) => ({
      ...current,
      classificationIds: current.classificationIds.includes(classificationId)
        ? current.classificationIds.filter((c) => c !== classificationId)
        : [...current.classificationIds, classificationId],
    }));
  };

  const save = async (event: SyntheticEvent, allowDuplicate = false) => {
    event.preventDefault();
    setProblem(null);
    if (
      draft.courtName.trim() === '' ||
      draft.subject.trim() === '' ||
      draft.sourceReference.trim() === '' ||
      draft.verificationNote.trim() === ''
    ) {
      setProblem('library.form.needFields');
      return;
    }
    if (draft.judgmentDate > todayIso()) {
      setProblem('library.form.futureDate');
      return;
    }
    setBusy(true);
    try {
      const saved = judgmentId !== undefined
        ? await replaceJudgment(judgmentId, toInput(draft, version))
        : await registerJudgment(toInput(draft, undefined, allowDuplicate));
      void navigate(withLang(`/library/${saved.id}`, locale));
    } catch (error) {
      setBusy(false);
      if (error instanceof ApiError && error.status === 409) setProblem('library.conflict');
      else if (error instanceof ApiError && error.problem?.rule === 'LIBRARY_DUPLICATE_SUSPECTED') setDuplicate(true);
      else if (error instanceof ApiError && error.problem?.rule === 'LIBRARY_CLASSIFICATION_UNKNOWN') setProblem('library.form.unknownClassification');
      else if (error instanceof ApiError && error.problem?.rule === 'LIBRARY_DATE_IN_FUTURE') setProblem('library.form.futureDate');
      else setProblem('library.saveFailed');
    }
  };

  const text = (field: keyof Omit<Draft, 'classificationIds'>, label: MessageKey, extra: { required?: boolean; max?: number; hint?: MessageKey; type?: string; wide?: boolean } = {}) => (
    <div className="case-field">
      <label htmlFor={`${id}-${field}`}>{t(label)}</label>
      <input
        id={`${id}-${field}`}
        type={extra.type ?? 'text'}
        required={extra.required === true}
        maxLength={extra.max ?? 200}
        aria-describedby={extra.hint === undefined ? undefined : `${id}-${field}-hint`}
        value={draft[field]}
        onChange={(e) => {
          set(field, e.target.value);
        }}
      />
      {extra.hint === undefined ? null : <small id={`${id}-${field}-hint`}>{t(extra.hint)}</small>}
    </div>
  );
  const area = (field: keyof Omit<Draft, 'classificationIds'>, label: MessageKey) => (
    <div className="case-field">
      <label htmlFor={`${id}-${field}`}>{t(label)}</label>
      <textarea
        id={`${id}-${field}`}
        rows={4}
        value={draft[field]}
        onChange={(e) => {
          set(field, e.target.value);
        }}
      />
    </div>
  );
  const choice = (field: keyof Omit<Draft, 'classificationIds'>, label: MessageKey, options: readonly string[], name: (o: string) => string, optional: boolean) => (
    <div className="case-field">
      <label htmlFor={`${id}-${field}`}>{t(label)}</label>
      <select
        id={`${id}-${field}`}
        value={draft[field]}
        onChange={(e) => {
          set(field, e.target.value);
        }}
      >
        {optional ? <option value="">{t('library.na')}</option> : null}
        {options.map((option) => (
          <option key={option} value={option}>
            {name(option)}
          </option>
        ))}
      </select>
    </div>
  );

  if (!ready) {
    return problem === null ? (
      <p role="status">{t('library.loading')}</p>
    ) : (
      <p className="case-alert" role="alert">
        {t(problem)}
      </p>
    );
  }

  return (
    <>
      <h1>{t(editing ? 'library.form.titleEdit' : 'library.form.titleNew')}</h1>
      <p>
        <Link to={withLang(judgmentId !== undefined ? `/library/${judgmentId}` : '/library', locale)}>{t('library.back')}</Link>
      </p>
      <p>{t('library.form.intro')}</p>
      {problem === null ? null : (
        <p className="case-alert" role="alert">
          {t(problem)}
        </p>
      )}
      {duplicate ? (
        <p className="case-alert" role="alert">
          {t('library.form.duplicate')}{' '}
          <button
            type="button"
            onClick={(e) => {
              void save(e, true);
            }}
          >
            {t('library.form.duplicateConfirm')}
          </button>
        </p>
      ) : null}
      <form
        className="case-form library-form"
        noValidate
        onSubmit={(e) => {
          void save(e);
        }}
      >
        {text('courtName', 'library.form.courtName', { required: true })}
        {choice('courtLevel', 'library.form.courtLevel', COURT_LEVELS, (o) => t(courtLevelKey(o)), true)}
        {text('chamber', 'library.form.chamber', { max: 120 })}
        {text('caseNumber', 'library.form.caseNumber', { max: 60 })}
        {text('caseYear', 'library.form.caseYear', { type: 'number', max: 4 })}
        {text('judgmentDate', 'library.form.judgmentDate', { type: 'date' })}
        {choice('caseType', 'library.form.caseType', CASE_TYPES, (o) => t(`cases.type.${o}` as MessageKey), true)}
        {text('subject', 'library.form.subject', { required: true, max: 1000 })}
        {text('partiesDescription', 'library.form.parties', { max: 1000, hint: 'library.form.partiesHint' })}
        {area('summary', 'library.form.summary')}
        {area('operativeText', 'library.form.operative')}
        {area('reasons', 'library.form.reasons')}
        {area('legalTexts', 'library.form.legalTexts')}
        {area('requestsAddressed', 'library.form.requests')}
        {area('requestResults', 'library.form.requestResults')}
        {choice('direction', 'library.form.direction', DIRECTIONS, (o) => t(directionKey(o as (typeof DIRECTIONS)[number])), true)}
        {area('keywords', 'library.form.keywords')}
        {choice('finality', 'library.form.finality', FINALITIES, (o) => t(finalityKey(o as (typeof FINALITIES)[number])), false)}
        {choice('confidentiality', 'library.form.confidentiality', CONFIDENTIALITIES, (o) => t(`library.confidentiality.${o}` as MessageKey), false)}
        {text('sourceReference', 'library.form.source', { required: true, max: 1000 })}
        {text('verificationNote', 'library.form.verification', { required: true, max: 1000 })}
        {text('copyReference', 'library.form.copy', { max: 500 })}
        {DIMENSIONS.map((dimension) => {
          const values = lists.filter((c) => c.dimension === dimension && (c.active || draft.classificationIds.includes(c.id)));
          return (
            <fieldset key={dimension} className="library-checks">
              <legend>
                {t('library.form.classifications')}: {t(dimensionKey(dimension))}
              </legend>
              {values.length === 0 ? <span>{t('library.form.noClassifications')}</span> : null}
              {values.map((value) => (
                <label key={value.id}>
                  <input
                    type="checkbox"
                    checked={draft.classificationIds.includes(value.id)}
                    onChange={() => {
                      toggle(value.id);
                    }}
                  />{' '}
                  {classificationLabel(value, locale)}
                </label>
              ))}
            </fieldset>
          );
        })}
        <div className="library-toolbar">
          <button type="submit" disabled={busy}>
            {busy ? t('library.form.saving') : t('library.form.save')}
          </button>
          <Link to={withLang(judgmentId !== undefined ? `/library/${judgmentId}` : '/library', locale)}>{t('library.form.cancel')}</Link>
        </div>
      </form>
    </>
  );
}
