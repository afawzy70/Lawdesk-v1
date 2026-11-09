import { type SyntheticEvent, useId, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { formatDateTime } from '../../i18n/format';
import { formatDate } from '../cases/format';
import type { CaseParty } from '../cases/api';
import { contentUrl, type CaseDocument } from '../operations/documents/documentsApi';
import {
  changeSettlementStatus,
  labelOf,
  linkSettlementDocument,
  settlementHistory,
  settlementInputOf,
  SETTLEMENT_NEXT,
  unlinkSettlementDocument,
  updateSettlement,
  type HistoryEntry,
  type ListValue,
  type Settlement,
  type SettlementInput,
  type SettlementStatus,
} from './api';
import { failureKey } from './errors';
import { formatKwd } from './format';
import { SettlementForm } from './SettlementForm';
import type { MessageKey } from '../../i18n/catalog';

interface Props {
  settlement: Settlement;
  kinds: readonly ListValue[];
  parties: readonly CaseParty[];
  documents: readonly CaseDocument[];
  editable: boolean;
  canApprove: boolean;
  /** Called with the saved settlement, or with the message of a failure. */
  onSaved: (message: MessageKey | null) => void;
  onFailed: (message: MessageKey) => void;
}

/** One settlement: its terms, its supporting documents, its history, and the moves the user may make. */
export function SettlementCard({ settlement, kinds, parties, documents, editable, canApprove, onSaved, onFailed }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [editing, setEditing] = useState(false);
  const [history, setHistory] = useState<HistoryEntry[] | null>(null);
  const [next, setNext] = useState('');
  const [reason, setReason] = useState('');
  const [completedOn, setCompletedOn] = useState('');
  const [documentId, setDocumentId] = useState('');

  const moves = SETTLEMENT_NEXT[settlement.status].filter((status) =>
    status === 'APPROVED' || status === 'REJECTED' ? canApprove : editable,
  );
  const termsEditable = editable && (settlement.status === 'PROPOSED' || settlement.status === 'UNDER_REVIEW');
  const linked = new Set(settlement.documents.map((d) => d.id));
  const linkable = documents.filter((d) => !linked.has(d.id));
  const closed = settlement.status === 'WITHDRAWN';

  const run = async (action: () => Promise<unknown>, done: MessageKey) => {
    try {
      await action();
      setHistory(null);
      onSaved(done);
    } catch (error) {
      onFailed(failureKey(error));
    }
  };

  const save = async (input: SettlementInput) => {
    await run(() => updateSettlement(settlement, input), 'wp9.settlement.saved');
    setEditing(false);
  };

  const move = async (event: SyntheticEvent) => {
    event.preventDefault();
    if (next === '') return;
    await run(
      () => changeSettlementStatus(settlement, { status: next as SettlementStatus, reason, completedOn }),
      'wp9.settlement.moved',
    );
    setNext('');
    setReason('');
    setCompletedOn('');
  };

  const loadHistory = (open: boolean) => {
    if (!open || history !== null) return;
    settlementHistory(settlement.id).then(setHistory, () => {
      onFailed('wp9.error.save');
    });
  };

  const partyName = settlement.offeringPartyName ?? settlement.offeringPartyText;

  return (
    <li aria-label={t('wp9.settlement.itemLabel', { kind: labelOf(kinds, settlement.kind, locale) })}>
      <p>
        <strong>{labelOf(kinds, settlement.kind, locale)}</strong>
        <span className="wp3-badge" data-status={settlement.status}>
          {t(`wp9.settlement.status.${settlement.status}`)}
        </span>
      </p>
      {editing ? (
        <SettlementForm
          initial={settlementInputOf(settlement)}
          kinds={kinds}
          parties={parties}
          submitLabel={t('wp9.settlement.save')}
          onSubmit={save}
          onCancel={() => {
            setEditing(false);
          }}
        />
      ) : (
        <dl className="wp9-facts">
          {partyName === null ? null : (
            <>
              <dt>{t('wp9.settlement.party')}</dt>
              <dd>{partyName}</dd>
            </>
          )}
          <dt>{t('wp9.settlement.amount')}</dt>
          <dd>
            {settlement.settlementAmount === null
              ? t('wp9.notSpecified')
              : t('wp9.kwd', { amount: formatKwd(settlement.settlementAmount, locale) })}
          </dd>
          {settlement.legalGrounds === null ? null : (
            <>
              <dt>{t('wp9.settlement.grounds')}</dt>
              <dd>{settlement.legalGrounds}</dd>
            </>
          )}
          {settlement.financialImpactNote === null ? null : (
            <>
              <dt>{t('wp9.settlement.impact')}</dt>
              <dd>{settlement.financialImpactNote}</dd>
            </>
          )}
          {settlement.legalOpinion === null ? null : (
            <>
              <dt>{t('wp9.settlement.opinion')}</dt>
              <dd>{settlement.legalOpinion}</dd>
            </>
          )}
          {settlement.approvingAuthority === null ? null : (
            <>
              <dt>{t('wp9.settlement.authority')}</dt>
              <dd>{settlement.approvingAuthority}</dd>
            </>
          )}
          {settlement.decidedAt === null ? null : (
            <>
              <dt>{t('wp9.settlement.decision')}</dt>
              <dd>
                {formatDateTime(settlement.decidedAt, locale)}
                {settlement.decisionReason === null ? null : ` — ${settlement.decisionReason}`}
              </dd>
            </>
          )}
          {settlement.completedOn === null ? null : (
            <>
              <dt>{t('wp9.settlement.completedOn')}</dt>
              <dd>{formatDate(settlement.completedOn, locale)}</dd>
            </>
          )}
        </dl>
      )}
      {editing || !termsEditable ? null : (
        <p>
          <button
            type="button"
            onClick={() => {
              setEditing(true);
            }}
          >
            {t('wp9.settlement.edit')}
          </button>
        </p>
      )}

      <h4>{t('wp9.settlement.documents')}</h4>
      {settlement.documents.length === 0 ? (
        <p className="case-field-hint">{t('wp9.settlement.noDocuments')}</p>
      ) : (
        <ul className="wp9-docs">
          {settlement.documents.map((doc) => (
            <li key={doc.id}>
              <a href={contentUrl(doc.id)}>{doc.title}</a>
              {!editable || closed ? null : (
                <>
                  {' '}
                  <button
                    type="button"
                    onClick={() => void run(() => unlinkSettlementDocument(settlement.id, doc.id), 'wp9.settlement.saved')}
                  >
                    {t('wp9.settlement.unlink', { title: doc.title })}
                  </button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
      {!editable || closed || linkable.length === 0 ? null : (
        <p>
          <label htmlFor={`${id}-doc`}>{t('wp9.settlement.linkDocument')}</label>{' '}
          <select
            id={`${id}-doc`}
            value={documentId}
            onChange={(e) => {
              setDocumentId(e.target.value);
            }}
          >
            <option value="">{t('wp9.choose')}</option>
            {linkable.map((doc) => (
              <option key={doc.id} value={doc.id}>
                {doc.title}
              </option>
            ))}
          </select>{' '}
          <button
            type="button"
            disabled={documentId === ''}
            onClick={() => {
              void run(() => linkSettlementDocument(settlement.id, documentId), 'wp9.settlement.saved');
              setDocumentId('');
            }}
          >
            {t('wp9.settlement.link')}
          </button>
        </p>
      )}

      {moves.length === 0 ? null : (
        <form className="case-form" aria-label={t('wp9.settlement.moveForm')} noValidate onSubmit={(e) => void move(e)}>
          <div className="case-field">
            <label htmlFor={`${id}-next`}>{t('wp9.settlement.moveTo')}</label>
            <select
              id={`${id}-next`}
              value={next}
              onChange={(e) => {
                setNext(e.target.value);
              }}
            >
              <option value="">{t('wp9.choose')}</option>
              {moves.map((status) => (
                <option key={status} value={status}>
                  {t(`wp9.settlement.status.${status}`)}
                </option>
              ))}
            </select>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-reason`}>{t('wp9.settlement.reason')}</label>
            <textarea
              id={`${id}-reason`}
              rows={2}
              maxLength={20000}
              value={reason}
              onChange={(e) => {
                setReason(e.target.value);
              }}
            />
          </div>
          {next !== 'EXECUTED' ? null : (
            <div className="case-field">
              <label htmlFor={`${id}-done`}>{t('wp9.settlement.completedOn')}</label>
              <input
                id={`${id}-done`}
                type="date"
                value={completedOn}
                onChange={(e) => {
                  setCompletedOn(e.target.value);
                }}
              />
            </div>
          )}
          <div className="case-actions">
            <button type="submit" disabled={next === ''}>
              {t('wp9.settlement.apply')}
            </button>
          </div>
        </form>
      )}

      <details
        onToggle={(e) => {
          loadHistory(e.currentTarget.open);
        }}
      >
        <summary>{t('wp9.settlement.history')}</summary>
        {history === null ? null : (
          <ol className="wp9-history">
            {history.map((entry) => (
              <li key={entry.id}>
                {formatDateTime(entry.occurredAt, locale)} — {entry.actorName ?? entry.actorId} —{' '}
                {t(`wp9.settlement.action.${entry.action}`)}
                {entry.toStatus === null ? null : `: ${t(`wp9.settlement.status.${entry.toStatus}`)}`}
                {entry.note === null ? null : ` — ${entry.note}`}
                {entry.changes === null ? null : ` (${Object.keys(entry.changes).join('، ')})`}
              </li>
            ))}
          </ol>
        )}
      </details>
    </li>
  );
}
