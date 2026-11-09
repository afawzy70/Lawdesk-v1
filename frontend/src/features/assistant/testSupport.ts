import type { Answer, Block, Output, Source } from './api';

/** Test-only fixtures of the assistant screens; the scripted backend and the providers are those of the WP3 screens. */
export { CASE, mount, page, problem, serve, type Request } from '../followup/testSupport';

export function source(overrides: Partial<Source> = {}): Source {
  return {
    n: 1,
    type: 'NOTE',
    id: 'n1',
    caseId: 'c0000000-0000-4000-8000-000000000001',
    caseReferenceNo: 'LD-2026-00001',
    title: 'Case note',
    date: '2026-03-01',
    page: null,
    excerpt: 'Check the bank guarantee terms',
    fields: [],
    suspectedInstruction: false,
    ...overrides,
  };
}

export function answer(overrides: Partial<Answer> = {}): Answer {
  const blocks: Block[] = [
    { kind: 'HEADING', code: 'ANSWER_SOURCES', text: null, fields: null, sources: [] },
    { kind: 'QUOTE', code: null, text: 'Check the bank guarantee terms', fields: null, sources: [1] },
    { kind: 'NOTE', code: 'DRAFT_FOR_REVIEW', text: null, fields: null, sources: [] },
  ];
  return {
    queryId: 7,
    sessionId: 's1',
    task: 'QUESTION',
    outcome: 'ANSWERED',
    refusal: null,
    operational: false,
    reason: 'NO_APPROVED_PROVIDER',
    mode: 'NON_PRODUCTION_TEST',
    provider: { code: 'LOCAL_EXTRACTIVE_TEST', nonProduction: true, external: false },
    caseId: 'c0000000-0000-4000-8000-000000000001',
    blocks,
    sources: [source()],
    outputId: null,
    isFinal: false,
    ...overrides,
  };
}

export function output(overrides: Partial<Output> = {}): Output {
  return {
    id: 'o1',
    caseId: 'c0000000-0000-4000-8000-000000000001',
    kind: 'MEMO_DRAFT',
    title: 'Reply memorandum',
    status: 'DRAFT',
    isFinal: false,
    nonProduction: true,
    reviewedAt: null,
    createdAt: '2026-11-01T08:00:00Z',
    ...overrides,
  };
}

export const STATUS = {
  operational: false,
  reason: 'NO_APPROVED_PROVIDER',
  mode: 'NON_PRODUCTION_TEST',
  provider: { code: 'LOCAL_EXTRACTIVE_TEST', nonProduction: true, external: false },
  approvedProviders: 0,
  configuredProviders: 0,
  externalCalls: false,
  caseDataLeavesTheSystem: false,
  tasks: ['QUESTION'],
};

export const INTEGRATIONS = [
  {
    code: 'CORRESPONDENCE',
    nameAr: 'نظام المراسلات',
    nameEn: 'Correspondence system',
    direction: 'INBOUND_AND_OUTBOUND',
    operations: ['registerIncomingLetter', 'getLetterStatus'],
    status: 'DISABLED',
    reason: 'NOT_APPROVED',
    hasCredentials: false,
    hasEndpoint: false,
  },
  {
    code: 'NEWER',
    nameAr: 'نظام جديد',
    nameEn: 'A newer system',
    direction: 'SIDEWAYS',
    operations: [],
    status: 'DISABLED',
    reason: 'NOT_APPROVED',
    hasCredentials: false,
    hasEndpoint: false,
  },
];
