import { type SyntheticEvent, useId, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { useLoad } from '../pleadings/useLoad';
import { getReminderSetting, parseLeadDays, saveReminderSetting } from './api';
import { failureKey } from './errors';

/**
 * How many days before the due date the in-app reminder is raised. The document gives no lead times, so there is no
 * default: until a competent member sets them, no approaching-deadline reminder is created.
 */
export function RemindersPanel({ canApprove }: { canApprove: boolean }) {
  const { t } = useI18n();
  const id = useId();
  const setting = useLoad((signal) => getReminderSetting(signal), []);
  // What the user typed; until they type, the field shows the saved lead times.
  const [typed, setTyped] = useState<string | null>(null);
  const text = typed ?? (setting.data?.leadDays ?? []).join(', ');
  const [message, setMessage] = useState<{ text: MessageKey; failed: boolean } | null>(null);

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    const days = parseLeadDays(text);
    if (days === null) {
      setMessage({ text: 'wp4.reminders.invalid', failed: true });
      return;
    }
    try {
      await saveReminderSetting(days);
      setMessage({ text: 'wp4.reminders.saved', failed: false });
      setTyped(null);
      setting.reload();
    } catch (error) {
      setMessage({ text: failureKey(error), failed: true });
    }
  };

  return (
    <section aria-labelledby={`${id}-h`} className="wp3-section">
      <h2 id={`${id}-h`}>{t('wp4.reminders.title')}</h2>
      <p className="case-field-hint">{t('wp4.reminders.hint')}</p>
      {setting.data === null ? null : (
        <p>{setting.data.configured ? t('wp4.reminders.current', { days: (setting.data.leadDays ?? []).join(', ') || '—' }) : t('wp4.reminders.notConfigured')}</p>
      )}
      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>{t(message.text)}</p>
      )}
      {!canApprove ? null : (
        <form className="case-form" aria-label={t('wp4.reminders.title')} noValidate onSubmit={(e) => void submit(e)}>
          <div className="case-field">
            <label htmlFor={`${id}-days`}>{t('wp4.reminders.field')}</label>
            <input id={`${id}-days`} type="text" inputMode="numeric" value={text} onChange={(e) => { setTyped(e.target.value); }} />
            <p className="case-field-hint">{t('wp4.reminders.fieldHint')}</p>
          </div>
          <div className="case-actions">
            <button type="submit" className="primary">{t('wp4.save')}</button>
          </div>
        </form>
      )}
    </section>
  );
}
