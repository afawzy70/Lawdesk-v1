import { api } from '../../../api/http';
import { blankToNull } from '../formErrors';

/** Tasks, assignments and deadline reminders (LD-027, LD-028, docs/delivery/PHASE-5-ACCEPTANCE.md). */
export const TASK_STATUSES = ['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED'] as const;
export type TaskStatus = (typeof TASK_STATUSES)[number];

export const TASK_PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'] as const;
export type TaskPriority = (typeof TASK_PRIORITIES)[number];

export const DUE_FILTERS = ['overdue', 'soon'] as const;
export type DueFilter = (typeof DUE_FILTERS)[number];

/** The moves the backend allows from each status (ASSUMPTION, PHASE-5-ACCEPTANCE LD-027). */
export const NEXT_STATUSES: Record<TaskStatus, readonly TaskStatus[]> = {
  OPEN: ['IN_PROGRESS', 'DONE', 'CANCELLED'],
  IN_PROGRESS: ['DONE', 'OPEN', 'CANCELLED'],
  DONE: ['OPEN'],
  CANCELLED: ['OPEN'],
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
  dueDate: string | null;
  priority: TaskPriority;
  status: TaskStatus;
  completedAt: string | null;
  overdue: boolean;
  dueSoon: boolean;
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
  dueDate: string;
  priority: TaskPriority;
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
    dueDate: blankToNull(input.dueDate),
    priority: input.priority,
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
