import { type SyntheticEvent, useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import {
  DIMENSIONS,
  addClassification,
  listClassifications,
  replaceClassification,
  type Classification,
  type Dimension,
} from './api';
import { dimensionKey } from './labels';
import './library.css';

/** The managed classification lists (owner decision D10): document values apart from proposed ones; never deleted. */
export function ClassificationsPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('library.lists.title'));
  const id = useId();
  const [dimension, setDimension] = useState<Dimension>('LEGAL_TOPIC');
  const [items, setItems] = useState<Classification[] | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [problem, setProblem] = useState<MessageKey | null>(null);
  const [notice, setNotice] = useState<MessageKey | null>(null);
  const [generation, setGeneration] = useState(0);
  const [code, setCode] = useState('');
  const [labelAr, setLabelAr] = useState('');
  const [labelEn, setLabelEn] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    listClassifications(dimension, true).then(
      (found) => {
        if (!controller.signal.aborted) setItems(found);
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiError && error.status === 403) setForbidden(true);
        else setProblem('library.failed');
      },
    );
    return () => {
      controller.abort();
    };
  }, [dimension, generation]);

  if (forbidden) return <AccessDeniedPage />;

  const explain = (error: unknown): MessageKey =>
    error instanceof ApiError && error.status === 409 ? 'library.lists.exists' : 'library.saveFailed';

  const add = async (event: SyntheticEvent) => {
    event.preventDefault();
    setNotice(null);
    if (code.trim() === '' || labelAr.trim() === '') {
      setProblem('library.lists.needFields');
      return;
    }
    try {
      await addClassification({
        dimension,
        code: code.trim().toUpperCase(),
        labelAr: labelAr.trim(),
        labelEn: labelEn.trim() === '' ? null : labelEn.trim(),
      });
      setCode('');
      setLabelAr('');
      setLabelEn('');
      setProblem(null);
      setNotice('library.lists.added');
      setGeneration((n) => n + 1);
    } catch (error) {
      setProblem(explain(error));
    }
  };

  const toggle = async (item: Classification) => {
    setNotice(null);
    try {
      await replaceClassification(item, { active: !item.active });
      setProblem(null);
      setNotice('library.lists.saved');
      setGeneration((n) => n + 1);
    } catch (error) {
      setProblem(explain(error));
    }
  };

  return (
    <>
      <h1>{t('library.lists.title')}</h1>
      <p>
        <Link to={withLang('/library', locale)}>{t('library.back')}</Link>
      </p>
      <p>{t('library.lists.intro')}</p>
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
      <div className="case-field">
        <label htmlFor={`${id}-dimension`}>{t('library.lists.dimension')}</label>
        <select
          id={`${id}-dimension`}
          value={dimension}
          onChange={(e) => {
            setItems(null);
            setDimension(e.target.value as Dimension);
          }}
        >
          {DIMENSIONS.map((d) => (
            <option key={d} value={d}>
              {t(dimensionKey(d))}
            </option>
          ))}
        </select>
      </div>
      {items === null ? (
        <p role="status">{t('library.loading')}</p>
      ) : items.length === 0 ? (
        <p>{t('library.lists.empty')}</p>
      ) : (
        <ul className="library-results">
          {items.map((item) => (
            <li key={item.id} className="library-result">
              <strong>{item.labelAr}</strong>
              {item.labelEn === null ? null : <span> / {item.labelEn}</span>} <code>{item.code}</code>{' '}
              <span className="library-badge">{t(`library.origin.${item.origin}` as MessageKey)}</span>
              {item.active ? null : <span className="library-badge" data-tone="warn">{t('library.lists.inactive')}</span>}{' '}
              <button
                type="button"
                onClick={() => {
                  void toggle(item);
                }}
              >
                {item.active ? t('library.lists.deactivate') : t('library.lists.activate')}
              </button>
            </li>
          ))}
        </ul>
      )}
      <form
        className="case-form library-form"
        noValidate
        aria-label={t('library.lists.add')}
        onSubmit={(e) => {
          void add(e);
        }}
      >
        <div className="case-field">
          <label htmlFor={`${id}-code`}>{t('library.lists.code')}</label>
          <input id={`${id}-code`} type="text" maxLength={40} value={code} onChange={(e) => { setCode(e.target.value); }} />
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-ar`}>{t('library.lists.labelAr')}</label>
          <input id={`${id}-ar`} type="text" maxLength={200} value={labelAr} onChange={(e) => { setLabelAr(e.target.value); }} />
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-en`}>{t('library.lists.labelEn')}</label>
          <input id={`${id}-en`} type="text" maxLength={200} value={labelEn} onChange={(e) => { setLabelEn(e.target.value); }} />
        </div>
        <button type="submit">{t('library.lists.add')}</button>
      </form>
    </>
  );
}
