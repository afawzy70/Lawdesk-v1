import { useEffect, useId, useRef, useState } from 'react';
import type { MessageKey } from '../../../i18n/catalog';
import { formatDateTime, formatNumber } from '../../../i18n/format';
import { useI18n } from '../../../i18n/I18nContext';
import { ApiError } from '../../../api/http';
import { loadError, saveError, useFormErrors } from '../formErrors';
import { Field } from '../FormParts';
import {
  attachDocument,
  contentUrl,
  fileProblem,
  formatSize,
  listCaseDocuments,
  MAX_DOCUMENTS,
  removeDocument,
  type CaseDocument,
} from './documentsApi';

type Result = { key: number; documents: CaseDocument[] } | { key: number; error: MessageKey };

const CLIENT_PROBLEMS = {
  missing: 'operations.document.chooseFile',
  notPdf: 'operations.fieldError.pdfOnly',
  tooLarge: 'operations.fieldError.maxSize',
} as const;

interface FormProps {
  caseId: string;
  onAttached: () => void;
}

/** Attaches one PDF; the browser checks type and size first, the backend checks them again (LD-026). */
function AttachForm({ caseId, onAttached }: FormProps) {
  const { t } = useI18n();
  const form = useFormErrors<'file' | 'title'>();
  const fileInput = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState('');
  const [local, setLocal] = useState<MessageKey | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    const problem = fileProblem(file);
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
      <Field id={fileControl.id} label={t('operations.document.fileInput')} error={fileError} errorId={form.errorId('file')}>
        <input
          id={fileControl.id}
          ref={fileInput}
          type="file"
          accept="application/pdf,.pdf"
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

/** The PDF attachments of one case (LD-026): at most ten, each downloadable, removable after confirmation. */
export function CaseDocuments({ caseId }: Props) {
  const { locale, t } = useI18n();
  const headingId = useId();
  const [reload, setReload] = useState(0);
  const [result, setResult] = useState<Result | null>(null);
  const [notice, setNotice] = useState<'attached' | 'removed' | MessageKey | null>(null);
  const [confirming, setConfirming] = useState<CaseDocument | null>(null);
  const [busy, setBusy] = useState(false);

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
      await removeDocument(target);
      setNotice('removed');
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
    const full = documents.length >= MAX_DOCUMENTS;
    return (
      <>
        <p>
          {t('operations.document.count', {
            count: formatNumber(documents.length, locale),
            limit: formatNumber(MAX_DOCUMENTS, locale),
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
                <div className="ops-actions">
                  <a href={contentUrl(d.id)} download aria-label={t('operations.document.downloadLabel', { title: d.title })}>
                    {t('operations.document.download')}
                  </a>
                  <button
                    type="button"
                    aria-label={t('operations.document.removeLabel', { title: d.title })}
                    onClick={() => {
                      setNotice(null);
                      setConfirming(d);
                    }}
                  >
                    {t('operations.document.remove')}
                  </button>
                </div>
                {confirming?.id === d.id ? (
                  <div className="ops-confirm">
                    <p>{t('operations.document.confirm', { title: d.title })}</p>
                    <div className="ops-actions">
                      <button
                        type="button"
                        disabled={busy}
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
    </section>
  );
}
