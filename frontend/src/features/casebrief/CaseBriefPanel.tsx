import { useContext, useId, useState } from 'react';
import { catalogs, type MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { AuthContext } from '../auth/AuthContext';
import { formatDate } from '../cases/format';
import { getCaseBrief, SECTION_KEYS, type Brief, type BriefEntry, type BriefSection } from './api';
import './casebrief.css';

interface Props {
  caseId: string;
}

/** Fields that are plumbing (identifiers), not facts to read. */
const HIDDEN_FIELDS = new Set(['sourceId', 'appealDeadlineId']);

/**
 * The case brief (section 22 rows that are queries over the system's own records). A read-only panel of the case page:
 * everything shown is a stored record, each item names its source, and what the records do not hold says "Not recorded".
 * It states on its face that it is not model-generated. The server decides what each user may see.
 */
export function CaseBriefPanel({ caseId }: Props) {
  const state = useContext(AuthContext)?.state;
  const allowed = state?.status === 'signedIn' && state.me.permissions.includes('CASE_VIEW');
  return allowed ? <Panel caseId={caseId} /> : null;
}

function Panel({ caseId }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [brief, setBrief] = useState<Brief | null>(null);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);

  const load = async () => {
    setBusy(true);
    setFailed(false);
    try {
      setBrief(await getCaseBrief(caseId));
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  };

  /** A message for a code the server sends, or the code itself when the catalog has none (never a blank). */
  const label = (prefix: string, code: string): string => {
    const key = `${prefix}.${code}`;
    return key in catalogs.en ? t(key as MessageKey) : code;
  };

  const value = (code: string): string => {
    if (code === 'true') return t('brief.yes');
    if (code === 'false') return t('brief.no');
    if (/^\d{4}-\d{2}-\d{2}$/.test(code)) return formatDate(code, locale);
    return `brief.code.${code}` in catalogs.en ? label('brief.code', code) : code;
  };

  const source = (entry: BriefEntry) => {
    const s = entry.source;
    const parts = [
      label('brief.kind', s.type),
      s.date === null ? null : t('brief.sourceDate', { date: formatDate(s.date, locale) }),
      s.documentTitle === null ? null : t('brief.sourceDocument', { title: s.documentTitle }),
      t('brief.sourcePage'),
    ].filter((p): p is string => p !== null);
    return (
      <p className="case-brief-source">
        {t('brief.source')}: {parts.join(' · ')}
      </p>
    );
  };

  const entryView = (entry: BriefEntry, key: string) => (
    <li key={key} className="case-brief-entry">
      <p className="case-brief-head">
        <strong>{entry.title === null || entry.title === '' ? label('brief.kind', entry.kind) : entry.title}</strong>{' '}
        <span className="case-brief-kind">({label('brief.kind', entry.kind)})</span>{' '}
        <span className="case-brief-status" data-status={entry.status}>
          {value(entry.status)}
        </span>
        {entry.date === null ? null : <> · {formatDate(entry.date, locale)}</>}
      </p>
      {entry.text === null || entry.text === '' ? null : <p className="case-brief-text">{entry.text}</p>}
      {Object.entries(entry.fields).filter(([name]) => !HIDDEN_FIELDS.has(name)).length === 0 ? null : (
        <dl className="case-brief-fields">
          {Object.entries(entry.fields)
            .filter(([name]) => !HIDDEN_FIELDS.has(name))
            .map(([name, fieldValue]) => (
              <div key={name}>
                <dt>{label('brief.field', name)}</dt>
                <dd>{value(fieldValue)}</dd>
              </div>
            ))}
        </dl>
      )}
      {entry.children.length === 0 ? null : (
        <ul className="case-brief-children">{entry.children.map((c, i) => entryView(c, `${key}-${String(i)}`))}</ul>
      )}
      {source(entry)}
    </li>
  );

  const sectionView = (name: (typeof SECTION_KEYS)[number], section: BriefSection) => (
    <section key={name} aria-labelledby={`${id}-${name}`} className="case-brief-section">
      <h3 id={`${id}-${name}`}>{t(`brief.section.${name}` as MessageKey)}</h3>
      {section.status === 'NOT_PERMITTED' ? (
        <p>{t('brief.notPermitted')}</p>
      ) : section.status === 'NOT_RECORDED' ? (
        <p>{t('brief.notRecorded')}</p>
      ) : (
        <ul>{section.items.map((e, i) => entryView(e, `${e.source.id}-${String(i)}`))}</ul>
      )}
      {section.omitted.length === 0 ? null : (
        <p className="case-field-hint">{t('brief.omitted', { kinds: section.omitted.map((k) => label('brief.kind', k)).join(', ') })}</p>
      )}
    </section>
  );

  return (
    <section aria-labelledby={`${id}-h`} className="case-brief">
      <h2 id={`${id}-h`}>{t('brief.title')}</h2>
      <p className="case-brief-notice" role="note">
        {t('brief.notice')}
      </p>
      <button
        type="button"
        disabled={busy}
        aria-expanded={brief !== null}
        onClick={() => {
          void load();
        }}
      >
        {t(brief === null ? 'brief.load' : 'brief.refresh')}
      </button>
      {busy ? <p role="status">{t('brief.loading')}</p> : null}
      {failed ? (
        <p className="case-alert" role="alert">
          {t('brief.failed')}
        </p>
      ) : null}
      {brief === null ? null : (
        <div>
          <p className="case-field-hint">{t('brief.asOf', { date: formatDate(brief.asOf, locale) })}</p>
          {SECTION_KEYS.map((name) => sectionView(name, brief[name]))}
        </div>
      )}
    </section>
  );
}

