import { api } from '../../../api/http';
import { listAll } from '../../../api/pagedList';
import { blankToNull } from '../formErrors';

/** Tasks, assignments and deadline reminders (LD-027, LD-028, docs/delivery/PHASE-5-ACCEPTANCE.md). */
export const TASK_STATUSES = [
  'NEW',
  'IN_PROGRESS',
  'WAITING_DOCUMENTS',
  'WAITING_OTHER',
  'COMPLETED',
  'LATE',
  'CANCELLED',
] as const;
export type TaskStatus = (typeof TASK_STATUSES)[number];

export const TASK_PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'] as const;
export type TaskPriority = (typeof TASK_PRIORITIES)[number];

export const DUE_FILTERS = ['overdue', 'soon'] as const;
export type DueFilter = (typeof DUE_FILTERS)[number];

/**
 * The moves the backend allows from each status (requirements section 14). LATE is set by the follow-up run, never chosen;
 * a late task is moved on, or gets a new due date.
 */
export const NEXT_STATUSES: Record<TaskStatus, readonly TaskStatus[]> = {
  NEW: ['IN_PROGRESS', 'WAITING_DOCUMENTS', 'WAITING_OTHER', 'COMPLETED', 'CANCELLED'],
  IN_PROGRESS: ['WAITING_DOCUMENTS', 'WAITING_OTHER', 'COMPLETED', 'NEW', 'CANCELLED'],
  WAITING_DOCUMENTS: ['IN_PROGRESS', 'WAITING_OTHER', 'COMPLETED', 'NEW', 'CANCELLED'],
  WAITING_OTHER: ['IN_PROGRESS', 'WAITING_DOCUMENTS', 'COMPLETED', 'NEW', 'CANCELLED'],
  LATE: ['IN_PROGRESS', 'WAITING_DOCUMENTS', 'WAITING_OTHER', 'COMPLETED', 'CANCELLED'],
  COMPLETED: ['NEW'],
  CANCELLED: ['NEW'],
};

export interface Task {
  id: string;
  caseId: string | null;
  caseReferenceNo: string | null;
  caseTitle: string | null;
  title: string;
  description: string | null;
  assigneeId: string;
  assigneeName: string | null;
  startsOn: string | null;
  dueDate: string | null;
  priority: TaskPriority;
  status: TaskStatus;
  completedAt: string | null;
  overdue: boolean;
  dueSoon: boolean;
  instructions: string | null;
  delayReason: string | null;
  delayReasonAt: string | null;
  version: number;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
}

export interface TaskPage {
  items: Task[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface Reminders {
  today: string;
  dueSoonDays: number;
  overdue: number;
  dueSoon: number;
}

export interface DirectoryUser {
  id: string;
  username: string;
  displayName: string;
  enabled: boolean;
}

export interface TaskFilter {
  /** `me` for the signed-in user's tasks, nothing for everyone's. */
  assignee?: 'me' | undefined;
  caseId?: string | undefined;
  status?: TaskStatus | undefined;
  due?: DueFilter | undefined;
  page: number;
  size?: number | undefined;
}

export interface TaskInput {
  title: string;
  description: string;
  assigneeId: string;
  startsOn: string;
  dueDate: string;
  priority: TaskPriority;
  instructions: string;
}

/** One line of the history of a task: who changed what (V720 task_history). */
export interface TaskHistoryEntry {
  id: string;
  changedAt: string;
  actorId: string | null;
  actorName: string | null;
  action: 'CREATED' | 'UPDATED' | 'STATUS' | 'REASSIGNED' | 'DELAY_REASON' | 'SYSTEM_LATE' | 'SYSTEM_RESTORED';
  field: string | null;
  oldValue: string | null;
  newValue: string | null;
  reason: string | null;
}

export const TASK_PAGE_SIZE = 20;

const taskPath = (id: string, suffix = '') => `/api/v1/tasks/${encodeURIComponent(id)}${suffix}`;

export function listTasks(filter: TaskFilter, signal: AbortSignal): Promise<TaskPage> {
  const query = new URLSearchParams({ page: String(filter.page), size: String(filter.size ?? TASK_PAGE_SIZE) });
  if (filter.assignee !== undefined) query.set('assignee', filter.assignee);
  if (filter.caseId !== undefined) query.set('caseId', filter.caseId);
  if (filter.status !== undefined) query.set('status', filter.status);
  if (filter.due !== undefined) query.set('due', filter.due);
  return api<TaskPage>(`/api/v1/tasks?${query.toString()}`, { signal });
}

/** Every task of the case, read page by page (QAD-044). */
export function listCaseTasks(caseId: string, signal: AbortSignal): Promise<Task[]> {
  return listAll<Task>('/api/v1/tasks', { caseId }, signal);
}

export function getReminders(signal: AbortSignal): Promise<Reminders> {
  return api<Reminders>('/api/v1/tasks/reminders', { signal });
}

export function listAssignees(signal: AbortSignal): Promise<DirectoryUser[]> {
  return api<DirectoryUser[]>('/api/v1/users/directory', { signal });
}

function body(input: TaskInput) {
  return {
    title: input.title,
    description: blankToNull(input.description),
    assigneeId: input.assigneeId === '' ? null : input.assigneeId,
    startsOn: blankToNull(input.startsOn),
    dueDate: blankToNull(input.dueDate),
    priority: input.priority,
    instructions: blankToNull(input.instructions),
  };
}

export function createTask(input: TaskInput, caseId: string | null): Promise<Task> {
  return api<Task>('/api/v1/tasks', { method: 'POST', body: { ...body(input), caseId } });
}

export function updateTask(id: string, input: TaskInput, version: number): Promise<Task> {
  return api<Task>(taskPath(id), { method: 'PUT', body: { ...body(input), version } });
}

export function changeTaskStatus(id: string, status: TaskStatus, version: number): Promise<Task> {
  return api<Task>(taskPath(id, '/status'), { method: 'POST', body: { status, version } });
}

export function getTaskHistory(id: string, signal?: AbortSignal): Promise<TaskHistoryEntry[]> {
  return api<TaskHistoryEntry[]>(taskPath(id, '/history'), signal === undefined ? {} : { signal });
}

export function reassignTask(id: string, assigneeId: string, reason: string, version: number): Promise<Task> {
  return api<Task>(taskPath(id, '/reassign'), { method: 'POST', body: { assigneeId, reason: blankToNull(reason), version } });
}

export function recordDelayReason(id: string, reason: string, version: number): Promise<Task> {
  return api<Task>(taskPath(id, '/delay-reason'), { method: 'POST', body: { reason, version } });
}
