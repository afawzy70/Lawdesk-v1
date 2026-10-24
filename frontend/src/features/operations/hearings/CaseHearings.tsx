import { useEffect, useId, useState } from 'react';
import { ApiError } from '../../../api/http';
import type { MessageKey } from '../../../i18n/catalog';
import { useI18n } from '../../../i18n/I18nContext';
import { numberOf } from '../../proceedings/dates';
import { listProceedings, type Proceeding } from '../../proceedings/proceedingsApi';
import { formatDay, formatTime } from '../days';
import { loadError } from '../formErrors';
import { HearingResultForm, RescheduleHearingForm, ScheduleHearingForm } from './HearingForms';
import { listCaseHearings, type Hearing } from './hearingsApi';

interface Loaded {
  hearings: Hearing[];
  /** `null` when the user may not list the case's proceedings (PROCEEDING_VIEW): hearings can then not be scheduled. */
  proceedings: Proceeding[] | null;
}

type Result = { key: number; loaded: Loaded } | { key: number; error: MessageKey };
type Open = null | { kind: 'schedule' } | { kind: 'result' | 'reschedule'; hearing: Hearing };

function withoutForbidden<T>(request: Promise<T>): Promise<T | null> {
  return request.catch((error: unknown) => {
    if (error instanceof ApiError && error.status === 403) return null;
    throw error;
  });
}

interface Props {
  caseId: string;
}

/** The hearings of one case (LD-024), oldest first, with the forms to schedule them and record their results. */
export function CaseHearings({ caseId }: Props) {
  const { locale, t } = useI18n();
  const headingId = useId();
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const [open, setOpen] = useState<Open>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([
      listCaseHearings(caseId, controller.signal),
      withoutForbidden(listProceedings(caseId, controller.signal)),
    ]).then(
      ([hearings, proceedings]) => {
        setResult({ key: reload, loaded: { hearings, proceedings } });
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
  const onSaved = () => {
    setOpen(null);
    setSaved(true);
    setReload((n) => n + 1);
  };
  const onCancel = () => {
    setOpen(null);
  };
  const show = (next: Open) => {
    setSaved(false);
    setOpen(next);
  };

  const body = () => {
    if (current === null) return <p>{t('operations.loading')}</p>;
    if ('error' in current) {
      return (
        // Not role="alert": this section loads with the case page, whose own alerts must stay the only ones announced.
        <div className="ops-alert ops-load-error">
          <p>{t(current.error === 'operations.error.viewForbidden' ? 'operations.hearing.hidden' : current.error)}</p>
          {current.error === 'operations.error.load' ? (
            <button
              type="button"
              onClick={() => {
                setReload((n) => n + 1);
              }}
            >
              {t('operations.retry')}
            </button>
          ) : null}
        </div>
      );
    }
    const { hearings, proceedings } = current.loaded;
    return (
      <>
        {proceedings === null ? <p className="ops-hint">{t('operations.hearing.proceedingsHidden')}</p> : null}
        {proceedings !== null && proceedings.length === 0 ? (
          <p className="ops-hint">{t('operations.hearing.noProceedings')}</p>
        ) : null}
        {proceedings !== null && proceedings.length > 0 ? (
          <button
            type="button"
            onClick={() => {
              show({ kind: 'schedule' });
            }}
          >
            {t('operations.hearing.schedule')}
          </button>
        ) : null}
        {open?.kind === 'schedule' && proceedings !== null ? (
          <ScheduleHearingForm proceedings={proceedings} onSaved={onSaved} onCancel={onCancel} />
        ) : null}
        {open?.kind === 'result' ? (
          <HearingResultForm key={open.hearing.id} hearing={open.hearing} onSaved={onSaved} onCancel={onCancel} />
        ) : null}
        {open?.kind === 'reschedule' ? (
          <RescheduleHearingForm key={open.hearing.id} hearing={open.hearing} onSaved={onSaved} onCancel={onCancel} />
        ) : null}
        {hearings.length === 0 ? (
          <p>{t('operations.hearing.none')}</p>
        ) : (
          <ul className="ops-list">
            {hearings.map((h) => (
              <li key={h.id} className="ops-item">
                <p>
                  <strong>{formatDay(h.hearingDate, locale)}</strong>
                  {h.hearingTime === null ? null : ` · ${formatTime(h.hearingTime, locale)}`}
                  {' · '}
                  {t(`operations.purpose.${h.purpose}`)}
                </p>
                <p className="ops-wrap">
                  {t('operations.hearing.inProceeding', {
                    stage: t(`proceedings.stage.${h.stage}`),
                    number: numberOf(h),
                    court: locale === 'ar' ? h.courtNameAr : h.courtNameEn,
                  })}
                </p>
                <p>
                  {t(`operations.hearingStatus.${h.status}`)}
                  {h.awaitingResult ? (
                    <span className="ops-badge ops-badge-warn">{t('operations.hearing.awaiting')}</span>
                  ) : null}
                </p>
                {h.decision === null ? null : <p className="ops-wrap">{h.decision}</p>}
                {h.notes === null ? null : <p className="ops-wrap ops-hint">{h.notes}</p>}
                {h.status === 'SCHEDULED' ? (
                  <div className="ops-actions">
                    <button
                      type="button"
                      aria-label={t('operations.hearing.resultLabel', {
                        date: formatDay(h.hearingDate, locale),
                        reference: h.caseReferenceNo,
                      })}
                      onClick={() => {
                        show({ kind: 'result', hearing: h });
                      }}
                    >
                      {t('operations.hearing.recordResult')}
                    </button>
                    <button
                      type="button"
                      aria-label={t('operations.hearing.rescheduleLabel', {
                        date: formatDay(h.hearingDate, locale),
                        reference: h.caseReferenceNo,
                      })}
                      onClick={() => {
                        show({ kind: 'reschedule', hearing: h });
                      }}
                    >
                      {t('operations.hearing.reschedule')}
                    </button>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </>
    );
  };

  return (
    <section className="ops-section" aria-labelledby={headingId}>
      <h3 id={headingId}>{t('operations.hearings.caseTitle')}</h3>
      {saved ? (
        <p role="status" className="ops-saved">
          {t('operations.hearing.saved')}
        </p>
      ) : null}
      {body()}
    </section>
  );
}
