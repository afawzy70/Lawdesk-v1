import { useContext, useId, useState, type SyntheticEvent } from 'react';
import type { MessageKey } from '../../../i18n/catalog';
import { useI18n } from '../../../i18n/I18nContext';
import { AuthContext } from '../../auth/AuthContext';
import { assignCase, type LegalCase } from '../api';
import { isStatus } from '../problems';
import { MemberFields } from './MemberFields';

interface Props {
  legalCase: LegalCase;
  onChanged: () => void;
}

/** Reassignment of the specialist and alternate members; shown to case supervisors, enforced by the server. */
export function CaseAssignmentSection({ legalCase, onChanged }: Props) {
  const { t } = useI18n();
  // Not useAuth(): the section simply stays hidden where there is no session (embedded views, tests).
  const state = useContext(AuthContext)?.state;
  const headingId = useId();
  const [primary, setPrimary] = useState(legalCase.primaryMember?.id ?? '');
  const [alternate, setAlternate] = useState(legalCase.alternateMember?.id ?? '');
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [saving, setSaving] = useState(false);

  const permissions = state?.status === 'signedIn' ? state.me.permissions : [];
  if (!permissions.includes('ASSIGN_CASE')) return null;

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    if (primary === '') {
      setFailure('cases.error.NotBlank');
      return;
    }
    setSaving(true);
    setFailure(null);
    try {
      await assignCase(legalCase.id, primary, alternate, legalCase.version);
      onChanged();
    } catch (error) {
      setFailure(
        isStatus(error, 409) ? 'intake.assign.stale' : isStatus(error, 403) ? 'intake.assign.forbidden'
          : isStatus(error, 422) ? 'intake.rule.MEMBER_NOT_ELIGIBLE' : 'intake.assign.failed',
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="case-section" aria-labelledby={headingId}>
      <h2 id={headingId}>{t('intake.assign.title')}</h2>
      <form className="case-form" noValidate onSubmit={(event) => void submit(event)}>
        {failure === null ? null : <p className="case-alert" role="alert">{t(failure)}</p>}
        <MemberFields
          primary={primary}
          alternate={alternate}
          onChange={(next) => {
            setPrimary(next.primary);
            setAlternate(next.alternate);
          }}
        />
        <div className="case-actions">
          <button type="submit" disabled={saving}>{t('intake.assign.save')}</button>
        </div>
      </form>
    </section>
  );
}
