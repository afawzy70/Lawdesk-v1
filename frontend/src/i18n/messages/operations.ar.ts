import type { operationsEn } from './operations.en';

/** Arabic operations messages; exactly the keys of operations.en.ts. */
export const operationsAr: Record<keyof typeof operationsEn, string> = {
  'operations.title': 'العمليات القانونية',
} as const;
