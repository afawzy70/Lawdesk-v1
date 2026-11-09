import { useI18n } from '../../i18n/I18nContext';
import type { CaseDocument } from '../operations/documents/documentsApi';
import { useLoad } from '../pleadings/useLoad';
import { listProceedings } from '../proceedings/proceedingsApi';
import { asList, listCategories, type ArchiveFormat } from './api';
import { DocumentClassification } from './DocumentClassification';
import { DocumentLinks } from './DocumentLinks';
import { DocumentVersions } from './DocumentVersions';

interface Props {
  doc: CaseDocument;
  canManage: boolean;
  formats: readonly ArchiveFormat[];
  onChanged: () => void;
}

/** Everything the archive keeps about one document besides the file: classification, versions with their text state, links. */
export function DocumentArchivePanel({ doc, canManage, formats, onChanged }: Props) {
  const { t } = useI18n();
  const categories = useLoad((signal) => listCategories(signal), []);
  const stages = useLoad((signal) => listProceedings(doc.caseId, signal), [doc.caseId]);
  return (
    <div className="archive-panel" role="group" aria-label={t('wp5.doc.detailsLabel', { title: doc.title })}>
      <p className="archive-meta">
        {t('wp5.doc.format')}: {t(`wp5.doc.typeLabel.${doc.mediaType}` as never)}
      </p>
      <DocumentClassification
        key={doc.version}
        doc={doc}
        categories={asList(categories.data)}
        stages={asList(stages.data)}
        canManage={canManage}
        onSaved={onChanged}
      />
      <DocumentVersions documentId={doc.id} title={doc.title} canManage={canManage} formats={formats} onChanged={onChanged} />
      <DocumentLinks caseId={doc.caseId} documentId={doc.id} canManage={canManage} />
    </div>
  );
}
