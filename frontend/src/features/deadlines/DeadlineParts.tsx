import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import type { Calculation, Deadline } from './api';

/** Whether the date is official, awaiting approval, and how overdue it is, as one badge each. */
export function DeadlineBadges({ deadline }: { deadline: Deadline }) {
  const { t } = useI18n();
  return (
    <>
      <span className="wp3-badge" data-calc={deadline.calcStatus}>{t(`wp4.calc.${deadline.calcStatus}`)}</span>
      {deadline.status === 'OPEN' ? null : (
        <span className="wp3-badge" data-state={deadline.status}>{t(`wp4.status.${deadline.status}`)}</span>
      )}
      {deadline.overdue ? <span className="wp3-badge" data-status="OVERDUE">{t('wp4.overdue')}</span> : null}
    </>
  );
}

/** The arithmetic of a computed date, exactly as it is stored with the proposal. */
export function CalculationView({ calculation }: { calculation: Calculation }) {
  const { t, locale } = useI18n();
  const date = (iso: string) => formatDate(iso, locale);
  const weekday = (day: number) => t(`wp4.weekday.${String(day) as '1'}`);
  return (
    <details className="wp4-calculation">
      <summary>{t('wp4.calc.show')}</summary>
      <dl className="case-facts">
        <dt>{t('wp4.calc.rule')}</dt>
        <dd>{calculation.ruleCode} — {calculation.ruleLabel}</dd>
        <dt>{t('wp4.calc.source')}</dt>
        <dd>{calculation.legalSource}</dd>
        <dt>{t('wp4.calc.method')}</dt>
        <dd>
          {t('wp4.calc.methodText', {
            days: String(calculation.periodDays),
            method: t(`wp4.method.${calculation.countMethod}`),
          })}
        </dd>
        <dt>{t('wp4.calc.counting')}</dt>
        <dd>
          {calculation.countsStartDay ? t('wp4.calc.startCounts') : t('wp4.calc.startNotCounted')}
          {' · '}
          {calculation.closedWeekdays.length === 0
            ? t('wp4.calc.noClosedWeekdays')
            : t('wp4.calc.closedWeekdays', { days: calculation.closedWeekdays.map(weekday).join('، ') })}
          {calculation.rollForward ? ` · ${t('wp4.calc.rolls')}` : ''}
        </dd>
        <dt>{t('wp4.calc.from')}</dt>
        <dd>{date(calculation.startsOn)} → {date(calculation.firstCountedDay)}</dd>
        <dt>{t('wp4.calc.skipped')}</dt>
        <dd>
          {calculation.skipped.length === 0 ? (
            t('wp4.calc.nothingSkipped')
          ) : (
            <ul>
              {calculation.skipped.map((s) => (
                <li key={s.date}>
                  {date(s.date)} — {s.kind === 'HOLIDAY' ? `${t('wp4.calc.holiday')}: ${s.label ?? ''}` : t('wp4.calc.weekday')}
                </li>
              ))}
            </ul>
          )}
        </dd>
        {calculation.rolledForwardFrom === null ? null : (
          <>
            <dt>{t('wp4.calc.rolledFrom')}</dt>
            <dd>{date(calculation.rolledForwardFrom)}</dd>
          </>
        )}
        <dt>{t('wp4.calc.result')}</dt>
        <dd><strong>{date(calculation.dueOn)}</strong></dd>
      </dl>
    </details>
  );
}
