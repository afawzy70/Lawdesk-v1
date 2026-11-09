import { useContext } from 'react';
import { AuthContext } from '../auth/AuthContext';
import { CaseLinksSection } from './CaseLinksSection';
import { CaseTimelinePanel } from './CaseTimelinePanel';
import { InfoRequestsSection } from './InfoRequestsSection';
import { RiskSection } from './RiskSection';
import './followup.css';

interface Props {
  caseId: string;
  archived: boolean;
  generation: number;
}

/**
 * WP3 sections of the case page (LD-053): linked cases, information requests, risk assessment and the unified timeline.
 * A section the user holds no permission for is not shown; the backend checks every permission and every case anyway.
 */
export function CaseFollowUp({ caseId, archived, generation }: Props) {
  // Without a session context (a screen shown on its own) nothing is granted, so only the read-only sections appear.
  const state = useContext(AuthContext)?.state;
  const granted = new Set<string>(state?.status === 'signedIn' ? state.me.permissions : []);
  return (
    <>
      <CaseLinksSection caseId={caseId} editable={!archived && granted.has('CASE_LINK_MANAGE')} />
      {!granted.has('INFO_REQUEST_VIEW') ? null : (
        <InfoRequestsSection
          caseId={caseId}
          editable={!archived && granted.has('INFO_REQUEST_MANAGE')}
          canEscalate={granted.has('INFO_REQUEST_ESCALATE')}
        />
      )}
      {!granted.has('RISK_VIEW') ? null : <RiskSection caseId={caseId} editable={!archived && granted.has('RISK_MANAGE')} />}
      <CaseTimelinePanel caseId={caseId} generation={generation} />
    </>
  );
}
