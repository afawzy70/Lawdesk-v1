import { useId, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { listAssignees } from '../operations/tasks/tasksApi';
import { useLoad } from '../pleadings/useLoad';
import { asList, getArchiveSettings, listCaseLegalDocuments, listCategories, type ArchiveFormat } from './api';
import { LegalDocumentCard } from './LegalDocumentCard';
import { LegalDocumentForm } from './LegalDocumentForm';
import { usePermissions } from './usePermissions';

const NO_FORMATS: ArchiveFormat[] = [];

interface Props {
  caseId: string;
  /** False for an archived case: everything is shown, nothing can change. */
  editable: boolean;
}

/**
 * The register of memos and correspondence of one case (requirement 12) with its approval cycle: draft, in review, approved,
 * sent (recorded by a member, never sent by the system), archived. Approving needs MEMO_APPROVE; the backend checks every step.
 */
export function CaseLegalDocuments({ caseId, editable }: Props) {
  const { t } = useI18n();
  const headingId = useId();
  const granted = usePermissions();
  const list = useLoad((signal) => listCaseLegalDocuments(caseId, signal), [caseId]);
  const categories = useLoad((signal) => listCategories(signal), []);
  const members = useLoad((signal) => listAssignees(signal), []);
  const settings = useLoad((signal) => getArchiveSettings(signal), []);
  const [creating, setCreating] = useState(false);
  const canManage = editable && granted.has('DOCUMENT_MANAGE');
  const canApprove = editable && granted.has('MEMO_APPROVE');
  const formats = settings.data === null ? NO_FORMATS : asList(settings.data.formats);

  if (list.status === 403 || list.status === 404) {
    return (
      <section className="archive-section" aria-labelledby={headingId}>
        <h3 id={headingId}>{t('wp5.legal.title')}</h3>
        <p className="case-alert">{t('wp5.legal.hidden')}</p>
      </section>
    );
  }
  const items = Array.isArray(list.data) ? list.data : [];
  return (
    <section className="archive-section" aria-labelledby={headingId}>
      <h3 id={headingId}>{t('wp5.legal.title')}</h3>
      <p className="archive-meta">{t('wp5.legal.intro')}</p>
      {list.data === null ? <p>{list.status === null ? t('wp5.loading') : t('wp5.error.load')}</p> : null}
      {list.data !== null && items.length === 0 ? <p>{t('wp5.legal.none')}</p> : null}
      <ul className="archive-list">
        {items.map((doc) => (
          <LegalDocumentCard
            key={`${doc.id}-${String(doc.version)}`}
            doc={doc}
            caseId={caseId}
            categories={asList(categories.data)}
            members={asList(members.data)}
            formats={formats}
            canManage={canManage}
            canApprove={canApprove}
            onChanged={list.reload}
          />
        ))}
      </ul>
      {canManage ? (
        creating ? (
          <LegalDocumentForm
            caseId={caseId}
            categories={asList(categories.data)}
            members={asList(members.data)}
            onSaved={() => {
              setCreating(false);
              list.reload();
            }}
            onCancel={() => {
              setCreating(false);
            }}
          />
        ) : (
          <button
            type="button"
            onClick={() => {
              setCreating(true);
            }}
          >
            {t('wp5.legal.add')}
          </button>
        )
      ) : null}
    </section>
  );
}
