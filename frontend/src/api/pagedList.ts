import { api } from './http';

/**
 * Reading a whole list through the paging contract (architecture/CONTRACTS.md §4): pages of the maximum size, in the
 * server's order, until the last one. A list too long to read is an error, never a silently shortened list (QAD-044).
 */
export const LIST_PAGE_SIZE = 100;
export const MAX_LIST_PAGES = 50;

interface Page<T> {
  items: T[];
  totalPages: number;
}

export async function listAll<T extends { id: string }>(
  path: string,
  params: Record<string, string>,
  signal: AbortSignal,
): Promise<T[]> {
  const all: T[] = [];
  const seen = new Set<string>();
  for (let page = 0; ; page += 1) {
    const query = new URLSearchParams({ page: String(page), size: String(LIST_PAGE_SIZE), ...params });
    const result = await api<Page<T>>(`${path}?${query.toString()}`, { signal });
    for (const item of result.items) {
      if (!seen.has(item.id)) {
        seen.add(item.id);
        all.push(item);
      }
    }
    if (page + 1 >= result.totalPages) return all;
    if (page + 1 >= MAX_LIST_PAGES) throw new Error('The list is too long to be read in full');
  }
}
