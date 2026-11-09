import { useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import type { MessageKey } from '../../i18n/catalog';
import type { DirectoryUser } from '../operations/tasks/tasksApi';
import { AreaField, ChoiceField, Notice, TextField } from '../pleadings/ui';
import { createLegalDocument, updateLegalDocument, type CategoryValue, type LegalDocument } from './api';
import { archiveError } from './errors';

interface Props {
  caseId: string;
  /** The memo being edited; absent when registering a new one. */
  existing?: LegalDocument;
  categories: readonly CategoryValue[];
  members: readonly DirectoryUser[];
  onSaved: () => void;
  onCancel?: () => void;
}

/** Name, type, date, issuer, addressee, responsible member and description of a memo or letter (requirement 12). */
export function LegalDocumentForm({ caseId, existing, categories, members, onSaved, onCancel }: Props) {
  const { t, locale } = useI18n();
  const [title, setTitle] = useState(existing?.title ?? '');
  const [categoryId, setCategoryId] = useState(existing?.categoryId ?? '');
  const [date, setDate] = useState(existing?.documentDate ?? '');
  const [issuer, setIssuer] = useState(existing?.issuer ?? '');
  const [addressee, setAddressee] = useState(existing?.addressee ?? '');
  const [memberId, setMemberId] = useState(existing?.responsibleMemberId ?? '');
  const [description, setDescription] = useState(existing?.description ?? '');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ failed: boolean; key: MessageKey } | null>(null);
  const [invalid, setInvalid] = useState(false);

  const orNull = (value: string) => (value.trim() === '' ? null : value.trim());
  const label = (c: CategoryValue) => (locale === 'ar' ? c.labelAr : (c.labelEn ?? c.labelAr)) + (c.needsApproval ? ` (${t('wp5.needsApproval')})` : '');

  const submit = async () => {
    if (title.trim() === '') {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    setBusy(true);
    setMessage(null);
    const body = {
      title: title.trim(),
      categoryId: orNull(categoryId),
      documentDate: orNull(date),
      issuer: orNull(issuer),
      addressee: orNull(addressee),
      responsibleMemberId: orNull(memberId),
      description: orNull(description),
      documentId: null,
    };
    try {
      if (existing === undefined) {
        await createLegalDocument(caseId, body);
        setTitle('');
        setCategoryId('');
        setDate('');
        setIssuer('');
        setAddressee('');
        setMemberId('');
        setDescription('');
      } else {
        await updateLegalDocument(existing.id, { ...body, version: existing.version });
      }
      setMessage({ failed: false, key: 'wp5.legal.saved' });
      onSaved();
    } catch (error) {
      setMessage({ failed: true, key: archiveError(error) });
    }
    setBusy(false);
  };

  return (
    <form
      className="case-form"
      aria-label={existing === undefined ? t('wp5.legal.add') : t('wp5.legal.edit')}
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <h4>{existing === undefined ? t('wp5.legal.add') : t('wp5.legal.edit')}</h4>
      <TextField label={t('wp5.legal.name')} value={title} onChange={setTitle} max={300} invalid={invalid} />
      <ChoiceField
        label={t('wp5.legal.category')}
        value={categoryId}
        onChange={setCategoryId}
        options={categories.map((c) => [c.id, label(c)] as const)}
        empty={t('wp5.notSpecified')}
      />
      <TextField label={t('wp5.legal.date')} type="date" value={date} onChange={setDate} />
      <TextField label={t('wp5.legal.issuer')} value={issuer} onChange={setIssuer} max={300} />
      <TextField label={t('wp5.legal.addressee')} value={addressee} onChange={setAddressee} max={300} />
      <ChoiceField
        label={t('wp5.legal.member')}
        value={memberId}
        onChange={setMemberId}
        options={members.map((m) => [m.id, m.displayName] as const)}
        empty={t('wp5.notSpecified')}
      />
      <AreaField label={t('wp5.legal.description')} value={description} onChange={setDescription} />
      {message === null ? null : <Notice failed={message.failed}>{t(message.key)}</Notice>}
      <div className="archive-actions">
        <button type="submit" disabled={busy}>
          {busy ? t('wp5.saving') : t('wp5.save')}
        </button>
        {onCancel === undefined ? null : (
          <button type="button" onClick={onCancel}>
            {t('wp5.cancel')}
          </button>
        )}
      </div>
    </form>
  );
}
