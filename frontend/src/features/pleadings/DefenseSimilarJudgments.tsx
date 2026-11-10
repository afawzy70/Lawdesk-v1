import { useContext, useId, useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { AuthContext } from '../auth/AuthContext';
import { formatDate } from '../cases/format';
import { similarJudgmentsForDefense, type Suggestions } from '../library/api';
import { listSeparator } from '../library/labels';

interface Props {
  caseId: string;
  defenses: readonly { id: string; seq: number; title: string }[];
}

/**
 * Similar judgments for ONE defence (requirements section 16, preparing a defence memo; LD-DOC-16-043). The member picks a
 * defence and the library's fixed comparison runs on that defence alone. Nothing is linked from here and no model is used.
 */
export function DefenseSimilarJudgments({ caseId, defenses }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const state = useContext(AuthContext)?.state;
  const granted = new Set(state?.status === 'signedIn' ? state.me.permissions : []);
  const [defenseId, setDefenseId] = useState('');
  const [result, setResult] = useState<Suggestions | null>(null);
  const [problem, setProblem] = useState<MessageKey | null>(null);
  const [busy, setBusy] = useState(false);

  if (!granted.has('LIBRARY_VIEW') || !granted.has('DEFENSE_VIEW') || defenses.length === 0) return null;

  const show = async () => {
    if (defenseId === '') return;
    setBusy(true);
    setProblem(null);
    try {
      setResult(await similarJudgmentsForDefense(caseId, defenseId));
    } catch (error) {
      setResult(null);
      setProblem(error instanceof ApiError && error.status === 403 ? 'gap.similar.refused' : 'gap.similar.failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section aria-labelledby={`${id}-h`} className="pleadings-section">
      <h3 id={`${id}-h`}>{t('gap.similar.title')}</h3>
      <p className="case-field-hint">{t('gap.similar.hint')}</p>
      <div className="case-field">
        <label htmlFor={`${id}-defense`}>{t('gap.similar.choose')}</label>
        <select
          id={`${id}-defense`}
          value={defenseId}
          onChange={(e) => {
            setDefenseId(e.target.value);
            setResult(null);
            setProblem(null);
          }}
        >
          <option value="">{t('gap.similar.pick')}</option>
          {defenses.map((d) => (
            <option key={d.id} value={d.id}>
              {`#${String(d.seq)} ${d.title}`}
            </option>
          ))}
        </select>{' '}
        <button
          type="button"
          disabled={defenseId === '' || busy}
          onClick={() => {
            void show();
          }}
        >
          {t('gap.similar.show')}
        </button>
      </div>
      {problem === null ? null : (
        <p className="case-alert" role="alert">
          {t(problem)}
        </p>
      )}
      <div aria-live="polite">
        {result === null ? null : result.items.length === 0 ? (
          <p>{t('gap.similar.none')}</p>
        ) : (
          <ul className="library-results">
            {result.items.map((s) => (
              <li key={s.judgment.id} className="library-result">
                <Link to={withLang(`/library/${s.judgment.id}`, locale)}>{s.judgment.subject}</Link>
                <p>
                  {s.judgment.courtName}
                  {s.judgment.caseNumber === null ? '' : ` ${s.judgment.caseNumber}${s.judgment.caseYear === null ? '' : `/${String(s.judgment.caseYear)}`}`}
                  {s.judgment.judgmentDate === null ? '' : ` - ${formatDate(s.judgment.judgmentDate, locale)}`}
                </p>
                <p>
                  <strong>{t('library.case.score', { score: String(s.score) })}</strong>
                </p>
                <ul>
                  {s.reasons.map((r) => (
                    <li key={r.code}>
                      {t(`library.reason.${r.code}` as MessageKey)}
                      {r.code === 'SUBJECT_SIMILARITY' ? '' : `: ${r.terms.join(listSeparator(locale))}`} ({t('library.points', { points: String(r.points) })})
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
