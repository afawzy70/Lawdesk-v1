import { type SyntheticEvent, useId, useState } from 'react';
import { Link } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { formatDate } from '../cases/format';
import {
  RELATION_TYPES,
  addRelation,
  removeRelation,
  searchJudgments,
  EMPTY_FILTERS,
  type Judgment,
  type ListItem,
  type Relation,
  type RelationType,
} from './api';
import { citation, relationKey } from './labels';

interface Props {
  judgment: Judgment;
  canManage: boolean;
  onChanged: () => void;
}

/** What happened to a judgment, with the standing warnings (requirements section 16.6). It records; it never decides. */
export function RelationsSection({ judgment, canManage, onChanged }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const { validity, relations } = judgment;
  const [type, setType] = useState<RelationType>('APPEALED');
  const [related, setRelated] = useState('');
  const [citationText, setCitationText] = useState('');
  const [date, setDate] = useState('');
  const [source, setSource] = useState('');
  const [notes, setNotes] = useState('');
  const [choices, setChoices] = useState<ListItem[] | null>(null);
  const [problem, setProblem] = useState<MessageKey | null>(null);
  const [notice, setNotice] = useState<MessageKey | null>(null);

  const loadChoices = () => {
    if (choices !== null) return;
    searchJudgments({ ...EMPTY_FILTERS }).then(
      (page) => {
        setChoices(page.items.filter((item) => item.id !== judgment.id));
      },
      () => {
        setChoices([]);
      },
    );
  };

  const add = async (event: SyntheticEvent) => {
    event.preventDefault();
    setNotice(null);
    if (source.trim() === '' || (related === '' && citationText.trim() === '')) {
      setProblem('library.relations.needSource');
      return;
    }
    try {
      await addRelation(judgment.id, {
        relationType: type,
        relatedJudgmentId: related === '' ? null : related,
        relatedCitation: citationText.trim() === '' ? null : citationText.trim(),
        relationDate: date === '' ? null : date,
        sourceReference: source.trim(),
        notes: notes.trim() === '' ? null : notes.trim(),
      });
      setSource('');
      setNotes('');
      setCitationText('');
      setRelated('');
      setDate('');
      setProblem(null);
      setNotice('library.relations.added');
      onChanged();
    } catch {
      setProblem('library.saveFailed');
    }
  };

  const remove = async (relation: Relation) => {
    try {
      await removeRelation(relation.id);
      setProblem(null);
      setNotice('library.relations.removed');
      onChanged();
    } catch {
      setProblem('library.saveFailed');
    }
  };

  const row = (relation: Relation) => (
    <li key={relation.id} className="library-card">
      <strong>{t(relationKey(relation.relationType))}</strong>
      {relation.relationDate === null ? null : <span> - {formatDate(relation.relationDate, locale)}</span>}
      <p>
        {relation.otherJudgmentId === null ? (
          relation.relatedCitation
        ) : (
          <Link to={withLang(`/library/${relation.otherJudgmentId}`, locale)}>
            {relation.otherSubject} ({citation({
              courtName: relation.otherCourtName ?? '',
              caseNumber: relation.otherCaseNumber,
              caseYear: relation.otherCaseYear,
            })})
          </Link>
        )}
      </p>
      <p>{t('library.case.source', { source: relation.sourceReference })}</p>
      {relation.notes === null ? null : <p className="library-text">{relation.notes}</p>}
      {canManage && relation.direction === 'OUTGOING' ? (
        <button
          type="button"
          onClick={() => {
            void remove(relation);
          }}
        >
          {t('library.relations.remove')}
        </button>
      ) : null}
    </li>
  );

  return (
    <section className="library-section" aria-labelledby={`${id}-h`}>
      <h2 id={`${id}-h`}>{t('library.relations.title')}</h2>
      <p className="case-notice">{t('library.validity.note')}</p>
      {validity.warnings.length === 0 ? null : (
        <ul role="status">
          {validity.warnings.map((w) => (
            <li key={w}>{t(`library.validity.warning.${w}` as MessageKey)}</li>
          ))}
        </ul>
      )}
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
      {relations.outgoing.length === 0 ? <p>{t('library.relations.none')}</p> : <ul>{relations.outgoing.map(row)}</ul>}
      {relations.incoming.length === 0 ? null : (
        <>
          <h3>{t('library.relations.incoming')}</h3>
          <ul>{relations.incoming.map(row)}</ul>
        </>
      )}
      {canManage ? (
        <form
          className="case-form library-form"
          noValidate
          aria-label={t('library.relations.add')}
          onSubmit={(e) => {
            void add(e);
          }}
        >
          <div className="case-field">
            <label htmlFor={`${id}-type`}>{t('library.relations.type')}</label>
            <select
              id={`${id}-type`}
              value={type}
              onChange={(e) => {
                setType(e.target.value as RelationType);
              }}
            >
              {RELATION_TYPES.map((r) => (
                <option key={r} value={r}>
                  {t(relationKey(r))}
                </option>
              ))}
            </select>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-related`}>{t('library.relations.related')}</label>
            <select
              id={`${id}-related`}
              value={related}
              onFocus={loadChoices}
              onChange={(e) => {
                setRelated(e.target.value);
              }}
            >
              <option value="">{t('library.relations.pickJudgment')}</option>
              {(choices ?? []).map((item) => (
                <option key={item.id} value={item.id}>
                  {item.subject} - {citation(item)}
                </option>
              ))}
            </select>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-citation`}>{t('library.relations.citation')}</label>
            <input
              id={`${id}-citation`}
              type="text"
              maxLength={500}
              value={citationText}
              onChange={(e) => {
                setCitationText(e.target.value);
              }}
            />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-date`}>{t('library.relations.date')}</label>
            <input
              id={`${id}-date`}
              type="date"
              value={date}
              onChange={(e) => {
                setDate(e.target.value);
              }}
            />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-source`}>{t('library.relations.source')}</label>
            <input
              id={`${id}-source`}
              type="text"
              maxLength={1000}
              value={source}
              onChange={(e) => {
                setSource(e.target.value);
              }}
            />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-notes`}>{t('library.relations.notes')}</label>
            <textarea
              id={`${id}-notes`}
              rows={2}
              maxLength={2000}
              value={notes}
              onChange={(e) => {
                setNotes(e.target.value);
              }}
            />
          </div>
          <button type="submit">{t('library.relations.add')}</button>
        </form>
      ) : null}
    </section>
  );
}
