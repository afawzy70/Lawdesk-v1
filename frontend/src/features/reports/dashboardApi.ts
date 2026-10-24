import { api } from '../../api/http';
import type { CaseStatus, CaseType } from '../cases/api';
import type { Outcome, Stage } from '../proceedings/proceedingsApi';

/** `GET /api/v1/reports/dashboard` (docs/delivery/PHASE-6-ACCEPTANCE.md, LD-030); every call goes through `api()`. */

/** Figures that wait for a Phase 5 report port: shown as "not available yet", never as a number. */
export type PendingFigure = 'HEARINGS_THIS_WEEK' | 'EXPERT_REPORTS_DUE' | 'OVERDUE_TASKS';

export interface StageCount {
  stage: Stage;
  total: number;
  awaitingJudgment: number;
}

export interface DashboardReport {
  /** The current month, `yyyy-MM`. */
  month: string;
  cases: {
    total: number;
    open: number;
    closed: number;
    newThisMonth: number;
    byStatus: Record<CaseStatus, number>;
    byType: Record<CaseType, number>;
  };
  proceedings: {
    awaitingJudgment: number;
    byStage: StageCount[];
  };
  judgments: {
    from: string;
    to: string;
    recorded: number;
    final: number;
    byOutcome: Record<Outcome, number>;
  };
  /** Legal operations, from the hearing, expert and task report ports (Phase 5). */
  operations: {
    hearingsThisWeek: number;
    hearingsAwaitingResult: number;
    expertReportsOverdue: number;
    overdueTasks: number;
    tasksDueSoon: number;
  };
  /** Figures still waiting for a report port; empty since Phase 5 is integrated. */
  pending: PendingFigure[];
}

export function getDashboard(signal?: AbortSignal): Promise<DashboardReport> {
  return api('/api/v1/reports/dashboard', signal === undefined ? {} : { signal });
}

/** The answer has the parts the page reads; anything else is treated as a failed load. */
export function isDashboard(value: unknown): value is DashboardReport {
  if (typeof value !== 'object' || value === null) return false;
  const report = value as Partial<DashboardReport>;
  return (
    typeof report.cases?.total === 'number' &&
    Array.isArray(report.proceedings?.byStage) &&
    typeof report.judgments?.recorded === 'number' &&
    typeof report.operations?.overdueTasks === 'number'
  );
}
