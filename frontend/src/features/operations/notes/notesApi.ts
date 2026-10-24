import { api } from '../../../api/http';

/** Legal notes of a case (LD-029, docs/delivery/PHASE-5-ACCEPTANCE.md). */
export interface CaseNote {
  id: string;
  caseId: string;
  body: string;
  authorId: string;
  authorName: string;
  /** The signed-in user wrote it, so may change or remove it. */
  editable: boolean;
  edited: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

const casePath = (caseId: string) => `/api/v1/cases/${encodeURIComponent(caseId)}/notes`;
const notePath = (id: string) => `/api/v1/notes/${encodeURIComponent(id)}`;

/** The newest hundred notes of the case. */
export async function listNotes(caseId: string, signal: AbortSignal): Promise<CaseNote[]> {
  return (await api<{ items: CaseNote[] }>(`${casePath(caseId)}?size=100`, { signal })).items;
}

export function addNote(caseId: string, body: string): Promise<CaseNote> {
  return api<CaseNote>(casePath(caseId), { method: 'POST', body: { body } });
}

export function editNote(note: CaseNote, body: string): Promise<CaseNote> {
  return api<CaseNote>(notePath(note.id), { method: 'PUT', body: { body, version: note.version } });
}

export function removeNote(note: CaseNote): Promise<void> {
  return api<undefined>(`${notePath(note.id)}?version=${String(note.version)}`, { method: 'DELETE' });
}
