import { type SyntheticEvent, useCallback, useContext, useEffect, useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { AuthContext } from '../auth/AuthContext';
import {
  addTemplateVersion,
  createTemplate,
  editTemplateVersion,
  getTemplate,
  labelOf,
  listTemplates,
  listValues,
  setTemplateActive,
  stepTemplateVersion,
  templateFields,
  valueLabel,
  type FieldInfo,
  type ListValue,
  type Template,
  type TemplateSummary,
  type TemplateVersion,
} from './api';
import { failureKey } from './errors';
import './wp9.css';

/**
 * The library of ready legal templates (section 29, D10): the kinds are a managed list, a template has versions that are all
 * kept (draft, reviewed, approved), and a reviewed version is never edited.
 */
export function TemplatesPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('wp9.template.title'));
  const id = useId();
  const state = useContext(AuthContext)?.state;
  const manage = state?.status === 'signedIn' && state.me.permissions.includes('TEMPLATE_MANAGE');
  const [templates, setTemplates] = useState<TemplateSummary[] | null>(null);
  const [kinds, setKinds] = useState<ListValue[]>([]);
  const [fields, setFields] = useState<FieldInfo[]>([]);
  const [open, setOpen] = useState<Template | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const [generation, setGeneration] = useState(0);
  const [form, setForm] = useState({ kind: '', title: '', description: '', body: '' });
  const [invalid, setInvalid] = useState(false);
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    listTemplates(controller.signal, true).then(
      (found) => {
        if (!controller.signal.aborted) setTemplates(found);
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiError && error.status === 403) setForbidden(true);
        else setMessage({ key: 'wp9.template.loadFailed', failed: true });
      },
    );
    return () => {
      controller.abort();
    };
  }, [generation]);

  useEffect(() => {
    listValues('TEMPLATE_KIND').then(setKinds, () => {
      setKinds([]);
    });
    templateFields().then(setFields, () => {
      setFields([]);
    });
  }, []);

  if (forbidden) return <AccessDeniedPage />;

  const act = async (action: () => Promise<unknown>, done: MessageKey, again?: string) => {
    setMessage(null);
    try {
      await action();
      setMessage({ key: done, failed: false });
      reload();
      if (again !== undefined) setOpen(await getTemplate(again));
    } catch (error) {
      setMessage({ key: failureKey(error), failed: true });
    }
  };

  const create = async (event: SyntheticEvent) => {
    event.preventDefault();
    if (form.kind === '' || form.title.trim() === '') {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    await act(async () => {
      const created = await createTemplate(form);
      setOpen(created);
      setForm({ kind: '', title: '', description: '', body: '' });
    }, 'wp9.template.created');
  };

  const show = (summary: TemplateSummary) => {
    getTemplate(summary.id).then(setOpen, () => {
      setMessage({ key: 'wp9.template.loadFailed', failed: true });
    });
  };

  return (
    <>
      <h1>{t('wp9.template.title')}</h1>
      <p>{t('wp9.template.intro')}</p>
      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>
          {t(message.key)}
        </p>
      )}
      {templates === null ? null : templates.length === 0 ? (
        <p>{t('wp9.template.none')}</p>
      ) : (
        <ul className="wp3-list">
          {templates.map((template) => (
            <li key={template.id}>
              <p>
                <strong>{template.title}</strong> — {labelOf(kinds, template.kind, locale)}
                <span className="wp3-badge" data-status={template.active ? 'ACTIVE' : 'INACTIVE'}>
                  {t(template.active ? 'wp9.template.active' : 'wp9.template.inactive')}
                </span>
                <span className="wp3-badge">{t(`wp9.origin.${template.origin}`)}</span>
              </p>
              <p className="case-field-hint">
                {template.latestApprovedVersionNo === null
                  ? t('wp9.template.noApproved')
                  : t('wp9.template.approvedUpTo', { no: String(template.latestApprovedVersionNo) })}
                {' · '}
                {t('wp9.template.versions', { count: String(template.versionCount) })}
              </p>
              <p>
                <button
                  type="button"
                  onClick={() => {
                    show(template);
                  }}
                >
                  {t('wp9.template.open', { title: template.title })}
                </button>
              </p>
            </li>
          ))}
        </ul>
      )}

      {open === null ? null : (
        <TemplateDetail
          template={open}
          kinds={kinds}
          manage={manage}
          onClose={() => {
            setOpen(null);
          }}
          onChange={(action, done) => act(action, done, open.id)}
        />
      )}

      {!manage ? null : (
        <>
          <h2>{t('wp9.template.new')}</h2>
          <form className="case-form" aria-label={t('wp9.template.new')} noValidate onSubmit={(e) => void create(e)}>
            {!invalid ? null : (
              <p className="case-alert" role="alert">
                {t('wp9.template.invalid')}
              </p>
            )}
            <div className="case-field">
              <label htmlFor={`${id}-kind`}>{t('wp9.template.kind')}</label>
              <select
                id={`${id}-kind`}
                value={form.kind}
                onChange={(e) => {
                  setForm({ ...form, kind: e.target.value });
                }}
              >
                <option value="">{t('wp9.choose')}</option>
                {kinds.map((kind) => (
                  <option key={kind.id} value={kind.code}>
                    {valueLabel(kind, locale)}
                  </option>
                ))}
              </select>
            </div>
            <div className="case-field">
              <label htmlFor={`${id}-title`}>{t('wp9.template.titleField')}</label>
              <input
                id={`${id}-title`}
                type="text"
                maxLength={200}
                value={form.title}
                onChange={(e) => {
                  setForm({ ...form, title: e.target.value });
                }}
              />
            </div>
            <div className="case-field">
              <label htmlFor={`${id}-description`}>{t('wp9.template.description')}</label>
              <input
                id={`${id}-description`}
                type="text"
                maxLength={1000}
                value={form.description}
                onChange={(e) => {
                  setForm({ ...form, description: e.target.value });
                }}
              />
            </div>
            <div className="case-field">
              <label htmlFor={`${id}-body`}>{t('wp9.template.body')}</label>
              <textarea
                id={`${id}-body`}
                rows={8}
                dir="auto"
                maxLength={100000}
                aria-describedby={`${id}-body-hint`}
                value={form.body}
                onChange={(e) => {
                  setForm({ ...form, body: e.target.value });
                }}
              />
              <small id={`${id}-body-hint`}>{t('wp9.template.bodyHint')}</small>
            </div>
            <div className="case-actions">
              <button type="submit" className="primary">
                {t('wp9.template.create')}
              </button>
            </div>
          </form>
          <h2>{t('wp9.template.fields')}</h2>
          <ul className="wp9-docs">
            {fields.map((field) => (
              <li key={field.key}>
                <code dir="ltr">{`{{${field.key}}}`}</code> — {locale === 'ar' ? field.labelAr : field.labelEn}
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  );
}

interface DetailProps {
  template: Template;
  kinds: readonly ListValue[];
  manage: boolean;
  onClose: () => void;
  onChange: (action: () => Promise<unknown>, done: MessageKey) => Promise<void>;
}

function TemplateDetail({ template, kinds, manage, onClose, onChange }: DetailProps) {
  const { t, locale } = useI18n();
  const id = useId();
  const [body, setBody] = useState('');
  const [note, setNote] = useState('');
  return (
    <section aria-labelledby={`${id}-h`} className="wp3-section">
      <h2 id={`${id}-h`}>{template.title}</h2>
      <p>
        {labelOf(kinds, template.kind, locale)}
        {template.description === null ? null : ` — ${template.description}`}
      </p>
      <p>
        <button type="button" onClick={onClose}>
          {t('wp9.template.close')}
        </button>{' '}
        {!manage ? null : (
          <button
            type="button"
            onClick={() => void onChange(() => setTemplateActive(template, !template.active), 'wp9.template.saved')}
          >
            {t(template.active ? 'wp9.template.deactivate' : 'wp9.template.activate')}
          </button>
        )}
      </p>
      {template.versions.length === 0 ? <p>{t('wp9.template.noVersions')}</p> : null}
      <ul className="wp3-list">
        {template.versions.map((version) => (
          <VersionRow key={version.id} version={version} manage={manage} onChange={onChange} />
        ))}
      </ul>
      {!manage ? null : (
        <form
          className="case-form"
          aria-label={t('wp9.template.newVersion')}
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            if (body.trim() === '') return;
            void onChange(() => addTemplateVersion(template.id, body, note), 'wp9.template.versionAdded').then(() => {
              setBody('');
              setNote('');
            });
          }}
        >
          <div className="case-field">
            <label htmlFor={`${id}-body`}>{t('wp9.template.body')}</label>
            <textarea
              id={`${id}-body`}
              rows={6}
              dir="auto"
              maxLength={100000}
              value={body}
              onChange={(e) => {
                setBody(e.target.value);
              }}
            />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-note`}>{t('wp9.template.changeNote')}</label>
            <input
              id={`${id}-note`}
              type="text"
              maxLength={1000}
              value={note}
              onChange={(e) => {
                setNote(e.target.value);
              }}
            />
          </div>
          <div className="case-actions">
            <button type="submit">{t('wp9.template.addVersion')}</button>
          </div>
        </form>
      )}
    </section>
  );
}

interface VersionProps {
  version: TemplateVersion;
  manage: boolean;
  onChange: (action: () => Promise<unknown>, done: MessageKey) => Promise<void>;
}

function VersionRow({ version, manage, onChange }: VersionProps) {
  const { t, locale } = useI18n();
  const id = useId();
  const [editing, setEditing] = useState(false);
  const [body, setBody] = useState(version.body ?? '');
  const unknown = version.placeholders.filter((p) => !p.known);
  return (
    <li aria-label={t('wp9.template.versionLabel', { no: String(version.versionNo) })}>
      <p>
        <strong>{t('wp9.template.versionLabel', { no: String(version.versionNo) })}</strong>
        <span className="wp3-badge" data-status={version.status}>
          {t(`wp9.template.vstatus.${version.status}`)}
        </span>
        {version.changeNote === null ? null : ` — ${version.changeNote}`}
      </p>
      {version.body === null ? null : editing ? (
        <div className="case-field">
          <label htmlFor={`${id}-edit`}>{t('wp9.template.body')}</label>
          <textarea
            id={`${id}-edit`}
            rows={6}
            dir="auto"
            value={body}
            onChange={(e) => {
              setBody(e.target.value);
            }}
          />
          <button
            type="button"
            onClick={() =>
              void onChange(() => editTemplateVersion(version, body, version.changeNote ?? ''), 'wp9.template.saved').then(() => {
                setEditing(false);
              })
            }
          >
            {t('wp9.template.saveVersion')}
          </button>
        </div>
      ) : (
        <pre className="wp9-draft" dir="auto">
          {version.body}
        </pre>
      )}
      {version.placeholders.length === 0 ? null : (
        <p className="case-field-hint">
          {t('wp9.template.uses')}{' '}
          {version.placeholders
            .map((p) => (p.known ? (locale === 'ar' ? p.labelAr : p.labelEn) : t('wp9.template.unknownField', { key: p.key })))
            .join('، ')}
        </p>
      )}
      {unknown.length === 0 ? null : (
        <p className="case-alert" role="alert">
          {t('wp9.template.unknownWarning')}
        </p>
      )}
      {!manage ? null : (
        <p>
          {version.status === 'DRAFT' && !editing ? (
            <button
              type="button"
              onClick={() => {
                setEditing(true);
              }}
            >
              {t('wp9.template.editVersion', { no: String(version.versionNo) })}
            </button>
          ) : null}{' '}
          {version.status === 'DRAFT' ? (
            <button type="button" onClick={() => void onChange(() => stepTemplateVersion(version, 'review'), 'wp9.template.reviewed')}>
              {t('wp9.template.review', { no: String(version.versionNo) })}
            </button>
          ) : null}
          {version.status === 'REVIEWED' ? (
            <button type="button" onClick={() => void onChange(() => stepTemplateVersion(version, 'approve'), 'wp9.template.approved')}>
              {t('wp9.template.approve', { no: String(version.versionNo) })}
            </button>
          ) : null}
        </p>
      )}
    </li>
  );
}
