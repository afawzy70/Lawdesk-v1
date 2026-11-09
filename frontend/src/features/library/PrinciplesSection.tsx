import { type SyntheticEvent, useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import { addPrinciple, replacePrinciple, reviewPrinciple, type Principle, type PrincipleInput } from './api';
import { citation } from './labels';

interface Props {
  judgmentId: string;
  principles: Principle[];
  canManage: boolean;
  onChanged: () => void;
}

const EMPTY = { principleText: '', legalTopic: '', legalBasis: '', factsApplied: '', scopeNote: '', memberNotes: '' };
type Draft = typeof EMPTY;

const orNull = (v: string): string | null => (v.trim() === '' ? null : v.trim());

/** The principles extracted from a judgment (requirements section 16.2): shown apart from the judgment's own text. */
export function PrinciplesSection({ judgmentId, principles, canManage, onChanged }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [editing, setEditing] = useState<Principle | 'new' | null>(null);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [problem, setProblem] = useState<MessageKey | null>(null);
  const [notice, setNotice] = useState<MessageKey | null>(null);

  const open = (target: Principle | 'new') => {
    setProblem(null);
    setNotice(null);
    setEditing(target);
    setDraft(
      target === 'new'
        ? EMPTY
        : {
            principleText: target.principleText,
            legalTopic: target.legalTopic ?? '',
            legalBasis: target.legalBasis ?? '',
            factsApplied: target.factsApplied ?? '',
            scopeNote: target.scopeNote ?? '',
            memberNotes: target.memberNotes ?? '',
          },
    );
  };

  const explain = (error: unknown): MessageKey =>
    error instanceof ApiError && error.status === 409 ? 'library.conflict' : 'library.saveFailed';

  const save = async (event: SyntheticEvent) => {
    event.preventDefault();
    if (draft.principleText.trim() === '') {
      setProblem('library.principles.needText');
      return;
    }
    const body: PrincipleInput = {
      principleText: draft.principleText.trim(),
      legalTopic: orNull(draft.legalTopic),
      legalBasis: orNull(draft.legalBasis),
      factsApplied: orNull(draft.factsApplied),
      scopeNote: orNull(draft.scopeNote),
      memberNotes: orNull(draft.memberNotes),
    };
    try {
      if (editing === 'new' || editing === null) await addPrinciple(judgmentId, body);
      else await replacePrinciple(editing.id, { ...body, version: editing.version });
      setEditing(null);
      setProblem(null);
      setNotice('library.principles.added');
      onChanged();
    } catch (error) {
      setProblem(explain(error));
    }
  };

  const review = async (principle: Principle) => {
    try {
      await reviewPrinciple(principle.id, principle.version);
      onChanged();
    } catch (error) {
      setProblem(explain(error));
    }
  };

  const field = (name: keyof Draft, label: MessageKey, area: boolean) => (
    <div className="case-field">
      <label htmlFor={`${id}-${name}`}>{t(label)}</label>
      {area ? (
        <textarea
          id={`${id}-${name}`}
          rows={name === 'principleText' ? 4 : 2}
          value={draft[name]}
          onChange={(e) => {
            setDraft({ ...draft, [name]: e.target.value });
          }}
        />
      ) : (
        <input
          id={`${id}-${name}`}
          type="text"
          maxLength={300}
          value={draft[name]}
          onChange={(e) => {
            setDraft({ ...draft, [name]: e.target.value });
          }}
        />
      )}
    </div>
  );

  return (
    <section className="library-section" aria-labelledby={`${id}-h`}>
      <h2 id={`${id}-h`}>{t('library.principles.title')}</h2>
      <p className="case-notice">{t('library.principles.notice')}</p>
      {problem === null ? null : (
        <p className="case-alert" role="alert">
          {t(problem)}
        </p>
      )}
      {notice === null ? null : (
        <p className="case-notice" role="status">
          {t(notice)}
        </p>
      )}
      {principles.length === 0 ? <p>{t('library.principles.none')}</p> : null}
      {principles.map((principle) => (
        <article key={principle.id} className="library-card">
          <p className="library-text">{principle.principleText}</p>
          <dl className="case-facts">
            {principle.legalTopic === null ? null : (
              <>
                <dt>{t('library.principles.topic')}</dt>
                <dd>{principle.legalTopic}</dd>
              </>
            )}
            {principle.legalBasis === null ? null : (
              <>
                <dt>{t('library.principles.basis')}</dt>
                <dd className="library-text">{principle.legalBasis}</dd>
              </>
            )}
            {principle.factsApplied === null ? null : (
              <>
                <dt>{t('library.principles.facts')}</dt>
                <dd className="library-text">{principle.factsApplied}</dd>
              </>
            )}
            {principle.scopeNote === null ? null : (
              <>
                <dt>{t('library.principles.scope')}</dt>
                <dd className="library-text">{principle.scopeNote}</dd>
              </>
            )}
            {principle.memberNotes === null ? null : (
              <>
                <dt>{t('library.principles.notes')}</dt>
                <dd className="library-text">{principle.memberNotes}</dd>
              </>
            )}
          </dl>
          <p>
            {t('library.principles.source', {
              court: citation(principle),
              number: principle.caseNumber ?? t('library.na'),
              date: principle.judgmentDate === null ? t('library.na') : formatDate(principle.judgmentDate, locale),
            })}
          </p>
          <p>
            <strong>{t(`library.principles.${principle.reviewStatus}`)}</strong>
          </p>
          {canManage ? (
            <div className="library-toolbar">
              <button
                type="button"
                onClick={() => {
                  open(principle);
                }}
              >
                {t('library.principles.edit')}
              </button>
              {principle.reviewStatus === 'DRAFT' ? (
                <button
                  type="button"
                  onClick={() => {
                    void review(principle);
                  }}
                >
                  {t('library.principles.review')}
                </button>
              ) : null}
            </div>
          ) : null}
        </article>
      ))}
      {canManage && editing === null ? (
        <button
          type="button"
          onClick={() => {
            open('new');
          }}
        >
          {t('library.principles.add')}
        </button>
      ) : null}
      {canManage && editing !== null ? (
        <form
          className="case-form library-form"
          noValidate
          aria-label={t('library.principles.add')}
          onSubmit={(e) => {
            void save(e);
          }}
        >
          {editing === 'new' ? null : <p>{t('library.principles.resetNote')}</p>}
          {field('principleText', 'library.principles.text', true)}
          {field('legalTopic', 'library.principles.topic', false)}
          {field('legalBasis', 'library.principles.basis', true)}
          {field('factsApplied', 'library.principles.facts', true)}
          {field('scopeNote', 'library.principles.scope', true)}
          {field('memberNotes', 'library.principles.notes', true)}
          <div className="library-toolbar">
            <button type="submit">{t('library.principles.save')}</button>
            <button
              type="button"
              onClick={() => {
                setEditing(null);
              }}
            >
              {t('library.principles.cancel')}
            </button>
          </div>
        </form>
      ) : null}
    </section>
  );
}
