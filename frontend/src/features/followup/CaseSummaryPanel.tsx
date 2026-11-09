import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { formatDateTime, formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { formatDate } from '../cases/format';
import { getSummary, type CaseSummaryData, type HearingBrief } from './api';
import { failureKey } from './errors';

interface Props {
  caseId: string;
  /** Changes whenever the case changed, so the summary is read again. */
  generation: number;
}

/** Section 21.1: the executive summary, read from the stored records every time the case page opens. */
export function CaseSummaryPanel({ caseId, generation }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [loaded, setLoaded] = useState<{ key: string; data: CaseSummaryData } | null>(null);
  const [failure, setFailure] = useState<{ key: string; message: MessageKey } | null>(null);
  const key = `${caseId}#${String(generation)}`;

  useEffect(() => {
    const controller = new AbortController();
    getSummary(caseId, controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setLoaded({ key, data });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setFailure({ key, message: failureKey(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, key]);

  const data = loaded?.key === key ? loaded.data : null;
  const amount = (value: string) =>
    formatNumber(Number(value), locale, { minimumFractionDigits: 3, maximumFractionDigits: 3 });
  const hearing = (h: HearingBrief | null) =>
    h === null
      ? t('wp3.notRecorded')
      : `${formatDate(h.hearingDate, locale)} · ${t(`wp3.hearing.kind.${h.kind as 'COURT'}`)}${
          h.decision === null ? '' : ` — ${h.decision}`
        }`;

  return (
    <section aria-labelledby={`${id}-h`} className="wp3-section wp3-summary">
      <h2 id={`${id}-h`}>{t('wp3.summary.title')}</h2>
      {failure?.key === key ? <p className="case-alert">{t(failure.message)}</p> : null}
      {data === null ? null : (
        <>
          <dl className="case-facts">
            <dt>{t('wp3.summary.parties')}</dt>
            <dd>
              {data.parties.length === 0
                ? t('wp3.notRecorded')
                : data.parties.map((p) => `${p.name} (${t(`wp3.summary.role.${p.role as 'CLIENT'}`)})`).join('، ')}
            </dd>
            <dt>{t('wp3.summary.units')}</dt>
            <dd>
              {data.involvedUnits.length === 0 ? t('wp3.notRecorded') : data.involvedUnits.map((u) => u.name).join('، ')}
            </dd>
            <dt>{t('wp3.summary.position')}</dt>
            <dd>
              {data.authorityPosition === null
                ? t('wp3.notRecorded')
                : t(`wp3.summary.position.${data.authorityPosition as 'PLAINTIFF'}`)}
            </dd>
            <dt>{t('wp3.summary.claimed')}</dt>
            <dd>{data.claimedAmount === null ? t('wp3.notRecorded') : amount(data.claimedAmount)}</dd>
            <dt>{t('wp3.summary.lastProcedure')}</dt>
            <dd>
              {data.lastProcedure === null
                ? t('wp3.notRecorded')
                : `${data.lastProcedure.summary} (${formatDateTime(data.lastProcedure.occurredAt, locale)})`}
            </dd>
            {data.hearings === null ? null : (
              <>
                <dt>{t('wp3.summary.lastHearing')}</dt>
                <dd>{hearing(data.hearings.lastHearing)}</dd>
                <dt>{t('wp3.summary.nextHearing')}</dt>
                <dd>
                  {data.hearings.nextHearing === null
                    ? data.hearings.followUp > 0
                      ? t('wp3.summary.noNextDate')
                      : t('wp3.notRecorded')
                    : hearing(data.hearings.nextHearing)}
                </dd>
                <dt>{t('wp3.summary.hearingCounts')}</dt>
                <dd>
                  {t('wp3.summary.counts', {
                    total: String(data.hearings.total),
                    adjourned: String(data.hearings.adjourned),
                    held: String(data.hearings.held),
                  })}
                </dd>
              </>
            )}
            {data.lastJudgment === null ? null : (
              <>
                <dt>{t('wp3.summary.lastJudgment')}</dt>
                <dd>
                  {formatDate(data.lastJudgment.judgmentDate, locale)} · {data.lastJudgment.outcome}
                  {data.lastJudgment.summary === null ? null : ` — ${data.lastJudgment.summary}`}
                </dd>
              </>
            )}
            {data.risk === null ? null : (
              <>
                <dt>{t('wp3.summary.risk')}</dt>
                <dd>
                  {t(`wp3.risk.level.${data.risk.level}`)}
                  {data.risk.potentialLoss === null ? null : ` · ${t('wp3.risk.loss', { amount: amount(data.risk.potentialLoss) })}`}
                </dd>
              </>
            )}
            {data.infoRequests === null ? null : (
              <>
                <dt>{t('wp3.summary.missing')}</dt>
                <dd>
                  {t('wp3.summary.requestCounts', {
                    open: String(data.infoRequests.open),
                    overdue: String(data.infoRequests.overdue),
                  })}
                  {data.infoRequests.pending.length === 0 ? null : (
                    <ul>
                      {data.infoRequests.pending.map((p) => (
                        <li key={p.id}>
                          {p.requestNo} — {p.subject}
                          {p.dueOn === null ? null : ` (${formatDate(p.dueOn, locale)})`}
                        </li>
                      ))}
                    </ul>
                  )}
                </dd>
              </>
            )}
            {data.tasks === null ? null : (
              <>
                <dt>{t('wp3.summary.tasks')}</dt>
                <dd>
                  {t('wp3.summary.taskCounts', { open: String(data.tasks.open), overdue: String(data.tasks.overdue) })}
                  {data.tasks.items.length === 0 ? null : (
                    <ul>
                      {data.tasks.items.map((task) => (
                        <li key={task.id}>{task.title}</li>
                      ))}
                    </ul>
                  )}
                </dd>
              </>
            )}
            {data.deadlines === undefined || data.deadlines === null ? null : (
              <>
                <dt>{t('wp4.summary.deadlines')}</dt>
                <dd>
                  {t('wp4.summary.counts', {
                    open: String(data.deadlines.open),
                    overdue: String(data.deadlines.overdue),
                    unapproved: String(data.deadlines.unapproved),
                  })}
                  {data.deadlines.items.length === 0 ? null : (
                    <ul>
                      {data.deadlines.items.map((d) => (
                        <li key={d.id}>
                          {d.action}
                          {d.dueOn === null ? '' : ` (${formatDate(d.dueOn, locale)})`}
                        </li>
                      ))}
                    </ul>
                  )}
                </dd>
              </>
            )}
            <dt>{t('wp3.summary.links')}</dt>
            <dd>
              {t('wp3.summary.linkCounts', { count: String(data.links.count), hidden: String(data.links.hiddenCount) })}
            </dd>
            {data.pendingModules.length === 0 ? null : (
              <>
                <dt>{t('wp3.summary.pending')}</dt>
                <dd>{data.pendingModules.map((m) => t(`wp3.summary.module.${m as 'CLAIMS'}`)).join('، ')}</dd>
              </>
            )}
          </dl>
          {data.hearings === null || data.hearings.followUp === 0 ? null : (
            <p className="case-notice">
              {t('wp3.summary.followUp')}{' '}
              <Link to={withLang('/hearings', locale)}>{t('wp3.summary.followUpLink')}</Link>
            </p>
          )}
        </>
      )}
    </section>
  );
}
