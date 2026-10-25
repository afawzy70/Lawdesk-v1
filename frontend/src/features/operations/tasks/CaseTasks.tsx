import { useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../../i18n/catalog';
import { useI18n } from '../../../i18n/I18nContext';
import { loadError, saveError } from '../formErrors';
import { TaskForm } from './TaskForm';
import { TaskActions, TaskDue } from './TaskParts';
import { changeTaskStatus, listTasks, type Task, type TaskStatus } from './tasksApi';

type Result = { key: number; tasks: Task[] } | { key: number; error: MessageKey };
type Editing = null | 'new' | Task;

interface Props {
  caseId: string;
}

/** The tasks of one case (LD-027), with the forms to add and edit them and their status moves. */
export function CaseTasks({ caseId }: Props) {
  const { t } = useI18n();
  const headingId = useId();
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const [editing, setEditing] = useState<Editing>(null);
  const [notice, setNotice] = useState<'saved' | MessageKey | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    listTasks({ caseId, page: 0, size: 100 }, controller.signal).then(
      (page) => {
        setResult({ key: reload, tasks: page.items });
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

  const body = () => {
    if (current === null) return <p>{t('operations.loading')}</p>;
    if ('error' in current) {
      return (
        // Not role="alert": this section loads with the case page, whose own alerts must stay the only ones announced.
        <div className="ops-alert ops-load-error">
          <p>{t(current.error === 'operations.error.viewForbidden' ? 'operations.task.hidden' : current.error)}</p>
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
    return (
      <>
        <button
          type="button"
          onClick={() => {
            setNotice(null);
            setEditing('new');
          }}
        >
          {t('operations.task.new')}
        </button>
        {editing === null ? null : (
          <TaskForm
            key={editing === 'new' ? 'new' : editing.id}
            task={editing === 'new' ? undefined : editing}
            caseId={caseId}
            onSaved={changed}
            onCancel={() => {
              setEditing(null);
            }}
          />
        )}
        {current.tasks.length === 0 ? (
          <p>{t('operations.task.none')}</p>
        ) : (
          <ul className="ops-list">
            {current.tasks.map((task) => (
              <li key={task.id} className="ops-item">
                <p>
                  <strong>{task.title}</strong>
                </p>
                <p>
                  {t('operations.task.assignedTo', { name: task.assigneeName ?? t('operations.task.unknownUser') })}
                  {' · '}
                  {t(`operations.priority.${task.priority}`)}
                  {' · '}
                  {t(`operations.taskStatus.${task.status}`)}
                </p>
                <p>
                  <TaskDue task={task} />
                </p>
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
              </li>
            ))}
          </ul>
        )}
      </>
    );
  };

  return (
    <section className="ops-section" aria-labelledby={headingId}>
      <h3 id={headingId}>{t('operations.tasks.caseTitle')}</h3>
      {notice === 'saved' ? (
        <p role="status" className="ops-saved">
          {t('operations.task.saved')}
        </p>
      ) : null}
      {notice !== null && notice !== 'saved' ? <p className="ops-alert">{t(notice)}</p> : null}
      {body()}
    </section>
  );
}
