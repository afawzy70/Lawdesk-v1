import { useEffect, useState } from 'react';
import { useI18n } from '../../../i18n/I18nContext';
import { useFormErrors } from '../formErrors';
import { Field, FormShell } from '../FormParts';
import {
  createTask,
  listAssignees,
  TASK_PRIORITIES,
  updateTask,
  type DirectoryUser,
  type Task,
  type TaskInput,
} from './tasksApi';

/** Creates or edits one task (LD-027). The backend validates; the form shows its answers next to the fields. */

const FIELDS = ['title', 'description', 'assigneeId', 'dueDate', 'priority'] as const;
type TaskField = (typeof FIELDS)[number];

interface Props {
  /** The task being edited; without one the form creates a task. */
  task?: Task | undefined;
  /** The case a new task belongs to; `null` for a task of the office. */
  caseId: string | null;
  onSaved: (task: Task) => void;
  onCancel: () => void;
}

export function TaskForm({ task, caseId, onSaved, onCancel }: Props) {
  const { t } = useI18n();
  const form = useFormErrors<TaskField>();
  const [values, setValues] = useState<TaskInput>({
    title: task?.title ?? '',
    description: task?.description ?? '',
    assigneeId: task?.assigneeId ?? '',
    dueDate: task?.dueDate ?? '',
    priority: task?.priority ?? 'NORMAL',
  });
  const [users, setUsers] = useState<DirectoryUser[] | 'failed' | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    listAssignees(controller.signal).then(setUsers, () => {
      if (!controller.signal.aborted) setUsers('failed');
    });
    return () => {
      controller.abort();
    };
  }, []);

  const set = (field: keyof TaskInput) => (value: string) => {
    setValues((previous) => ({ ...previous, [field]: value }));
  };

  const submit = async () => {
    setSaving(true);
    try {
      onSaved(task === undefined ? await createTask(values, caseId) : await updateTask(task.id, values, task.version));
    } catch (error) {
      form.fail(error, 'operations.error.stale', FIELDS);
      setSaving(false);
    }
  };

  const options = Array.isArray(users) ? users : [];
  // An assignee who left the directory (disabled) stays selectable on the task they already hold.
  const keepCurrent = task !== undefined && !options.some((u) => u.id === task.assigneeId);

  return (
    <FormShell
      label={task === undefined ? t('operations.task.new') : t('operations.task.editTitle', { title: task.title })}
      error={form.formError}
      saving={saving}
      submitLabel={task === undefined ? t('operations.task.create') : t('operations.save')}
      onSubmit={() => {
        void submit();
      }}
      onCancel={onCancel}
    >
      <Field id={form.control('title').id} label={t('operations.task.title')} error={form.errors.title}
        errorId={form.errorId('title')}>
        <input {...form.control('title')} type="text" required maxLength={200} value={values.title}
          onChange={(event) => { set('title')(event.target.value); }} />
      </Field>
      <Field id={form.control('description').id} label={t('operations.task.descriptionOptional')}
        error={form.errors.description} errorId={form.errorId('description')}>
        <textarea {...form.control('description')} rows={3} maxLength={4000} value={values.description}
          onChange={(event) => { set('description')(event.target.value); }} />
      </Field>
      <Field id={form.control('assigneeId').id} label={t('operations.task.assignee')} error={form.errors.assigneeId}
        errorId={form.errorId('assigneeId')}
        hint={users === 'failed' ? t('operations.task.usersFailed') : undefined}>
        <select {...form.control('assigneeId')} required value={values.assigneeId}
          onChange={(event) => { set('assigneeId')(event.target.value); }}>
          <option value="">{t('operations.choose')}</option>
          {keepCurrent ? <option value={task.assigneeId}>{task.assigneeName ?? task.assigneeId}</option> : null}
          {options.map((u) => (
            <option key={u.id} value={u.id}>{u.displayName}</option>
          ))}
        </select>
      </Field>
      <Field id={form.control('dueDate').id} label={t('operations.task.dueOptional')} error={form.errors.dueDate}
        errorId={form.errorId('dueDate')}>
        <input {...form.control('dueDate')} type="date" value={values.dueDate}
          onChange={(event) => { set('dueDate')(event.target.value); }} />
      </Field>
      <Field id={form.control('priority').id} label={t('operations.task.priority')} error={form.errors.priority}
        errorId={form.errorId('priority')}>
        <select {...form.control('priority')} value={values.priority}
          onChange={(event) => { set('priority')(event.target.value); }}>
          {TASK_PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>{t(`operations.priority.${priority}`)}</option>
          ))}
        </select>
      </Field>
    </FormShell>
  );
}
