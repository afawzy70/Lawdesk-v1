import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { getCaseParties, searchCases, type CaseListItem, type CaseParty } from '../features/cases/api';
import { casePath } from '../features/cases/paths';
import { addDays, formatDay, formatTime, legalToday } from '../features/operations/days';
import { listCalendar, type Hearing } from '../features/operations/hearings/hearingsApi';
import { useI18n } from '../i18n/I18nContext';
import { withLang } from '../i18n/locale';
import './modern-chambers-dashboard.css';

type Preview<T> =
  | { kind: 'loading' }
  | { kind: 'loaded'; items: T[] }
  | { kind: 'failed' };

/** Live, permission-scoped records for the diary and dossier layout in the approved Stitch screen. */
export function DashboardWorkspace({ canCases, canHearings }: { canCases: boolean; canHearings: boolean }) {
  const { locale, t } = useI18n();
  const [cases, setCases] = useState<Preview<CaseListItem>>({ kind: 'loading' });
  const [hearings, setHearings] = useState<Preview<Hearing>>({ kind: 'loading' });
  const [parties, setParties] = useState<Preview<CaseParty>>({ kind: 'loading' });
  const leadCaseId = cases.kind === 'loaded' ? cases.items[0]?.id : undefined;
  const leadHearing = hearings.kind === 'loaded' ? hearings.items.find((hearing) => hearing.caseId === leadCaseId) : undefined;

  useEffect(() => {
    if (!canCases) return undefined;
    const controller = new AbortController();
    searchCases({ status: 'ACTIVE' }, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) setCases(Array.isArray(page.items) ? { kind: 'loaded', items: page.items } : { kind: 'failed' });
      },
      () => {
        if (!controller.signal.aborted) setCases({ kind: 'failed' });
      },
    );
    return () => {
      controller.abort();
    };
  }, [canCases]);

  useEffect(() => {
    if (!canHearings) return undefined;
    const controller = new AbortController();
    const today = legalToday();
    listCalendar({ from: today, to: addDays(today, 7), status: 'SCHEDULED', page: 0 }, controller.signal).then(
      (page) => {
        if (!controller.signal.aborted) setHearings(Array.isArray(page.items) ? { kind: 'loaded', items: page.items } : { kind: 'failed' });
      },
      () => {
        if (!controller.signal.aborted) setHearings({ kind: 'failed' });
      },
    );
    return () => {
      controller.abort();
    };
  }, [canHearings]);

  useEffect(() => {
    if (!canCases || leadCaseId === undefined) return undefined;
    const controller = new AbortController();
    getCaseParties(leadCaseId, controller.signal).then(
      (items) => {
        if (!controller.signal.aborted) setParties(Array.isArray(items) ? { kind: 'loaded', items } : { kind: 'failed' });
      },
      () => {
        if (!controller.signal.aborted) setParties({ kind: 'failed' });
      },
    );
    return () => {
      controller.abort();
    };
  }, [canCases, leadCaseId]);

  if (!canCases && !canHearings) return null;

  return (
    <div className={`mc-daily-workspace${canCases && canHearings ? '' : ' mc-single'}`}>
      {canHearings && (
        <section className="mc-work-panel mc-diary" aria-labelledby="mc-diary-heading">
          <div className="mc-work-heading">
            <div>
              <p className="mc-kicker">{t('reports.dashboard.dailyWorkspace')}</p>
              <h2 id="mc-diary-heading">{t('reports.dashboard.diary')}</h2>
              <p>{t('reports.dashboard.diaryPeriod')}</p>
            </div>
            <Link to={withLang('/hearings', locale)}>{t('reports.dashboard.viewCalendar')}</Link>
          </div>
          {hearings.kind === 'loading' && <p>{t('reports.dashboard.loadingHearings')}</p>}
          {hearings.kind === 'failed' && <p>{t('reports.dashboard.unavailableHearings')}</p>}
          {hearings.kind === 'loaded' && hearings.items.length === 0 && <p className="mc-work-empty">{t('reports.dashboard.emptyHearings')}</p>}
          {hearings.kind === 'loaded' && hearings.items.length > 0 && (
            <ol className="mc-hearing-list">
              {hearings.items.slice(0, 3).map((hearing) => (
                <li key={hearing.id} className={hearing === hearings.items[0] ? 'mc-hearing-lead' : undefined}>
                  <p className="mc-hearing-time">
                    <time dateTime={`${hearing.hearingDate}${hearing.hearingTime === null ? '' : `T${hearing.hearingTime}`}`}>
                      {formatDay(hearing.hearingDate, locale)}
                      {hearing.hearingTime === null ? '' : ` · ${formatTime(hearing.hearingTime.slice(0, 5), locale)}`}
                    </time>
                  </p>
                  <p className="mc-hearing-title">
                    {canCases ? (
                      <Link to={withLang(casePath(hearing.caseId), locale)}><bdi dir="auto">{hearing.caseTitle}</bdi></Link>
                    ) : <bdi dir="auto">{hearing.caseTitle}</bdi>}
                  </p>
                  <p className="mc-hearing-meta">
                    <bdi dir="ltr">{hearing.caseReferenceNo}</bdi>
                    <span aria-hidden="true"> · </span>
                    <bdi dir="auto">{locale === 'ar' ? hearing.courtNameAr : hearing.courtNameEn}</bdi>
                  </p>
                </li>
              ))}
            </ol>
          )}
        </section>
      )}
      {canCases && (
        <section className="mc-work-panel mc-matters" aria-labelledby="mc-matters-heading">
          <div className="mc-work-heading">
            <div>
              <p className="mc-kicker">{t('reports.dashboard.caseRegister')}</p>
              <h2 id="mc-matters-heading">{t('reports.dashboard.activeMatters')}</h2>
              <p>{t('reports.dashboard.activeMattersIntro')}</p>
            </div>
            <Link to={withLang('/cases', locale)}>{t('reports.dashboard.viewAllCases')}</Link>
          </div>
          {cases.kind === 'loading' && <p>{t('reports.dashboard.loadingCases')}</p>}
          {cases.kind === 'failed' && <p>{t('reports.dashboard.unavailableCases')}</p>}
          {cases.kind === 'loaded' && cases.items.length === 0 && <p className="mc-work-empty">{t('reports.dashboard.emptyCases')}</p>}
          {cases.kind === 'loaded' && cases.items.length > 0 && (
            <ol className="mc-matter-list">
              {cases.items.slice(0, 3).map((legalCase, index) => (
                <li key={legalCase.id} className={index === 0 ? 'mc-matter-lead' : undefined}>
                  <span className="mc-case-reference"><bdi dir="ltr">{legalCase.referenceNo}</bdi></span>
                  <h3><Link to={withLang(casePath(legalCase.id), locale)}><bdi dir="auto">{legalCase.title}</bdi></Link></h3>
                  <p className="mc-matter-classification">{t(`cases.type.${legalCase.caseType}`)} <span aria-hidden="true">·</span> {t(`cases.status.${legalCase.status}`)}</p>
                  {index === 0 && parties.kind === 'loaded' && parties.items.some((party) => party.role === 'CLIENT' || party.role === 'OPPONENT') && (
                    <dl className="mc-matter-parties">
                      {(['CLIENT', 'OPPONENT'] as const).map((role) => {
                        const party = parties.items.find((item) => item.role === role);
                        return party === undefined ? null : <div key={role}><dt>{t(`cases.role.${role}`)}</dt><dd><bdi dir="auto">{party.name}</bdi></dd></div>;
                      })}
                    </dl>
                  )}
                  {index === 0 && <p className="mc-matter-opened">{t('reports.column.openedOn')}: <time dateTime={legalCase.openedOn}>{formatDay(legalCase.openedOn, locale)}</time></p>}
                  {index === 0 && leadHearing !== undefined && (
                    <p className="mc-matter-next-hearing">{t('reports.dashboard.diary')}: <time dateTime={leadHearing.hearingDate}>{formatDay(leadHearing.hearingDate, locale)}</time></p>
                  )}
                  {index === 0 && <Link className="mc-open-dossier" to={withLang(casePath(legalCase.id), locale)}>{t('reports.dashboard.openDossier')}</Link>}
                </li>
              ))}
            </ol>
          )}
        </section>
      )}
    </div>
  );
}
