import { api } from '../../api/http';

/** The organisation tree and case access API (LD-050, owner decision D3). */
export type OrgTree = 'LEGAL' | 'AUTHORITY';
export type OrgLevel = 'SECTOR' | 'DEPARTMENT' | 'SECTION';
export const ORG_LEVELS: readonly OrgLevel[] = ['SECTOR', 'DEPARTMENT', 'SECTION'];

export interface OrgUnit {
  id: string;
  tree: OrgTree;
  parentId: string | null;
  level: OrgLevel;
  nameAr: string;
  nameEn: string | null;
  active: boolean;
  validFrom: string | null;
  validTo: string | null;
  version: number;
}

export interface NewOrgUnit {
  tree: OrgTree;
  parentId: string | null;
  level: OrgLevel;
  nameAr: string;
  nameEn: string | null;
}

export const listOrgUnits = (tree: OrgTree, includeInactive = true): Promise<OrgUnit[]> =>
  api(`/api/v1/org-units?tree=${tree}&includeInactive=${String(includeInactive)}`);

export const createOrgUnit = (unit: NewOrgUnit): Promise<OrgUnit> =>
  api('/api/v1/org-units', { method: 'POST', body: unit });

export const updateOrgUnit = (
  unit: OrgUnit,
  changes: Partial<Pick<OrgUnit, 'nameAr' | 'nameEn' | 'active' | 'parentId'>>,
): Promise<OrgUnit> =>
  api(`/api/v1/org-units/${unit.id}`, {
    method: 'PUT',
    body: {
      parentId: unit.parentId,
      nameAr: unit.nameAr,
      nameEn: unit.nameEn,
      active: unit.active,
      validFrom: unit.validFrom,
      validTo: unit.validTo,
      ...changes,
      version: unit.version,
    },
  });

export const placeUser = (userId: string, orgUnitId: string | null): Promise<void> =>
  api(`/api/v1/users/${userId}/org-unit`, {
    method: 'PUT',
    body: { orgUnitId },
  });

export type GrantLevel = 'VIEW' | 'CONTRIBUTE';

export interface CaseGrant {
  id: string;
  caseId: string;
  userId: string;
  userName: string;
  level: GrantLevel;
  reason: string;
  grantedAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
}

export const listGrants = (caseId: string): Promise<CaseGrant[]> => api(`/api/v1/cases/${caseId}/grants`);

export const grantAccess = (
  caseId: string,
  body: { userId: string; level: GrantLevel; reason: string },
): Promise<CaseGrant> => api(`/api/v1/cases/${caseId}/grants`, { method: 'POST', body });

export const revokeGrant = (caseId: string, grantId: string): Promise<void> =>
  api(`/api/v1/cases/${caseId}/grants/${grantId}`, { method: 'DELETE' });

export const setConfidentiality = (
  caseId: string,
  level: 'NORMAL' | 'RESTRICTED',
  reason: string,
  version: number,
): Promise<void> =>
  api(`/api/v1/cases/${caseId}/confidentiality`, {
    method: 'PUT',
    body: { level, reason, version },
  });

export type ScopeType = 'ALL' | 'ORG_UNIT' | 'OWN_UNIT' | 'CASE_TYPE';
export const SCOPE_TYPES: readonly ScopeType[] = ['ALL', 'ORG_UNIT', 'OWN_UNIT', 'CASE_TYPE'];

export interface AccessScope {
  id: string;
  subjectType: 'ROLE' | 'USER';
  subjectRef: string;
  scopeType: ScopeType;
  scopeRef: string | null;
}

export const listScopes = (): Promise<AccessScope[]> => api('/api/v1/access-scopes');

export const addScope = (scope: Omit<AccessScope, 'id'>): Promise<AccessScope> =>
  api('/api/v1/access-scopes', { method: 'POST', body: scope });

export const removeScope = (id: string): Promise<void> => api(`/api/v1/access-scopes/${id}`, { method: 'DELETE' });
