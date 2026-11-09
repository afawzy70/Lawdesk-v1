import { useId, useState } from 'react';
import { ApiError } from '../../api/http';
import { useI18n } from '../../i18n/I18nContext';
import type { MessageKey } from '../../i18n/catalog';
import { formatDate } from '../cases/format';
import { listCaseDocuments } from '../operations/documents/documentsApi';
import { listAssignees } from '../operations/tasks/tasksApi';
import { AreaField, ChoiceField, Notice, TextField } from '../pleadings/ui';
import { amountText, orNull, useLoad } from '../pleadings/useLoad';
import {
  ACTION_KINDS,
  asList,
  addEnforcementAction,
  createEnforcement,
  ENFORCEMENT_STATUSES,
  getEnforcement,
  updateEnforcement,
  updateEnforcementAction,
  type ActionKind,
  type EnforcementAction,
  type EnforcementFile,
  type EnforcementStatus,
} from './api';
import { archiveError } from './errors';
import { usePermissions } from './usePermissions';

interface Props {
  judgmentId: string;
  caseId: string;
  /** False for an archived case. */
  editable: boolean;
}

type Message = { failed: boolean; key: MessageKey } | null;

/**
 * The enforcement file of one judgment (requirement 11): who must enforce it, the amount asked, the obstacles, the status,
 * the target and completion dates, and the actions and correspondence with their dates and results. The judgment's own
 * number, date and operative part are shown from the judgment, never copied.
 */
export function JudgmentEnforcement({ judgmentId, caseId, editable }: Props) {
  const { t, locale } = useI18n();
  const headingId = useId();
  const granted = usePermissions();
  const overview = useLoad((signal) => getEnforcement(judgmentId, signal), [judgmentId]);
  const members = useLoad((signal) => listAssignees(signal), []);
  const documents = useLoad((signal) => listCaseDocuments(caseId, signal), [caseId]);
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState<Message>(null);

  if (!granted.has('ENFORCEMENT_VIEW')) return null;
  if (overview.status === 403 || overview.status === 404) {
    return (
      <section className="archive-section" aria-labelledby={headingId}>
        <h5 id={headingId}>{t('wp5.enf.title')}</h5>
        <p className="archive-meta">{t('wp5.enf.hidden')}</p>
      </section>
    );
  }
  const canManage = editable && granted.has('ENFORCEMENT_MANAGE');
  const data = overview.data;
  const file = data?.file ?? null;
  const changed = (key: MessageKey) => {
    setNotice({ failed: false, key });
    setEditing(false);
    overview.reload();
  };

  return (
    <section className="archive-section" aria-labelledby={headingId}>
      <h5 id={headingId}>{t('wp5.enf.title')}</h5>
      {data === null ? <p>{overview.status === null ? t('wp5.loading') : t('wp5.error.load')}</p> : null}
      {notice === null ? null : <Notice failed={notice.failed}>{t(notice.key)}</Notice>}
      {data !== null && file === null && !editing ? (
        <>
          <p className="archive-meta">{t('wp5.enf.none')}</p>
          {canManage ? (
            <button
              type="button"
              onClick={() => {
                setNotice(null);
                setEditing(true);
              }}
            >
              {t('wp5.enf.open')}
            </button>
          ) : null}
        </>
      ) : null}
      {file !== null && !editing ? (
        <>
          <dl className="case-facts">
            <Fact name={t('wp5.enf.status')} value={t(`wp5.enf.status.${file.status}`)} />
            <Fact name={t('wp5.enf.member')} value={file.responsibleMemberName} />
            <Fact name={t('wp5.enf.party')} value={file.executingParty} />
            <Fact name={t('wp5.enf.amount')} value={file.amountRequired === null ? null : `${file.amountRequired} KWD`} />
            <Fact name={t('wp5.enf.obstacles')} value={file.obstacles} />
            <Fact name={t('wp5.enf.dueOn')} value={file.dueOn === null ? null : formatDate(file.dueOn, locale)} />
            <Fact name={t('wp5.enf.completedOn')} value={file.completedOn === null ? null : formatDate(file.completedOn, locale)} />
            <Fact name={t('wp5.enf.notes')} value={file.notes} />
          </dl>
          {file.late ? <p className="case-alert">{t('wp5.enf.late')}</p> : null}
          {canManage ? (
            <button
              type="button"
              onClick={() => {
                setNotice(null);
                setEditing(true);
              }}
            >
              {t('wp5.enf.edit')}
            </button>
          ) : null}
        </>
      ) : null}
      {editing && data !== null ? (
        <FileForm
          judgmentId={judgmentId}
          file={file}
          members={asList(members.data)}
          onSaved={() => {
            changed('wp5.enf.saved');
          }}
          onCancel={() => {
            setEditing(false);
          }}
        />
      ) : null}
      {file !== null ? (
        <Actions
          file={file}
          actions={asList(data?.actions)}
          documents={asList(documents.data).map((d) => [d.id, d.title] as const)}
          canManage={canManage}
          onChanged={overview.reload}
        />
      ) : null}
    </section>
  );
}

function Fact({ name, value }: { name: string; value: string | null }) {
  if (value === null) return null;
  return (
    <div className="pleadings-fact">
      <dt>{name}</dt>
      <dd>{value}</dd>
    </div>
  );
}

interface FormProps {
  judgmentId: string;
  file: EnforcementFile | null;
  members: readonly { id: string; displayName: string }[];
  onSaved: () => void;
  onCancel: () => void;
}

function FileForm({ judgmentId, file, members, onSaved, onCancel }: FormProps) {
  const { t } = useI18n();
  const [status, setStatus] = useState<EnforcementStatus>(file?.status ?? 'NOT_STARTED');
  const [memberId, setMemberId] = useState(file?.responsibleMemberId ?? '');
  const [party, setParty] = useState(file?.executingParty ?? '');
  const [amount, setAmount] = useState(file?.amountRequired ?? '');
  const [obstacles, setObstacles] = useState(file?.obstacles ?? '');
  const [dueOn, setDueOn] = useState(file?.dueOn ?? '');
  const [completedOn, setCompletedOn] = useState(file?.completedOn ?? '');
  const [notes, setNotes] = useState(file?.notes ?? '');
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [flagged, setFlagged] = useState<string[]>([]);

  const submit = async () => {
    setBusy(true);
    setFailure(null);
    setFlagged([]);
    const body = {
      status,
      responsibleMemberId: orNull(memberId),
      executingParty: orNull(party),
      amountRequired: amountText(amount),
      obstacles: orNull(obstacles),
      dueOn: orNull(dueOn),
      completedOn: orNull(completedOn),
      notes: orNull(notes),
    };
    try {
      if (file === null) await createEnforcement(judgmentId, body);
      else await updateEnforcement(file.id, { ...body, version: file.version });
      onSaved();
    } catch (error) {
      setFailure(archiveError(error));
      setFlagged(fieldsOf(error));
    }
    setBusy(false);
  };

  return (
    <form
      className="case-form"
      aria-label={t('wp5.enf.title')}
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <ChoiceField
        label={t('wp5.enf.status')}
        value={status}
        onChange={(value) => {
          setStatus(value as EnforcementStatus);
        }}
        options={ENFORCEMENT_STATUSES.map((s) => [s, t(`wp5.enf.status.${s}`)] as const)}
        invalid={flagged.includes('status')}
      />
      <ChoiceField
        label={t('wp5.enf.member')}
        value={memberId}
        onChange={setMemberId}
        options={members.map((m) => [m.id, m.displayName] as const)}
        empty={t('wp5.notSpecified')}
        invalid={flagged.includes('responsibleMemberId')}
      />
      <TextField label={t('wp5.enf.party')} value={party} onChange={setParty} max={300} />
      <TextField label={t('wp5.enf.amount')} value={amount} onChange={setAmount} dir="ltr" invalid={flagged.includes('amountRequired')} />
      <p className="archive-meta">{t('wp5.enf.noteMoney')}</p>
      <AreaField label={t('wp5.enf.obstacles')} value={obstacles} onChange={setObstacles} />
      <TextField label={t('wp5.enf.dueOn')} type="date" value={dueOn} onChange={setDueOn} />
      <TextField label={t('wp5.enf.completedOn')} type="date" value={completedOn} onChange={setCompletedOn} invalid={flagged.includes('completedOn')} />
      <AreaField label={t('wp5.enf.notes')} value={notes} onChange={setNotes} />
      {failure === null ? null : <Notice failed>{t(failure)}</Notice>}
      <div className="archive-actions">
        <button type="submit" disabled={busy}>
          {busy ? t('wp5.saving') : t('wp5.enf.save')}
        </button>
        <button type="button" onClick={onCancel}>
          {t('wp5.cancel')}
        </button>
      </div>
    </form>
  );
}

function fieldsOf(error: unknown): string[] {
  return error instanceof ApiError ? (error.problem?.errors ?? []).map((e) => e.field) : [];
}

interface ActionsProps {
  file: EnforcementFile;
  actions: readonly EnforcementAction[];
  documents: readonly (readonly [string, string])[];
  canManage: boolean;
  onChanged: () => void;
}

function Actions({ file, actions, documents, canManage, onChanged }: ActionsProps) {
  const { t, locale } = useI18n();
  const [editing, setEditing] = useState<EnforcementAction | 'new' | null>(null);
  return (
    <div>
      <h5>{t('wp5.enf.actions')}</h5>
      {actions.length === 0 ? <p className="archive-meta">{t('wp5.enf.actionsNone')}</p> : null}
      <ol className="archive-list">
        {actions.map((a) => (
          <li key={a.id} className="archive-item">
            <p>
              <strong>{t('wp5.enf.action.number', { number: String(a.seq) })}</strong> · {t(`wp5.enf.action.${a.kind}`)} · {formatDate(a.actionDate, locale)}
            </p>
            <p>{a.description}</p>
            {a.result === null ? null : (
              <p className="archive-meta">
                {t('wp5.enf.action.result')}: {a.result}
              </p>
            )}
            {a.documentTitle === null ? null : (
              <p className="archive-meta">
                {t('wp5.enf.action.document')}: {a.documentTitle}
              </p>
            )}
            {canManage ? (
              <button
                type="button"
                aria-label={t('wp5.enf.action.edit', { number: String(a.seq) })}
                onClick={() => {
                  setEditing(a);
                }}
              >
                {t('wp5.legal.edit')}
              </button>
            ) : null}
          </li>
        ))}
      </ol>
      {canManage && editing === null ? (
        <button
          type="button"
          onClick={() => {
            setEditing('new');
          }}
        >
          {t('wp5.enf.action.add')}
        </button>
      ) : null}
      {editing === null ? null : (
        <ActionForm
          key={editing === 'new' ? 'new' : editing.id}
          file={file}
          action={editing === 'new' ? null : editing}
          documents={documents}
          onSaved={() => {
            setEditing(null);
            onChanged();
          }}
          onCancel={() => {
            setEditing(null);
          }}
        />
      )}
    </div>
  );
}

function ActionForm({
  file,
  action,
  documents,
  onSaved,
  onCancel,
}: {
  file: EnforcementFile;
  action: EnforcementAction | null;
  documents: readonly (readonly [string, string])[];
  onSaved: () => void;
  onCancel: () => void;
}) {
  const { t } = useI18n();
  const [kind, setKind] = useState<ActionKind>(action?.kind ?? 'PROCEDURE');
  const [date, setDate] = useState(action?.actionDate ?? '');
  const [description, setDescription] = useState(action?.description ?? '');
  const [result, setResult] = useState(action?.result ?? '');
  const [documentId, setDocumentId] = useState(action?.documentId ?? '');
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<MessageKey | null>(null);

  const submit = async () => {
    setBusy(true);
    setFailure(null);
    const body = {
      kind,
      actionDate: date,
      description: description.trim(),
      result: orNull(result),
      documentId: orNull(documentId),
    };
    try {
      if (action === null) await addEnforcementAction(file.id, body);
      else await updateEnforcementAction(action.id, { ...body, version: action.version });
      onSaved();
    } catch (error) {
      setFailure(archiveError(error));
    }
    setBusy(false);
  };

  return (
    <form
      className="case-form"
      aria-label={action === null ? t('wp5.enf.action.add') : t('wp5.enf.action.edit', { number: String(action.seq) })}
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <ChoiceField
        label={t('wp5.enf.action.kind')}
        value={kind}
        onChange={(value) => {
          setKind(value as ActionKind);
        }}
        options={ACTION_KINDS.map((k) => [k, t(`wp5.enf.action.${k}`)] as const)}
      />
      <TextField label={t('wp5.enf.action.date')} type="date" value={date} onChange={setDate} />
      <AreaField label={t('wp5.enf.action.description')} value={description} onChange={setDescription} />
      <AreaField label={t('wp5.enf.action.result')} value={result} onChange={setResult} />
      <ChoiceField label={t('wp5.enf.action.document')} value={documentId} onChange={setDocumentId} options={documents} empty={t('wp5.notSpecified')} />
      {failure === null ? null : <Notice failed>{t(failure)}</Notice>}
      <div className="archive-actions">
        <button type="submit" disabled={busy}>
          {busy ? t('wp5.saving') : t('wp5.save')}
        </button>
        <button type="button" onClick={onCancel}>
          {t('wp5.cancel')}
        </button>
      </div>
    </form>
  );
}
