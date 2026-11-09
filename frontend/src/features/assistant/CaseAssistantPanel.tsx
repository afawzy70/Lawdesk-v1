import { type SyntheticEvent, useContext, useEffect, useId, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import type { MessageKey } from '../../i18n/catalog';
import { AuthContext } from '../auth/AuthContext';
import { listCaseDocuments, type CaseDocument } from '../operations/documents/documentsApi';
import { AnswerView } from './AnswerView';
import { ask, listOutputs, reviewOutput, TASKS, type Answer, type AskInput, type Output, type Task } from './api';
import { failureKey, keyFor } from './labels';
import './assistant.css';

interface Props {
  caseId: string;
}

const NEEDS_DOCUMENT: readonly Task[] = ['DOCUMENT_ANALYSIS', 'COMPARISON'];
const NEEDS_QUESTION: readonly Task[] = ['QUESTION'];
const MAY_ASK: readonly Task[] = ['QUESTION', 'PRECEDENTS', 'MEMO_DRAFT'];

/**
 * The assistant panel of the case page (section 22). A NON-PRODUCTION test mode: the banner says so, nothing leaves the system
 * and every answer quotes stored records with their sources. It reads only; what it produces is a draft that the member
 * reviews. Shown only to holders of ASSISTANT_USE (the server checks that and the case access on every request).
 */
export function CaseAssistantPanel({ caseId }: Props) {
  const state = useContext(AuthContext)?.state;
  const allowed = state?.status === 'signedIn' && state.me.permissions.includes('ASSISTANT_USE');
  return allowed ? <Panel caseId={caseId} /> : null;
}

function Panel({ caseId }: Props) {
  const { t } = useI18n();
  const id = useId();
  const [task, setTask] = useState<Task>('QUESTION');
  const [question, setQuestion] = useState('');
  const [documentId, setDocumentId] = useState('');
  const [otherId, setOtherId] = useState('');
  const [keep, setKeep] = useState(false);
  const [documents, setDocuments] = useState<CaseDocument[] | null>(null);
  const [outputs, setOutputs] = useState<Output[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<MessageKey | null>(null);
  const [answer, setAnswer] = useState<Answer | null>(null);

  const needsDocument = NEEDS_DOCUMENT.includes(task);

  useEffect(() => {
    const controller = new AbortController();
    listOutputs(caseId, controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) setOutputs(found);
      },
      () => undefined,
    );
    return () => {
      controller.abort();
    };
  }, [caseId, answer]);

  useEffect(() => {
    if (!needsDocument || documents !== null) return undefined;
    const controller = new AbortController();
    listCaseDocuments(caseId, controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) setDocuments(found);
      },
      () => {
        if (!controller.signal.aborted) setDocuments([]);
      },
    );
    return () => {
      controller.abort();
    };
  }, [needsDocument, documents, caseId]);

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    const input: AskInput = { task, caseId, saveDraft: keep };
    if (MAY_ASK.includes(task) && question.trim() !== '') input.question = question.trim();
    if (needsDocument) {
      input.documentId = documentId;
      if (task === 'COMPARISON') input.otherDocumentId = otherId;
    }
    setBusy(true);
    setError(null);
    try {
      setAnswer(await ask(input));
    } catch (failure: unknown) {
      setAnswer(null);
      setError(failureKey(failure));
    } finally {
      setBusy(false);
    }
  };

  const review = async (output: Output, decision: 'APPROVED' | 'DISCARDED') => {
    try {
      const updated = await reviewOutput(output.id, decision);
      setOutputs((current) => current.map((o) => (o.id === updated.id ? updated : o)));
    } catch (failure: unknown) {
      setError(failureKey(failure));
    }
  };

  const incomplete =
    (NEEDS_QUESTION.includes(task) && question.trim() === '') ||
    (needsDocument && (documentId === '' || (task === 'COMPARISON' && otherId === '')));

  return (
    <section className="case-section assistant-panel" aria-labelledby={`${id}-title`}>
      <h2 id={`${id}-title`}>{t('assistant.panel.title')}</h2>
      <p role="note" className="case-notice">
        {t('assistant.banner')}
      </p>
      <form className="case-form" noValidate onSubmit={(event) => void submit(event)}>
        <div className="case-field">
          <label htmlFor={`${id}-task`}>{t('assistant.panel.task')}</label>
          <select id={`${id}-task`} value={task} onChange={(event) => { setTask(event.target.value as Task); setAnswer(null); }}>
            {TASKS.map((value) => (
              <option key={value} value={value}>
                {t(`assistant.task.${value}`)}
              </option>
            ))}
          </select>
        </div>
        {(MAY_ASK.includes(task)) && (
          <div className="case-field">
            <label htmlFor={`${id}-q`}>{t('assistant.panel.question')}</label>
            <textarea id={`${id}-q`} dir="auto" rows={3} maxLength={1000} aria-describedby={`${id}-qh`} value={question}
              onChange={(event) => { setQuestion(event.target.value); }} />
            <small id={`${id}-qh`}>{t('assistant.panel.questionHint')}</small>
          </div>
        )}
        {needsDocument && documents !== null && documents.length === 0 && <p>{t('assistant.panel.noDocuments')}</p>}
        {needsDocument && documents !== null && documents.length > 0 && (
          <>
            <DocumentSelect id={`${id}-d1`} label={t('assistant.panel.document')} value={documentId} documents={documents} onChange={setDocumentId} />
            {task === 'COMPARISON' && (
              <DocumentSelect id={`${id}-d2`} label={t('assistant.panel.otherDocument')} value={otherId} documents={documents} onChange={setOtherId} />
            )}
          </>
        )}
        <label className="case-field assistant-keep">
          <input type="checkbox" checked={keep} onChange={(event) => { setKeep(event.target.checked); }} /> {t('assistant.panel.save')}
        </label>
        <button type="submit" disabled={busy || incomplete}>
          {busy ? t('assistant.panel.working') : t('assistant.panel.submit')}
        </button>
      </form>
      {error !== null && (
        <p role="alert" className="case-alert">
          {t(error)}
        </p>
      )}
      {answer !== null && <AnswerView answer={answer} />}
      <h3>{t('assistant.drafts.title')}</h3>
      {outputs.length === 0 ? (
        <p>{t('assistant.drafts.none')}</p>
      ) : (
        <ul className="assistant-drafts">
          {outputs.map((output) => (
            <li key={output.id}>
              <span dir="auto">{output.title}</span> — {t(keyFor('assistant.drafts.status', output.status) ?? 'assistant.error.generic')}
              {output.status === 'DRAFT' && (
                <>
                  {' '}
                  <button type="button" onClick={() => void review(output, 'APPROVED')}>{t('assistant.drafts.approve')}</button>{' '}
                  <button type="button" onClick={() => void review(output, 'DISCARDED')}>{t('assistant.drafts.discard')}</button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

interface SelectProps {
  id: string;
  label: string;
  value: string;
  documents: readonly CaseDocument[];
  onChange: (value: string) => void;
}

function DocumentSelect({ id, label, value, documents, onChange }: SelectProps) {
  const { t } = useI18n();
  return (
    <div className="case-field">
      <label htmlFor={id}>{label}</label>
      <select id={id} value={value} onChange={(event) => { onChange(event.target.value); }}>
        <option value="">{t('assistant.panel.choose')}</option>
        {documents.map((document) => (
          <option key={document.id} value={document.id}>
            {document.title}
          </option>
        ))}
      </select>
    </div>
  );
}
