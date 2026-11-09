import { api } from '../../api/http';

/** LD-053 (WP3): linked cases, information requests, risk assessment, the executive summary, the unified timeline, notifications. */

export const blankToNull = (value: string): string | null => (value.trim() === '' ? null : value.trim());

// ---- linked cases ------------------------------------------------------------------------------------------------

export const LINK_KINDS = ['SUBJECT_LINK', 'JOINDER_REQUEST', 'JOINDER_DECISION', 'REFERRAL'] as const;
export type LinkKind = (typeof LINK_KINDS)[number];
export const LINK_STATUSES = ['REQUESTED', 'ACTIVE', 'REJECTED', 'ENDED'] as const;
export type LinkStatus = (typeof LINK_STATUSES)[number];

export interface CaseLink {
  id: string;
  caseId: string;
  direction: 'OUTGOING' | 'INCOMING';
  otherCaseId: string;
  otherCaseReferenceNo: string;
  otherCaseTitle: string;
  otherCaseStatus: string;
  kind: LinkKind;
  reason: string;
  requestedBy: string | null;
  requestedOn: string | null;
  courtDecision: boolean;
  decisionDate: string | null;
  operative: string | null;
  decisionDocumentId: string | null;
  status: LinkStatus;
  version: number;
  createdAt: string;
}

export interface CaseLinks {
  items: CaseLink[];
  /** Links to cases the user may not see: only counted. */
  hiddenCount: number;
}

export interface NewLink {
  otherCaseId: string;
  kind: LinkKind;
  reason: string;
  requestedBy: string;
  requestedOn: string;
  courtDecision: boolean;
  decisionDate: string;
  operative: string;
}

export function listLinks(caseId: string, signal?: AbortSignal): Promise<CaseLinks> {
  return api<CaseLinks>(`/api/v1/cases/${encodeURIComponent(caseId)}/links`, signal === undefined ? {} : { signal });
}

export function addLink(caseId: string, input: NewLink): Promise<CaseLink> {
  return api<CaseLink>(`/api/v1/cases/${encodeURIComponent(caseId)}/links`, {
    method: 'POST',
    body: {
      otherCaseId: input.otherCaseId,
      kind: input.kind,
      reason: input.reason,
      requestedBy: blankToNull(input.requestedBy),
      requestedOn: blankToNull(input.requestedOn),
      courtDecision: input.courtDecision,
      decisionDate: input.courtDecision ? blankToNull(input.decisionDate) : null,
      operative: input.courtDecision ? blankToNull(input.operative) : null,
    },
  });
}

export function updateLink(link: CaseLink, changes: { status: LinkStatus }): Promise<CaseLink> {
  return api<CaseLink>(`/api/v1/case-links/${encodeURIComponent(link.id)}`, {
    method: 'PUT',
    body: {
      reason: link.reason,
      requestedBy: link.requestedBy,
      requestedOn: link.requestedOn,
      status: changes.status,
      courtDecision: link.courtDecision,
      decisionDate: link.decisionDate,
      operative: link.operative,
      decisionDocumentId: link.decisionDocumentId,
      version: link.version,
    },
  });
}

// ---- information requests ------------------------------------------------------------------------------------------

export const REQUEST_STATUSES = [
  'DRAFT',
  'PENDING_APPROVAL',
  'SENT',
  'AWAITING',
  'PARTIAL',
  'INCOMPLETE',
  'COMPLETE',
  'OVERDUE',
  'CLOSED',
] as const;
export type RequestStatus = (typeof REQUEST_STATUSES)[number];
export const SETTABLE_STATUSES = ['DRAFT', 'PENDING_APPROVAL', 'SENT', 'AWAITING', 'CLOSED'] as const;
export type SettableStatus = (typeof SETTABLE_STATUSES)[number];
export const COMPLETENESS = ['PARTIAL', 'INCOMPLETE', 'COMPLETE'] as const;
export type Completeness = (typeof COMPLETENESS)[number];
export const FOLLOW_UP_KINDS = ['REMINDER', 'RE_ADDRESS', 'ESCALATION'] as const;
export type FollowUpKind = (typeof FOLLOW_UP_KINDS)[number];

export interface InfoReply {
  id: string;
  requestId: string;
  receivedOn: string;
  completeness: Completeness;
  summary: string | null;
  documentId: string | null;
  documentTitle: string | null;
  createdByName: string | null;
}

export interface InfoFollowUp {
  id: string;
  requestId: string;
  kind: FollowUpKind;
  onDate: string;
  note: string | null;
  escalatedTo: string | null;
  escalatedToName: string | null;
  byName: string | null;
}

export interface InfoRequest {
  id: string;
  caseId: string;
  caseReferenceNo: string;
  requestNo: string;
  unitId: string | null;
  unitName: string | null;
  subject: string;
  details: string | null;
  requiredDocuments: string | null;
  draftedOn: string | null;
  sentOn: string | null;
  letterNo: string | null;
  letterDate: string | null;
  dueOn: string | null;
  responsibleMemberId: string | null;
  responsibleMemberName: string | null;
  addressee: string | null;
  notes: string | null;
  status: RequestStatus;
  delayReason: string | null;
  closeReason: string | null;
  closedOn: string | null;
  overdue: boolean;
  replyCount: number;
  replies: InfoReply[];
  followUps: InfoFollowUp[];
  version: number;
}

export interface NewRequest {
  subject: string;
  unitId: string;
  details: string;
  requiredDocuments: string;
  dueOn: string;
  addressee: string;
}

export async function listCaseRequests(caseId: string, signal?: AbortSignal): Promise<InfoRequest[]> {
  const query = new URLSearchParams({ page: '0', size: '100' });
  const result = await api<{ items: InfoRequest[] }>(
    `/api/v1/cases/${encodeURIComponent(caseId)}/info-requests?${query.toString()}`,
    signal === undefined ? {} : { signal },
  );
  return result.items;
}

export function createRequest(caseId: string, input: NewRequest): Promise<InfoRequest> {
  return api<InfoRequest>(`/api/v1/cases/${encodeURIComponent(caseId)}/info-requests`, {
    method: 'POST',
    body: {
      subject: input.subject,
      unitId: blankToNull(input.unitId),
      details: blankToNull(input.details),
      requiredDocuments: blankToNull(input.requiredDocuments),
      dueOn: blankToNull(input.dueOn),
      addressee: blankToNull(input.addressee),
      draftedOn: new Date().toISOString().slice(0, 10),
    },
  });
}

export function changeRequestStatus(
  request: InfoRequest,
  status: SettableStatus,
  extra: { letterNo?: string; closeReason?: string } = {},
): Promise<InfoRequest> {
  return api<InfoRequest>(`/api/v1/info-requests/${encodeURIComponent(request.id)}/status`, {
    method: 'POST',
    body: {
      status,
      letterNo: blankToNull(extra.letterNo ?? ''),
      closeReason: blankToNull(extra.closeReason ?? ''),
      version: request.version,
    },
  });
}

/** Records the reason a reply is late (or any other change of the fields) without touching the rest. */
export function recordDelayReason(request: InfoRequest, delayReason: string, dueOn: string): Promise<InfoRequest> {
  return api<InfoRequest>(`/api/v1/info-requests/${encodeURIComponent(request.id)}`, {
    method: 'PUT',
    body: {
      subject: request.subject,
      unitId: request.unitId,
      details: request.details,
      requiredDocuments: request.requiredDocuments,
      draftedOn: request.draftedOn,
      sentOn: request.sentOn,
      letterNo: request.letterNo,
      letterDate: request.letterDate,
      dueOn: blankToNull(dueOn),
      responsibleMemberId: request.responsibleMemberId,
      addressee: request.addressee,
      notes: request.notes,
      delayReason: blankToNull(delayReason),
      version: request.version,
    },
  });
}

export interface NewReply {
  receivedOn: string;
  completeness: Completeness;
  summary: string;
  file: File | null;
}

export function addReply(requestId: string, input: NewReply): Promise<InfoReply> {
  const path = `/api/v1/info-requests/${encodeURIComponent(requestId)}/replies`;
  if (input.file !== null) {
    const form = new FormData();
    form.set('file', input.file);
    form.set('receivedOn', input.receivedOn);
    form.set('completeness', input.completeness);
    if (input.summary.trim() !== '') form.set('summary', input.summary.trim());
    return api<InfoReply>(path, { method: 'POST', body: form });
  }
  return api<InfoReply>(path, {
    method: 'POST',
    body: { receivedOn: input.receivedOn, completeness: input.completeness, summary: blankToNull(input.summary) },
  });
}

export interface NewFollowUp {
  kind: FollowUpKind;
  note: string;
  escalatedTo: string;
}

export function addFollowUp(requestId: string, input: NewFollowUp): Promise<InfoFollowUp> {
  return api<InfoFollowUp>(`/api/v1/info-requests/${encodeURIComponent(requestId)}/follow-ups`, {
    method: 'POST',
    body: {
      kind: input.kind,
      note: blankToNull(input.note),
      escalatedTo: input.kind === 'ESCALATION' ? blankToNull(input.escalatedTo) : null,
    },
  });
}

// ---- risk assessment ------------------------------------------------------------------------------------------------

export const RISK_LEVELS = ['LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'] as const;
export type RiskLevel = (typeof RISK_LEVELS)[number];
export const RISK_RATINGS = ['NOT_ASSESSED', 'LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'] as const;
export type RiskRating = (typeof RISK_RATINGS)[number];
export const RISK_TRIGGERS = ['NEW_DOCUMENT', 'JUDGMENT', 'DECISION', 'POSITION_CHANGE', 'PERIODIC', 'MANUAL'] as const;
export type RiskTrigger = (typeof RISK_TRIGGERS)[number];
export const RISK_ELEMENTS = [
  'LEGAL_POSITION_STRENGTH',
  'EVIDENCE_STRENGTH',
  'ADMINISTRATIVE_PROCEDURE_SOUNDNESS',
  'POTENTIAL_PROCEDURAL_DEFECTS',
  'SIMILAR_PRECEDENTS',
  'JUDICIAL_TREND_STABILITY',
  'POTENTIAL_FINANCIAL_LOSS',
  'ADMINISTRATIVE_IMPACT',
  'SPILLOVER_TO_OTHER_CASES',
  'INFORMATION_AVAILABILITY',
] as const;
export type RiskElementCode = (typeof RISK_ELEMENTS)[number];
export const REMEDIATION_STATUSES = ['OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED'] as const;
export type RemediationStatus = (typeof REMEDIATION_STATUSES)[number];

export interface RiskElement {
  code: RiskElementCode;
  rating: RiskRating;
  reason: string | null;
}

export interface Remediation {
  id: string;
  assessmentId: string;
  cause: string;
  action: string;
  documents: string | null;
  defenses: string | null;
  unitToAddress: string | null;
  ownerName: string | null;
  targetDate: string | null;
  status: RemediationStatus;
  version: number;
}

export interface RiskAssessment {
  id: string;
  caseId: string;
  versionNo: number;
  level: RiskLevel;
  levelReasons: string;
  /** Dinars with three decimals, as text. */
  potentialLoss: string | null;
  trigger: RiskTrigger;
  triggerNote: string | null;
  assessedByName: string | null;
  assessedAt: string;
  elements: RiskElement[];
  remediation: Remediation[];
}

export interface NewAssessment {
  level: RiskLevel;
  levelReasons: string;
  potentialLoss: string;
  trigger: RiskTrigger;
  triggerNote: string;
  elements: Record<RiskElementCode, { rating: RiskRating; reason: string }>;
  plan: { cause: string; action: string; unitToAddress: string; targetDate: string };
}

export async function listAssessments(caseId: string, signal?: AbortSignal): Promise<RiskAssessment[]> {
  const result = await api<{ items: RiskAssessment[] }>(
    `/api/v1/cases/${encodeURIComponent(caseId)}/risk-assessments`,
    signal === undefined ? {} : { signal },
  );
  return result.items;
}

export function assess(caseId: string, input: NewAssessment): Promise<RiskAssessment> {
  const needsPlan = input.level === 'HIGH' || input.level === 'VERY_HIGH';
  return api<RiskAssessment>(`/api/v1/cases/${encodeURIComponent(caseId)}/risk-assessments`, {
    method: 'POST',
    body: {
      level: input.level,
      levelReasons: input.levelReasons,
      potentialLoss: blankToNull(input.potentialLoss),
      trigger: input.trigger,
      triggerNote: blankToNull(input.triggerNote),
      elements: RISK_ELEMENTS.filter((code) => input.elements[code].rating !== 'NOT_ASSESSED').map((code) => ({
        code,
        rating: input.elements[code].rating,
        reason: blankToNull(input.elements[code].reason),
      })),
      remediation: needsPlan
        ? [
            {
              cause: input.plan.cause,
              action: input.plan.action,
              unitToAddress: blankToNull(input.plan.unitToAddress),
              targetDate: blankToNull(input.plan.targetDate),
            },
          ]
        : [],
    },
  });
}

export function setRemediationStatus(step: Remediation, status: RemediationStatus): Promise<Remediation> {
  return api<Remediation>(`/api/v1/risk-remediations/${encodeURIComponent(step.id)}`, {
    method: 'PUT',
    body: {
      cause: step.cause,
      action: step.action,
      documents: step.documents,
      defenses: step.defenses,
      unitToAddress: step.unitToAddress,
      targetDate: step.targetDate,
      status,
      version: step.version,
    },
  });
}

// ---- executive summary and unified timeline ---------------------------------------------------------------------------

export interface HearingBrief {
  id: string;
  hearingDate: string;
  hearingTime: string | null;
  status: string;
  purpose: string;
  kind: string;
  decision: string | null;
  adjournmentReason: string | null;
  noNextDate: boolean;
}

export interface CaseSummaryData {
  caseId: string;
  referenceNo: string;
  automatedNo: string | null;
  title: string;
  subject: string | null;
  status: string;
  authorityPosition: string | null;
  claimedAmount: string | null;
  primaryMember: { id: string; displayName: string } | null;
  alternateMember: { id: string; displayName: string } | null;
  parties: { name: string; role: string }[];
  involvedUnits: { name: string; primary: boolean }[];
  lastProcedure: { type: string; summary: string; occurredAt: string } | null;
  hearings: {
    total: number;
    held: number;
    adjourned: number;
    cancelled: number;
    scheduled: number;
    awaitingResult: number;
    followUp: number;
    lastHearing: HearingBrief | null;
    nextHearing: HearingBrief | null;
  } | null;
  lastJudgment: { id: string; judgmentDate: string; outcome: string; summary: string | null } | null;
  risk: { versionNo: number; level: RiskLevel; potentialLoss: string | null; openRemediation: number } | null;
  infoRequests: {
    open: number;
    overdue: number;
    incompleteReplies: number;
    pending: { id: string; requestNo: string; subject: string; status: string; dueOn: string | null; overdue: boolean }[];
  } | null;
  tasks: { open: number; overdue: number; items: { id: string; title: string; dueDate: string | null }[] } | null;
  links: { count: number; hiddenCount: number };
  pendingModules: string[];
  /** Absent when the user holds no deadline permission. */
  deadlines?: {
    open: number;
    overdue: number;
    unapproved: number;
    items: { id: string; action: string; dueOn: string | null; calcStatus: string; overdue: boolean }[];
  } | null;
}

export function getSummary(caseId: string, signal?: AbortSignal): Promise<CaseSummaryData> {
  return api<CaseSummaryData>(`/api/v1/cases/${encodeURIComponent(caseId)}/summary`, signal === undefined ? {} : { signal });
}

export const TIMELINE_KINDS = [
  'EVENT',
  'HEARING',
  'INFO_REQUEST',
  'INFO_REQUEST_DUE',
  'INFO_REPLY',
  'RISK',
  'JUDGMENT',
  'LINK',
  'DEADLINE',
] as const;
export type TimelineKind = (typeof TIMELINE_KINDS)[number];

export interface TimelineEntry {
  at: string;
  kind: TimelineKind;
  status: string | null;
  summary: string;
  refType: string;
  refId: string | null;
  isFuture: boolean;
  needsAction: boolean;
  actorName: string | null;
}

export interface TimelinePage {
  items: TimelineEntry[];
  page: number;
  totalPages: number;
  totalItems: number;
  deadlinesAvailable: boolean;
}

export function getTimeline(
  caseId: string,
  filter: { types: readonly TimelineKind[]; page: number },
  signal?: AbortSignal,
): Promise<TimelinePage> {
  const query = new URLSearchParams({ page: String(filter.page), size: '20' });
  if (filter.types.length > 0) query.set('types', filter.types.join(','));
  return api<TimelinePage>(
    `/api/v1/cases/${encodeURIComponent(caseId)}/unified-timeline?${query.toString()}`,
    signal === undefined ? {} : { signal },
  );
}

// ---- notifications -----------------------------------------------------------------------------------------------------

export interface AppNotification {
  id: string;
  type: string;
  caseId: string | null;
  caseReferenceNo: string | null;
  recordRef: string | null;
  /** The two halves of `recordRef` (HEARING and the hearing's id; CALENDAR and a day), so the record can be opened. */
  recordType: string | null;
  recordId: string | null;
  params: Record<string, string>;
  createdAt: string;
  readAt: string | null;
}

/** The number of unread notifications, or null when the answer has no number (the bell then shows none). */
export async function getUnreadCount(signal?: AbortSignal): Promise<number | null> {
  const result = await api<{ unread?: number } | undefined>(
    '/api/v1/notifications/unread-count',
    signal === undefined ? {} : { signal },
  );
  return typeof result?.unread === 'number' ? result.unread : null;
}

export interface NotificationFilter {
  type?: string | undefined;
  /** `unread`, `read` or nothing for both. */
  read?: 'unread' | 'read' | undefined;
  caseId?: string | undefined;
}

export function listNotifications(page: number, signal?: AbortSignal, filter: NotificationFilter = {}): Promise<{
  items: AppNotification[];
  page: number;
  totalPages: number;
  totalItems: number;
}> {
  const query = new URLSearchParams({ page: String(page), size: '20' });
  if (filter.type !== undefined) query.append('type', filter.type);
  if (filter.read !== undefined) query.set('read', String(filter.read === 'read'));
  if (filter.caseId !== undefined) query.set('caseId', filter.caseId);
  return api(`/api/v1/notifications?${query.toString()}`, signal === undefined ? {} : { signal });
}

export function markRead(id: string): Promise<void> {
  return api(`/api/v1/notifications/${encodeURIComponent(id)}/read`, { method: 'POST', body: {} });
}

export function markUnread(id: string): Promise<void> {
  return api(`/api/v1/notifications/${encodeURIComponent(id)}/unread`, { method: 'POST', body: {} });
}

export function markAllRead(): Promise<void> {
  return api('/api/v1/notifications/read-all', { method: 'POST', body: {} });
}

/** One kind of in-app notification and whether the signed-in user receives it (requirements section 32). */
export interface NotificationPreference {
  type: string;
  /** One of the eleven kinds of the requirements; the others are system notices. */
  core: boolean;
  position: number;
  enabled: boolean;
}

export function getPreferences(signal?: AbortSignal): Promise<NotificationPreference[]> {
  return api<NotificationPreference[]>('/api/v1/notifications/preferences', signal === undefined ? {} : { signal });
}

export function setPreference(type: string, enabled: boolean): Promise<NotificationPreference> {
  return api<NotificationPreference>(`/api/v1/notifications/preferences/${encodeURIComponent(type)}`, {
    method: 'PUT',
    body: { enabled },
  });
}
