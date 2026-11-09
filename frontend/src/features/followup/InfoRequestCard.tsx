import { type SyntheticEvent, useId, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate, todayIso } from '../cases/format';
import type { MemberRef } from '../cases/api';
import {
  addFollowUp,
  addReply,
  changeRequestStatus,
  COMPLETENESS,
  FOLLOW_UP_KINDS,
  recordDelayReason,
  type Completeness,
  type FollowUpKind,
  type InfoRequest,
  type SettableStatus,
} from './api';
import { failureKey } from './errors';

type Panel = null | 'reply' | 'followUp' | 'close' | 'delay';

interface Props {
  request: InfoRequest;
  /** May change the request: holds INFO_REQUEST_MANAGE and can change the case. */
  editable: boolean;
  canEscalate: boolean;
  members: MemberRef[];
  onChanged: (message: MessageKey) => void;
  onFailed: (message: MessageKey) => void;
}

/** The statuses a request may be moved to by hand from its current one (mirrors the server's flow). */
const NEXT: Partial<Record<InfoRequest['status'], Exclude<SettableStatus, 'CLOSED'>[]>> = {
  DRAFT: ['PENDING_APPROVAL', 'SENT'],
  PENDING_APPROVAL: ['DRAFT', 'SENT'],
  SENT: ['AWAITING'],
  PARTIAL: ['AWAITING'],
  INCOMPLETE: ['AWAITING'],
  CLOSED: ['AWAITING'],
};

const ACCEPTS_REPLIES = new Set<InfoRequest['status']>(['SENT', 'AWAITING', 'PARTIAL', 'INCOMPLETE', 'COMPLETE', 'OVERDUE']);

/** One request to a unit with its replies, its follow-up and the actions on it (requirements section 18). */
export function InfoRequestCard({ request, editable, canEscalate, members, onChanged, onFailed }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [panel, setPanel] = useState<Panel>(null);
  const [reply, setReply] = useState({ receivedOn: todayIso(), completeness: 'PARTIAL' as Completeness, summary: '', file: null as File | null });
  const [followUp, setFollowUp] = useState({ kind: 'REMINDER' as FollowUpKind, note: '', escalatedTo: '' });
  const [closeReason, setCloseReason] = useState('');
  const [delay, setDelay] = useState({ reason: request.delayReason ?? '', dueOn: request.dueOn ?? '' });

  const run = async (action: () => Promise<unknown>, done: MessageKey) => {
    try {
      await action();
      setPanel(null);
      onChanged(done);
    } catch (error) {
      onFailed(failureKey(error));
    }
  };

  const move = (status: Exclude<SettableStatus, 'CLOSED'>) => {
    void run(() => changeRequestStatus(request, status), 'wp3.requests.moved');
  };
  const submitReply = (event: SyntheticEvent) => {
    event.preventDefault();
    void run(() => addReply(request.id, reply), 'wp3.requests.replyAdded');
  };
  const submitFollowUp = (event: SyntheticEvent) => {
    event.preventDefault();
    void run(() => addFollowUp(request.id, followUp), 'wp3.requests.followUpAdded');
  };
  const submitClose = (event: SyntheticEvent) => {
    event.preventDefault();
    void run(() => changeRequestStatus(request, 'CLOSED', { closeReason }), 'wp3.requests.moved');
  };
  const submitDelay = (event: SyntheticEvent) => {
    event.preventDefault();
    void run(() => recordDelayReason(request, delay.reason, delay.dueOn), 'wp3.requests.delayRecorded');
  };

  const toggle = (next: Exclude<Panel, null>) => {
    setPanel((current) => (current === next ? null : next));
  };
  const open = request.status !== 'CLOSED';
  const nextStatuses = NEXT[request.status] ?? [];
  const escalation = FOLLOW_UP_KINDS.filter((kind) => kind !== 'ESCALATION' || canEscalate);

  return (
    <li className="wp3-card" data-status={request.status}>
      <p>
        <strong>{request.requestNo}</strong> — {request.subject}{' '}
        <span className="wp3-badge" data-status={request.status}>
          {t(`wp3.requests.status.${request.status}`)}
        </span>
        {request.overdue ? <span className="wp3-badge" data-status="OVERDUE">{t('wp3.requests.overdue')}</span> : null}
      </p>
      <p className="case-field-hint">
        {request.unitName ?? t('wp3.notSpecified')}
        {request.dueOn === null ? null : ` · ${t('wp3.requests.due', { date: formatDate(request.dueOn, locale) })}`}
        {request.sentOn === null ? null : ` · ${t('wp3.requests.sent', { date: formatDate(request.sentOn, locale) })}`}
        {request.letterNo === null ? null : ` · ${t('wp3.requests.letter', { no: request.letterNo })}`}
        {request.responsibleMemberName === null ? null : ` · ${request.responsibleMemberName}`}
      </p>
      {request.details === null ? null : <p>{request.details}</p>}
      {request.requiredDocuments === null ? null : <p>{t('wp3.requests.documents', { text: request.requiredDocuments })}</p>}
      {request.delayReason === null ? null : <p>{t('wp3.requests.delayReason', { text: request.delayReason })}</p>}
      {request.closeReason === null ? null : <p>{t('wp3.requests.closeReason', { text: request.closeReason })}</p>}

      <h4>{t('wp3.requests.replies')}</h4>
      {request.replies.length === 0 ? (
        <p>{t('wp3.requests.noReplies')}</p>
      ) : (
        <ul>
          {request.replies.map((r) => (
            <li key={r.id}>
              {formatDate(r.receivedOn, locale)} — {t(`wp3.requests.completeness.${r.completeness}`)}
              {r.summary === null ? null : ` — ${r.summary}`}
              {r.documentTitle === null ? null : ` — ${t('wp3.requests.archived', { title: r.documentTitle })}`}
            </li>
          ))}
        </ul>
      )}
      {request.followUps.length === 0 ? null : (
        <>
          <h4>{t('wp3.requests.followUps')}</h4>
          <ul>
            {request.followUps.map((f) => (
              <li key={f.id}>
                {formatDate(f.onDate, locale)} — {t(`wp3.requests.followKind.${f.kind}`)}
                {f.escalatedToName === null ? null : ` → ${f.escalatedToName}`}
                {f.note === null ? null : ` — ${f.note}`}
                {f.byName === null ? null : ` (${f.byName})`}
              </li>
            ))}
          </ul>
        </>
      )}

      {!editable ? null : (
        <div className="case-actions">
          {nextStatuses.map((status) => (
            <button key={status} type="button" onClick={() => { move(status); }}>
              {t(`wp3.requests.moveTo.${status}`)}
            </button>
          ))}
          {!ACCEPTS_REPLIES.has(request.status) ? null : (
            <>
              <button type="button" aria-expanded={panel === 'reply'} onClick={() => { toggle('reply'); }}>
                {t('wp3.requests.addReply')}
              </button>
              <button type="button" aria-expanded={panel === 'followUp'} onClick={() => { toggle('followUp'); }}>
                {t('wp3.requests.addFollowUp')}
              </button>
            </>
          )}
          {request.status === 'CLOSED' ? null : (
            <>
              <button type="button" aria-expanded={panel === 'delay'} onClick={() => { toggle('delay'); }}>
                {t('wp3.requests.recordDelay')}
              </button>
              <button type="button" aria-expanded={panel === 'close'} onClick={() => { toggle('close'); }}>
                {t('wp3.requests.close')}
              </button>
            </>
          )}
        </div>
      )}

      {panel !== 'reply' ? null : (
        <form className="case-form" aria-label={t('wp3.requests.addReply')} noValidate onSubmit={submitReply}>
          <div className="case-field">
            <label htmlFor={`${id}-rd`}>{t('wp3.requests.receivedOn')}</label>
            <input id={`${id}-rd`} type="date" value={reply.receivedOn} max={todayIso()} onChange={(e) => { setReply({ ...reply, receivedOn: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-rc`}>{t('wp3.requests.completeness')}</label>
            <select id={`${id}-rc`} value={reply.completeness} onChange={(e) => { setReply({ ...reply, completeness: e.target.value as Completeness }); }}>
              {COMPLETENESS.map((c) => (
                <option key={c} value={c}>{t(`wp3.requests.completeness.${c}`)}</option>
              ))}
            </select>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-rs`}>{t('wp3.requests.summary')}</label>
            <textarea id={`${id}-rs`} rows={3} maxLength={4000} value={reply.summary} onChange={(e) => { setReply({ ...reply, summary: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-rf`}>{t('wp3.requests.file')}</label>
            <input id={`${id}-rf`} type="file" accept="application/pdf" onChange={(e) => { setReply({ ...reply, file: e.target.files?.[0] ?? null }); }} />
            <span className="case-field-hint">{t('wp3.requests.fileHint')}</span>
          </div>
          <div className="case-actions"><button type="submit" className="primary">{t('wp3.requests.saveReply')}</button></div>
        </form>
      )}

      {panel !== 'followUp' ? null : (
        <form className="case-form" aria-label={t('wp3.requests.addFollowUp')} noValidate onSubmit={submitFollowUp}>
          <div className="case-field">
            <label htmlFor={`${id}-fk`}>{t('wp3.requests.followKind')}</label>
            <select id={`${id}-fk`} value={followUp.kind} onChange={(e) => { setFollowUp({ ...followUp, kind: e.target.value as FollowUpKind }); }}>
              {escalation.map((kind) => (
                <option key={kind} value={kind}>{t(`wp3.requests.followKind.${kind}`)}</option>
              ))}
            </select>
          </div>
          {followUp.kind !== 'ESCALATION' ? null : (
            <div className="case-field">
              <label htmlFor={`${id}-ft`}>{t('wp3.requests.escalateTo')}</label>
              <select id={`${id}-ft`} value={followUp.escalatedTo} onChange={(e) => { setFollowUp({ ...followUp, escalatedTo: e.target.value }); }}>
                <option value="">{t('wp3.choose')}</option>
                {members.map((m) => (
                  <option key={m.id} value={m.id}>{m.displayName}</option>
                ))}
              </select>
            </div>
          )}
          <div className="case-field">
            <label htmlFor={`${id}-fn`}>{t('wp3.requests.note')}</label>
            <textarea id={`${id}-fn`} rows={2} maxLength={2000} value={followUp.note} onChange={(e) => { setFollowUp({ ...followUp, note: e.target.value }); }} />
          </div>
          <div className="case-actions"><button type="submit" className="primary">{t('wp3.requests.saveFollowUp')}</button></div>
        </form>
      )}

      {panel !== 'close' || !open ? null : (
        <form className="case-form" aria-label={t('wp3.requests.close')} noValidate onSubmit={submitClose}>
          <div className="case-field">
            <label htmlFor={`${id}-cr`}>{t('wp3.requests.closeWhy')}</label>
            <textarea id={`${id}-cr`} rows={2} maxLength={2000} value={closeReason} onChange={(e) => { setCloseReason(e.target.value); }} />
            <span className="case-field-hint">{t('wp3.requests.closeHint')}</span>
          </div>
          <div className="case-actions"><button type="submit" className="primary">{t('wp3.requests.close')}</button></div>
        </form>
      )}

      {panel !== 'delay' ? null : (
        <form className="case-form" aria-label={t('wp3.requests.recordDelay')} noValidate onSubmit={submitDelay}>
          <div className="case-field">
            <label htmlFor={`${id}-dr`}>{t('wp3.requests.delayWhy')}</label>
            <textarea id={`${id}-dr`} rows={2} maxLength={2000} value={delay.reason} onChange={(e) => { setDelay({ ...delay, reason: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-dd`}>{t('wp3.requests.newDue')}</label>
            <input id={`${id}-dd`} type="date" value={delay.dueOn} onChange={(e) => { setDelay({ ...delay, dueOn: e.target.value }); }} />
          </div>
          <div className="case-actions"><button type="submit" className="primary">{t('wp3.requests.saveDelay')}</button></div>
        </form>
      )}
    </li>
  );
}
