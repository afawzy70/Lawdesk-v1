import { api } from '../../api/http';

/**
 * The case brief (requirements section 22, the rows that are queries over the system's own records): read-only and derived
 * from stored records by fixed rules. No model and no provider are involved; `modelGenerated` is always false.
 */

export const NOT_RECORDED = 'NOT_RECORDED';

/** Where an item comes from. `page` is null: none of the records stores a page number. */
export interface BriefSource {
  type: string;
  id: string;
  date: string | null;
  documentId: string | null;
  documentTitle: string | null;
  page: number | null;
}

export interface BriefEntry {
  kind: string;
  title: string | null;
  status: string;
  date: string | null;
  text: string | null;
  source: BriefSource;
  fields: Record<string, string>;
  children: BriefEntry[];
}

/** RECORDED (items found), NOT_RECORDED (the records hold none) or NOT_PERMITTED (the user may not read them). */
export interface BriefSection {
  status: 'RECORDED' | 'NOT_RECORDED' | 'NOT_PERMITTED';
  items: BriefEntry[];
  omitted: string[];
}

export const SECTION_KEYS = [
  'lastAction',
  'nextHearing',
  'nextRequiredActions',
  'afterLastHearing',
  'timeline',
  'requests',
  'rejectedRequests',
  'defenses',
  'defensesNotRuledOn',
  'openDeadlines',
  'appealDeadlines',
  'missingFiles',
  'facts',
  'evidence',
  'ourPosition',
  'legalTexts',
  'linkedJudgments',
  'similarJudgments',
] as const;
export type SectionKey = (typeof SECTION_KEYS)[number];

export type Brief = {
  caseId: string;
  origin: string;
  modelGenerated: boolean;
  notice: string;
  asOf: string;
  caseData: BriefEntry;
} & Record<SectionKey, BriefSection>;

export const getCaseBrief = (caseId: string, signal?: AbortSignal): Promise<Brief> =>
  api(`/api/v1/cases/${caseId}/brief`, signal === undefined ? {} : { signal });
