import { useCallback, useId, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { usePermissions } from '../archive/usePermissions';
import { InfoRequestsSection } from '../followup/InfoRequestsSection';
import { CaseInvolvementsSection } from './CaseInvolvementsSection';

interface Props {
  caseId: string;
  /** An archived case keeps both lists as they are. */
  archived: boolean;
}

/**
 * One section for «الجهات الإدارية المعنية» and «طلبات الإفادة»: an information request is addressed to the
 * administrative units the case concerns. Each half keeps its own permission, and the backend checks both.
 */
export function InvolvementsAndRequests({ caseId, archived }: Props) {
  const { t } = useI18n();
  const id = useId();
  const granted = usePermissions();
  const [involvedVersion, setInvolvedVersion] = useState(0);
  const involvementsChanged = useCallback(() => {
    setInvolvedVersion((n) => n + 1);
  }, []);
  return (
    <section className="case-involvements-requests" aria-labelledby={`${id}-h`}>
      <h2 id={`${id}-h`}>{t('involveRequests.title')}</h2>
      <p className="case-field-hint">{t('involveRequests.intro')}</p>
      <CaseInvolvementsSection caseId={caseId} editable={!archived} embedded onChanged={involvementsChanged} />
      {!granted.has('INFO_REQUEST_VIEW') ? null : (
        <InfoRequestsSection
          caseId={caseId}
          editable={!archived && granted.has('INFO_REQUEST_MANAGE')}
          canEscalate={granted.has('INFO_REQUEST_ESCALATE')}
          embedded
          involvedVersion={involvedVersion}
        />
      )}
    </section>
  );
}
