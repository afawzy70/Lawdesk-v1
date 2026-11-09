import { type SyntheticEvent, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AdminTabs } from '../admin/AdminTabs';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { usePermissions } from '../archive/usePermissions';
import { useLoad } from '../pleadings/useLoad';
import { getArchiveSettings, saveArchiveSettings, type ArchiveLimits, type ArchiveSettings } from './api';

const LIMIT_FIELDS: readonly { key: keyof ArchiveLimits; label: MessageKey }[] = [
  { key: 'maxFileBytes', label: 'gap.archive.maxFileBytes' },
  { key: 'maxDocumentsPerCase', label: 'gap.archive.maxDocumentsPerCase' },
  { key: 'maxVersionsPerDocument', label: 'gap.archive.maxVersionsPerDocument' },
  { key: 'maxOcrPages', label: 'gap.archive.maxOcrPages' },
  { key: 'maxTextChars', label: 'gap.archive.maxTextChars' },
];

/** The archive's limits and accepted formats (requirements section 13); changing them needs the archive administration right. */
export function ArchiveSettingsPage() {
  const { t } = useI18n();
  usePageTitle(t('gap.archive.title'));
  const load = useLoad((signal) => getArchiveSettings(signal), []);
  if (load.status === 403) return <AccessDeniedPage />;
  if (load.data === null) return <p>{load.status === null ? t('gap.loading') : t('gap.failed')}</p>;
  return <SettingsForm settings={load.data} />;
}

function SettingsForm({ settings }: { settings: ArchiveSettings }) {
  const { t } = useI18n();
  const admin = usePermissions().has('DOCUMENT_ARCHIVE_ADMIN');
  const [limits, setLimits] = useState<Record<keyof ArchiveLimits, string>>({
    maxFileBytes: String(settings.limits.maxFileBytes),
    maxDocumentsPerCase: String(settings.limits.maxDocumentsPerCase),
    maxVersionsPerDocument: String(settings.limits.maxVersionsPerDocument),
    maxOcrPages: String(settings.limits.maxOcrPages),
    maxTextChars: String(settings.limits.maxTextChars),
  });
  const [enabled, setEnabled] = useState(settings.formats.filter((f) => f.enabled).map((f) => f.code));
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);

  const submit = (event: SyntheticEvent) => {
    event.preventDefault();
    const numbers = Object.fromEntries(Object.entries(limits).map(([k, v]) => [k, Number(v)]));
    saveArchiveSettings(numbers, enabled).then(
      () => { setMessage({ key: 'gap.saved', failed: false }); },
      (error: unknown) => { setMessage({ key: error instanceof ApiError && error.status === 400 ? 'gap.archive.range' : 'gap.failed', failed: true }); },
    );
  };

  return (
    <>
      <AdminTabs />
      <h1>{t('gap.archive.title')}</h1>
      <p>{settings.ocr.available ? t('gap.archive.ocrOn') : t('gap.archive.ocrOff')}</p>
      <form aria-label={t('gap.archive.title')} onSubmit={submit}>
        {LIMIT_FIELDS.map((field) => (
          <p key={field.key}>
            <label>
              {t(field.label)}{' '}
              <input
                type="number"
                min={1}
                value={limits[field.key]}
                disabled={!admin}
                onChange={(e) => { setLimits({ ...limits, [field.key]: e.target.value }); }}
              />
            </label>
          </p>
        ))}
        <fieldset disabled={!admin}>
          <legend>{t('gap.archive.formats')}</legend>
          {settings.formats.map((format) => (
            <label key={format.code}>
              <input
                type="checkbox"
                checked={enabled.includes(format.code)}
                onChange={(e) => { setEnabled(e.target.checked ? [...enabled, format.code] : enabled.filter((c) => c !== format.code)); }}
              />{' '}
              {format.code} ({format.extension}){' '}
            </label>
          ))}
        </fieldset>
        {admin ? <button type="submit">{t('gap.save')}</button> : <p>{t('gap.readOnly')}</p>}
      </form>
      {message === null ? null : <p role={message.failed ? 'alert' : 'status'}>{t(message.key)}</p>}
    </>
  );
}
