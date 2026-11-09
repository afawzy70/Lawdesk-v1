import { useEffect, useId, useRef, useState } from 'react';
import type { MessageKey } from '../../../i18n/catalog';
import { formatDateTime, formatNumber } from '../../../i18n/format';
import { useI18n } from '../../../i18n/I18nContext';
import { ApiError } from '../../../api/http';
import { loadError, saveError, useFormErrors } from '../formErrors';
import { Field } from '../FormParts';
import { listDocumentUsage } from '../../pleadings/api';
import { DocumentUsageList } from '../../pleadings/DocumentUsageList';
import { useLoad } from '../../pleadings/useLoad';
import { DocumentArchivePanel } from '../../archive/DocumentArchivePanel';
import { WithdrawnDocuments } from '../../archive/WithdrawnDocuments';
import { usePermissions } from '../../archive/usePermissions';
import {
  acceptAttribute,
  DEFAULT_LIMITS,
  getArchiveSettings,
  type ArchiveFormat,
  type ArchiveLimits,
} from '../../archive/api';
import {
  attachDocument,
  contentUrl,
  fileProblem,
  formatSize,
  listCaseDocuments,
  withdrawDocumentWithReason,
  type Accepted,
  type CaseDocument,
} from './documentsApi';

type Result = { key: number; documents: CaseDocument[] } | { key: number; error: MessageKey };

const CLIENT_PROBLEMS = {
  missing: 'operations.document.chooseFile',
  notAccepted: 'operations.fieldError.pdfOnly',
  tooLarge: 'operations.fieldError.maxSize',
} as const;

/** The formats the archive accepts (from its settings, else all it knows) as the browser-side check wants them. */
const ALL_FORMATS: ArchiveFormat[] = [
  { code: 'PDF', family: 'PDF', mediaType: 'application/pdf', extension: 'pdf', enabled: true },
  {
    code: 'DOCX',
    family: 'WORD',
    mediaType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    extension: 'docx',
    enabled: true,
  },
  { code: 'DOC', family: 'WORD', mediaType: 'application/msword', extension: 'doc', enabled: true },
  { code: 'PNG', family: 'IMAGE', mediaType: 'image/png', extension: 'png', enabled: true },
  { code: 'JPEG', family: 'IMAGE', mediaType: 'image/jpeg', extension: 'jpg', enabled: true },
  { code: 'TIFF', family: 'IMAGE', mediaType: 'image/tiff', extension: 'tif', enabled: true },
];

function acceptedOf(formats: readonly ArchiveFormat[], limits: ArchiveLimits): Accepted {
  const enabled = formats.filter((f) => f.enabled);
  return {
    mediaTypes: enabled.map((f) => f.mediaType),
    extensions: enabled.flatMap((f) => (f.code === 'JPEG' ? ['jpg', 'jpeg'] : f.code === 'TIFF' ? ['tif', 'tiff'] : [f.extension])),
    maxBytes: limits.maxFileBytes,
  };
}

interface FormProps {
  caseId: string;
  accepted: Accepted;
  accept: string;
  onAttached: () => void;
}

/** Attaches one file (PDF, Word or image); the browser checks type and size first, the backend checks them again (LD-026, LD-055). */
function AttachForm({ caseId, accepted, accept, onAttached }: FormProps) {
  const { t, locale } = useI18n();
  const form = useFormErrors<'file' | 'title'>();
  const fileInput = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState('');
  const [local, setLocal] = useState<MessageKey | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    const problem = fileProblem(file, accepted);
    if (problem !== null || file === null) {
      setLocal(CLIENT_PROBLEMS[problem ?? 'missing']);
      return;
    }
    setLocal(null);
    setSaving(true);
    try {
      await attachDocument(caseId, file, title);
      setFile(null);
      setTitle('');
      if (fileInput.current !== null) fileInput.current.value = '';
      onAttached();
    } catch (error) {
      // Above its transport limit the web server answers a bare 413 (no problem document): the same condition as the
      // backend's MaxSize, one layer earlier, so it is explained the same way (QAD-037).
      const refused =
        error instanceof ApiError && error.status === 413
          ? new ApiError(400, { errors: [{ field: 'file', code: 'MaxSize' }] })
          : error;
      form.fail(refused, 'operations.error.stale', ['file', 'title']);
    }
    setSaving(false);
  };

  const fileError = local ?? form.errors.file;
  const fileControl = form.control('file');
  return (
    <form
      className="ops-form"
      aria-label={t('operations.document.attachTitle')}
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <h4>{t('operations.document.attachTitle')}</h4>
      {local === null && form.formError !== null ? (
        <p className="ops-alert">{t(form.formError)}</p>
      ) : null}
      <Field
        id={fileControl.id}
        label={t('operations.document.fileInput', { size: formatSize(accepted.maxBytes, locale) })}
        error={fileError}
        errorId={form.errorId('file')}
      >
        <input
          id={fileControl.id}
          ref={fileInput}
          type="file"
          accept={accept}
          aria-invalid={fileError === undefined ? undefined : true}
          aria-describedby={fileError === undefined ? undefined : form.errorId('file')}
          onChange={(event) => {
            setLocal(null);
            setFile(event.target.files?.[0] ?? null);
          }}
        />
      </Field>
      <Field
        id={form.control('title').id}
        label={t('operations.document.titleOptional')}
        error={form.errors.title}
        errorId={form.errorId('title')}
      >
        <input
          {...form.control('title')}
          type="text"
          maxLength={200}
          value={title}
          onChange={(event) => {
            setTitle(event.target.value);
          }}
        />
      </Field>
      <div className="ops-form-actions">
        <button type="submit" disabled={saving}>
          {saving ? t('operations.saving') : t('operations.document.attach')}
        </button>
      </div>
    </form>
  );
}

interface Props {
  caseId: string;
}

/**
 * The archive of one case (LD-026, LD-055): documents in PDF, Word and image formats up to the archive's limits, each with
 * its versions, classification and links; a document is taken out of the case file with a reason, never deleted here.
 */
export function CaseDocuments({ caseId }: Props) {
  const { locale, t } = useI18n();
  const headingId = useId();
  const granted = usePermissions();
  const canManage = granted.has('DOCUMENT_MANAGE');
  const [openId, setOpenId] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [withdrawals, setWithdrawals] = useState(0);
  // The archive's limits and accepted formats; if they cannot be read the defaults it ships with are used.
  const settings = useLoad((signal) => getArchiveSettings(signal), []);
  const limits = settings.data?.limits ?? DEFAULT_LIMITS;
  const formats = settings.data?.formats ?? ALL_FORMATS;
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const [notice, setNotice] = useState<'attached' | 'removed' | MessageKey | null>(null);
  const [confirming, setConfirming] = useState<CaseDocument | null>(null);
  const [busy, setBusy] = useState(false);
  // Where each document is used in the case file (evidence, a claim's source, a defence memo, a judgment copy; LD-051).
  const usage = useLoad((signal) => listDocumentUsage(caseId, signal), [caseId, reload]);

  useEffect(() => {
    const controller = new AbortController();
    listCaseDocuments(caseId, controller.signal).then(
      (documents) => {
        setResult({ key: reload, documents });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setResult({ key: reload, error: loadError(error) });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, reload]);

  const current = result?.key === reload ? result : null;

  const remove = async (target: CaseDocument) => {
    setBusy(true);
    setNotice(null);
    try {
      await withdrawDocumentWithReason(target, reason.trim());
      setNotice('removed');
      setReason('');
      setWithdrawals((n) => n + 1);
      setReload((n) => n + 1);
    } catch (error) {
      setNotice(saveError(error, 'operations.error.stale'));
    }
    setConfirming(null);
    setBusy(false);
  };

  const body = () => {
    if (current === null) return <p>{t('operations.loading')}</p>;
    if ('error' in current) {
      return (
        // Not role="alert": this section loads with the case page, whose own alerts must stay the only ones announced.
        <div className="ops-alert ops-load-error">
          <p>
            {t(current.error === 'operations.error.viewForbidden' ? 'operations.document.hidden' : current.error)}
          </p>
          {current.error === 'operations.error.load' ? (
            <button
              type="button"
              onClick={() => {
                setReload((n) => n + 1);
              }}
            >
              {t('operations.retry')}
            </button>
          ) : null}
        </div>
      );
    }
    const { documents } = current;
    const full = documents.length >= limits.maxDocumentsPerCase;
    return (
      <>
        <p>
          {t('operations.document.count', {
            count: formatNumber(documents.length, locale),
            limit: formatNumber(limits.maxDocumentsPerCase, locale),
          })}
        </p>
        {documents.length === 0 ? <p>{t('operations.document.none')}</p> : null}
        {documents.length > 0 ? (
          <ul className="ops-list">
            {documents.map((d) => (
              <li key={d.id} className="ops-item">
                <p>
                  <strong>{d.title}</strong>
                </p>
                <p className="ops-hint">
                  <bdi dir="ltr">{d.fileName}</bdi>
                  {' · '}
                  {formatSize(d.sizeBytes, locale)}
                  {' · '}
                  {formatDateTime(d.createdAt, locale)}
                </p>
                <DocumentUsageList uses={(usage.data ?? []).filter((u) => u.documentId === d.id)} />
                <div className="ops-actions">
                  <a href={contentUrl(d.id)} download aria-label={t('operations.document.downloadLabel', { title: d.title })}>
                    {t('operations.document.download')}
                  </a>
                  <button
                    type="button"
                    aria-expanded={openId === d.id}
                    aria-label={t('wp5.doc.detailsLabel', { title: d.title })}
                    onClick={() => {
                      setOpenId(openId === d.id ? null : d.id);
                    }}
                  >
                    {openId === d.id ? t('wp5.doc.hideDetails') : t('wp5.doc.details')}
                  </button>
                  {canManage ? (
                    <button
                      type="button"
                      aria-label={t('operations.document.removeLabel', { title: d.title })}
                      onClick={() => {
                        setNotice(null);
                        setReason('');
                        setConfirming(d);
                      }}
                    >
                      {t('operations.document.remove')}
                    </button>
                  ) : null}
                </div>
                {openId === d.id ? (
                  <DocumentArchivePanel
                    doc={d}
                    canManage={canManage}
                    formats={formats}
                    onChanged={() => {
                      setReload((n) => n + 1);
                    }}
                  />
                ) : null}
                {confirming?.id === d.id ? (
                  <div className="ops-confirm">
                    <p>{t('operations.document.confirm', { title: d.title })}</p>
                    <div className="case-field">
                      <label htmlFor={`withdraw-${d.id}`}>{t('wp5.doc.withdrawReason')}</label>
                      <input
                        id={`withdraw-${d.id}`}
                        type="text"
                        maxLength={500}
                        value={reason}
                        onChange={(event) => {
                          setReason(event.target.value);
                        }}
                      />
                    </div>
                    <div className="ops-actions">
                      <button
                        type="button"
                        disabled={busy || reason.trim() === ''}
                        onClick={() => {
                          void remove(d);
                        }}
                      >
                        {t('operations.document.confirmRemove')}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setConfirming(null);
                        }}
                      >
                        {t('operations.document.keep')}
                      </button>
                    </div>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        ) : null}
        {full ? (
          <p className="ops-hint">{t('operations.rule.DOCUMENT_LIMIT_REACHED')}</p>
        ) : (
          <AttachForm
            caseId={caseId}
            accepted={acceptedOf(formats, limits)}
            accept={acceptAttribute(formats)}
            onAttached={() => {
              setNotice('attached');
              setReload((n) => n + 1);
            }}
          />
        )}
      </>
    );
  };

  return (
    <section className="ops-section" aria-labelledby={headingId}>
      <h3 id={headingId}>{t('operations.documents.caseTitle')}</h3>
      {notice === 'attached' || notice === 'removed' ? (
        <p role="status" className="ops-saved">
          {t(notice === 'attached' ? 'operations.document.attached' : 'operations.document.removed')}
        </p>
      ) : null}
      {notice !== null && notice !== 'attached' && notice !== 'removed' ? (
        <p className="ops-alert">{t(notice)}</p>
      ) : null}
      {body()}
      {canManage ? (
        <WithdrawnDocuments
          caseId={caseId}
          canAdminister={granted.has('DOCUMENT_ARCHIVE_ADMIN')}
          generation={withdrawals}
          onRestored={() => {
            setReload((n) => n + 1);
          }}
        />
      ) : null}
    </section>
  );
}
