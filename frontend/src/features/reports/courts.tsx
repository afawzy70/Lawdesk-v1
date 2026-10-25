import { useId, useState } from 'react';
import { api } from '../../api/http';
import { LIST_PAGE_SIZE } from '../../api/pagedList';
import { PagedSelect, type PagedResult } from '../../components/PagedSelect';
import { useI18n } from '../../i18n/I18nContext';
import { useAuth } from '../auth/AuthContext';
import type { Court } from '../courts/courtsApi';

/**
 * The court filter of the reports (QAD-044): every court by code, active or not, because reports cover past
 * proceedings too, offered through the shared paged selector so none is out of reach. Needs COURT_VIEW; without it
 * the filter is not shown. The form reads it by its name, `courtId`, like the other filters.
 */
function listReportCourts(page: number, q: string, signal: AbortSignal): Promise<PagedResult<Court>> {
  const query = new URLSearchParams({ page: String(page), size: String(LIST_PAGE_SIZE), sort: 'code,asc' });
  if (q.trim() !== '') query.set('q', q.trim());
  return api<PagedResult<Court>>(`/api/v1/courts?${query.toString()}`, { signal });
}

const getCourt = (id: string, signal: AbortSignal) => api<Court>(`/api/v1/courts/${encodeURIComponent(id)}`, { signal });

export function CourtFilter({ initial, anyLabel }: { initial: string; anyLabel: string }) {
  const { locale, t } = useI18n();
  const { state } = useAuth();
  const id = useId();
  const [value, setValue] = useState(initial);
  if (state.status !== 'signedIn' || !state.me.permissions.includes('COURT_VIEW')) return null;
  return (
    <div className="rpt-field" role="group" aria-labelledby={id}>
      <span id={id}>{t('reports.filter.court')}</span>
      <PagedSelect<Court>
        selectProps={{ name: 'courtId', 'aria-label': t('reports.filter.court') }}
        value={value}
        onChange={setValue}
        load={(page, query, signal) => listReportCourts(page, query, signal)}
        resolve={getCourt}
        optionLabel={(court) => (locale === 'ar' ? court.nameAr : court.nameEn)}
        serverSearch
        searchLabel={t('proceedings.searchCourts')}
        chooseLabel={anyLabel}
      />
    </div>
  );
}
