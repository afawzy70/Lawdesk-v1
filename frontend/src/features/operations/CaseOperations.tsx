import { useId } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { CaseDocuments } from './documents/CaseDocuments';
import { CaseExperts } from './experts/CaseExperts';
import { CaseHearings } from './hearings/CaseHearings';
import { CaseTasks } from './tasks/CaseTasks';
import './operations.css';

interface Props {
  caseId: string;
}

/**
 * The legal operations of one case (architecture/CONTRACTS.md §8.1): its hearings (LD-024), tasks (LD-027),
 * documents (LD-026) and expert referrals (LD-025), and later its notes. The case details page mounts this one
 * component; each section asks the backend for its own data and explains a refusal itself.
 */
export function CaseOperations({ caseId }: Props) {
  const { t } = useI18n();
  const headingId = useId();
  return (
    <section className="case-operations" aria-labelledby={headingId}>
      <h2 id={headingId}>{t('operations.title')}</h2>
      <CaseHearings caseId={caseId} />
      <CaseTasks caseId={caseId} />
      <CaseDocuments caseId={caseId} />
      <CaseExperts caseId={caseId} />
    </section>
  );
}
