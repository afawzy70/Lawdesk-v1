import { useContext } from 'react';
import { AuthContext } from '../auth/AuthContext';
import { DraftSection } from './DraftSection';
import { SettlementsSection } from './SettlementsSection';

interface Props {
  caseId: string;
  archived: boolean;
}

/**
 * WP9 sections of the case page (LD-057): settlements (section 23) and the draft from a ready template (section 29). A
 * section the user may not open is left out; the backend checks every permission and every case anyway.
 */
export function CaseWp9({ caseId, archived }: Props) {
  // Without a session context (a screen shown on its own) nothing is granted, so only the read-only parts appear.
  const state = useContext(AuthContext)?.state;
  const granted = new Set<string>(state?.status === 'signedIn' ? state.me.permissions : []);
  return (
    <>
      <SettlementsSection
        caseId={caseId}
        editable={!archived && granted.has('SETTLEMENT_MANAGE')}
        canApprove={!archived && granted.has('SETTLEMENT_APPROVE')}
      />
      <DraftSection caseId={caseId} />
    </>
  );
}
