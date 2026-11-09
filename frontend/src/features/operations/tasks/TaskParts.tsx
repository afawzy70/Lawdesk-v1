import { useI18n } from '../../../i18n/I18nContext';
import { formatDate } from '../../proceedings/dates';
import { NEXT_STATUSES, type Task, type TaskStatus } from './tasksApi';

/** Pieces shared by the task list and the case's tasks (LD-027, LD-028). */

/** What the button moving a task to `next` is called, from its current status. */
function moveLabel(from: TaskStatus, next: TaskStatus) {
  switch (next) {
    case 'IN_PROGRESS':
      return from === 'NEW' ? ('operations.task.move.start' as const) : ('wp6.task.move.resume' as const);
    case 'WAITING_DOCUMENTS':
      return 'wp6.task.move.waitingDocuments' as const;
    case 'WAITING_OTHER':
      return 'wp6.task.move.waitingOther' as const;
    case 'COMPLETED':
      return 'operations.task.move.complete' as const;
    case 'CANCELLED':
      return 'operations.task.move.cancel' as const;
    default:
      // NEW: LATE is never a target (the follow-up run sets it).
      return from === 'COMPLETED' || from === 'CANCELLED'
        ? ('operations.task.move.reopen' as const)
        : ('operations.task.move.backToOpen' as const);
  }
}

interface ActionsProps {
  task: Task;
  busy: boolean;
  onEdit: (task: Task) => void;
  onMove: (task: Task, next: TaskStatus) => void;
  /** Opens the details of the task: instructions, delay reason, reassignment and history. */
  onDetails: (task: Task) => void;
}

/** Edit, then the status moves the backend allows. Each button names the task for screen readers. */
export function TaskActions({ task, busy, onEdit, onMove, onDetails }: ActionsProps) {
  const { t } = useI18n();
  const named = (label: string) => t('operations.task.actionLabel', { action: label, title: task.title });
  return (
    <div className="ops-actions">
      <button
        type="button"
        aria-label={named(t('operations.edit'))}
        onClick={() => {
          onEdit(task);
        }}
      >
        {t('operations.edit')}
      </button>
      <button
        type="button"
        aria-label={named(t('wp6.task.details'))}
        onClick={() => {
          onDetails(task);
        }}
      >
        {t('wp6.task.details')}
      </button>
      {NEXT_STATUSES[task.status].map((next) => {
        const label = t(moveLabel(task.status, next));
        return (
          <button
            key={next}
            type="button"
            disabled={busy}
            aria-label={named(label)}
            onClick={() => {
              onMove(task, next);
            }}
          >
            {label}
          </button>
        );
      })}
    </div>
  );
}

/** The due date with the overdue and due-soon reminders (LD-028). */
export function TaskDue({ task }: { task: Task }) {
  const { locale, t } = useI18n();
  return (
    <>
      {task.dueDate === null ? t('operations.task.noDue') : formatDate(task.dueDate, locale)}
      {task.overdue ? <span className="ops-badge ops-badge-danger">{t('operations.task.overdue')}</span> : null}
      {task.dueSoon ? <span className="ops-badge ops-badge-warn">{t('operations.task.dueSoon')}</span> : null}
    </>
  );
}
