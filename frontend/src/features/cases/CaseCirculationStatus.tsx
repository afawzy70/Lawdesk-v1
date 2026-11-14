import { useEffect, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { listJudgments } from '../proceedings/proceedingsApi';
import { formatDate } from './format';

/**
 * What the case page says about the case instead of a status field: «متداولة» while no judgment has been issued in any
 * stage, and the date of the latest judgment once there is one. When the judgments cannot be read (no right to them, or
 * an error) nothing is claimed.
 */
export function CaseCirculationStatus({ caseId, generation }: { caseId: string; generation: number }) {
  const { t, locale } = useI18n();
  const [latest, setLatest] = useState<string | null | undefined>(undefined);

  useEffect(() => {
    const controller = new AbortController();
    listJudgments(caseId, controller.signal).then(
      (found) => {
        if (controller.signal.aborted) return;
        const dates = found.map((judgment) => judgment.judgmentDate).sort();
        setLatest(dates.length === 0 ? null : (dates[dates.length - 1] ?? null));
      },
      () => {
        if (!controller.signal.aborted) setLatest(undefined);
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, generation]);

  if (latest === undefined) return null;
  return (
    <p className="case-circulation" data-circulation={latest === null ? 'PENDING' : 'JUDGED'}>
      {latest === null ? t('cases.circulation.pending') : t('cases.circulation.judged', { date: formatDate(latest, locale) })}
    </p>
  );
}
