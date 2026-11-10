import { useId, useState, type SyntheticEvent } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import { DefensePrecedents } from '../gaps/DefensePrecedents';
import { numberOf } from '../proceedings/dates';
import {
  ASSERTION_STATUSES,
  assertDefense,
  createDefense,
  DEFENSE_KINDS,
  getDefense,
  listDefenses,
  listTemplates,
  updateDefense,
  type AssertionStatus,
  type Defense,
  type DefenseInput,
} from './api';
import { DefenseSimilarJudgments } from './DefenseSimilarJudgments';
import { documentOptions, stageOptions, usePickers } from './pickers';
import { AreaField, ChoiceField, Notice, TextField } from './ui';
import { flaggedFields, orNull, saveMessage, useLoad } from './useLoad';

interface FormState {
  title: string;
  kind: string;
  argument: string;
  legalBasis: string;
  facts: string;
  legalTexts: string;
  memoDocumentId: string;
  raisedOn: string;
  raisedStageId: string;
  notes: string;
  templateId: string;
}

const EMPTY: FormState = {
  title: '',
  kind: 'SUBSTANTIVE',
  argument: '',
  legalBasis: '',
  facts: '',
  legalTexts: '',
  memoDocumentId: '',
  raisedOn: '',
  raisedStageId: '',
  notes: '',
  templateId: '',
};

const stateOf = (d: Defense): FormState => ({
  title: d.title,
  kind: d.kind,
  argument: d.argument ?? '',
  legalBasis: d.legalBasis ?? '',
  facts: d.facts ?? '',
  legalTexts: d.legalTexts ?? '',
  memoDocumentId: d.memoDocumentId ?? '',
  raisedOn: d.raisedOn ?? '',
  raisedStageId: d.raisedStageId ?? '',
  notes: d.notes ?? '',
  templateId: d.templateId ?? '',
});

interface Props {
  caseId: string;
  canManage: boolean;
}

/** الدفوع وأوجه الدفاع (section 17): the defences, their fate in each stage and the court's position on each. */
export function CaseDefenses({ caseId, canManage }: Props) {
  const { t, locale } = useI18n();
  const headingId = useId();
  const defenses = useLoad((signal) => listDefenses(caseId, signal), [caseId]);
  const pickers = usePickers(caseId);
  const templates = useLoad((signal) => listTemplates('', signal), []);
  const [editing, setEditing] = useState<Defense | 'new' | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [flagged, setFlagged] = useState<string[]>([]);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const detail = useLoad(
    (signal) => (openId === null ? Promise.resolve(null) : getDefense(openId, signal)),
    [openId],
  );

  const open = (target: Defense | 'new') => {
    setEditing(target);
    setForm(target === 'new' ? EMPTY : stateOf(target));
    setMessage(null);
    setFlagged([]);
  };
  const set = (field: keyof FormState) => (value: string) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  /** Fills the form with the library wording for the member to review and adapt; nothing is saved by choosing it. */
  const applyTemplate = (id: string) => {
    const template = (templates.data ?? []).find((x) => x.id === id);
    setForm((current) =>
      template === undefined
        ? { ...current, templateId: '' }
        : {
            ...current,
            templateId: id,
            title: current.title === '' ? template.title : current.title,
            kind: template.kind,
            argument: template.text ?? current.argument,
            legalBasis: template.legalBasis ?? current.legalBasis,
          },
    );
  };

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    if (editing === null) return;
    const input: DefenseInput = {
      title: form.title,
      kind: form.kind as DefenseInput['kind'],
      argument: orNull(form.argument),
      legalBasis: orNull(form.legalBasis),
      facts: orNull(form.facts),
      legalTexts: orNull(form.legalTexts),
      memoDocumentId: orNull(form.memoDocumentId),
      raisedOn: orNull(form.raisedOn),
      raisedStageId: orNull(form.raisedStageId),
      notes: orNull(form.notes),
      templateId: orNull(form.templateId),
    };
    try {
      if (editing === 'new') {
        await createDefense(caseId, input);
      } else {
        await updateDefense(editing.id, { ...input, version: editing.version });
      }
      setMessage({ key: 'pl.defenses.saved', failed: false });
      setEditing(null);
      setFlagged([]);
      defenses.reload();
      detail.reload();
    } catch (error) {
      setFlagged(flaggedFields(error));
      setMessage({ key: saveMessage(error), failed: true });
    }
  };

  const assertIn = async (defenseId: string, proceedingId: string, status: AssertionStatus) => {
    try {
      await assertDefense(defenseId, proceedingId, status, null);
      setMessage({ key: 'pl.defenses.assertionSaved', failed: false });
      detail.reload();
    } catch (error) {
      setMessage({ key: saveMessage(error), failed: true });
    }
  };

  const current = detail.data;
  return (
    <section aria-labelledby={headingId} className="pleadings-section">
      <h2 id={headingId}>{t('pl.defenses.title')}</h2>
      <p className="case-field-hint">{t('pl.defenses.hint')}</p>
      {message === null ? null : <Notice failed={message.failed}>{t(message.key)}</Notice>}
      {defenses.status !== null ? <Notice failed>{t(defenses.status === 403 ? 'pl.error.hidden' : 'pl.error.load')}</Notice> : null}
      {defenses.data === null ? null : defenses.data.length === 0 ? (
        <p>{t('pl.defenses.none')}</p>
      ) : (
        <table className="case-table">
          <caption className="visually-hidden">{t('pl.defenses.title')}</caption>
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">{t('pl.defenses.name')}</th>
              <th scope="col">{t('pl.defenses.kind')}</th>
              <th scope="col">{t('pl.defenses.memo')}</th>
              <th scope="col">{t('pl.defenses.evidenceCount')}</th>
              <th scope="col">{t('pl.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {defenses.data.map((d) => (
              <tr key={d.id}>
                <td>{d.seq}</td>
                <td>{d.title}</td>
                <td>{t(`pl.defenses.kind.${d.kind}`)}</td>
                <td>{d.memoDocumentTitle ?? '—'}</td>
                <td>{d.evidenceCount}</td>
                <td>
                  <button
                    type="button"
                    aria-label={t('pl.defenses.detailsNumber', { number: String(d.seq) })}
                    onClick={() => {
                      setOpenId(openId === d.id ? null : d.id);
                    }}
                  >
                    {t('pl.defenses.details')}
                  </button>{' '}
                  {canManage ? (
                    <button
                      type="button"
                      aria-label={t('pl.defenses.editNumber', { number: String(d.seq) })}
                      onClick={() => {
                        open(d);
                      }}
                    >
                      {t('pl.edit')}
                    </button>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <DefenseSimilarJudgments caseId={caseId} defenses={defenses.data ?? []} />

      {current === null || openId === null ? null : (
        <div className="case-fieldset" aria-label={t('pl.defenses.detailsOf', { number: String(current.defense.seq) })} role="group">
          <h3>{t('pl.defenses.detailsOf', { number: String(current.defense.seq) })}</h3>
          <dl className="case-facts">
            <dt>{t('pl.defenses.argument')}</dt>
            <dd>{current.defense.argument ?? t('pl.notSpecified')}</dd>
            <dt>{t('pl.defenses.legalBasis')}</dt>
            <dd>{current.defense.legalBasis ?? t('pl.notSpecified')}</dd>
            <dt>{t('pl.defenses.preparedBy')}</dt>
            <dd>{current.defense.preparedByName ?? t('pl.notSpecified')}</dd>
          </dl>
          <DefensePrecedents defenseId={current.defense.id} canManage={canManage} />
          <h4>{t('pl.defenses.stages')}</h4>
          <p className="case-field-hint">{t('pl.defenses.stagesHint')}</p>
          <table className="case-table">
            <thead>
              <tr>
                <th scope="col">{t('pl.claims.stage')}</th>
                <th scope="col">{t('pl.defenses.assertion')}</th>
                <th scope="col">{t('pl.defenses.position')}</th>
              </tr>
            </thead>
            <tbody>
              {pickers.stages.map((p) => {
                const assertion = current.assertions.find((a) => a.proceedingId === p.id);
                const positions = current.positions.filter((x) => x.proceedingId === p.id);
                return (
                  <tr key={p.id}>
                    <td>
                      {t(`proceedings.stage.${p.stage}`)} {numberOf(p)}
                    </td>
                    <td>
                      {canManage ? (
                        <select
                          aria-label={t('pl.defenses.assertionIn', { stage: `${t(`proceedings.stage.${p.stage}`)} ${numberOf(p)}` })}
                          value={assertion?.status ?? ''}
                          onChange={(e) => {
                            if (e.target.value !== '') void assertIn(current.defense.id, p.id, e.target.value as AssertionStatus);
                          }}
                        >
                          <option value="">{t('pl.notSpecified')}</option>
                          {ASSERTION_STATUSES.map((s) => (
                            <option key={s} value={s}>
                              {t(`pl.defenses.assertion.${s}`)}
                            </option>
                          ))}
                        </select>
                      ) : assertion === undefined ? (
                        t('pl.notSpecified')
                      ) : (
                        t(`pl.defenses.assertion.${assertion.status}`)
                      )}
                    </td>
                    <td>
                      {positions.length === 0
                        ? t('pl.claims.notRecorded')
                        : positions.map((x) => (
                            <span key={x.judgmentId}>
                              {t(`pl.defenses.position.${x.position}`)} ({formatDate(x.judgmentDate, locale)})
                            </span>
                          ))}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <h4>{t('pl.defenses.supportingEvidence')}</h4>
          {current.evidence.length === 0 ? (
            <p>{t('pl.defenses.noEvidence')}</p>
          ) : (
            <ul>
              {current.evidence.map((e) => (
                <li key={e.evidenceId}>
                  {e.name} — {t(`pl.evidence.role.${e.role}`)}
                  {e.submittedToCourt ? ` — ${t('pl.evidence.submitted')}` : ''}
                </li>
              ))}
            </ul>
          )}
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
            {t('pl.defenses.add')}
          </button>
        </p>
      ) : (
        <form
          className="case-form"
          aria-label={t(editing === 'new' ? 'pl.defenses.add' : 'pl.defenses.editForm')}
          noValidate
          onSubmit={(e) => void submit(e)}
        >
          {editing !== 'new' || (templates.data ?? []).length === 0 ? null : (
            <ChoiceField
              label={t('pl.defenses.template')}
              value={form.templateId}
              onChange={applyTemplate}
              options={(templates.data ?? []).map((x) => [x.id, x.title] as const)}
              empty={t('pl.notSpecified')}
            />
          )}
          <TextField label={t('pl.defenses.name')} value={form.title} onChange={set('title')} max={300} invalid={flagged.includes('title')} />
          <ChoiceField
            label={t('pl.defenses.kind')}
            value={form.kind}
            onChange={set('kind')}
            options={DEFENSE_KINDS.map((k) => [k, t(`pl.defenses.kind.${k}`)] as const)}
          />
          <AreaField label={t('pl.defenses.argument')} value={form.argument} onChange={set('argument')} />
          <AreaField label={t('pl.defenses.legalBasis')} value={form.legalBasis} onChange={set('legalBasis')} />
          <AreaField label={t('pl.defenses.facts')} value={form.facts} onChange={set('facts')} />
          <AreaField label={t('pl.defenses.legalTexts')} value={form.legalTexts} onChange={set('legalTexts')} />
          <ChoiceField
            label={t('pl.defenses.memo')}
            value={form.memoDocumentId}
            onChange={set('memoDocumentId')}
            options={documentOptions(pickers.documents)}
            empty={t('pl.notSpecified')}
            invalid={flagged.includes('memoDocumentId')}
          />
          <TextField label={t('pl.defenses.raisedOn')} type="date" value={form.raisedOn} onChange={set('raisedOn')} />
          <ChoiceField
            label={t('pl.defenses.raisedStage')}
            value={form.raisedStageId}
            onChange={set('raisedStageId')}
            options={stageOptions(pickers.stages, t)}
            empty={t('pl.notSpecified')}
            invalid={flagged.includes('raisedStageId')}
          />
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
    </section>
  );
}
