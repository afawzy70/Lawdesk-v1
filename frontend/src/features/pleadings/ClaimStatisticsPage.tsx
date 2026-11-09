import { useState, type SyntheticEvent } from 'react';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { CASE_TYPES } from '../cases/api';
import { ReportSections } from '../reports/ReportParts';
import { claimStatistics, defenseStatistics, STAGES, type StatisticsFilter } from './api';
import { ChoiceField, Notice, TextField } from './ui';
import { useLoad } from './useLoad';
import './pleadings.css';

const NO_FILTER: StatisticsFilter = { stage: '', caseType: '', from: '', to: '' };

/**
 * Statistics of the results courts gave to claims (section 5) and of their positions on defences (section 17), counted
 * from the recorded results of the cases the user may see. Amounts are the awarded amounts only, in KWD.
 */
export function ClaimStatisticsPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('pl.stats.title'));
  const [draft, setDraft] = useState(NO_FILTER);
  const [applied, setApplied] = useState(NO_FILTER);
  const claims = useLoad((signal) => claimStatistics(applied, signal), [JSON.stringify(applied)]);
  const defenses = useLoad((signal) => defenseStatistics(applied, signal), [JSON.stringify(applied)]);
  const set = (field: keyof StatisticsFilter) => (value: string) => {
    setDraft((current) => ({ ...current, [field]: value }));
  };
  const number = (n: number) => formatNumber(n, locale);

  if (claims.status === 403 || defenses.status === 403) return <AccessDeniedPage />;
  return (
    <>
      <h1>{t('pl.stats.title')}</h1>
      <ReportSections />
      <form
        className="case-form"
        aria-label={t('pl.stats.filters')}
        noValidate
        onSubmit={(event: SyntheticEvent) => {
          event.preventDefault();
          setApplied(draft);
        }}
      >
        <ChoiceField
          label={t('pl.claims.stage')}
          value={draft.stage}
          onChange={set('stage')}
          options={STAGES.map((s) => [s, t(`proceedings.stage.${s}`)] as const)}
          empty={t('pl.stats.all')}
        />
        <ChoiceField
          label={t('pl.stats.caseType')}
          value={draft.caseType}
          onChange={set('caseType')}
          options={CASE_TYPES.map((c) => [c, t(`cases.type.${c}`)] as const)}
          empty={t('pl.stats.all')}
        />
        <TextField label={t('pl.stats.from')} type="date" value={draft.from} onChange={set('from')} />
        <TextField label={t('pl.stats.to')} type="date" value={draft.to} onChange={set('to')} />
        <div className="case-actions">
          <button type="submit" className="primary">
            {t('pl.stats.apply')}
          </button>
        </div>
      </form>
      {claims.status !== null || defenses.status !== null ? <Notice failed>{t('pl.error.load')}</Notice> : null}

      {claims.data === null ? null : (
        <section className="pleadings-section" aria-label={t('pl.stats.claimResults')}>
          <h2>{t('pl.stats.claimResults')}</h2>
          <p>{t('pl.stats.total', { count: number(claims.data.total) })}</p>
          <table className="case-table">
            <thead>
              <tr>
                <th scope="col">{t('pl.stats.result')}</th>
                <th scope="col">{t('pl.stats.count')}</th>
                <th scope="col">{t('pl.stats.awarded')}</th>
              </tr>
            </thead>
            <tbody>
              {claims.data.byResult.map((r) => (
                <tr key={r.result}>
                  <td>{t(`pl.claims.result.${r.result}`)}</td>
                  <td>{number(r.count)}</td>
                  <td dir="ltr">{r.awardedTotal}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <table className="case-table">
            <thead>
              <tr>
                <th scope="col">{t('pl.claims.stage')}</th>
                <th scope="col">{t('pl.stats.count')}</th>
              </tr>
            </thead>
            <tbody>
              {claims.data.byStage.map((s) => (
                <tr key={s.stage}>
                  <td>{t(`proceedings.stage.${s.stage}`)}</td>
                  <td>{number(s.count)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {defenses.data === null ? null : (
        <section className="pleadings-section" aria-label={t('pl.stats.defensePositions')}>
          <h2>{t('pl.stats.defensePositions')}</h2>
          <p>{t('pl.stats.total', { count: number(defenses.data.total) })}</p>
          <table className="case-table">
            <thead>
              <tr>
                <th scope="col">{t('pl.defenses.position')}</th>
                <th scope="col">{t('pl.stats.count')}</th>
              </tr>
            </thead>
            <tbody>
              {defenses.data.byPosition.map((p) => (
                <tr key={p.position}>
                  <td>{t(`pl.defenses.position.${p.position}`)}</td>
                  <td>{number(p.count)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <h3>{t('pl.stats.mostUsed')}</h3>
          {defenses.data.mostUsed.length === 0 ? (
            <p>{t('pl.stats.none')}</p>
          ) : (
            <ol className="pleadings-list">
              {defenses.data.mostUsed.map((m) => (
                <li key={m.title}>
                  {m.title} ({number(m.count)})
                </li>
              ))}
            </ol>
          )}
        </section>
      )}
    </>
  );
}
