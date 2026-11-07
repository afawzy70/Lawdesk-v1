import { useId, useState, type ReactNode, type SyntheticEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '../../api/http';
import { PagedSelect } from '../../components/PagedSelect';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { listCourtsPage } from '../proceedings/proceedingsApi';
import type { Court } from '../courts/courtsApi';
import { CASE_TYPES, registerCase, type IntakeInput, type Side } from './api';
import { FinancialImpactFields, type FinancialValue } from './intake/FinancialImpactFields';
import { IntakePartiesSection, type DraftParty } from './intake/IntakePartiesSection';
import { MemberFields } from './intake/MemberFields';
import { todayIso } from './format';
import { CASES_PATH, casePath } from './paths';
import { problemMessage } from './problems';
import './cases.css';

/** What the details page shows after a creation (router state, so a reload does not repeat it). */
export interface CreatedState {
  created: string;
}

type Field =
  | 'automatedNo' | 'title' | 'caseType' | 'subject' | 'openedOn' | 'courtId' | 'courtCaseNumber' | 'courtCaseYear'
  | 'filingDate' | 'authorityPosition' | 'parties' | 'primaryMemberId' | 'alternateMemberId' | 'financialImpact'
  | 'financialAmount';
type Errors = Partial<Record<Field, MessageKey>>;

const SERVER_FIELDS: Record<string, Field> = {
  automatedNo: 'automatedNo', title: 'title', caseType: 'caseType', subject: 'subject', openedOn: 'openedOn',
  'firstInstance.courtId': 'courtId', 'firstInstance.courtCaseNumber': 'courtCaseNumber',
  'firstInstance.courtCaseYear': 'courtCaseYear', 'firstInstance.filingDate': 'filingDate',
  firstInstance: 'courtId', authorityPosition: 'authorityPosition', primaryMemberId: 'primaryMemberId',
  alternateMemberId: 'alternateMemberId', financialImpact: 'financialImpact', financialAmount: 'financialAmount',
};

const CODES: Record<string, MessageKey> = {
  NotBlank: 'cases.error.NotBlank', NotNull: 'cases.error.NotNull', Size: 'cases.error.Size',
  Pattern: 'cases.error.Pattern', PastOrPresent: 'cases.error.PastOrPresent', Min: 'cases.error.invalid',
  Max: 'cases.error.invalid', Positive: 'intake.error.amount', Digits: 'intake.error.amount',
  Null: 'intake.error.amount', SameAsPrimary: 'intake.error.same', OneOf: 'intake.error.unique',
};

function serverField(path: string): Field | null {
  if (path.startsWith('parties')) return 'parties';
  return SERVER_FIELDS[path] ?? null;
}

interface Failure {
  message: MessageKey;
  fields: Errors;
}

function explain(error: unknown): Failure {
  if (error instanceof ApiError) {
    if (error.status === 400) {
      const fields: Errors = {};
      for (const { field, code } of error.problem?.errors ?? []) {
        const mapped = serverField(field);
        if (mapped !== null) fields[mapped] = CODES[code] ?? 'cases.error.invalid';
      }
      return { message: 'intake.invalid', fields };
    }
    if (error.status === 409) return { message: 'intake.duplicate', fields: {} };
    if (error.status === 404) return { message: 'intake.notFound', fields: {} };
    if (error.status === 422) {
      const key = `intake.rule.${error.problem?.rule ?? ''}` as MessageKey;
      return { message: key in intakeRuleKeys ? key : 'intake.rule.unknown', fields: {} };
    }
  }
  return { message: problemMessage(error, 'intake.failed'), fields: {} };
}

const intakeRuleKeys: Partial<Record<MessageKey, true>> = {
  'intake.rule.COURT_INACTIVE': true,
  'intake.rule.COURT_LEVEL_MISMATCH': true,
  'intake.rule.MEMBER_NOT_ELIGIBLE': true,
  'intake.rule.PARTY_IS_AUTHORITY': true,
};

const AMOUNT = /^\d{1,12}([.,]\d{1,3})?$/;

/** The client-side required checks; the server repeats and decides. */
function check(v: IntakeInput, financial: FinancialValue, reviewPending: boolean): Errors {
  const errors: Errors = {};
  const required = (field: Field, value: string) => {
    if (value.trim() === '') errors[field] = 'cases.error.NotBlank';
  };
  required('automatedNo', v.automatedNo);
  required('title', v.title);
  required('caseType', v.caseType);
  required('openedOn', v.openedOn);
  required('courtId', v.courtId);
  required('courtCaseNumber', v.courtCaseNumber);
  required('courtCaseYear', v.courtCaseYear);
  required('filingDate', v.filingDate);
  required('primaryMemberId', v.primaryMemberId);
  if (v.authorityPosition === '') errors.authorityPosition = 'cases.error.NotNull';
  if (reviewPending) errors.parties = 'intake.review.pending';
  if (financial.impact === '') errors.financialImpact = 'intake.financial.required';
  if (financial.impact === 'YES') {
    const amount = financial.amount.trim();
    if (!AMOUNT.test(amount) || Number(amount.replace(',', '.')) <= 0) errors.financialAmount = 'intake.error.amount';
  }
  return errors;
}

/**
 * LD-045: registers a case for the authority's legal department in one screen — the case and its automated number, the
 * first litigation stage with its own number, the authority's position and the parties of both sides, the specialist
 * and alternate members, and the financial impact. The server validates everything again.
 */
export function CaseCreatePage() {
  const { locale, t } = useI18n();
  const navigate = useNavigate();
  const formId = useId();
  usePageTitle(t('intake.title'));
  const [values, setValues] = useState<IntakeInput>(() => ({
    automatedNo: '', title: '', caseType: '', subject: '', openedOn: todayIso(), courtId: '', courtCaseNumber: '',
    courtCaseYear: '', filingDate: '', authorityPosition: '', parties: [], primaryMemberId: '', alternateMemberId: '',
    financialImpact: '', financialAmount: '',
  }));
  const [drafts, setDrafts] = useState<DraftParty[]>([]);
  const [reviewPending, setReviewPending] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [formError, setFormError] = useState<MessageKey | null>(null);
  const [saving, setSaving] = useState(false);

  const set = (field: keyof IntakeInput) => (value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
  };
  const financial: FinancialValue = { impact: values.financialImpact, amount: values.financialAmount };

  const changePosition = (position: Side) => {
    if (position === values.authorityPosition) return;
    if (values.authorityPosition !== '' && drafts.length > 0) setReviewPending(true);
    setValues((current) => ({ ...current, authorityPosition: position }));
  };
  const swap = () => {
    setDrafts((list) =>
      list.map((party) => ({
        ...party,
        entry: { ...party.entry, side: party.entry.side === 'PLAINTIFF' ? 'DEFENDANT' : 'PLAINTIFF' },
      })),
    );
    setReviewPending(false);
  };

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    if (saving) return;
    const input: IntakeInput = { ...values, parties: drafts.map((party) => party.entry) };
    const found = check(input, financial, reviewPending);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      setFormError('intake.invalid');
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      const created = await registerCase(input);
      const state: CreatedState = { created: created.referenceNo };
      await navigate(withLang(casePath(created.id), locale), { state });
    } catch (error) {
      const failure = explain(error);
      setErrors(failure.fields);
      setFormError(failure.message);
    } finally {
      setSaving(false);
    }
  };

  const field = (name: Field, label: MessageKey, control: (props: Record<string, unknown>) => ReactNode, hint?: MessageKey) => {
    const error = errors[name];
    const base = `${formId}-${name}`;
    const described = [error === undefined ? '' : `${base}-error`, hint === undefined ? '' : `${base}-hint`]
      .filter((x) => x !== '').join(' ');
    return (
      <div className="case-field">
        <label htmlFor={base}>{t(label)} *</label>
        {control({
          id: base,
          'aria-required': 'true',
          'aria-invalid': error === undefined ? undefined : true,
          'aria-describedby': described === '' ? undefined : described,
        })}
        {hint === undefined ? null : <p id={`${base}-hint`} className="case-field-hint">{t(hint)}</p>}
        {error === undefined ? null : <p id={`${base}-error`} className="case-field-error">{t(error)}</p>}
      </div>
    );
  };
  const text = (name: keyof IntakeInput, extra: Record<string, unknown> = {}) => (props: Record<string, unknown>) => (
    <input
      type="text"
      {...props}
      {...extra}
      value={values[name] as string}
      onChange={(event) => {
        set(name)(event.target.value);
      }}
    />
  );
  const section = (key: MessageKey, children: ReactNode) => (
    <section className="case-section" aria-labelledby={`${formId}-${key}`}>
      <h2 id={`${formId}-${key}`}>{t(key)}</h2>
      {children}
    </section>
  );

  return (
    <>
      <h1>{t('intake.title')}</h1>
      <p>
        <Link to={withLang(CASES_PATH, locale)}>{t('cases.form.back')}</Link>
      </p>
      <p>{t('intake.intro')}</p>
      <form className="case-form intake-form" noValidate onSubmit={(event) => void submit(event)}>
        {formError === null ? null : (
          <div className="case-alert" role="alert">
            <p>{t(formError)}</p>
          </div>
        )}
        {section('intake.section.case', (
          <>
            {field('automatedNo', 'intake.field.automatedNo', text('automatedNo', { maxLength: 100, autoComplete: 'off' }), 'intake.field.automatedNoHint')}
            {field('title', 'cases.field.title', text('title', { maxLength: 200 }))}
            {field('caseType', 'cases.field.caseType', (props) => (
              <select {...props} value={values.caseType} onChange={(event) => { set('caseType')(event.target.value); }}>
                <option value="">{t('cases.form.chooseType')}</option>
                {CASE_TYPES.map((type) => (
                  <option key={type} value={type}>{t(`cases.type.${type}`)}</option>
                ))}
              </select>
            ))}
            <div className="case-field">
              <label htmlFor={`${formId}-subject`}>{t('cases.field.subject')}</label>
              <textarea id={`${formId}-subject`} rows={3} maxLength={4000} value={values.subject}
                onChange={(event) => { set('subject')(event.target.value); }} />
            </div>
            {field('openedOn', 'cases.field.openedOn', (props) => (
              <input type="date" {...props} value={values.openedOn} onChange={(event) => { set('openedOn')(event.target.value); }} />
            ))}
          </>
        ))}
        {section('intake.section.stage', (
          <>
            <div className="case-field">
              <span className="case-label">{t('intake.field.stage')}</span>
              <p>{t('intake.stage.FIRST_INSTANCE')}</p>
              <p className="case-field-hint">{t('intake.field.stageHint')}</p>
            </div>
            {field('courtId', 'intake.field.court', (props) => (
              <PagedSelect<Court>
                selectProps={props}
                value={values.courtId}
                onChange={set('courtId')}
                load={(page, query, signal) => listCourtsPage('FIRST_INSTANCE', page, query, signal)}
                optionLabel={(court) => (locale === 'ar' ? court.nameAr : court.nameEn)}
                serverSearch
                searchLabel={t('intake.field.courtSearch')}
                chooseLabel={t('intake.choose')}
              />
            ))}
            {field('courtCaseNumber', 'intake.field.stageNumber', text('courtCaseNumber', { maxLength: 32, autoComplete: 'off' }), 'intake.field.stageNumberHint')}
            {field('courtCaseYear', 'intake.field.stageYear', text('courtCaseYear', { inputMode: 'numeric', maxLength: 4 }))}
            {field('filingDate', 'intake.field.filingDate', (props) => (
              <input type="date" {...props} value={values.filingDate} onChange={(event) => { set('filingDate')(event.target.value); }} />
            ))}
          </>
        ))}
        {section('intake.section.parties', (
          <>
            <IntakePartiesSection
              position={values.authorityPosition}
              onPosition={changePosition}
              parties={drafts}
              onParties={setDrafts}
              reviewPending={reviewPending}
              onReviewed={() => { setReviewPending(false); }}
              onSwap={swap}
              positionError={errors.authorityPosition === undefined ? undefined : t('intake.position.required')}
            />
            {errors.parties === undefined ? null : <p className="case-field-error">{t(errors.parties)}</p>}
          </>
        ))}
        {section('intake.section.members', (
          <MemberFields
            primary={values.primaryMemberId}
            alternate={values.alternateMemberId}
            onChange={(next) => { setValues((c) => ({ ...c, primaryMemberId: next.primary, alternateMemberId: next.alternate })); }}
            primaryError={errors.primaryMemberId}
            alternateError={errors.alternateMemberId}
          />
        ))}
        {section('intake.section.financial', (
          <FinancialImpactFields
            value={financial}
            onChange={(next) => { setValues((c) => ({ ...c, financialImpact: next.impact, financialAmount: next.amount })); }}
            impactError={errors.financialImpact}
            amountError={errors.financialAmount}
          />
        ))}
        <div className="case-actions">
          <button type="submit" className="primary" disabled={saving}>
            {t(saving ? 'intake.saving' : 'intake.submit')}
          </button>
        </div>
      </form>
    </>
  );
}
