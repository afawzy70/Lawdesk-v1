import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { formatDateTime, formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { NotFoundPage } from '../../pages/NotFoundPage';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { getCase, type LegalCase } from './api';
import { CaseOperations } from '../operations/CaseOperations';
import { CaseLibrarySection } from '../library/CaseLibrarySection';
import { CaseProceedings } from '../proceedings/CaseProceedings';
import { CasePleadings } from '../pleadings/CasePleadings';
import { CaseAccessSection } from '../access/CaseAccessSection';
import { CaseCounselSection } from './CaseCounselSection';
import { CaseDetailsView } from './CaseDetailsView';
import { CaseInvolvementsSection } from './CaseInvolvementsSection';
import { CaseAssignmentSection } from './intake/CaseAssignmentSection';
import { CasePartiesSection } from './CasePartiesSection';
import { CaseStatusForm } from './CaseStatusForm';
import { CaseFollowUp } from '../followup/CaseFollowUp';
import { CaseWp9 } from '../wp9/CaseWp9';
import { CaseAssistantPanel } from '../assistant/CaseAssistantPanel';
import { CaseBriefPanel } from '../casebrief/CaseBriefPanel';
import { CaseSummaryPanel } from '../followup/CaseSummaryPanel';
import { CaseTimelineList } from './CaseTimelineList';
import type { CreatedState } from './CaseCreatePage';
import { formatDate } from './format';
import { CASES_PATH, editCasePath } from './paths';
import { isStatus, problemMessage } from './problems';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Load =
  | { kind: 'loading' }
  | { kind: 'forbidden' }
  | { kind: 'failed'; message: MessageKey }
  | { kind: 'loaded'; legalCase: LegalCase };

/** An address under /cases that cannot name a case is an unknown page, not a failed request. */
export function CaseDetailsPage() {
  const { id = '' } = useParams();
  return UUID.test(id) ? <CaseDetails id={id} /> : <NotFoundPage />;
}

/** LD-014: the case with its parties (LD-016), its status actions (LD-017) and its timeline. */
function CaseDetails({ id }: { id: string }) {
  const { locale, t } = useI18n();
  const created = (useLocation().state as CreatedState | null)?.created;
  const [generation, setGeneration] = useState(0);
  // The last answer for this case stays visible while a refresh is under way.
  const [result, setResult] = useState<{ id: string; load: Load } | null>(null);
  const load: Load = result?.id === id ? result.load : { kind: 'loading' };
  const [notice, setNotice] = useState<MessageKey | null>(null);

  usePageTitle(
    load.kind === 'loaded' ? t('cases.details.title', { referenceNo: load.legalCase.referenceNo }) : t('cases.title'),
  );

  useEffect(() => {
    const controller = new AbortController();
    getCase(id, controller.signal).then(
      (legalCase) => {
        if (!controller.signal.aborted) setResult({ id, load: { kind: 'loaded', legalCase } });
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        const failed: Load = isStatus(error, 403)
          ? { kind: 'forbidden' }
          : {
              kind: 'failed',
              message: isStatus(error, 404) ? 'cases.edit.notFound' : problemMessage(error, 'cases.edit.loadFailed'),
            };
        setResult({ id, load: failed });
      },
    );
    return () => {
      controller.abort();
    };
  }, [id, generation]);

  const refresh = (message: MessageKey | null) => {
    setNotice(message);
    setGeneration((n) => n + 1);
  };

  const back = (
    <p>
      <Link to={withLang(CASES_PATH, locale)}>{t('cases.form.back')}</Link>
    </p>
  );

  if (load.kind === 'forbidden') {
    return <AccessDeniedPage />;
  }
  if (load.kind === 'loading') {
    return (
      <>
        <h1>{t('cases.title')}</h1>
        <p role="status">{t('cases.edit.loading')}</p>
      </>
    );
  }
  if (load.kind === 'failed') {
    return (
      <>
        <h1>{t('cases.title')}</h1>
        <p className="case-alert" role="alert">
          {t(load.message)}
        </p>
        {back}
      </>
    );
  }

  const legalCase = load.legalCase;
  const financialText = (c: LegalCase) =>
    c.financialImpact == null
      ? t('intake.financial.unspecified')
      : c.financialImpact === 'NO' || c.financialAmount == null
        ? t('intake.financial.NO')
        : `${t('intake.financial.YES')}: ${t('intake.financial.show', {
            amount: formatNumber(Number(c.financialAmount), locale, { minimumFractionDigits: 3, maximumFractionDigits: 3 }),
          })}`;
  const archived = legalCase.status === 'ARCHIVED';
  // The latest action's confirmation, else the confirmation of a creation that just opened this page.
  const confirmation =
    notice !== null ? t(notice) : created === undefined ? null : t('cases.create.done', { referenceNo: created });
  return (
    <>
      <div className="case-dossier-head">
        <h1>{t('cases.details.title', { referenceNo: legalCase.referenceNo })}</h1>
        {back}
        {confirmation === null ? null : (
          <p className="case-notice" role="status">
            {confirmation}
          </p>
        )}
        <dl className="case-facts">
          <dt>{t('intake.details.automatedNo')}</dt>
          <dd>{legalCase.automatedNo ?? t('intake.details.notSpecified')}</dd>
          <dt>{t('intake.details.referenceNo')}</dt>
          <dd>{legalCase.referenceNo}</dd>
          <dt>{t('cases.field.title')}</dt>
          <dd>{legalCase.title}</dd>
          <dt>{t('cases.field.caseType')}</dt>
          <dd>{t(`cases.type.${legalCase.caseType}`)}</dd>
          <dt>{t('cases.field.status')}</dt>
          <dd>
            <span className="case-status" data-status={legalCase.status}>{t(`cases.status.${legalCase.status}`)}</span>
          </dd>
          <dt>{t('cases.field.subject')}</dt>
          <dd>{legalCase.subject ?? '—'}</dd>
          <dt>{t('cases.field.openedOn')}</dt>
          <dd>{formatDate(legalCase.openedOn, locale)}</dd>
          <dt>{t('intake.details.position')}</dt>
          <dd>
            {legalCase.authorityPosition == null
              ? t('intake.details.notSpecified')
              : t(`intake.position.${legalCase.authorityPosition}`)}
          </dd>
          <dt>{t('intake.details.financial')}</dt>
          <dd>{financialText(legalCase)}</dd>
          <dt>{t('intake.details.primary')}</dt>
          <dd>{legalCase.primaryMember?.displayName ?? t('intake.details.notSpecified')}</dd>
          <dt>{t('intake.details.alternate')}</dt>
          <dd>{legalCase.alternateMember?.displayName ?? t('intake.details.noAlternate')}</dd>
          <dt>{t('cases.field.updatedAt')}</dt>
          <dd>{formatDateTime(legalCase.updatedAt, locale)}</dd>
        </dl>
        {legalCase.status === 'CLOSED' || archived ? null : (
          <p>
            <Link to={withLang(editCasePath(legalCase.id), locale)}>{t('cases.details.edit')}</Link>
          </p>
        )}
      </div>
      <CaseSummaryPanel caseId={legalCase.id} generation={generation} />
      <CaseDetailsView details={legalCase.details} />
      <div className="case-workspace">
        <div className="case-workspace-index">
          {archived ? (
            <p className="case-notice">{t('cases.details.archived')}</p>
          ) : (
            <CaseStatusForm
              key={`${String(legalCase.version)}-${String(generation)}`}
              legalCase={legalCase}
              onChanged={() => {
                refresh('cases.status.done');
              }}
              onReload={() => {
                refresh(null);
              }}
            />
          )}
          <CaseAssignmentSection
            key={`assign-${String(legalCase.version)}`}
            legalCase={legalCase}
            onChanged={() => {
              refresh('intake.assign.done');
            }}
          />
          <CaseInvolvementsSection caseId={legalCase.id} editable={!archived} />
          <CaseAccessSection
            key={`access-${String(legalCase.version)}`}
            caseId={legalCase.id}
            confidentiality={legalCase.confidentiality ?? 'NORMAL'}
            version={legalCase.version}
            onChanged={() => {
              refresh('access.case.saved');
            }}
          />
          <CasePartiesSection
            caseId={legalCase.id}
            authorityPosition={legalCase.authorityPosition ?? null}
            generation={generation}
            editable={!archived}
            onChanged={() => {
              refresh(null);
            }}
          />
          <CaseCounselSection caseId={legalCase.id} editable={!archived} generation={generation} />
          <CaseTimelineList caseId={legalCase.id} generation={generation} />
        </div>
        <div className="case-workspace-content">
          {/* Judicial stages belong to Workstream C; the case only hosts them (architecture/CONTRACTS.md §5). */}
          <CaseProceedings caseId={legalCase.id} automatedNo={legalCase.automatedNo ?? null} />
          <CasePleadings caseId={legalCase.id} editable={!archived} />
          <CaseOperations caseId={legalCase.id} archived={archived} />
          <CaseLibrarySection caseId={legalCase.id} editable={!archived} />
          <CaseFollowUp caseId={legalCase.id} archived={archived} generation={generation} />
          <CaseWp9 caseId={legalCase.id} archived={archived} />
          <CaseBriefPanel caseId={legalCase.id} />
          <CaseAssistantPanel caseId={legalCase.id} />
        </div>
      </div>
    </>
  );
}
