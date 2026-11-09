import { Link } from 'react-router';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import type { DuplicateResult } from './api';
import { casePath } from './paths';

interface Props {
  result: DuplicateResult;
  confirmed: boolean;
  note: string;
  needConfirmation: boolean;
  onConfirmed: (confirmed: boolean) => void;
  onNote: (note: string) => void;
}

/** Section 3: the warning shown before a case that resembles another is registered. It never blocks by itself. */
export function DuplicateWarning({ result, confirmed, note, needConfirmation, onConfirmed, onNote }: Props) {
  const { t, locale } = useI18n();
  return (
    <section className="case-alert" role="alert" aria-label={t('dup.title')}>
      <h2>{t('dup.title')}</h2>
      <p>{t('dup.intro')}</p>
      <ul>
        {result.visible.map((hit) => (
          <li key={hit.caseId}>
            <Link to={withLang(casePath(hit.caseId), locale)}>
              {hit.referenceNo} — {hit.title}
            </Link>{' '}
            ({t(hit.kind === 'EXACT' ? 'dup.EXACT' : 'dup.SIMILAR')}:{' '}
            {hit.reasons.map((reason) => t(`dup.reason.${reason}` as 'dup.reason.TITLE')).join('، ')})
          </li>
        ))}
      </ul>
      {result.hidden === 0 ? null : <p>{t('dup.hidden', { count: String(result.hidden) })}</p>}
      <label>
        <input
          type="checkbox"
          checked={confirmed}
          onChange={(e) => {
            onConfirmed(e.target.checked);
          }}
        />{' '}
        {t('dup.confirm')}
      </label>
      {!confirmed ? null : (
        <div className="case-field">
          <label htmlFor="dup-note">{t('dup.note')}</label>
          <input
            id="dup-note"
            type="text"
            maxLength={500}
            value={note}
            onChange={(e) => {
              onNote(e.target.value);
            }}
          />
        </div>
      )}
      {needConfirmation && !confirmed ? <p className="case-field-error">{t('dup.needConfirm')}</p> : null}
    </section>
  );
}
