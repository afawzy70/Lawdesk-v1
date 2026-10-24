import type { reportsEn } from './reports.en';

/** Arabic reports messages; exactly the keys of reports.en.ts. */
export const reportsAr: Record<keyof typeof reportsEn, string> = {
  'reports.title': 'التقارير',
} as const;
