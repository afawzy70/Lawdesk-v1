import { useId, type ReactNode } from 'react';
import { NavLink } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { useAuth } from '../auth/AuthContext';
import { JUDGMENTS_PATH, REPORTS_PATH } from './common';

/** Shared parts of the report screens (docs/delivery/PHASE-6-ACCEPTANCE.md, common rule 8). */

/** The report sections; reports waiting for a Phase 5 port are named as not available, never linked. */
export function ReportSections() {
  const { locale, t } = useI18n();
  const pending: MessageKey[] = ['reports.section.hearings', 'reports.section.experts'];
  return (
    <nav aria-label={t('reports.sections')} className="rpt-sections">
      <ul>
        <li>
          <NavLink end to={withLang(REPORTS_PATH, locale)}>
            {t('reports.section.inventory')}
          </NavLink>
        </li>
        <li>
          <NavLink to={withLang(JUDGMENTS_PATH, locale)}>{t('reports.section.judgments')}</NavLink>
        </li>
        {pending.map((name) => (
          <li key={name} className="rpt-section-pending">
            {t('reports.section.pending', { name: t(name) })}
          </li>
        ))}
      </ul>
    </nav>
  );
}

/** A titled summary list of label/count pairs. */
export function Summary({ items }: { items: readonly (readonly [string, number])[] }) {
  const { locale, t } = useI18n();
  const headingId = useId();
  return (
    <section className="rpt-summary" aria-labelledby={headingId}>
      <h3 id={headingId}>{t('reports.summary')}</h3>
      <ul>
        {items.map(([label, value]) => (
          <li key={label} className="rpt-count">
            <span>{label}</span>
            <span className="rpt-count-value">{formatNumber(value, locale)}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** "Page n of m" and the previous / next buttons. */
export function Pager({ page, pages, onPage }: { page: number; pages: number; onPage: (page: number) => void }) {
  const { locale, t } = useI18n();
  return (
    <>
      <p>{t('reports.list.page', { page: formatNumber(page + 1, locale), pages: formatNumber(pages, locale) })}</p>
      {pages > 1 ? (
        <nav aria-label={t('reports.list.pagination')} className="rpt-actions">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => {
              onPage(page - 1);
            }}
          >
            {t('reports.list.previous')}
          </button>
          <button
            type="button"
            disabled={page + 1 >= pages}
            onClick={() => {
              onPage(page + 1);
            }}
          >
            {t('reports.list.next')}
          </button>
        </nav>
      ) : null}
    </>
  );
}

/**
 * LD-034: the CSV download of the report as filtered, for users holding REPORT_EXPORT (the backend checks it again).
 * A plain link, so the browser downloads the attachment with the session cookie; paging is not part of an export.
 */
export function ExportLinks({ path, params }: { path: string; params: URLSearchParams }) {
  const { locale, t } = useI18n();
  const { state } = useAuth();
  if (state.status !== 'signedIn' || !state.me.permissions.includes('REPORT_EXPORT')) return null;
  const query = new URLSearchParams(params);
  query.delete('page');
  query.set('format', 'csv');
  query.set('lang', locale);
  return (
    <p className="rpt-exports">
      <a href={`${path}?${query.toString()}`} download>
        {t('reports.export.csv')}
      </a>
    </p>
  );
}

/** A labelled field of the filter form. */
export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="rpt-field">
      <span>{label}</span>
      {children}
    </label>
  );
}
