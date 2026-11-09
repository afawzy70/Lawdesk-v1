import { useId, useState, type SyntheticEvent } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import { numberOf } from '../proceedings/dates';
import { listJudgments } from '../proceedings/proceedingsApi';
import {
  CLAIM_KINDS,
  CLAIM_STATUSES,
  createClaim,
  listCaseClaimResults,
  listClaims,
  listRevisions,
  updateClaim,
  type CaseClaimResult,
  type Claim,
  type ClaimInput,
} from './api';
import { documentOptions, partyOptions, stageOptions, usePickers } from './pickers';
import { AreaField, ChoiceField, Notice, TextField } from './ui';
import { flaggedFields, orNull, saveMessage, useLoad } from './useLoad';

interface FormState {
  text: string;
  kind: string;
  status: string;
  partyId: string;
  submittedOn: string;
  stageId: string;
  documentId: string;
  notes: string;
  reason: string;
}

const EMPTY: FormState = {
  text: '',
  kind: 'PRIMARY',
  status: 'STANDING',
  partyId: '',
  submittedOn: '',
  stageId: '',
  documentId: '',
  notes: '',
  reason: '',
};

const stateOf = (c: Claim): FormState => ({
  text: c.text,
  kind: c.kind,
  status: c.status,
  partyId: c.submittedByPartyId ?? '',
  submittedOn: c.submittedOn ?? '',
  stageId: c.stageId ?? '',
  documentId: c.sourceDocumentId ?? '',
  notes: c.notes ?? '',
  reason: '',
});

interface Props {
  caseId: string;
  canManage: boolean;
}

/** طلبات الدعوى (section 5): the claims, their amendments with history, and the result of each judgment on each. */
export function CaseClaims({ caseId, canManage }: Props) {
  const { t, locale } = useI18n();
  const headingId = useId();
  const claims = useLoad((signal) => listClaims(caseId, signal), [caseId]);
  const results = useLoad((signal) => listCaseClaimResults(caseId, signal), [caseId]);
  const judgmentList = useLoad((signal) => listJudgments(caseId, signal), [caseId]);
  const pickers = usePickers(caseId);
  const [editing, setEditing] = useState<Claim | 'new' | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const [flagged, setFlagged] = useState<string[]>([]);
  const [historyOf, setHistoryOf] = useState<Claim | null>(null);
  const history = useLoad(
    (signal) => (historyOf === null ? Promise.resolve([]) : listRevisions(historyOf.id, signal)),
    [historyOf?.id, historyOf?.version],
  );

  const open = (target: Claim | 'new') => {
    setEditing(target);
    setForm(target === 'new' ? EMPTY : stateOf(target));
    setMessage(null);
    setFlagged([]);
  };
  const set = (field: keyof FormState) => (value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    if (editing === null) return;
    const input: ClaimInput = {
      text: form.text,
      kind: form.kind as ClaimInput['kind'],
      submittedByPartyId: orNull(form.partyId),
      submittedOn: orNull(form.submittedOn),
      stageId: orNull(form.stageId),
      sourceDocumentId: orNull(form.documentId),
      notes: orNull(form.notes),
    };
    try {
      if (editing === 'new') {
        await createClaim(caseId, input);
        setMessage({ key: 'pl.claims.added', failed: false });
      } else {
        await updateClaim(editing.id, {
          ...input,
          status: form.status as Claim['status'],
          reason: orNull(form.reason),
          version: editing.version,
        });
        setMessage({ key: 'pl.claims.amended', failed: false });
        if (historyOf?.id === editing.id) setHistoryOf(null);
      }
      setEditing(null);
      setFlagged([]);
      claims.reload();
    } catch (error) {
      setFlagged(flaggedFields(error));
      setMessage({ key: saveMessage(error), failed: true });
    }
  };

  const stageLabel = (id: string | null) => {
    const found = pickers.stages.find((p) => p.id === id);
    return found === undefined ? '—' : `${t(`proceedings.stage.${found.stage}`)} ${numberOf(found)}`;
  };

  // One column per judgment of the case, so a judgment with no recorded results says so instead of being left out.
  const judgments = (judgmentList.data ?? []).map((j) => ({
    id: j.id,
    date: j.judgmentDate,
    stage: pickers.stages.find((p) => p.id === j.proceedingId)?.stage ?? 'FIRST_INSTANCE',
  }));
  const resultOf = (claimId: string, judgmentId: string): CaseClaimResult | undefined =>
    (results.data ?? []).find((r) => r.claimId === claimId && r.judgmentId === judgmentId);

  return (
    <section aria-labelledby={headingId} className="pleadings-section">
      <h2 id={headingId}>{t('pl.claims.title')}</h2>
      <p className="case-field-hint">{t('pl.claims.hint')}</p>
      {message === null ? null : <Notice failed={message.failed}>{t(message.key)}</Notice>}
      {claims.status !== null ? <Notice failed>{t(claims.status === 403 ? 'pl.error.hidden' : 'pl.error.load')}</Notice> : null}
      {claims.data === null ? null : claims.data.length === 0 ? (
        <p>{t('pl.claims.none')}</p>
      ) : (
        <table className="case-table">
          <caption className="visually-hidden">{t('pl.claims.title')}</caption>
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">{t('pl.claims.text')}</th>
              <th scope="col">{t('pl.claims.kind')}</th>
              <th scope="col">{t('pl.claims.status')}</th>
              <th scope="col">{t('pl.claims.party')}</th>
              <th scope="col">{t('pl.claims.stage')}</th>
              <th scope="col">{t('pl.claims.document')}</th>
              <th scope="col">{t('pl.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {claims.data.map((c) => (
              <tr key={c.id}>
                <td>{c.seq}</td>
                <td>{c.text}</td>
                <td>{t(`pl.claims.kind.${c.kind}`)}</td>
                <td>{t(`pl.claims.status.${c.status}`)}</td>
                <td>{c.submittedByPartyName ?? '—'}</td>
                <td>{stageLabel(c.stageId)}</td>
                <td>{c.sourceDocumentTitle ?? '—'}</td>
                <td>
                  {canManage ? (
                    <button
                      type="button"
                      aria-label={t('pl.claims.amendNumber', { number: String(c.seq) })}
                      onClick={() => {
                        open(c);
                      }}
                    >
                      {t('pl.claims.amend')}
                    </button>
                  ) : null}{' '}
                  <button
                    type="button"
                    aria-label={t('pl.claims.historyNumber', { number: String(c.seq) })}
                    onClick={() => {
                      setHistoryOf(historyOf?.id === c.id ? null : c);
                    }}
                  >
                    {t('pl.claims.history')}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {historyOf === null ? null : (
        <div className="case-fieldset">
          <h3>{t('pl.claims.historyOf', { number: String(historyOf.seq) })}</h3>
          <table className="case-table">
            <thead>
              <tr>
                <th scope="col">{t('pl.claims.revision')}</th>
                <th scope="col">{t('pl.claims.text')}</th>
                <th scope="col">{t('pl.claims.status')}</th>
                <th scope="col">{t('pl.claims.reason')}</th>
                <th scope="col">{t('pl.claims.changedAt')}</th>
              </tr>
            </thead>
            <tbody>
              {(history.data ?? []).map((r) => (
                <tr key={r.rev}>
                  <td>{r.rev}</td>
                  <td>{r.text}</td>
                  <td>{t(`pl.claims.status.${r.status}`)}</td>
                  <td>{r.reason ?? '—'}</td>
                  <td>{formatDate(r.changedAt.slice(0, 10), locale)}</td>
                </tr>
              ))}
            </tbody>
          </table>
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
            {t('pl.claims.add')}
          </button>
        </p>
      ) : (
        <form
          className="case-form"
          aria-label={t(editing === 'new' ? 'pl.claims.add' : 'pl.claims.amendForm', {
            number: editing === 'new' ? '' : String(editing.seq),
          })}
          noValidate
          onSubmit={(e) => void submit(e)}
        >
          <AreaField label={t('pl.claims.text')} value={form.text} onChange={set('text')} invalid={flagged.includes('text')} />
          <ChoiceField
            label={t('pl.claims.kind')}
            value={form.kind}
            onChange={set('kind')}
            options={CLAIM_KINDS.map((k) => [k, t(`pl.claims.kind.${k}`)] as const)}
          />
          {editing === 'new' ? null : (
            <ChoiceField
              label={t('pl.claims.status')}
              value={form.status}
              onChange={set('status')}
              options={CLAIM_STATUSES.map((s) => [s, t(`pl.claims.status.${s}`)] as const)}
            />
          )}
          <ChoiceField
            label={t('pl.claims.party')}
            value={form.partyId}
            onChange={set('partyId')}
            options={partyOptions(pickers.parties)}
            empty={t('pl.notSpecified')}
          />
          <TextField label={t('pl.claims.submittedOn')} type="date" value={form.submittedOn} onChange={set('submittedOn')} />
          <ChoiceField
            label={t('pl.claims.stage')}
            value={form.stageId}
            onChange={set('stageId')}
            options={stageOptions(pickers.stages, t)}
            empty={t('pl.notSpecified')}
          />
          <ChoiceField
            label={t('pl.claims.document')}
            value={form.documentId}
            onChange={set('documentId')}
            options={documentOptions(pickers.documents)}
            empty={t('pl.notSpecified')}
          />
          <TextField label={t('pl.notes')} value={form.notes} onChange={set('notes')} max={2000} />
          {editing === 'new' ? null : (
            <TextField label={t('pl.claims.reason')} value={form.reason} onChange={set('reason')} max={500} />
          )}
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

      {judgments.length === 0 || claims.data === null ? null : (
        <div>
          <h3>{t('pl.claims.resultsTitle')}</h3>
          <p className="case-field-hint">{t('pl.claims.resultsHint')}</p>
          <table className="case-table">
            <thead>
              <tr>
                <th scope="col">#</th>
                {judgments.map((j) => (
                  <th key={j.id} scope="col">
                    {t(`proceedings.stage.${j.stage}`)} — {formatDate(j.date, locale)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {claims.data.map((c) => (
                <tr key={c.id}>
                  <td>{c.seq}</td>
                  {judgments.map((j) => {
                    const r = resultOf(c.id, j.id);
                    return (
                      <td key={j.id}>
                        {r === undefined ? t('pl.claims.notRecorded') : t(`pl.claims.result.${r.result}`)}
                        {r?.awardedAmount == null ? null : <span dir="ltr"> ({r.awardedAmount} KWD)</span>}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
