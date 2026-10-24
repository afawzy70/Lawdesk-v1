import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import type { MessageKey } from '../../../i18n/catalog';
import { formatNumber } from '../../../i18n/format';
import { useI18n } from '../../../i18n/I18nContext';
import { usePageTitle } from '../../../i18n/usePageTitle';
import { AccessDeniedPage } from '../../auth/AccessDeniedPage';
import { casePath } from '../../cases/paths';
import { loadError, saveError } from '../formErrors';
import { TaskForm } from './TaskForm';
import { TaskActions, TaskDue } from './TaskParts';
import {
  changeTaskStatus,
  DUE_FILTERS,
  getReminders,
  listTasks,
  TASK_STATUSES,
  type DueFilter,
  type Reminders,
  type Task,
  type TaskFilter,
  type TaskPage,
  type TaskStatus,
} from './tasksApi';
import '../operations.css';

type Result = { key: string; page: TaskPage } | { key: string; error: MessageKey };
type Editing = null | 'new' | Task;

/**
 * Tasks of the office (LD-027) with the signed-in user's in-app reminders (LD-028): "my tasks" or every task, filtered
 * by status and due state, with create, edit and status moves. The backend checks TASK_VIEW and TASK_MANAGE.
 */
export function TasksPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('operations.tasks.title'));
  const captionId = useId();
  const filterId = useId();
  const [filter, setFilter] = useState<TaskFilter>({ assignee: 'me', page: 0 });
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const [reminders, setReminders] = useState<Reminders | 'failed' | null>(null);
  const [editing, setEditing] = useState<Editing>(null);
  const [notice, setNotice] = useState<'saved' | MessageKey | null>(null);
  const [busy, setBusy] = useState(false);
  const requestKey = `${JSON.stringify(filter)}#${String(reload)}`;

  useEffect(() => {
    const controller = new AbortController();
    listTasks(filter, controller.signal).then(
      (page) => {
        setResult({ key: requestKey, page });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: requestKey, error: loadError(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [filter, requestKey]);

  useEffect(() => {
    const controller = new AbortController();
    getReminders(controller.signal).then(setReminders, () => {
      if (!controller.signal.aborted) setReminders('failed');
    });
    return () => {
      controller.abort();
    };
  }, [reload]);

  const current = result?.key === requestKey ? result : null;
  if (current !== null && 'error' in current && current.error === 'operations.error.viewForbidden') {
    return <AccessDeniedPage />;
  }

  const changed = () => {
    setEditing(null);
    setNotice('saved');
    setReload((n) => n + 1);
  };
  const move = async (task: Task, next: TaskStatus) => {
    setBusy(true);
    setNotice(null);
    try {
      await changeTaskStatus(task.id, next, task.version);
      changed();
    } catch (error) {
      setNotice(saveError(error, 'operations.error.stale'));
    }
    setBusy(false);
  };
  const remind = (due: DueFilter) => {
    setFilter({ assignee: 'me', due, page: 0 });
  };
  const scope = (assignee: 'me' | undefined) => {
    setFilter((f) => ({ ...f, assignee, page: 0 }));
  };

  return (
    <>
      <h1>{t('operations.tasks.title')}</h1>

      <section className="ops-reminders" aria-label={t('operations.tasks.reminders')}>
        {reminders === null ? <p>{t('operations.loading')}</p> : null}
        {reminders === 'failed' ? <p className="ops-hint">{t('operations.tasks.remindersFailed')}</p> : null}
        {reminders !== null && reminders !== 'failed' ? (
          <ul className="ops-reminder-list">
            <li>
              {reminders.overdue === 0 ? (
                t('operations.tasks.noneOverdue')
              ) : (
                <button
                  type="button"
                  className="ops-reminder ops-reminder-danger"
                  onClick={() => {
                    remind('overdue');
                  }}
                >
                  {t('operations.tasks.overdueCount', { count: formatNumber(reminders.overdue, locale) })}
                </button>
              )}
            </li>
            <li>
              {reminders.dueSoon === 0 ? (
                t('operations.tasks.noneDueSoon', { days: formatNumber(reminders.dueSoonDays, locale) })
              ) : (
                <button
                  type="button"
                  className="ops-reminder"
                  onClick={() => {
                    remind('soon');
                  }}
                >
                  {t('operations.tasks.dueSoonCount', {
                    count: formatNumber(reminders.dueSoon, locale),
                    days: formatNumber(reminders.dueSoonDays, locale),
                  })}
                </button>
              )}
            </li>
          </ul>
        ) : null}
      </section>

      <div className="ops-toolbar">
        <div className="ops-actions" role="group" aria-label={t('operations.tasks.scope')}>
          <button
            type="button"
            aria-pressed={filter.assignee === 'me'}
            onClick={() => {
              scope('me');
            }}
          >
            {t('operations.tasks.mine')}
          </button>
          <button
            type="button"
            aria-pressed={filter.assignee === undefined}
            onClick={() => {
              scope(undefined);
            }}
          >
            {t('operations.tasks.everyone')}
          </button>
        </div>
        <fieldset className="ops-filters">
          <legend>{t('operations.filters')}</legend>
          <label htmlFor={`${filterId}-status`}>{t('operations.tasks.status')}</label>
          <select
            id={`${filterId}-status`}
            value={filter.status ?? ''}
            onChange={(event) => {
              const value = event.target.value;
              setFilter((f) => ({ ...f, page: 0, status: value === '' ? undefined : (value as TaskStatus) }));
            }}
          >
            <option value="">{t('operations.all')}</option>
            {TASK_STATUSES.map((status) => (
              <option key={status} value={status}>
                {t(`operations.taskStatus.${status}`)}
              </option>
            ))}
          </select>
          <label htmlFor={`${filterId}-due`}>{t('operations.tasks.due')}</label>
          <select
            id={`${filterId}-due`}
            value={filter.due ?? ''}
            onChange={(event) => {
              const value = event.target.value;
              setFilter((f) => ({ ...f, page: 0, due: value === '' ? undefined : (value as DueFilter) }));
            }}
          >
            <option value="">{t('operations.tasks.anyDue')}</option>
            {DUE_FILTERS.map((due) => (
              <option key={due} value={due}>
                {t(`operations.tasks.due.${due}`)}
              </option>
            ))}
          </select>
        </fieldset>
        <button
          type="button"
          onClick={() => {
            setNotice(null);
            setEditing('new');
          }}
        >
          {t('operations.task.new')}
        </button>
      </div>

      {notice === 'saved' ? (
        <p role="status" className="ops-saved">
          {t('operations.task.saved')}
        </p>
      ) : null}
      {notice !== null && notice !== 'saved' ? (
        <p role="alert" className="ops-alert">
          {t(notice)}
        </p>
      ) : null}

      {editing === null ? null : (
        <TaskForm
          key={editing === 'new' ? 'new' : editing.id}
          task={editing === 'new' ? undefined : editing}
          caseId={editing === 'new' ? null : editing.caseId}
          onSaved={changed}
          onCancel={() => {
            setEditing(null);
          }}
        />
      )}

      {current === null ? <p>{t('operations.loading')}</p> : null}
      {current !== null && 'error' in current ? (
        <div role="alert" className="ops-alert">
          <p>{t(current.error)}</p>
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
      ) : null}
      {current !== null && 'page' in current && current.page.items.length === 0 ? (
        <p>{t('operations.tasks.empty')}</p>
      ) : null}
      {current !== null && 'page' in current && current.page.items.length > 0 ? (
        <>
          <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
            <table className="ops-table">
              <caption id={captionId} className="visually-hidden">
                {t('operations.tasks.title')}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{t('operations.task.title')}</th>
                  <th scope="col">{t('operations.task.case')}</th>
                  <th scope="col">{t('operations.task.assignee')}</th>
                  <th scope="col">{t('operations.task.due')}</th>
                  <th scope="col">{t('operations.task.priority')}</th>
                  <th scope="col">{t('operations.tasks.status')}</th>
                  <th scope="col">{t('operations.task.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {current.page.items.map((task) => (
                  <tr key={task.id}>
                    <td className="ops-wrap">{task.title}</td>
                    <td className="ops-wrap">
                      {task.caseId === null ? (
                        t('operations.task.noCase')
                      ) : (
                        <Link to={`${casePath(task.caseId)}?lang=${locale}`}>
                          <bdi dir="ltr">{task.caseReferenceNo ?? task.caseId}</bdi>
                        </Link>
                      )}
                    </td>
                    <td className="ops-wrap">{task.assigneeName ?? t('operations.task.unknownUser')}</td>
                    <td>
                      <TaskDue task={task} />
                    </td>
                    <td>{t(`operations.priority.${task.priority}`)}</td>
                    <td>{t(`operations.taskStatus.${task.status}`)}</td>
                    <td>
                      <TaskActions
                        task={task}
                        busy={busy}
                        onEdit={(target) => {
                          setNotice(null);
                          setEditing(target);
                        }}
                        onMove={(target, next) => {
                          void move(target, next);
                        }}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {current.page.totalPages > 1 ? (
            <nav className="ops-pager" aria-label={t('operations.tasks.pages')}>
              <button
                type="button"
                disabled={current.page.page === 0}
                onClick={() => {
                  setFilter((f) => ({ ...f, page: f.page - 1 }));
                }}
              >
                {t('operations.previous')}
              </button>
              <span>
                {t('operations.pageOf', {
                  page: formatNumber(current.page.page + 1, locale),
                  pages: formatNumber(current.page.totalPages, locale),
                })}
              </span>
              <button
                type="button"
                disabled={current.page.page + 1 >= current.page.totalPages}
                onClick={() => {
                  setFilter((f) => ({ ...f, page: f.page + 1 }));
                }}
              >
                {t('operations.next')}
              </button>
            </nav>
          ) : null}
        </>
      ) : null}
    </>
  );
}
