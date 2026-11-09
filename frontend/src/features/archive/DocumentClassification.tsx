import { useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import type { MessageKey } from '../../i18n/catalog';
import type { CaseDocument } from '../operations/documents/documentsApi';
import { numberOf } from '../proceedings/dates';
import type { Proceeding } from '../proceedings/proceedingsApi';
import { AreaField, ChoiceField, Notice, TextField } from '../pleadings/ui';
import { classifyDocument, type CategoryValue } from './api';
import { archiveError } from './errors';

interface Props {
  doc: CaseDocument;
  categories: readonly CategoryValue[];
  stages: readonly Proceeding[];
  canManage: boolean;
  onSaved: () => void;
}

/** The type, date and judicial stage of a document, and its description (requirement 13: classification by type, date, stage). */
export function DocumentClassification({ doc, categories, stages, canManage, onSaved }: Props) {
  const { t, locale } = useI18n();
  const [title, setTitle] = useState(doc.title);
  const [categoryId, setCategoryId] = useState(doc.categoryId ?? '');
  const [date, setDate] = useState(doc.documentDate ?? '');
  const [stageId, setStageId] = useState(doc.proceedingId ?? '');
  const [description, setDescription] = useState(doc.description ?? '');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ failed: boolean; key: MessageKey } | null>(null);

  const label = (c: CategoryValue) => (locale === 'ar' ? c.labelAr : (c.labelEn ?? c.labelAr)) + (c.needsApproval ? ` (${t('wp5.needsApproval')})` : '');

  const submit = async () => {
    setBusy(true);
    setMessage(null);
    try {
      await classifyDocument(doc.id, {
        title,
        categoryId: categoryId === '' ? null : categoryId,
        documentDate: date === '' ? null : date,
        proceedingId: stageId === '' ? null : stageId,
        description: description.trim() === '' ? null : description.trim(),
        version: doc.version,
      });
      setMessage({ failed: false, key: 'wp5.doc.classified' });
      onSaved();
    } catch (error) {
      setMessage({ failed: true, key: archiveError(error) });
    }
    setBusy(false);
  };

  return (
    <form
      className="case-form"
      aria-label={t('wp5.doc.classification')}
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <h5>{t('wp5.doc.classification')}</h5>
      <fieldset disabled={!canManage || busy} className="case-fieldset">
        <TextField label={t('wp5.doc.docTitle')} value={title} onChange={setTitle} max={200} />
        <ChoiceField
          label={t('wp5.doc.category')}
          value={categoryId}
          onChange={setCategoryId}
          options={categories.map((c) => [c.id, label(c)] as const)}
          empty={t('wp5.notSpecified')}
        />
        <TextField label={t('wp5.doc.date')} type="date" value={date} onChange={setDate} />
        <ChoiceField
          label={t('wp5.doc.stage')}
          value={stageId}
          onChange={setStageId}
          options={stages.map((p) => [p.id, `${t(`proceedings.stage.${p.stage}`)} ${numberOf(p)}`] as const)}
          empty={t('wp5.notSpecified')}
        />
        <AreaField label={t('wp5.doc.description')} value={description} onChange={setDescription} />
      </fieldset>
      {message === null ? null : <Notice failed={message.failed}>{t(message.key)}</Notice>}
      {canManage ? (
        <button type="submit" disabled={busy}>
          {busy ? t('wp5.saving') : t('wp5.doc.classify')}
        </button>
      ) : null}
    </form>
  );
}
