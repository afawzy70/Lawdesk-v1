import { useState } from 'react';
import { Link } from 'react-router';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { listPartyCases, type PartyCase } from './api';
import { casePath } from './paths';

/** Section 2: one party can be on many cases; lists the ones the viewer may see. */
export function PartyCases({ partyId, currentCaseId }: { partyId: string; currentCaseId: string }) {
  const { t, locale } = useI18n();
  const [cases, setCases] = useState<PartyCase[] | null>(null);
  const [failed, setFailed] = useState(false);

  const load = async () => {
    setFailed(false);
    try {
      setCases(await listPartyCases(partyId));
    } catch {
      setFailed(true);
    }
  };

  const others = cases?.filter((item) => item.caseId !== currentCaseId) ?? null;
  return (
    <div className="party-cases">
      {others !== null ? null : (
        <button type="button" onClick={() => void load()}>
          {t('party.casesLoad')}
        </button>
      )}
      {!failed ? null : <p role="alert">{t('party.casesFailed')}</p>}
      {others === null ? null : others.length === 0 ? (
        <p>{t('party.casesNone')}</p>
      ) : (
        <ul aria-label={t('party.cases')}>
          {others.map((item) => (
            <li key={item.caseId}>
              <Link to={withLang(casePath(item.caseId), locale)}>
                {item.referenceNo} — {item.title}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
