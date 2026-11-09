import { useId } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { CaseLegalDocuments } from '../archive/CaseLegalDocuments';
import { usePermissions } from '../archive/usePermissions';
import { CaseDates } from '../calendar/CaseDates';
import { CaseDocuments } from './documents/CaseDocuments';
import { CaseExperts } from './experts/CaseExperts';
import { CaseHearings } from './hearings/CaseHearings';
import { CaseNotes } from './notes/CaseNotes';
import { CaseTasks } from './tasks/CaseTasks';
import './operations.css';

interface Props {
  caseId: string;
  /** An archived case takes no new dates. */
  archived?: boolean;
}

/**
 * The legal operations of one case (architecture/CONTRACTS.md §8.1): its hearings (LD-024), tasks (LD-027),
 * documents (LD-026), expert referrals (LD-025) and legal notes (LD-029). The case details page mounts this one
 * component; each section asks the backend for its own data and explains a refusal itself.
 */
export function CaseOperations({ caseId, archived = false }: Props) {
  const { t } = useI18n();
  const headingId = useId();
  const granted = usePermissions();
  return (
    <section className="case-operations" aria-labelledby={headingId}>
      <h2 id={headingId}>{t('operations.title')}</h2>
      <CaseHearings caseId={caseId} />
      <CaseTasks caseId={caseId} />
      <CaseDates caseId={caseId} archived={archived} />
      <CaseDocuments caseId={caseId} />
      {granted.has('DOCUMENT_VIEW') ? <CaseLegalDocuments caseId={caseId} editable /> : null}
      <CaseExperts caseId={caseId} />
      <CaseNotes caseId={caseId} />
    </section>
  );
}
