import { type SyntheticEvent, useCallback, useContext, useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { formatDate } from '../cases/format';
import { AuthContext } from '../auth/AuthContext';
import {
  linkPrecedent,
  listPrecedents,
  similarJudgments,
  unlinkPrecedent,
  type JudgmentRef,
  type Precedent,
  type Suggestions,
} from './api';
import { finalityKey, relationKey, listSeparator } from './labels';
import './library.css';

interface Props {
  caseId: string;
  /** False for an archived case: it is read-only. */
  editable: boolean;
}

/**
 * The library on the case page (LD-052, requirements section 16.4 and 16.5): judgments already linked to the case and
 * the read-only "similar judgments" panel. A suggestion never links itself: the member gives the reason and decides.
 * Shown to holders of LIBRARY_VIEW; the backend decides what each user may see and change.
 */
export function CaseLibrarySection({ caseId, editable }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const state = useContext(AuthContext)?.state;
  const allowed = state?.status === 'signedIn' && state.me.permissions.includes('LIBRARY_VIEW');
  const [linked, setLinked] = useState<Precedent[] | null>(null);
  const [similar, setSimilar] = useState<Suggestions | null>(null);
  const [problem, setProblem] = useState<MessageKey | null>(null);
  const [notice, setNotice] = useState<MessageKey | null>(null);
  const [linking, setLinking] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [generation, setGeneration] = useState(0);
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);

  useEffect(() => {
    if (!allowed) return;
    const controller = new AbortController();
    Promise.all([listPrecedents(caseId, controller.signal), similarJudgments(caseId, controller.signal)]).then(
      ([precedents, suggestions]) => {
        if (controller.signal.aborted) return;
        setLinked(precedents);
        setSimilar(suggestions);
      },
      () => {
        if (!controller.signal.aborted) setProblem('library.case.failed');
      },
    );
    return () => {
      controller.abort();
    };
  }, [allowed, caseId, generation]);

  if (!allowed) return null;

  const explain = (error: unknown): MessageKey =>
    error instanceof ApiError && error.status === 403
      ? 'library.case.refused'
      : error instanceof ApiError && error.status === 409
        ? 'library.conflict'
        : 'library.case.failed';

  const link = async (event: SyntheticEvent, judgmentId: string) => {
    event.preventDefault();
    setNotice(null);
    if (reason.trim() === '') {
      setProblem('library.case.needReason');
      return;
    }
    try {
      await linkPrecedent(caseId, {
        judgmentId,
        reason: reason.trim(),
        similarityNote: note.trim() === '' ? null : note.trim(),
        origin: 'SUGGESTION',
      });
      setLinking(null);
      setReason('');
      setNote('');
      setProblem(null);
      setNotice('library.case.linkedDone');
      reload();
    } catch (error) {
      setProblem(explain(error));
    }
  };

  const unlink = async (precedent: Precedent) => {
    setNotice(null);
    try {
      await unlinkPrecedent(caseId, precedent.id);
      setProblem(null);
      setNotice('library.case.unlinked');
      reload();
    } catch (error) {
      setProblem(explain(error));
    }
  };

  const cite = (j: JudgmentRef) =>
    t('library.case.citation', {
      court: j.courtName,
      number: j.caseNumber === null ? t('library.na') : `${j.caseNumber}${j.caseYear === null ? '' : `/${String(j.caseYear)}`}`,
      date: j.judgmentDate === null ? t('library.na') : formatDate(j.judgmentDate, locale),
    });

  return (
    <section aria-labelledby={`${id}-h`} className="library-section">
      <h2 id={`${id}-h`}>{t('library.case.title')}</h2>
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

      <h3>{t('library.case.linked')}</h3>
      {linked === null ? (
        <p role="status">{t('library.loading')}</p>
      ) : linked.length === 0 ? (
        <p>{t('library.case.noneLinked')}</p>
      ) : (
        <ul className="library-results">
          {linked.map((p) => (
            <li key={p.id} className="library-result">
              <Link to={withLang(`/library/${p.judgment.id}`, locale)}>{p.judgment.subject}</Link>
              <p>{cite(p.judgment)}</p>
              <p>{t('library.case.source', { source: p.judgment.sourceReference })}</p>
              <p className="library-text">{p.reason}</p>
              {p.similarityNote === null ? null : <p className="library-text">{p.similarityNote}</p>}
              {p.relationTypes.length === 0 ? null : (
                <p className="library-badge" data-tone="warn">
                  {t('library.badge.relations')}: {p.relationTypes.map((r) => t(relationKey(r))).join(listSeparator(locale))}
                </p>
              )}
              {editable ? (
                <button
                  type="button"
                  onClick={() => {
                    void unlink(p);
                  }}
                >
                  {t('library.case.unlink')}
                </button>
              ) : null}
            </li>
          ))}
        </ul>
      )}

      <h3>{t('library.case.suggestions')}</h3>
      <p className="case-notice">{t('library.case.suggestionsNote')}</p>
      {similar === null ? null : similar.items.length === 0 ? (
        <p>{t('library.case.noSuggestions')}</p>
      ) : (
        <ul className="library-results">
          {similar.items.map((s) => (
            <li key={s.judgment.id} className="library-result">
              <h4>
                <Link to={withLang(`/library/${s.judgment.id}`, locale)}>{s.judgment.subject}</Link>
              </h4>
              <p>
                {cite(s.judgment)} - {t(finalityKey(s.judgment.finality))}
                {s.judgment.hasCopy ? ` - ${t('library.case.hasCopy')}` : ''}
              </p>
              <p>{t('library.case.source', { source: s.judgment.sourceReference })}</p>
              <p>
                <strong>{t('library.case.score', { score: String(s.score) })}</strong>
              </p>
              {s.warnings.includes('LATER_RELATION_EXISTS') ? (
                <p className="library-badge" data-tone="warn">
                  {t('library.validity.warning.LATER_RELATION_EXISTS')}
                </p>
              ) : null}
              <p>{t('library.case.similarities')}</p>
              <ul>
                {s.reasons.map((r) => (
                  <li key={r.code}>
                    {t(`library.reason.${r.code}` as MessageKey)}
                    {r.code === 'SUBJECT_SIMILARITY' ? '' : `: ${r.terms.join(listSeparator(locale))}`} ({t('library.points', { points: String(r.points) })})
                  </li>
                ))}
              </ul>
              {s.differences.length === 0 ? null : (
                <>
                  <p>{t('library.case.differences')}</p>
                  <ul>
                    {s.differences.map((d) => (
                      <li key={d.code}>
                        {t(`library.difference.${d.code}` as MessageKey)}
                        {': '}
                        {d.code === 'DIFFERENT_CASE_TYPE' ? t(`cases.type.${d.detail}` as MessageKey) : t(`library.courtLevel.${d.detail}` as MessageKey)}
                      </li>
                    ))}
                  </ul>
                </>
              )}
              {s.alreadyLinked ? (
                <p>{t('library.case.alreadyLinked')}</p>
              ) : editable && linking !== s.judgment.id ? (
                <button
                  type="button"
                  onClick={() => {
                    setLinking(s.judgment.id);
                    setReason('');
                    setNote('');
                    setProblem(null);
                  }}
                >
                  {t('library.case.link')}
                </button>
              ) : null}
              {linking === s.judgment.id ? (
                <form
                  className="case-form library-form"
                  noValidate
                  aria-label={t('library.case.link')}
                  onSubmit={(e) => {
                    void link(e, s.judgment.id);
                  }}
                >
                  <div className="case-field">
                    <label htmlFor={`${id}-reason`}>{t('library.case.reason')}</label>
                    <textarea
                      id={`${id}-reason`}
                      rows={2}
                      maxLength={2000}
                      value={reason}
                      onChange={(e) => {
                        setReason(e.target.value);
                      }}
                    />
                  </div>
                  <div className="case-field">
                    <label htmlFor={`${id}-note`}>{t('library.case.similarityNote')}</label>
                    <textarea
                      id={`${id}-note`}
                      rows={2}
                      maxLength={2000}
                      value={note}
                      onChange={(e) => {
                        setNote(e.target.value);
                      }}
                    />
                  </div>
                  <button type="submit">{t('library.case.confirmLink')}</button>
                </form>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {similar !== null && !similar.basis.defensesIncluded ? <p>{t('library.case.defensesNote')}</p> : null}
    </section>
  );
}
