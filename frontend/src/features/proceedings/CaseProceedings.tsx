import { useEffect, useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate, numberOf } from './dates';
import { DecisionForm, JudgmentForm, ProceedingForm } from './ProceedingForms';
import {
  listJudgments,
  listProceedings,
  listSpecialDecisions,
  type Judgment,
  type Proceeding,
  type SpecialDecision,
} from './proceedingsApi';
import './proceedings.css';

interface Stages {
  proceedings: Proceeding[];
  /** `null` when the user may not see judgments and special decisions (JUDGMENT_VIEW). */
  judgments: Judgment[] | null;
  decisions: SpecialDecision[] | null;
}

type Result = { key: number; stages: Stages } | { key: number; error: MessageKey };

/** Which form is open. */
type Open = null | { kind: 'proceeding' } | { kind: 'judgment' | 'decision'; proceedingId: string };

function loadError(error: unknown): MessageKey {
  if (error instanceof ApiError) {
    if (error.status === 403) return 'proceedings.error.viewForbidden';
    if (error.status === 404) return 'proceedings.error.caseNotFound';
    if (error.status === 401) return 'proceedings.error.signIn';
  }
  return 'proceedings.error.load';
}

/** Judgments and decisions need JUDGMENT_VIEW; without it the stages are still shown. */
function unlessForbidden<T>(request: Promise<T>): Promise<T | null> {
  return request.catch((error: unknown) => {
    if (error instanceof ApiError && error.status === 403) return null;
    throw error;
  });
}

async function loadStages(caseId: string, signal: AbortSignal): Promise<Stages> {
  const [proceedings, judgments, decisions] = await Promise.all([
    listProceedings(caseId, signal),
    unlessForbidden(listJudgments(caseId, signal)),
    unlessForbidden(listSpecialDecisions(caseId, signal)),
  ]);
  return { proceedings, judgments, decisions };
}

interface Props {
  caseId: string;
}

/**
 * The judicial stages of one case (LD-019…LD-023): first instance, appeal and cassation proceedings with their
 * judgments and special decisions, and forms to record them. Built to be embedded in the case details page
 * (Workstream B). The backend enforces every permission and rule; this component explains its answers.
 */
export function CaseProceedings({ caseId }: Props) {
  const { locale, t } = useI18n();
  const headingId = useId();
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const [open, setOpen] = useState<Open>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    loadStages(caseId, controller.signal).then(
      (stages) => {
        setResult({ key: reload, stages });
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

  const title = (p: Proceeding) =>
    t('proceedings.number', { stage: t(`proceedings.stage.${p.stage}`), number: numberOf(p) });

  const body = () => {
    if (current === null) return <p>{t('proceedings.loading')}</p>;
    if ('error' in current) {
      return (
        <div role="alert" className="judicial-alert">
          <p>{t(current.error)}</p>
          {current.error === 'proceedings.error.load' ? (
            <button
              type="button"
              onClick={() => {
                setReload((n) => n + 1);
              }}
            >
              {t('proceedings.retry')}
            </button>
          ) : null}
        </div>
      );
    }
    const { proceedings, judgments, decisions } = current.stages;
    const byId = new Map(proceedings.map((p) => [p.id, p]));
    const judgmentById = new Map((judgments ?? []).map((j) => [j.id, j]));
    const visible = judgments !== null && decisions !== null;

    return (
      <>
        {visible ? null : <p className="judicial-hint">{t('proceedings.judgmentsHidden')}</p>}
        <button
          type="button"
          onClick={() => {
            show({ kind: 'proceeding' });
          }}
        >
          {t('proceedings.record')}
        </button>
        {open?.kind === 'proceeding' ? (
          <ProceedingForm caseId={caseId} proceedings={proceedings} onSaved={onSaved} onCancel={onCancel} />
        ) : null}
        {proceedings.length === 0 ? <p>{t('proceedings.empty')}</p> : null}
        <ol className="judicial-ladder">
          {proceedings.map((p) => {
            const parent = p.parentProceedingId === null ? undefined : byId.get(p.parentProceedingId);
            const judgment = judgments?.find((j) => j.proceedingId === p.id);
            const own = (decisions ?? []).filter((d) => d.proceedingId === p.id);
            return (
              <li key={p.id}>
                <article className="judicial-stage" aria-label={title(p)}>
                  <h3>{title(p)}</h3>
                  <p>{locale === 'ar' ? p.courtNameAr : p.courtNameEn}</p>
                  <p>{t('proceedings.filedOn', { date: formatDate(p.filingDate, locale) })}</p>
                  {parent === undefined ? null : <p>{t('proceedings.attacks', { number: numberOf(parent) })}</p>}

                  {!visible ? null : judgment === undefined ? (
                    <>
                      <p>{t('proceedings.noJudgment')}</p>
                      <button
                        type="button"
                        onClick={() => {
                          show({ kind: 'judgment', proceedingId: p.id });
                        }}
                      >
                        {t('proceedings.recordJudgment')}
                      </button>
                    </>
                  ) : (
                    <div className="judicial-judgment">
                      <p>
                        {t('proceedings.judgmentOf', {
                          date: formatDate(judgment.judgmentDate, locale),
                          outcome: t(`proceedings.outcome.${judgment.outcome}`),
                        })}{' '}
                        <span className="judicial-badge">
                          {judgment.final ? t('proceedings.final') : t('proceedings.notFinal')}
                        </span>
                      </p>
                      {judgment.summary === null ? null : <p>{judgment.summary}</p>}
                    </div>
                  )}
                  {open?.kind === 'judgment' && open.proceedingId === p.id ? (
                    <JudgmentForm proceeding={p} onSaved={onSaved} onCancel={onCancel} />
                  ) : null}

                  {visible && own.length > 0 ? (
                    <>
                      <h4>{t('proceedings.decisions')}</h4>
                      <ul>
                        {own.map((d) => {
                          const concerned = d.judgmentId === null ? undefined : judgmentById.get(d.judgmentId);
                          const main = t('proceedings.decision', {
                            type: t(`proceedings.decisionType.${d.decisionType}`),
                            effect: t(`proceedings.effect.${d.effect}`),
                            date: formatDate(d.decisionDate, locale),
                          });
                          const suffix =
                            concerned === undefined
                              ? ''
                              : ` ${t('proceedings.decisionJudgment', { date: formatDate(concerned.judgmentDate, locale) })}`;
                          return (
                            <li key={d.id}>
                              {main}
                              {suffix}
                              {d.summary === null ? null : <p>{d.summary}</p>}
                            </li>
                          );
                        })}
                      </ul>
                    </>
                  ) : null}
                  {visible ? (
                    <button
                      type="button"
                      onClick={() => {
                        show({ kind: 'decision', proceedingId: p.id });
                      }}
                    >
                      {t('proceedings.recordDecision')}
                    </button>
                  ) : null}
                  {open?.kind === 'decision' && open.proceedingId === p.id ? (
                    <DecisionForm
                      proceeding={p}
                      judgments={judgments ?? []}
                      proceedingsById={byId}
                      onSaved={onSaved}
                      onCancel={onCancel}
                    />
                  ) : null}
                </article>
              </li>
            );
          })}
        </ol>
      </>
    );
  };

  return (
    <section className="case-proceedings" aria-labelledby={headingId}>
      <h2 id={headingId}>{t('proceedings.stages')}</h2>
      {saved ? (
        <p role="status" className="judicial-saved">
          {t('proceedings.saved')}
        </p>
      ) : null}
      {body()}
    </section>
  );
}
