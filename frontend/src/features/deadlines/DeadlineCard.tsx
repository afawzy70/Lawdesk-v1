import { useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { formatDateTime } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import type { MemberRef } from '../cases/api';
import type { CaseDocument } from '../operations/documents/documentsApi';
import { approveDeadline, setDeadlineStatus, updateDeadline, type Deadline, type DeadlineEdit } from './api';
import { CalculationView, DeadlineBadges } from './DeadlineParts';
import { useSourceName } from './useSourceName';
import { CompleteDeadlineForm, EditDeadlineForm } from './DeadlineForms';
import { failureKey } from './errors';

interface Props {
  deadline: Deadline;
  /** Holds DEADLINE_MANAGE and the case is not archived. */
  editable: boolean;
  /** Holds DEADLINE_APPROVE. */
  canApprove: boolean;
  members: MemberRef[];
  documents: CaseDocument[];
  onChanged: (message: MessageKey) => void;
  onFailed: (message: MessageKey) => void;
}

/** One deadline: what it runs from, the date and whether it is official, and the actions the user may take. */
export function DeadlineCard({ deadline, editable, canApprove, members, documents, onChanged, onFailed }: Props) {
  const { t, locale } = useI18n();
  const sourceName = useSourceName();
  const [panel, setPanel] = useState<'edit' | 'complete' | null>(null);
  const date = (iso: string | null) => (iso === null ? t('wp3.notSpecified') : formatDate(iso, locale));

  const run = async (action: () => Promise<unknown>, done: MessageKey) => {
    try {
      await action();
      setPanel(null);
      onChanged(done);
    } catch (error) {
      onFailed(failureKey(error));
    }
  };

  const open = deadline.status === 'OPEN';
  return (
    <li id={`wp3-ref-DEADLINE-${deadline.id}`} className="wp3-card" data-overdue={deadline.overdue}>
      <p>
        <strong>{deadline.action}</strong> <DeadlineBadges deadline={deadline} />
      </p>
      <dl className="case-facts">
        <dt>{t('wp4.field.source')}</dt>
        <dd>{sourceName(deadline)}</dd>
        <dt>{t('wp4.field.startsOn')}</dt>
        <dd>{date(deadline.startsOn)}</dd>
        <dt>{t('wp4.field.periodDays')}</dt>
        <dd>{deadline.periodDays === null ? t('wp3.notSpecified') : String(deadline.periodDays)}</dd>
        <dt>{t('wp4.field.dueOn')}</dt>
        <dd>
          {date(deadline.dueOn)}
          {deadline.daysRemaining === null || !open ? null : ` (${t('wp4.daysRemaining', { days: String(deadline.daysRemaining) })})`}
        </dd>
        <dt>{t('wp4.field.owner')}</dt>
        <dd>{deadline.ownerName ?? t('wp3.notSpecified')}</dd>
        {deadline.approvedByName === null || deadline.approvedAt === null ? null : (
          <>
            <dt>{t('wp4.approvedBy')}</dt>
            <dd>{deadline.approvedByName} — {formatDateTime(deadline.approvedAt, locale)}</dd>
          </>
        )}
        {deadline.completedOn === null ? null : (
          <>
            <dt>{t('wp4.field.completedOn')}</dt>
            <dd>{date(deadline.completedOn)}</dd>
            <dt>{t('wp4.field.proof')}</dt>
            <dd>{deadline.proofDocumentTitle ?? t('wp4.noProof')}</dd>
          </>
        )}
        {deadline.changeReason === null ? null : (
          <>
            <dt>{t('wp4.field.changeReason')}</dt>
            <dd>{deadline.changeReason}</dd>
          </>
        )}
        {deadline.notes === null ? null : (
          <>
            <dt>{t('wp4.field.notes')}</dt>
            <dd>{deadline.notes}</dd>
          </>
        )}
      </dl>
      {deadline.calculation === null ? null : <CalculationView calculation={deadline.calculation} />}
      {deadline.calcStatus === 'APPROVED' ? null : (
        <p className="case-notice">{t(deadline.calcStatus === 'PROPOSED' ? 'wp4.awaiting.PROPOSED' : 'wp4.awaiting.MANUAL')}</p>
      )}
      <div className="case-actions">
        {canApprove && editable && deadline.calcStatus !== 'APPROVED' ? (
          <button type="button" className="primary" onClick={() => void run(() => approveDeadline(deadline), 'wp4.approved')}>
            {t('wp4.approve')}
          </button>
        ) : null}
        {editable && open ? (
          <>
            <button type="button" onClick={() => { setPanel(panel === 'edit' ? null : 'edit'); }}>{t('wp4.edit')}</button>
            {deadline.calcStatus === 'PROPOSED' ? null : (
              <>
                <button type="button" onClick={() => { setPanel(panel === 'complete' ? null : 'complete'); }}>{t('wp4.complete')}</button>
                <button type="button" onClick={() => void run(() => setDeadlineStatus(deadline, 'MISSED'), 'wp4.missedDone')}>
                  {t('wp4.markMissed')}
                </button>
              </>
            )}
          </>
        ) : null}
        {editable && !open ? (
          <button type="button" onClick={() => void run(() => setDeadlineStatus(deadline, 'OPEN'), 'wp4.reopened')}>
            {t('wp4.reopen')}
          </button>
        ) : null}
      </div>
      {panel === 'edit' ? (
        <EditDeadlineForm
          deadline={deadline}
          members={members}
          onSave={(edit: DeadlineEdit) => run(() => updateDeadline(deadline, edit), 'wp4.saved')}
          onCancel={() => { setPanel(null); }}
        />
      ) : null}
      {panel === 'complete' ? (
        <CompleteDeadlineForm
          documents={documents}
          onComplete={(completedOn, proofDocumentId) =>
            run(() => setDeadlineStatus(deadline, 'COMPLETED', { completedOn, proofDocumentId }), 'wp4.completedDone')
          }
          onCancel={() => { setPanel(null); }}
        />
      ) : null}
    </li>
  );
}
