import { useEffect, useId, useState, type ReactNode } from 'react';
import { ApiError } from '../api/http';
import { useAuth } from '../features/auth/AuthContext';
import { CASE_STATUSES, CASE_TYPES } from '../features/cases/api';
import { OUTCOMES } from '../features/proceedings/proceedingsApi';
import { getDashboard, isDashboard, type DashboardReport } from '../features/reports/dashboardApi';
import { formatDay } from '../features/reports/format';
import '../features/reports/reports.css';
import { formatNumber } from '../i18n/format';
import { useI18n } from '../i18n/I18nContext';
import { usePageTitle } from '../i18n/usePageTitle';

type Load =
  | { kind: 'loading' }
  | { kind: 'forbidden' }
  | { kind: 'failed' }
  | { kind: 'loaded'; report: DashboardReport };

/** A titled region of the dashboard; its heading names it for assistive technology. */
function Panel({ title, children }: { title: string; children: ReactNode }) {
  const headingId = useId();
  return (
    <section className="rpt-panel" aria-labelledby={headingId}>
      <h2 id={headingId}>{title}</h2>
      {children}
    </section>
  );
}

/** A large figure with its label underneath. */
function Tile({ value, label }: { value: string; label: string }) {
  return (
    <li className="kpi">
      <span className="kpi-value">{value}</span>
      <span className="kpi-label">{label}</span>
    </li>
  );
}

/** A label and its count on one line. */
function Count({ label, value }: { label: string; value: string }) {
  return (
    <li className="rpt-count">
      <span>{label}</span>
      <span className="rpt-count-value">{value}</span>
    </li>
  );
}

/**
 * LD-030: the dashboard. Its figures come from `GET /api/v1/reports/dashboard`, computed from stored records; figures
 * that need a Phase 5 module are shown as not available yet. Users without REPORT_VIEW see the page without them.
 */
export function DashboardPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('nav.dashboard'));
  const { state } = useAuth();
  const canView = state.status === 'signedIn' && state.me.permissions.includes('REPORT_VIEW');
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ attempt: number; load: Load } | null>(null);
  const load: Load = result?.attempt === attempt ? result.load : { kind: 'loading' };

  useEffect(() => {
    if (!canView) return;
    const controller = new AbortController();
    getDashboard(controller.signal).then(
      (report) => {
        if (controller.signal.aborted) return;
        setResult({ attempt, load: isDashboard(report) ? { kind: 'loaded', report } : { kind: 'failed' } });
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        const forbidden = error instanceof ApiError && error.status === 403;
        setResult({ attempt, load: forbidden ? { kind: 'forbidden' } : { kind: 'failed' } });
      },
    );
    return () => {
      controller.abort();
    };
  }, [attempt, canView]);

  const n = (value: number) => formatNumber(value, locale);

  let body: ReactNode;
  if (!canView || load.kind === 'forbidden') {
    body = <p className="rpt-note">{t('reports.dashboard.noPermission')}</p>;
  } else if (load.kind === 'loading') {
    body = <p role="status">{t('reports.dashboard.loading')}</p>;
  } else if (load.kind === 'failed') {
    body = (
      <div className="rpt-alert" role="alert">
        <p>{t('reports.dashboard.failed')}</p>
        <button
          type="button"
          onClick={() => {
            setAttempt((value) => value + 1);
          }}
        >
          {t('reports.dashboard.retry')}
        </button>
      </div>
    );
  } else {
    const { cases, proceedings, judgments, pending } = load.report;
    body = (
      <div className="rpt-dashboard">
        <Panel title={t('reports.dashboard.cases')}>
          <ul className="kpi-grid">
            <Tile value={n(cases.open)} label={t('reports.dashboard.cases.open')} />
            <Tile value={n(cases.closed)} label={t('reports.dashboard.cases.closed')} />
            <Tile value={n(cases.total)} label={t('reports.dashboard.cases.total')} />
            <Tile value={n(cases.newThisMonth)} label={t('reports.dashboard.cases.newThisMonth')} />
          </ul>
        </Panel>

        <Panel title={t('reports.dashboard.byStatus')}>
          <ul className="rpt-counts">
            {CASE_STATUSES.map((status) => (
              <Count key={status} label={t(`cases.status.${status}`)} value={n(cases.byStatus[status])} />
            ))}
          </ul>
        </Panel>

        <Panel title={t('reports.dashboard.byType')}>
          <ul className="rpt-counts">
            {CASE_TYPES.map((type) => (
              <Count key={type} label={t(`cases.type.${type}`)} value={n(cases.byType[type])} />
            ))}
          </ul>
        </Panel>

        <Panel title={t('reports.dashboard.stages')}>
          <ul className="rpt-counts">
            <Count label={t('reports.dashboard.awaitingJudgment')} value={n(proceedings.awaitingJudgment)} />
            {proceedings.byStage.map(({ stage, total, awaitingJudgment }) => (
              <Count
                key={stage}
                label={t(`proceedings.stage.${stage}`)}
                value={t('reports.dashboard.stageValue', { total: n(total), awaiting: n(awaitingJudgment) })}
              />
            ))}
          </ul>
        </Panel>

        <Panel
          title={t('reports.dashboard.judgments', {
            from: formatDay(judgments.from, locale),
            to: formatDay(judgments.to, locale),
          })}
        >
          <ul className="kpi-grid">
            <Tile value={n(judgments.recorded)} label={t('reports.dashboard.judgments.recorded')} />
            <Tile value={n(judgments.final)} label={t('reports.dashboard.judgments.final')} />
          </ul>
          <ul className="rpt-counts">
            {OUTCOMES.map((outcome) => (
              <Count
                key={outcome}
                label={t(`proceedings.outcome.${outcome}`)}
                value={n(judgments.byOutcome[outcome])}
              />
            ))}
          </ul>
        </Panel>

        <Panel title={t('reports.dashboard.operations')}>
          <ul className="kpi-grid">
            {pending.map((figure) => (
              <li key={figure} className="kpi rpt-pending">
                <span className="kpi-label">{t(`reports.pending.${figure}`)}</span>
                <span className="rpt-pending-value">{t('reports.dashboard.notAvailable')}</span>
              </li>
            ))}
          </ul>
          <p className="rpt-note">{t('reports.dashboard.notAvailableHint')}</p>
        </Panel>
      </div>
    );
  }

  return (
    <>
      <h1>{t('nav.dashboard')}</h1>
      <p className="dashboard-intro">{t('reports.dashboard.intro')}</p>
      {body}
    </>
  );
}
