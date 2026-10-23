import { useId } from 'react';
import { SampleRegion } from '../components/SampleRegion';
import { formatDateTime, formatNumber, formatPercent } from '../i18n/format';
import { useI18n } from '../i18n/I18nContext';
import { usePageTitle } from '../i18n/usePageTitle';
import { PREVIEW_CASES_BY_STAGE, PREVIEW_HEARINGS, PREVIEW_KPIS } from '../preview/mockData';

/** Dashboard preview. Read-only and rendered entirely from `preview/mockData.ts`. */
export function DashboardPage() {
  const { locale, t } = useI18n();
  usePageTitle(t('nav.dashboard'));
  const captionId = useId();

  const sampleRef = (n: number) => t('sample.caseRef', { number: formatNumber(n, locale, { minimumIntegerDigits: 3 }) });
  const stageTotal = PREVIEW_CASES_BY_STAGE.reduce((sum, s) => sum + s.count, 0);

  return (
    <>
      <h1>{t('nav.dashboard')}</h1>
      <p className="dashboard-intro">{t('dashboard.intro')}</p>

      <SampleRegion title={t('dashboard.kpis')}>
        <ul className="kpi-grid">
          {PREVIEW_KPIS.map((kpi) => (
            <li key={kpi.id} className="kpi">
              <span className="kpi-value">{formatNumber(kpi.value, locale)}</span>
              <span className="kpi-label">{t(`kpi.${kpi.id}`)}</span>
            </li>
          ))}
        </ul>
      </SampleRegion>

      <div className="dashboard-columns">
        <SampleRegion title={t('dashboard.hearings')} className="hearings">
          {/* Focusable so keyboard users can scroll the table on narrow screens (QAD-015). */}
          <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={captionId}>
            <table>
              <caption id={captionId} className="visually-hidden">
                {`${t('dashboard.hearings')} (${t('preview.sampleData')})`}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{t('hearings.case')}</th>
                  <th scope="col">{t('hearings.court')}</th>
                  <th scope="col">{t('hearings.stage')}</th>
                  <th scope="col">{t('hearings.when')}</th>
                </tr>
              </thead>
              <tbody>
                {PREVIEW_HEARINGS.map((h) => (
                  <tr key={h.id}>
                    <td>{sampleRef(h.sampleCaseNumber)}</td>
                    <td>{h.court[locale]}</td>
                    <td>{t(`stage.${h.stage}`)}</td>
                    <td>
                      <time dateTime={h.startsAt}>{formatDateTime(h.startsAt, locale)}</time>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </SampleRegion>

        <SampleRegion title={t('dashboard.stages')} className="stages">
          <ul className="stage-list">
            {PREVIEW_CASES_BY_STAGE.map(({ stage, count }) => {
              const share = count / stageTotal;
              return (
                <li key={stage} className="stage">
                  <span className="stage-label">{t(`stage.${stage}`)}</span>
                  <span className="stage-value">
                    {t('stages.value', { count: formatNumber(count, locale), percent: formatPercent(share, locale) })}
                  </span>
                  <span className="stage-bar" aria-hidden="true">
                    <span className="stage-bar-fill" aria-hidden="true" style={{ inlineSize: `${String(share * 100)}%` }} />
                  </span>
                </li>
              );
            })}
          </ul>
        </SampleRegion>
      </div>
    </>
  );
}
