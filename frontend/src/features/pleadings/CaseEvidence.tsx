import { useId, useState, type SyntheticEvent } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import { listCaseTasks } from '../operations/tasks/tasksApi';
import {
  createEvidence,
  createFact,
  EVIDENCE_IMPORTANCE,
  EVIDENCE_ROLES,
  followUpEvidence,
  linkEvidence,
  listClaims,
  listDefenses,
  listEvidence,
  listFacts,
  unlinkEvidence,
  updateEvidence,
  type EvidenceInput,
  type EvidenceItem,
} from './api';
import { documentOptions, usePickers } from './pickers';
import { CheckField, ChoiceField, Notice, TextField } from './ui';
import { flaggedFields, orNull, saveMessage, useLoad } from './useLoad';

interface FormState {
  name: string;
  role: string;
  importance: string;
  documentId: string;
  docType: string;
  issuer: string;
  issuedOn: string;
  receivedOn: string;
  source: string;
  submittedToCourt: boolean;
  submittedOn: string;
  submittedIn: string;
  opponentResponded: string;
  opponentResponse: string;
  courtPosition: string;
  notes: string;
}

const EMPTY: FormState = {
  name: '',
  role: 'SUPPORTING',
  importance: '',
  documentId: '',
  docType: '',
  issuer: '',
  issuedOn: '',
  receivedOn: '',
  source: '',
  submittedToCourt: false,
  submittedOn: '',
  submittedIn: '',
  opponentResponded: '',
  opponentResponse: '',
  courtPosition: '',
  notes: '',
};

const stateOf = (e: EvidenceItem): FormState => ({
  name: e.name,
  role: e.role,
  importance: e.importance ?? '',
  documentId: e.documentId ?? '',
  docType: e.docType ?? '',
  issuer: e.issuer ?? '',
  issuedOn: e.issuedOn ?? '',
  receivedOn: e.receivedOn ?? '',
  source: e.source ?? '',
  submittedToCourt: e.submittedToCourt,
  submittedOn: e.submittedOn ?? '',
  submittedIn: e.submittedIn ?? '',
  opponentResponded: e.opponentResponded === null ? '' : String(e.opponentResponded),
  opponentResponse: e.opponentResponse ?? '',
  courtPosition: e.courtPosition ?? '',
  notes: e.notes ?? '',
});

interface Props {
  caseId: string;
  canManage: boolean;
}

/** الوقائع والأدلة (section 20): facts, evidence with its role, links to facts, defences and claims, and the gaps. */
export function CaseEvidence({ caseId, canManage }: Props) {
  const { t, locale } = useI18n();
  const headingId = useId();
  const facts = useLoad((signal) => listFacts(caseId, signal), [caseId]);
  const items = useLoad((signal) => listEvidence(caseId, signal), [caseId]);
  const defenses = useLoad((signal) => listDefenses(caseId, signal), [caseId]);
  const claims = useLoad((signal) => listClaims(caseId, signal), [caseId]);
  const tasks = useLoad((signal) => listCaseTasks(caseId, signal), [caseId]);
  const pickers = usePickers(caseId);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const [factText, setFactText] = useState('');
  const [factDate, setFactDate] = useState('');
  const [editing, setEditing] = useState<EvidenceItem | 'new' | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [flagged, setFlagged] = useState<string[]>([]);
  const [linking, setLinking] = useState<EvidenceItem | null>(null);
  const [linkKind, setLinkKind] = useState('FACT');
  const [linkTarget, setLinkTarget] = useState('');
  const [taskChoice, setTaskChoice] = useState<Record<string, string>>({});

  const set = (field: keyof FormState) => (value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
  };
  const reload = () => {
    items.reload();
    facts.reload();
    defenses.reload();
  };
  const fail = (error: unknown) => {
    setFlagged(flaggedFields(error));
    setMessage({ key: saveMessage(error), failed: true });
  };

  const addFact = async (event: SyntheticEvent) => {
    event.preventDefault();
    try {
      await createFact(caseId, factText, orNull(factDate));
      setFactText('');
      setFactDate('');
      setMessage({ key: 'pl.facts.added', failed: false });
      facts.reload();
    } catch (error) {
      fail(error);
    }
  };

  const open = (target: EvidenceItem | 'new') => {
    setEditing(target);
    setForm(target === 'new' ? EMPTY : stateOf(target));
    setMessage(null);
    setFlagged([]);
  };

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    if (editing === null) return;
    const input: EvidenceInput = {
      name: form.name,
      docType: orNull(form.docType),
      issuedOn: orNull(form.issuedOn),
      issuer: orNull(form.issuer),
      receivedOn: orNull(form.receivedOn),
      source: orNull(form.source),
      role: form.role as EvidenceInput['role'],
      importance: form.importance === '' ? null : (form.importance as EvidenceInput['importance']),
      documentId: orNull(form.documentId),
      submittedToCourt: form.submittedToCourt,
      submittedOn: form.submittedToCourt ? orNull(form.submittedOn) : null,
      submittedIn: form.submittedToCourt ? orNull(form.submittedIn) : null,
      opponentResponded: form.opponentResponded === '' ? null : form.opponentResponded === 'true',
      opponentResponse: orNull(form.opponentResponse),
      courtPosition: orNull(form.courtPosition),
      notes: orNull(form.notes),
    };
    try {
      if (editing === 'new') await createEvidence(caseId, input);
      else await updateEvidence(editing.id, { ...input, version: editing.version });
      setMessage({ key: 'pl.evidence.saved', failed: false });
      setEditing(null);
      setFlagged([]);
      reload();
    } catch (error) {
      fail(error);
    }
  };

  const targets: Record<string, readonly (readonly [string, string])[]> = {
    FACT: (facts.data ?? []).map((f) => [f.id, `${String(f.seq)}. ${f.text}`] as const),
    DEFENSE: (defenses.data ?? []).map((d) => [d.id, `${String(d.seq)}. ${d.title}`] as const),
    CLAIM: (claims.data ?? []).map((c) => [c.id, `${String(c.seq)}. ${c.text}`] as const),
  };

  const link = async () => {
    if (linking === null || linkTarget === '') return;
    const body = { [linkKind === 'FACT' ? 'factId' : linkKind === 'DEFENSE' ? 'defenseId' : 'claimId']: linkTarget };
    try {
      await linkEvidence(linking.id, body);
      setMessage({ key: 'pl.evidence.linked', failed: false });
      setLinking(null);
      setLinkTarget('');
      reload();
    } catch (error) {
      fail(error);
    }
  };

  const unlink = async (item: EvidenceItem, linkId: string) => {
    try {
      await unlinkEvidence(item.id, linkId);
      setMessage({ key: 'pl.evidence.unlinked', failed: false });
      reload();
    } catch (error) {
      fail(error);
    }
  };

  const follow = async (item: EvidenceItem) => {
    const taskId = taskChoice[item.id] ?? '';
    if (taskId === '') return;
    try {
      await followUpEvidence(item.id, taskId);
      setMessage({ key: 'pl.gaps.linked', failed: false });
      items.reload();
    } catch (error) {
      fail(error);
    }
  };

  const gaps = (items.data ?? []).filter((e) => e.gap !== null);
  return (
    <section aria-labelledby={headingId} className="pleadings-section">
      <h2 id={headingId}>{t('pl.evidence.title')}</h2>
      <p className="case-field-hint">{t('pl.evidence.hint')}</p>
      {message === null ? null : <Notice failed={message.failed}>{t(message.key)}</Notice>}
      {items.status !== null ? <Notice failed>{t(items.status === 403 ? 'pl.error.hidden' : 'pl.error.load')}</Notice> : null}

      <h3>{t('pl.facts.title')}</h3>
      {(facts.data ?? []).length === 0 ? (
        <p>{t('pl.facts.none')}</p>
      ) : (
        <ol className="pleadings-list">
          {(facts.data ?? []).map((f) => (
            <li key={f.id}>
              {f.text}
              {f.factDate === null ? '' : ` — ${formatDate(f.factDate, locale)}`} ({t('pl.facts.evidenceCount', { count: String(f.evidenceCount) })})
            </li>
          ))}
        </ol>
      )}
      {!canManage ? null : (
        <form className="case-form" aria-label={t('pl.facts.add')} noValidate onSubmit={(e) => void addFact(e)}>
          <TextField label={t('pl.facts.text')} value={factText} onChange={setFactText} invalid={flagged.includes('text')} />
          <TextField label={t('pl.facts.date')} type="date" value={factDate} onChange={setFactDate} />
          <div className="case-actions">
            <button type="submit">{t('pl.facts.add')}</button>
          </div>
        </form>
      )}

      <h3>{t('pl.evidence.matrix')}</h3>
      {(items.data ?? []).length === 0 ? (
        <p>{t('pl.evidence.none')}</p>
      ) : (
        <table className="case-table">
          <caption className="visually-hidden">{t('pl.evidence.matrix')}</caption>
          <thead>
            <tr>
              <th scope="col">{t('pl.evidence.name')}</th>
              <th scope="col">{t('pl.evidence.role')}</th>
              <th scope="col">{t('pl.evidence.importance')}</th>
              <th scope="col">{t('pl.evidence.links')}</th>
              <th scope="col">{t('pl.evidence.submittedToCourt')}</th>
              <th scope="col">{t('pl.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {(items.data ?? []).map((e) => (
              <tr key={e.id}>
                <td>
                  {e.name}
                  {e.documentTitle === null ? null : <span className="case-field-hint"> ({e.documentTitle})</span>}
                </td>
                <td>{t(`pl.evidence.role.${e.role}`)}</td>
                <td>{e.importance === null ? '—' : t(`pl.evidence.importance.${e.importance}`)}</td>
                <td>
                  {e.links.length === 0 ? '—' : null}
                  <ul className="pleadings-list">
                    {e.links.map((l) => (
                      <li key={l.id}>
                        {l.label}
                        {canManage ? (
                          <>
                            {' '}
                            <button
                              type="button"
                              aria-label={t('pl.evidence.unlinkFrom', { name: e.name, label: l.label })}
                              onClick={() => void unlink(e, l.id)}
                            >
                              {t('pl.evidence.unlink')}
                            </button>
                          </>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                </td>
                <td>
                  {e.submittedToCourt
                    ? `${t('pl.yes')}${e.submittedIn === null ? '' : ` — ${e.submittedIn}`}${e.submittedOn === null ? '' : ` — ${formatDate(e.submittedOn, locale)}`}`
                    : t('pl.no')}
                </td>
                <td>
                  {canManage ? (
                    <>
                      <button
                        type="button"
                        aria-label={t('pl.evidence.editNamed', { name: e.name })}
                        onClick={() => {
                          open(e);
                        }}
                      >
                        {t('pl.edit')}
                      </button>{' '}
                      <button
                        type="button"
                        aria-label={t('pl.evidence.linkNamed', { name: e.name })}
                        onClick={() => {
                          setLinking(e);
                          setLinkTarget('');
                        }}
                      >
                        {t('pl.evidence.link')}
                      </button>
                    </>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {linking === null ? null : (
        <div className="case-form" role="group" aria-label={t('pl.evidence.linkNamed', { name: linking.name })}>
          <ChoiceField
            label={t('pl.evidence.linkKind')}
            value={linkKind}
            onChange={(kind) => {
              setLinkKind(kind);
              setLinkTarget('');
            }}
            options={[
              ['FACT', t('pl.evidence.kind.FACT')],
              ['DEFENSE', t('pl.evidence.kind.DEFENSE')],
              ['CLAIM', t('pl.evidence.kind.CLAIM')],
            ]}
          />
          <ChoiceField
            label={t('pl.evidence.linkTarget')}
            value={linkTarget}
            onChange={setLinkTarget}
            options={targets[linkKind] ?? []}
            empty={t('pl.notSpecified')}
          />
          <div className="case-actions">
            <button type="button" className="primary" onClick={() => void link()}>
              {t('pl.evidence.link')}
            </button>
            <button
              type="button"
              onClick={() => {
                setLinking(null);
              }}
            >
              {t('pl.cancel')}
            </button>
          </div>
        </div>
      )}

      {!canManage ? null : editing === null ? (
        <p>
          <button
            type="button"
            onClick={() => {
              open('new');
            }}
          >
            {t('pl.evidence.add')}
          </button>
        </p>
      ) : (
        <form
          className="case-form"
          aria-label={t(editing === 'new' ? 'pl.evidence.add' : 'pl.evidence.editForm')}
          noValidate
          onSubmit={(e) => void submit(e)}
        >
          <TextField label={t('pl.evidence.name')} value={form.name} onChange={set('name')} max={300} invalid={flagged.includes('name')} />
          <ChoiceField
            label={t('pl.evidence.role')}
            value={form.role}
            onChange={set('role')}
            options={EVIDENCE_ROLES.map((r) => [r, t(`pl.evidence.role.${r}`)] as const)}
          />
          <ChoiceField
            label={t('pl.evidence.importance')}
            value={form.importance}
            onChange={set('importance')}
            options={EVIDENCE_IMPORTANCE.map((r) => [r, t(`pl.evidence.importance.${r}`)] as const)}
            empty={t('pl.notSpecified')}
          />
          <ChoiceField
            label={t('pl.evidence.document')}
            value={form.documentId}
            onChange={set('documentId')}
            options={documentOptions(pickers.documents)}
            empty={t('pl.evidence.noCopy')}
            invalid={flagged.includes('documentId')}
          />
          <TextField label={t('pl.evidence.docType')} value={form.docType} onChange={set('docType')} max={100} />
          <TextField label={t('pl.evidence.issuer')} value={form.issuer} onChange={set('issuer')} max={300} />
          <TextField label={t('pl.evidence.issuedOn')} type="date" value={form.issuedOn} onChange={set('issuedOn')} />
          <TextField label={t('pl.evidence.receivedOn')} type="date" value={form.receivedOn} onChange={set('receivedOn')} />
          <TextField label={t('pl.evidence.source')} value={form.source} onChange={set('source')} max={300} />
          <CheckField
            label={t('pl.evidence.submittedToCourt')}
            checked={form.submittedToCourt}
            onChange={(value) => {
              setForm((current) => ({ ...current, submittedToCourt: value }));
            }}
          />
          {!form.submittedToCourt ? null : (
            <>
              <TextField label={t('pl.evidence.submittedOn')} type="date" value={form.submittedOn} onChange={set('submittedOn')} />
              <TextField label={t('pl.evidence.submittedIn')} value={form.submittedIn} onChange={set('submittedIn')} max={300} />
            </>
          )}
          <ChoiceField
            label={t('pl.evidence.opponentResponded')}
            value={form.opponentResponded}
            onChange={set('opponentResponded')}
            options={[
              ['true', t('pl.yes')],
              ['false', t('pl.no')],
            ]}
            empty={t('pl.notSpecified')}
          />
          <TextField label={t('pl.evidence.opponentResponse')} value={form.opponentResponse} onChange={set('opponentResponse')} max={2000} />
          <TextField label={t('pl.evidence.courtPosition')} value={form.courtPosition} onChange={set('courtPosition')} max={2000} />
          <TextField label={t('pl.notes')} value={form.notes} onChange={set('notes')} max={2000} />
          <div className="case-actions">
            <button type="submit" className="primary">
              {t('pl.save')}
            </button>
            <button
              type="button"
              onClick={() => {
                setEditing(null);
              }}
            >
              {t('pl.cancel')}
            </button>
          </div>
        </form>
      )}

      <h3>{t('pl.gaps.title')}</h3>
      {gaps.length === 0 ? (
        <p>{t('pl.gaps.none')}</p>
      ) : (
        <ul className="pleadings-list">
          {gaps.map((e) => (
            <li key={e.id}>
              <strong>{e.name}</strong> — {t(e.gap === 'NOT_OBTAINED' ? 'pl.gaps.NOT_OBTAINED' : 'pl.gaps.NOT_SUBMITTED')}
              {e.followups.map((f) => (
                <span key={f.id} className="case-tag">
                  {f.taskTitle ?? t('pl.notSpecified')}
                </span>
              ))}
              {!canManage || (tasks.data ?? []).length === 0 ? null : (
                <div className="case-form">
                  <ChoiceField
                    label={t('pl.gaps.taskFor', { name: e.name })}
                    value={taskChoice[e.id] ?? ''}
                    onChange={(value) => {
                      setTaskChoice((current) => ({ ...current, [e.id]: value }));
                    }}
                    options={(tasks.data ?? []).map((task) => [task.id, task.title] as const)}
                    empty={t('pl.notSpecified')}
                  />
                  <button type="button" onClick={() => void follow(e)}>
                    {t('pl.gaps.linkTask')}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
