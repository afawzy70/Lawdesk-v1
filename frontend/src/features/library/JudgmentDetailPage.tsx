import { useContext, useEffect, useId, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { formatDateTime } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { NotFoundPage } from '../../pages/NotFoundPage';
import { formatDate } from '../cases/format';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { AuthContext } from '../auth/AuthContext';
import { getJudgment, reinstateJudgment, withdrawJudgment, type Judgment } from './api';
import { citation, classificationLabel, courtLevelKey, directionKey, finalityKey, listSeparator } from './labels';
import { PrinciplesSection } from './PrinciplesSection';
import { RelationsSection } from './RelationsSection';
import './library.css';

type Load = { kind: 'loading' } | { kind: 'forbidden' } | { kind: 'missing' } | { kind: 'failed' } | { kind: 'loaded'; judgment: Judgment };

/** One judgment with its principles, what happened to it and the cases that use it (LD-052, requirements section 16). */
export function JudgmentDetailPage() {
  const { id = '' } = useParams();
  const { t, locale } = useI18n();
  const heading = useId();
  const state = useContext(AuthContext)?.state;
  const manage = state?.status === 'signedIn' && state.me.permissions.includes('LIBRARY_MANAGE');
  const [generation, setGeneration] = useState(0);
  // The last answer for this judgment stays on screen while a refresh is under way, so open forms and notices survive it.
  const [result, setResult] = useState<{ id: string; load: Load } | null>(null);
  const [withdrawing, setWithdrawing] = useState(false);
  const [reason, setReason] = useState('');
  const [problem, setProblem] = useState<MessageKey | null>(null);
  const load: Load = result?.id === id ? result.load : { kind: 'loading' };
  usePageTitle(load.kind === 'loaded' ? load.judgment.subject : t('library.title'));

  useEffect(() => {
    const controller = new AbortController();
    getJudgment(id, controller.signal).then(
      (judgment) => {
        if (!controller.signal.aborted) setResult({ id, load: { kind: 'loaded', judgment } });
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        const failed: Load =
          error instanceof ApiError && error.status === 403
            ? { kind: 'forbidden' }
            : error instanceof ApiError && error.status === 404
              ? { kind: 'missing' }
              : { kind: 'failed' };
        setResult({ id, load: failed });
      },
    );
    return () => {
      controller.abort();
    };
  }, [id, generation]);

  const refresh = () => {
    setGeneration((n) => n + 1);
  };

  if (load.kind === 'forbidden') return <AccessDeniedPage />;
  if (load.kind === 'missing') return <NotFoundPage />;
  if (load.kind === 'loading') return <p role="status">{t('library.loading')}</p>;
  if (load.kind === 'failed') {
    return (
      <p className="case-alert" role="alert">
        {t('library.failed')}
      </p>
    );
  }
  const j = load.judgment;

  const withdraw = async () => {
    if (reason.trim() === '') {
      setProblem('library.saveFailed');
      return;
    }
    try {
      await withdrawJudgment(j.id, reason.trim(), j.version);
      setWithdrawing(false);
      setReason('');
      setProblem(null);
      refresh();
    } catch (error) {
      setProblem(error instanceof ApiError && error.status === 409 ? 'library.conflict' : 'library.saveFailed');
    }
  };
  const reinstate = async () => {
    try {
      await reinstateJudgment(j.id, j.version);
      setProblem(null);
      refresh();
    } catch (error) {
      setProblem(error instanceof ApiError && error.status === 409 ? 'library.conflict' : 'library.saveFailed');
    }
  };

  const text = (label: MessageKey, value: string | null) =>
    value === null ? null : (
      <>
        <dt>{t(label)}</dt>
        <dd className="library-text">{value}</dd>
      </>
    );

  return (
    <article aria-labelledby={heading}>
      <h1 id={heading}>{j.subject}</h1>
      <p>
        <Link to={withLang('/library', locale)}>{t('library.back')}</Link>
      </p>
      <p>
        {citation(j)}
        {j.judgmentDate === null ? '' : ` - ${formatDate(j.judgmentDate, locale)}`}
      </p>
      {problem === null ? null : (
        <p className="case-alert" role="alert">
          {t(problem)}
        </p>
      )}
      {j.status === 'WITHDRAWN' ? (
        <p className="case-alert" role="status">
          {t('library.detail.withdrawnNotice', { reason: j.withdrawnReason ?? '' })}
        </p>
      ) : null}
      {j.validity.warnings.length === 0 ? null : (
        <ul className="case-notice" aria-label={t('library.validity.title')}>
          {j.validity.warnings.map((w) => (
            <li key={w}>{t(`library.validity.warning.${w}` as MessageKey)}</li>
          ))}
        </ul>
      )}
      {manage ? (
        <div className="library-toolbar">
          <Link to={withLang(`/library/${j.id}/edit`, locale)}>{t('library.detail.edit')}</Link>
          {j.status === 'ACTIVE' ? (
            <button
              type="button"
              onClick={() => {
                setWithdrawing(!withdrawing);
              }}
            >
              {t('library.detail.withdraw')}
            </button>
          ) : (
            <button
              type="button"
              onClick={() => {
                void reinstate();
              }}
            >
              {t('library.detail.reinstate')}
            </button>
          )}
        </div>
      ) : null}
      {withdrawing ? (
        <div className="case-form library-form">
          <div className="case-field">
            <label htmlFor={`${heading}-reason`}>{t('library.detail.withdrawReason')}</label>
            <input
              id={`${heading}-reason`}
              type="text"
              maxLength={500}
              value={reason}
              onChange={(e) => {
                setReason(e.target.value);
              }}
            />
          </div>
          <button
            type="button"
            onClick={() => {
              void withdraw();
            }}
          >
            {t('library.detail.withdrawConfirm')}
          </button>
        </div>
      ) : null}

      <section className="library-section" aria-label={t('library.detail.section.facts')}>
        <h2>{t('library.detail.section.facts')}</h2>
        <dl className="case-facts">
          <dt>{t('library.form.courtName')}</dt>
          <dd>{j.courtName}</dd>
          <dt>{t('library.form.courtLevel')}</dt>
          <dd>{j.courtLevel === null ? t('library.na') : t(courtLevelKey(j.courtLevel))}</dd>
          <dt>{t('library.form.chamber')}</dt>
          <dd>{j.chamber ?? t('library.na')}</dd>
          <dt>{t('library.form.caseType')}</dt>
          <dd>{j.caseType === null ? t('library.na') : t(`cases.type.${j.caseType}` as MessageKey)}</dd>
          <dt>{t('library.form.direction')}</dt>
          <dd>{j.direction === null ? t('library.na') : t(directionKey(j.direction))}</dd>
          <dt>{t('library.form.finality')}</dt>
          <dd>{t(finalityKey(j.finality))}</dd>
          <dt>{t('library.form.confidentiality')}</dt>
          <dd>{t(`library.confidentiality.${j.confidentiality}` as MessageKey)}</dd>
          <dt>{t('library.form.parties')}</dt>
          <dd>{j.partiesMasked ? t('library.detail.partiesMasked') : (j.partiesDescription ?? t('library.na'))}</dd>
          <dt>{t('library.form.classifications')}</dt>
          <dd>
            {j.classifications.length === 0
              ? t('library.na')
              : j.classifications.map((c) => classificationLabel(c, locale)).join(listSeparator(locale))}
          </dd>
          <dt>{t('library.detail.keywords')}</dt>
          <dd>{j.keywords.length === 0 ? t('library.detail.none') : j.keywords.join(listSeparator(locale))}</dd>
        </dl>
      </section>

      <section className="library-section" aria-label={t('library.detail.section.texts')}>
        <h2>{t('library.detail.section.texts')}</h2>
        <dl className="case-facts">
          {text('library.form.summary', j.summary)}
          {text('library.form.operative', j.operativeText)}
          {text('library.form.reasons', j.reasons)}
          {text('library.form.legalTexts', j.legalTexts)}
          {text('library.form.requests', j.requestsAddressed)}
          {text('library.form.requestResults', j.requestResults)}
        </dl>
      </section>

      <section className="library-section" aria-label={t('library.detail.section.source')}>
        <h2>{t('library.detail.section.source')}</h2>
        <dl className="case-facts">
          <dt>{t('library.form.source')}</dt>
          <dd className="library-text">{j.sourceReference}</dd>
          <dt>{t('library.form.verification')}</dt>
          <dd className="library-text">{j.verificationNote}</dd>
          <dt>{t('library.form.copy')}</dt>
          <dd>{j.copyReference ?? t('library.na')}</dd>
          <dt>{t('library.detail.entered')}</dt>
          <dd>{formatDateTime(j.createdAt, locale)}</dd>
        </dl>
      </section>

      <PrinciplesSection judgmentId={j.id} principles={j.principles} canManage={manage} onChanged={refresh} />
      <RelationsSection judgment={j} canManage={manage} onChanged={refresh} />

      <section className="library-section" aria-label={t('library.linked.title')}>
        <h2>{t('library.linked.title')}</h2>
        {j.linkedCases.length === 0 ? (
          <p>{t('library.linked.none')}</p>
        ) : (
          <ul>
            {j.linkedCases.map((c) => (
              <li key={c.linkId}>
                <Link to={withLang(`/cases/${c.caseId}`, locale)}>
                  {c.referenceNo} - {c.title}
                </Link>
                <p className="library-text">{c.reason}</p>
              </li>
            ))}
          </ul>
        )}
      </section>
    </article>
  );
}
