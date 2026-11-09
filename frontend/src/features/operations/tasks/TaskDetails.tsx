import { useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../../i18n/catalog';
import { formatDateTime } from '../../../i18n/format';
import { useI18n } from '../../../i18n/I18nContext';
import { formatDate } from '../../proceedings/dates';
import { saveError } from '../formErrors';
import {
  getTaskHistory,
  listAssignees,
  recordDelayReason,
  reassignTask,
  type DirectoryUser,
  type Task,
  type TaskHistoryEntry,
} from './tasksApi';

interface Props {
  task: Task;
  /** Called with the task as the server now holds it, after a delay reason or a reassignment. */
  onChanged: (task: Task) => void;
  onClose: () => void;
}

/**
 * The details of one task (requirements section 14): instructions, the reason for delay, reassignment and the history of who
 * changed what. The assignee of a task records the delay reason; reassignment is refused by the server to anyone who may not
 * move the task, and the answer is shown.
 */
export function TaskDetails({ task, onChanged, onClose }: Props) {
  const { locale, t } = useI18n();
  const id = useId();
  const [history, setHistory] = useState<TaskHistoryEntry[] | 'failed' | null>(null);
  const [users, setUsers] = useState<DirectoryUser[]>([]);
  const [reason, setReason] = useState('');
  const [assignee, setAssignee] = useState('');
  const [why, setWhy] = useState('');
  const [error, setError] = useState<MessageKey | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    getTaskHistory(task.id, controller.signal).then(setHistory, () => {
      if (!controller.signal.aborted) setHistory('failed');
    });
    listAssignees(controller.signal).then(setUsers, () => undefined);
    return () => {
      controller.abort();
    };
  }, [task.id, task.version]);

  const nameOf = (value: string | null) =>
    value === null ? t('wp6.history.none') : (users.find((u) => u.id === value)?.displayName ?? value);

  const run = async (action: () => Promise<Task>, after: () => void) => {
    setBusy(true);
    setError(null);
    setSaved(false);
    try {
      const changed = await action();
      after();
      setSaved(true);
      onChanged(changed);
    } catch (failure) {
      setError(saveError(failure, 'operations.error.stale'));
    }
    setBusy(false);
  };

  const describe = (entry: TaskHistoryEntry): string => {
    const who = entry.actorName ?? t('wp6.history.system');
    switch (entry.action) {
      case 'CREATED':
        return t('wp6.history.CREATED', { who });
      case 'STATUS':
      case 'SYSTEM_LATE':
      case 'SYSTEM_RESTORED':
        return t(`wp6.history.${entry.action}`, {
          who,
          from: t(`operations.taskStatus.${entry.oldValue === 'LATE' ? 'LATE' : (entry.oldValue ?? 'NEW')}` as MessageKey),
          to: t(`operations.taskStatus.${entry.newValue ?? 'NEW'}` as MessageKey),
        });
      case 'REASSIGNED':
        return t('wp6.history.REASSIGNED', { who, from: nameOf(entry.oldValue), to: nameOf(entry.newValue) });
      case 'DELAY_REASON':
        return t('wp6.history.DELAY_REASON', { who, text: entry.newValue ?? '' });
      case 'UPDATED':
        return t('wp6.history.UPDATED', { who, field: t(`wp6.history.field.${entry.field ?? 'title'}` as MessageKey) });
    }
  };

  return (
    <section className="ops-form wp6-details" aria-label={t('wp6.task.detailsTitle', { title: task.title })}>
      <h3>{t('wp6.task.detailsTitle', { title: task.title })}</h3>
      <dl className="wp6-facts">
        <dt>{t('wp6.task.startsOn')}</dt>
        <dd>{task.startsOn === null ? t('operations.task.noDue') : formatDate(task.startsOn, locale)}</dd>
        <dt>{t('wp6.task.instructions')}</dt>
        <dd className="ops-wrap">{task.instructions ?? t('wp6.task.noInstructions')}</dd>
        <dt>{t('wp6.task.delayReason')}</dt>
        <dd className="ops-wrap">
          {task.delayReason === null
            ? t('wp6.task.noDelayReason')
            : `${task.delayReason}${task.delayReasonAt === null ? '' : ` (${formatDateTime(task.delayReasonAt, locale)})`}`}
        </dd>
      </dl>
      {error === null ? null : (
        <p role="alert" className="ops-alert">
          {t(error)}
        </p>
      )}
      {saved ? (
        <p role="status" className="ops-saved">
          {t('operations.task.saved')}
        </p>
      ) : null}

      <div className="ops-field">
        <label htmlFor={`${id}-reason`}>{t('wp6.task.delayReasonField')}</label>
        <textarea id={`${id}-reason`} rows={2} maxLength={2000} value={reason}
          onChange={(event) => { setReason(event.target.value); }} />
        <button type="button" disabled={busy || reason.trim() === ''}
          onClick={() => void run(() => recordDelayReason(task.id, reason.trim(), task.version), () => { setReason(''); })}>
          {t('wp6.task.saveDelayReason')}
        </button>
      </div>

      <div className="ops-field">
        <label htmlFor={`${id}-assignee`}>{t('wp6.task.reassignTo')}</label>
        <select id={`${id}-assignee`} value={assignee} onChange={(event) => { setAssignee(event.target.value); }}>
          <option value="">{t('operations.choose')}</option>
          {users.filter((u) => u.id !== task.assigneeId).map((u) => (
            <option key={u.id} value={u.id}>{u.displayName}</option>
          ))}
        </select>
        <label htmlFor={`${id}-why`}>{t('wp6.task.reassignReason')}</label>
        <input id={`${id}-why`} type="text" maxLength={500} value={why}
          onChange={(event) => { setWhy(event.target.value); }} />
        <button type="button" disabled={busy || assignee === ''}
          onClick={() => void run(() => reassignTask(task.id, assignee, why, task.version), () => { setAssignee(''); setWhy(''); })}>
          {t('wp6.task.reassign')}
        </button>
      </div>

      <h4>{t('wp6.task.history')}</h4>
      {history === null ? <p>{t('operations.loading')}</p> : null}
      {history === 'failed' ? <p className="ops-alert">{t('wp6.task.historyFailed')}</p> : null}
      {Array.isArray(history) ? (
        <ol className="ops-list wp6-history">
          {history.map((entry) => (
            <li key={entry.id} className="ops-item">
              <p>{describe(entry)}</p>
              <p className="ops-hint">
                {formatDateTime(entry.changedAt, locale)}
                {entry.reason === null ? '' : ` · ${entry.reason}`}
              </p>
            </li>
          ))}
        </ol>
      ) : null}
      <div className="ops-form-actions">
        <button type="button" onClick={onClose}>{t('wp6.close')}</button>
      </div>
    </section>
  );
}
