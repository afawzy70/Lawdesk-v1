import { useId, useRef, useState } from 'react';
import { formatDateTime } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import type { MessageKey } from '../../i18n/catalog';
import { Notice } from '../pleadings/ui';
import { useLoad } from '../pleadings/useLoad';
import { formatSize } from '../operations/documents/documentsApi';
import {
  acceptAttribute,
  addVersion,
  asList,
  reprocessText,
  versionContentUrl,
  listVersions,
  type ArchiveFormat,
  type DocumentVersion,
  type TextState,
} from './api';
import { archiveError } from './errors';

const TONE: Record<string, string> = {
  EXTRACTED: 'good',
  OCR_DONE: 'good',
  PENDING: 'warn',
  NO_TEXT: 'warn',
  UNSUPPORTED: 'warn',
  OCR_UNAVAILABLE: 'bad',
  FAILED: 'bad',
};

/** What happened when the archive tried to read the text of a version, said honestly (nothing is claimed as read that was not). */
export function TextStatus({ text }: { text: TextState | null }) {
  const { t, locale } = useI18n();
  if (text === null) return null;
  const searchable = text.status === 'EXTRACTED' || text.status === 'OCR_DONE';
  return (
    <p className="archive-meta">
      <span className="archive-badge" data-tone={TONE[text.status] ?? 'warn'}>
        {t(`wp5.text.${text.status}`)}
      </span>{' '}
      {searchable ? t('wp5.text.searchable') : t('wp5.text.notSearchable')}
      {text.pageCount !== null && text.pageCount > 0 ? ` · ${t('wp5.text.pages', { count: new Intl.NumberFormat(locale === 'ar' ? 'ar-KW' : 'en').format(text.pageCount) })}` : ''}
      {text.truncated ? ` · ${t('wp5.text.truncated')}` : ''}
      {text.status === 'OCR_UNAVAILABLE' && text.detail !== null ? ` · ${t('wp5.text.engineDetail', { detail: text.detail })}` : ''}
    </p>
  );
}

interface Props {
  documentId: string;
  title: string;
  canManage: boolean;
  formats: readonly ArchiveFormat[];
  onChanged: () => void;
}

/** Every version of a document with who uploaded it and when, its text state, and the form that adds a new version. */
export function DocumentVersions({ documentId, title, canManage, formats, onChanged }: Props) {
  const { t, locale } = useI18n();
  const versions = useLoad((signal) => listVersions(documentId, signal), [documentId]);
  const fileId = useId();
  const noteId = useId();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ failed: boolean; key: MessageKey } | null>(null);

  const submit = async () => {
    if (file === null) {
      setMessage({ failed: true, key: 'wp5.doc.chooseFile' });
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      await addVersion(documentId, file, note);
      setFile(null);
      setNote('');
      if (input.current !== null) input.current.value = '';
      setMessage({ failed: false, key: 'wp5.doc.versionAdded' });
      versions.reload();
      onChanged();
    } catch (error) {
      setMessage({ failed: true, key: archiveError(error) });
    }
    setBusy(false);
  };

  const reread = async () => {
    setMessage(null);
    try {
      await reprocessText(documentId);
      setMessage({ failed: false, key: 'wp5.text.reprocessed' });
      versions.reload();
    } catch (error) {
      setMessage({ failed: true, key: archiveError(error) });
    }
  };

  return (
    <section aria-label={t('wp5.doc.versions')}>
      <h5>{t('wp5.doc.versions')}</h5>
      {versions.data === null ? <p>{versions.status === null ? t('wp5.loading') : t('wp5.error.load')}</p> : null}
      {versions.data !== null && asList(versions.data).length === 0 ? <p>{t('wp5.doc.versionsNone')}</p> : null}
      <ul className="archive-list">
        {asList(versions.data).map((v: DocumentVersion) => (
          <li key={v.id} className="archive-item">
            <p>
              <strong>{t('wp5.doc.version', { number: String(v.versionNo) })}</strong>
              {v.current ? ` (${t('wp5.doc.current')})` : ''}
            </p>
            <p className="archive-meta">
              <bdi dir="ltr">{v.fileName}</bdi> · {t(`wp5.doc.typeLabel.${v.mediaType}` as MessageKey)} · {formatSize(v.sizeBytes, locale)}
            </p>
            <p className="archive-meta">
              {t('wp5.doc.uploadedBy')}: <bdi dir="ltr">{v.uploadedBy.slice(0, 8)}</bdi> · {t('wp5.doc.uploadedAt')}:{' '}
              {formatDateTime(v.uploadedAt, locale)}
            </p>
            {v.changeNote === null ? null : (
              <p className="archive-meta">
                {t('wp5.doc.changeNote')}: {v.changeNote}
              </p>
            )}
            <TextStatus text={v.text} />
            <div className="archive-actions">
              <a href={versionContentUrl(documentId, v.versionNo)} download aria-label={t('wp5.doc.downloadVersion', { number: String(v.versionNo) })}>
                {t('operations.document.download')}
              </a>
              {canManage && v.current && v.text !== null && v.text.status !== 'EXTRACTED' && v.text.status !== 'OCR_DONE' ? (
                <button type="button" onClick={() => void reread()}>
                  {t('wp5.text.reprocess')}
                </button>
              ) : null}
            </div>
          </li>
        ))}
      </ul>
      {message === null ? null : <Notice failed={message.failed}>{t(message.key)}</Notice>}
      {canManage ? (
        <form
          className="case-form"
          aria-label={t('wp5.doc.addVersion')}
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
        >
          <h5>{t('wp5.doc.addVersion')}</h5>
          <div className="case-field">
            <label htmlFor={fileId}>{t('wp5.doc.versionFile')}</label>
            <input
              id={fileId}
              ref={input}
              type="file"
              accept={acceptAttribute(formats)}
              aria-label={`${t('wp5.doc.versionFile')} (${title})`}
              onChange={(event) => {
                setFile(event.target.files?.[0] ?? null);
              }}
            />
          </div>
          <div className="case-field">
            <label htmlFor={noteId}>{t('wp5.doc.versionNote')}</label>
            <input
              id={noteId}
              type="text"
              maxLength={500}
              value={note}
              onChange={(event) => {
                setNote(event.target.value);
              }}
            />
          </div>
          <button type="submit" disabled={busy}>
            {busy ? t('wp5.saving') : t('wp5.doc.versionAdd')}
          </button>
        </form>
      ) : null}
    </section>
  );
}
