import { useContext } from 'react';
import { AuthContext } from '../auth/AuthContext';
import { CaseClaims } from './CaseClaims';
import { CaseDefenses } from './CaseDefenses';
import { CaseEvidence } from './CaseEvidence';
import './pleadings.css';

interface Props {
  caseId: string;
  /** False for an archived case: everything is shown, nothing can change. */
  editable: boolean;
}

/**
 * The claims (section 5), defences (17), facts and evidence (20) of one case. A section appears for a user who holds its
 * view permission and offers changes to one who holds its manage permission; the backend checks every request again.
 */
export function CasePleadings({ caseId, editable }: Props) {
  const state = useContext(AuthContext)?.state;
  const granted = new Set(state?.status === 'signedIn' ? state.me.permissions : []);
  return (
    <>
      {granted.has('CLAIM_VIEW') ? <CaseClaims caseId={caseId} canManage={editable && granted.has('CLAIM_MANAGE')} /> : null}
      {granted.has('DEFENSE_VIEW') ? <CaseDefenses caseId={caseId} canManage={editable && granted.has('DEFENSE_MANAGE')} /> : null}
      {granted.has('EVIDENCE_VIEW') ? <CaseEvidence caseId={caseId} canManage={editable && granted.has('EVIDENCE_MANAGE')} /> : null}
    </>
  );
}
