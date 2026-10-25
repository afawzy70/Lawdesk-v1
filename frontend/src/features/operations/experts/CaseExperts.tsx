import { useEffect, useId, useState } from 'react';
import { ApiError } from '../../../api/http';
import type { MessageKey } from '../../../i18n/catalog';
import { useI18n } from '../../../i18n/I18nContext';
import { formatDate, numberOf } from '../../proceedings/dates';
import { listProceedings, type Proceeding } from '../../proceedings/proceedingsApi';
import { formatTime } from '../days';
import { loadError, saveError } from '../formErrors';
import { cancelReferral, listCaseReferrals, type Referral } from './expertsApi';
import { ReferralForm, ReportForm, SessionForm } from './ReferralForms';

interface Loaded {
  referrals: Referral[];
  /** `null` when the user may not list the case's proceedings: no referral can then be made here. */
  proceedings: Proceeding[] | null;
}

type Result = { key: number; loaded: Loaded } | { key: number; error: MessageKey };
type Open = null | { kind: 'refer' } | { kind: 'session' | 'report' | 'cancel'; referral: Referral };

function withoutForbidden<T>(request: Promise<T>): Promise<T | null> {
  return request.catch((error: unknown) => {
    if (error instanceof ApiError && error.status === 403) return null;
    throw error;
  });
}

interface Props {
  caseId: string;
}

/** The expert referrals of one case (LD-025) with their sessions, and the forms to refer, record and close them. */
export function CaseExperts({ caseId }: Props) {
  const { locale, t } = useI18n();
  const headingId = useId();
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const [open, setOpen] = useState<Open>(null);
  const [notice, setNotice] = useState<'saved' | MessageKey | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      listCaseReferrals(caseId, controller.signal),
      withoutForbidden(listProceedings(caseId, controller.signal)),
    ]).then(
      ([referrals, proceedings]) => {
        setResult({ key: reload, loaded: { referrals, proceedings } });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: reload, error: loadError(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, reload]);

  const current = result?.key === reload ? result : null;
  const saved = () => {
    setOpen(null);
    setNotice('saved');
    setReload((n) => n + 1);
  };
  const cancel = async (referral: Referral) => {
    setNotice(null);
    try {
      await cancelReferral(referral);
      saved();
    } catch (error) {
      setOpen(null);
      setNotice(saveError(error, 'operations.error.stale'));
    }
  };
  const show = (next: Open) => {
    setNotice(null);
    setOpen(next);
  };

  const card = (r: Referral) => {
    const isOpen = r.status === 'OPEN';
    return (
      <li key={r.id}>
        <article className="ops-item" aria-label={t('operations.referral.label', { name: r.expertName })}>
          <p>
            <strong>{t('operations.referral.expertOption', { name: r.expertName, specialty: r.expertSpecialty })}</strong>
          </p>
          <p>
            {t('operations.referral.proceeding', { stage: t(`proceedings.stage.${r.stage}`), number: numberOf(r) })}
          </p>
          <p>
            {t('operations.referral.referredOn', { date: formatDate(r.referralDate, locale) })}
            {r.reportDueDate === null
              ? null
              : ` · ${t('operations.referral.dueOn', { date: formatDate(r.reportDueDate, locale) })}`}
            {r.reportOverdue ? (
              <span className="ops-badge ops-badge-danger">{t('operations.referral.overdue')}</span>
            ) : null}
          </p>
          <p>
            {r.status === 'REPORT_SUBMITTED' && r.reportSubmittedDate !== null
              ? t('operations.referral.submittedOn', { date: formatDate(r.reportSubmittedDate, locale) })
              : t(`operations.referralStatus.${r.status}`)}
          </p>
          {r.mission === null ? null : <p className="ops-wrap">{r.mission}</p>}
          <h4>{t('operations.referral.sessions')}</h4>
          {r.sessions.length === 0 ? (
            <p className="ops-hint">{t('operations.referral.noSessions')}</p>
          ) : (
            <ul>
              {r.sessions.map((s) => (
                <li key={s.id} className="ops-wrap">
                  {[
                    formatDate(s.sessionDate, locale),
                    s.sessionTime === null ? null : formatTime(s.sessionTime, locale),
                    s.location,
                  ]
                    .filter((part) => part !== null)
                    .join(' · ')}
                  {s.notes === null ? null : <span className="ops-hint"> — {s.notes}</span>}
                </li>
              ))}
            </ul>
          )}
          {isOpen ? (
            <div className="ops-actions">
              <button
                type="button"
                onClick={() => {
                  show({ kind: 'session', referral: r });
                }}
              >
                {t('operations.referral.recordSession')}
              </button>
              <button
                type="button"
                onClick={() => {
                  show({ kind: 'report', referral: r });
                }}
              >
                {t('operations.referral.reportSubmitted')}
              </button>
              <button
                type="button"
                onClick={() => {
                  show({ kind: 'cancel', referral: r });
                }}
              >
                {t('operations.referral.cancel')}
              </button>
            </div>
          ) : null}
          {open !== null && open.kind !== 'refer' && open.referral.id === r.id ? (
            <>
              {open.kind === 'session' ? (
                <SessionForm referral={r} onSaved={saved} onCancel={() => { setOpen(null); }} />
              ) : null}
              {open.kind === 'report' ? (
                <ReportForm referral={r} onSaved={saved} onCancel={() => { setOpen(null); }} />
              ) : null}
              {open.kind === 'cancel' ? (
                <div className="ops-confirm">
                  <p>{t('operations.referral.confirmCancel', { name: r.expertName })}</p>
                  <div className="ops-actions">
                    <button type="button" onClick={() => { void cancel(r); }}>
                      {t('operations.referral.confirmCancelButton')}
                    </button>
                    <button type="button" onClick={() => { setOpen(null); }}>
                      {t('operations.referral.keep')}
                    </button>
                  </div>
                </div>
              ) : null}
            </>
          ) : null}
        </article>
      </li>
    );
  };

  const body = () => {
    if (current === null) return <p>{t('operations.loading')}</p>;
    if ('error' in current) {
      return (
        // Not role="alert": this section loads with the case page, whose own alerts must stay the only ones announced.
        <div className="ops-alert ops-load-error">
          <p>
            {t(current.error === 'operations.error.viewForbidden' ? 'operations.referral.hidden' : current.error)}
          </p>
          {current.error === 'operations.error.load' ? (
            <button type="button" onClick={() => { setReload((n) => n + 1); }}>
              {t('operations.retry')}
            </button>
          ) : null}
        </div>
      );
    }
    const { referrals, proceedings } = current.loaded;
    return (
      <>
        {proceedings !== null && proceedings.length === 0 ? (
          <p className="ops-hint">{t('operations.referral.noProceedings')}</p>
        ) : null}
        {proceedings !== null && proceedings.length > 0 ? (
          <button type="button" onClick={() => { show({ kind: 'refer' }); }}>
            {t('operations.referral.new')}
          </button>
        ) : null}
        {open?.kind === 'refer' && proceedings !== null ? (
          <ReferralForm proceedings={proceedings} onSaved={saved} onCancel={() => { setOpen(null); }} />
        ) : null}
        {referrals.length === 0 ? (
          <p>{t('operations.referral.none')}</p>
        ) : (
          <ul className="ops-list">{referrals.map(card)}</ul>
        )}
      </>
    );
  };

  return (
    <section className="ops-section" aria-labelledby={headingId}>
      <h3 id={headingId}>{t('operations.experts.caseTitle')}</h3>
      {notice === 'saved' ? (
        <p role="status" className="ops-saved">
          {t('operations.referral.saved')}
        </p>
      ) : null}
      {notice !== null && notice !== 'saved' ? <p className="ops-alert">{t(notice)}</p> : null}
      {body()}
    </section>
  );
}
