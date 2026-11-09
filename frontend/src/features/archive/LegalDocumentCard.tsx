import { useId, useRef, useState } from 'react';
import { formatDateTime } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import type { MessageKey } from '../../i18n/catalog';
import { formatDate } from '../cases/format';
import { contentUrl } from '../operations/documents/documentsApi';
import type { DirectoryUser } from '../operations/tasks/tasksApi';
import { Notice, TextField } from '../pleadings/ui';
import { useLoad } from '../pleadings/useLoad';
import {
  acceptAttribute,
  attachLegalFile,
  legalEvents,
  legalStep,
  recordSent,
  type ArchiveFormat,
  type CategoryValue,
  type LegalDocument,
  type LegalStep,
} from './api';
import { archiveError } from './errors';
import { LegalDocumentForm } from './LegalDocumentForm';

interface Props {
  doc: LegalDocument;
  caseId: string;
  categories: readonly CategoryValue[];
  members: readonly DirectoryUser[];
  formats: readonly ArchiveFormat[];
  canManage: boolean;
  canApprove: boolean;
  onChanged: () => void;
}

const TONE: Record<string, string> = { DRAFT: 'warn', IN_REVIEW: 'warn', APPROVED: 'good', SENT: 'good', ARCHIVED: 'good' };
const NOTE_REQUIRED: readonly LegalStep[] = ['return', 'reopen'];

type Pending = { kind: 'step'; step: LegalStep } | { kind: 'sent' } | { kind: 'edit' } | null;

/** One memo or letter: its data, electronic copy, approval and sending records, history, and the steps its status allows. */
export function LegalDocumentCard({ doc, caseId, categories, members, formats, canManage, canApprove, onChanged }: Props) {
  const { t, locale } = useI18n();
  const [pending, setPending] = useState<Pending>(null);
  const [note, setNote] = useState('');
  const [sentOn, setSentOn] = useState('');
  const [method, setMethod] = useState('');
  const [reference, setReference] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ failed: boolean; key: MessageKey; vars?: Record<string, string> } | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const fileId = useId();
  const file = useRef<HTMLInputElement>(null);
  const events = useLoad((signal) => (historyOpen ? legalEvents(doc.id, signal) : Promise.resolve([])), [doc.id, doc.version, historyOpen]);

  const status = (s: string) => t(`wp5.legal.status.${s}` as MessageKey);
  const open = (next: Pending) => {
    setPending(next);
    setNote('');
    setMessage(null);
  };

  const run = async (action: () => Promise<LegalDocument>, done: MessageKey) => {
    setBusy(true);
    setMessage(null);
    try {
      const updated = await action();
      setPending(null);
      setNote('');
      setMessage({ failed: false, key: done, vars: { status: status(updated.status) } });
      onChanged();
    } catch (error) {
      setMessage({ failed: true, key: archiveError(error) });
    }
    setBusy(false);
  };

  const step = (which: LegalStep) => {
    if (NOTE_REQUIRED.includes(which) && note.trim() === '') {
      setMessage({ failed: true, key: 'wp5.legal.noteRequired' });
      return;
    }
    void run(() => legalStep(doc.id, which, note, doc.version), 'wp5.legal.stepDone');
  };

  const send = () => {
    if (sentOn === '' || method.trim() === '') {
      setMessage({ failed: true, key: 'wp5.error.fields' });
      return;
    }
    void run(() => recordSent(doc.id, sentOn, method.trim(), reference, doc.version), 'wp5.legal.stepDone');
  };

  const attach = async () => {
    const chosen = file.current?.files?.[0];
    if (chosen === undefined) {
      setMessage({ failed: true, key: 'wp5.doc.chooseFile' });
      return;
    }
    await run(() => attachLegalFile(doc.id, chosen, ''), 'wp5.legal.attached');
    if (file.current !== null) file.current.value = '';
  };

  const stepButton = (which: LegalStep, label: MessageKey) => (
    <button
      type="button"
      aria-label={t('wp5.legal.stepLabel', { step: t(label), title: doc.title })}
      onClick={() => {
        // Steps without a note go straight through; those that need a reason or an optional note ask for it first.
        open({ kind: 'step', step: which });
      }}
    >
      {t(label)}
    </button>
  );

  const label = (c: string | null, ar: string | null, en: string | null) => (c === null ? null : locale === 'ar' ? ar : (en ?? ar));
  const category = label(doc.categoryId, doc.categoryLabelAr, doc.categoryLabelEn);

  return (
    <li className="archive-item">
      <h4>{doc.title}</h4>
      <p className="archive-meta">
        <span className="archive-badge" data-tone={TONE[doc.status] ?? 'warn'}>
          {status(doc.status)}
        </span>
        {category === null ? '' : ` · ${category}`}
        {doc.documentDate === null ? '' : ` · ${formatDate(doc.documentDate, locale)}`}
      </p>
      {doc.issuer === null ? null : (
        <p className="archive-meta">
          {t('wp5.legal.issuer')}: {doc.issuer}
        </p>
      )}
      {doc.addressee === null ? null : (
        <p className="archive-meta">
          {t('wp5.legal.addressee')}: {doc.addressee}
        </p>
      )}
      {doc.responsibleMemberName === null ? null : (
        <p className="archive-meta">
          {t('wp5.legal.member')}: {doc.responsibleMemberName}
        </p>
      )}
      {doc.description === null ? null : <p>{doc.description}</p>}
      <p className="archive-meta">
        {t('wp5.legal.file')}:{' '}
        {doc.documentId === null ? (
          t('wp5.legal.noFile')
        ) : (
          <a href={contentUrl(doc.documentId)} download>
            {t('wp5.legal.fileVersion', { name: doc.documentFileName ?? '', number: String(doc.documentVersionNo ?? 1) })}
          </a>
        )}
      </p>
      {doc.approvedAt === null ? null : (
        <p className="archive-meta">
          {t('wp5.legal.approvedBy', { name: doc.approvedByName ?? t('wp5.doc.unknownUser'), date: formatDateTime(doc.approvedAt, locale) })}
          {doc.approvalNote === null ? '' : ` · ${t('wp5.legal.approvalNote', { note: doc.approvalNote })}`}
        </p>
      )}
      {doc.sentRecorded ? (
        <p className="archive-meta">
          {t('wp5.legal.sentRecorded', { date: doc.sentOn === null ? '' : formatDate(doc.sentOn, locale), method: doc.sentMethod ?? '' })}
          {doc.sentReference === null ? '' : ` · ${t('wp5.legal.sentReference', { reference: doc.sentReference })}`}
          {doc.sentViaIntegration ? '' : ` · ${t('wp5.legal.notViaSystem')}`}
        </p>
      ) : null}

      <div className="archive-actions">
        {canManage && doc.status === 'DRAFT' ? (
          <>
            <button type="button" onClick={() => { open({ kind: 'edit' }); }}>
              {t('wp5.legal.edit')}
            </button>
            {stepButton('submit', 'wp5.legal.step.submit')}
          </>
        ) : null}
        {(canManage || canApprove) && doc.status === 'IN_REVIEW' ? stepButton('return', 'wp5.legal.step.return') : null}
        {canApprove && doc.status === 'IN_REVIEW' ? stepButton('approve', 'wp5.legal.step.approve') : null}
        {canManage && doc.status === 'APPROVED' ? (
          <button type="button" onClick={() => { open({ kind: 'sent' }); }}>
            {t('wp5.legal.step.recordSent')}
          </button>
        ) : null}
        {canApprove && doc.status === 'APPROVED' ? stepButton('reopen', 'wp5.legal.step.reopen') : null}
        {canManage && (doc.status === 'APPROVED' || doc.status === 'SENT') ? stepButton('archive', 'wp5.legal.step.archive') : null}
        <button
          type="button"
          aria-expanded={historyOpen}
          aria-label={t('wp5.legal.historyLabel', { title: doc.title })}
          onClick={() => {
            setHistoryOpen(!historyOpen);
          }}
        >
          {historyOpen ? t('wp5.legal.hideHistory') : t('wp5.legal.history')}
        </button>
      </div>

      {canManage && doc.status === 'DRAFT' ? (
        <div className="case-field">
          <label htmlFor={fileId}>{doc.documentId === null ? t('wp5.legal.attachLabel', { title: doc.title }) : t('wp5.legal.attachNew')}</label>
          <input id={fileId} ref={file} type="file" accept={acceptAttribute(formats)} />
          <button type="button" disabled={busy} onClick={() => void attach()}>
            {doc.documentId === null ? t('wp5.legal.attach') : t('wp5.legal.attachNew')}
          </button>
        </div>
      ) : null}

      {pending?.kind === 'edit' ? (
        <LegalDocumentForm
          caseId={caseId}
          existing={doc}
          categories={categories}
          members={members}
          onSaved={() => {
            setPending(null);
            onChanged();
          }}
          onCancel={() => {
            setPending(null);
          }}
        />
      ) : null}

      {pending?.kind === 'step' ? (
        <div className="case-form" role="group" aria-label={t(`wp5.legal.step.${pending.step}` as MessageKey)}>
          <TextField label={t('wp5.legal.note')} value={note} onChange={setNote} max={500} invalid={NOTE_REQUIRED.includes(pending.step) && message?.key === 'wp5.legal.noteRequired'} />
          <div className="archive-actions">
            <button type="button" disabled={busy} onClick={() => { step(pending.step); }}>
              {t('wp5.legal.confirm')}
            </button>
            <button type="button" onClick={() => { setPending(null); }}>
              {t('wp5.cancel')}
            </button>
          </div>
        </div>
      ) : null}

      {pending?.kind === 'sent' ? (
        <div className="case-form" role="group" aria-label={t('wp5.legal.step.recordSent')}>
          <TextField label={t('wp5.legal.sentOn')} type="date" value={sentOn} onChange={setSentOn} />
          <TextField label={t('wp5.legal.sentMethod')} value={method} onChange={setMethod} max={100} />
          <TextField label={t('wp5.legal.sentRefField')} value={reference} onChange={setReference} max={200} />
          <p className="archive-meta">{t('wp5.legal.notViaSystem')}</p>
          <div className="archive-actions">
            <button type="button" disabled={busy} onClick={send}>
              {t('wp5.legal.confirm')}
            </button>
            <button type="button" onClick={() => { setPending(null); }}>
              {t('wp5.cancel')}
            </button>
          </div>
        </div>
      ) : null}

      {message === null ? null : <Notice failed={message.failed}>{t(message.key, message.vars)}</Notice>}

      {historyOpen ? (
        <ol className="archive-list" aria-label={t('wp5.legal.historyLabel', { title: doc.title })}>
          {(events.data ?? []).map((e) => (
            <li key={e.id} className="archive-meta">
              {e.fromStatus === null
                ? t('wp5.legal.eventStart', { to: status(e.toStatus) })
                : t('wp5.legal.event', { from: status(e.fromStatus), to: status(e.toStatus) })}
              {' · '}
              {t('wp5.legal.by', { name: e.createdByName ?? t('wp5.doc.unknownUser') })} · {formatDateTime(e.createdAt, locale)}
              {e.note === null ? '' : ` · ${e.note}`}
            </li>
          ))}
        </ol>
      ) : null}
    </li>
  );
}
