import { useId } from 'react';
import { Link } from 'react-router';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { NONE_KEY, indicatorHref, indicatorPath, type IndicatorFigure, type IndicatorGroup } from './indicatorsApi';

/**
 * LD-058 (requirements section 26): the 14 indicators of the main dashboard. Every figure links to the list of the
 * records it counts, which carries the same number; a distribution links each of its groups. An indicator whose data
 * belongs to a module that is not installed yet says so instead of showing a number.
 */
export function IndicatorGrid({ figures }: { figures: readonly IndicatorFigure[] }) {
  const { locale, t } = useI18n();
  const headingId = useId();
  return (
    <section className="rpt-panel" aria-labelledby={headingId}>
      <h2 id={headingId}>{t('wp8.indicators.title')}</h2>
      <p className="rpt-note">{t('wp8.indicators.intro')}</p>
      <ul className="wp8-indicators">
        {figures.map((figure) => (
          <li key={figure.code} className="wp8-indicator">
            {figure.available && figure.count !== null ? (
              <Link className="wp8-indicator-link" to={indicatorHref(indicatorPath(figure.code), locale)}>
                <span className="wp8-indicator-value">{formatNumber(figure.count, locale)}</span>
                <span className="wp8-indicator-name">{t(`wp8.indicator.${figure.code}`)}</span>
              </Link>
            ) : (
              <div className="wp8-indicator-pending">
                <span className="wp8-indicator-value">—</span>
                <span className="wp8-indicator-name">{t(`wp8.indicator.${figure.code}`)}</span>
                <span>{t('wp8.indicator.unavailable')}</span>
              </div>
            )}
            {figure.groups === null || figure.groups.length === 0 ? null : <Groups figure={figure} groups={figure.groups} />}
          </li>
        ))}
      </ul>
    </section>
  );
}

function groupLabel(group: IndicatorGroup, unknown: string, none: string): string {
  if (group.key === NONE_KEY) return none;
  return group.label ?? unknown;
}

function Groups({ figure, groups }: { figure: IndicatorFigure; groups: readonly IndicatorGroup[] }) {
  const { locale, t } = useI18n();
  const levels = [...new Set(groups.map((group) => group.level))];
  return (
    <details className="wp8-groups">
      <summary>{t('wp8.indicator.groups')}</summary>
      {levels.map((level) => (
        <div key={level ?? 'MEMBER'}>
          <h3>{t(`wp8.level.${level === 'SECTOR' || level === 'DEPARTMENT' || level === 'SECTION' ? level : 'MEMBER'}`)}</h3>
          <ul>
            {groups
              .filter((group) => group.level === level)
              .map((group) => (
                <li key={group.key}>
                  <Link to={indicatorHref(indicatorPath(figure.code, group), locale)}>
                    <bdi dir="auto">{groupLabel(group, t('wp8.group.unknown'), t('wp8.group.none'))}</bdi>
                  </Link>{' '}
                  <span>{formatNumber(group.count, locale)}</span>
                </li>
              ))}
          </ul>
        </div>
      ))}
    </details>
  );
}
