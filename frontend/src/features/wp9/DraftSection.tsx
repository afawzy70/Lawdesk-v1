import { useEffect, useId, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import type { MessageKey } from '../../i18n/catalog';
import { generateDraft, labelOf, listTemplates, listValues, type Draft, type ListValue, type TemplateSummary } from './api';
import { failureKey, isForbidden } from './errors';
import './wp9.css';

interface Props {
  caseId: string;
}

/** Splits a draft at its unfilled `{{placeholders}}` so each can be marked, with the text of everything else untouched. */
function parts(text: string): { text: string; missing: boolean }[] {
  return text
    .split(/(\{\{[^}]*}})/)
    .filter((piece) => piece !== '')
    .map((piece) => ({ text: piece, missing: piece.startsWith('{{') }));
}

/**
 * Section 29: a draft document from a ready template, filled from the case file. It is a draft, never final: it names the
 * fields the case file does not hold and leaves their placeholders in the text. Nothing is saved.
 */
export function DraftSection({ caseId }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [templates, setTemplates] = useState<TemplateSummary[]>([]);
  const [kinds, setKinds] = useState<ListValue[]>([]);
  const [hidden, setHidden] = useState(false);
  const [choice, setChoice] = useState('');
  const [draft, setDraft] = useState<Draft | null>(null);
  const [error, setError] = useState<MessageKey | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    listTemplates(controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) setTemplates(found.filter((template) => template.latestApprovedVersionNo !== null));
      },
      (failure: unknown) => {
        if (!controller.signal.aborted && isForbidden(failure)) setHidden(true);
      },
    );
    listValues('TEMPLATE_KIND', controller.signal).then(setKinds, () => {
      setKinds([]);
    });
    return () => {
      controller.abort();
    };
  }, []);

  if (hidden || templates.length === 0) return null;

  const generate = async () => {
    setError(null);
    setDraft(null);
    try {
      setDraft(await generateDraft(caseId, choice));
    } catch (failure) {
      setError(failureKey(failure));
    }
  };

  return (
    <section aria-labelledby={`${id}-h`} className="wp3-section">
      <h2 id={`${id}-h`}>{t('wp9.draft.title')}</h2>
      <p className="case-field-hint">{t('wp9.draft.hint')}</p>
      <div className="case-field">
        <label htmlFor={`${id}-template`}>{t('wp9.draft.template')}</label>
        <select
          id={`${id}-template`}
          value={choice}
          onChange={(e) => {
            setChoice(e.target.value);
            setDraft(null);
          }}
        >
          <option value="">{t('wp9.choose')}</option>
          {templates.map((template) => (
            <option key={template.id} value={template.id}>
              {template.title} — {labelOf(kinds, template.kind, locale)}
            </option>
          ))}
        </select>{' '}
        <button type="button" disabled={choice === ''} onClick={() => void generate()}>
          {t('wp9.draft.generate')}
        </button>
      </div>
      {error === null ? null : (
        <p className="case-alert" role="alert">
          {t(error)}
        </p>
      )}
      {draft === null ? null : (
        <div aria-live="polite">
          <p className="case-notice" role="status">
            <strong>{t('wp9.draft.badge')}</strong> {t('wp9.draft.notFinal')}
          </p>
          <div className="wp9-draft" dir="auto" data-testid="draft-text">
            {parts(draft.text).map((piece, index) =>
              piece.missing ? (
                <mark key={index} className="wp9-missing">
                  {piece.text}
                </mark>
              ) : (
                <span key={index}>{piece.text}</span>
              ),
            )}
          </div>
          {draft.missing.length === 0 ? (
            <p>{t('wp9.draft.nothingMissing')}</p>
          ) : (
            <>
              <h3>{t('wp9.draft.missing')}</h3>
              <ul>
                {draft.missing.map((field) => (
                  <li key={field.key}>{locale === 'ar' ? field.labelAr : field.labelEn}</li>
                ))}
              </ul>
            </>
          )}
          {draft.unknown.length === 0 ? null : (
            <p className="case-alert" role="alert">
              {t('wp9.draft.unknown', { fields: draft.unknown.join('، ') })}
            </p>
          )}
          <p className="case-field-hint">
            {t('wp9.draft.source', { no: String(draft.versionNo), status: t(`wp9.template.vstatus.${draft.versionStatus}`) })}
          </p>
        </div>
      )}
    </section>
  );
}
