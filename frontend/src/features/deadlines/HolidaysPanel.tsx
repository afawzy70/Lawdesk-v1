import { type SyntheticEvent, useId, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { formatDateTime } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import { useLoad } from '../pleadings/useLoad';
import { approveHoliday, createHoliday, listHolidays, retireHoliday } from './api';
import { failureKey } from './errors';

interface Props {
  canManage: boolean;
  canApprove: boolean;
}

const EMPTY = { holidayOn: '', label: '', legalSource: '' };

/** The official holidays a working-day count or a roll-forward may skip: typed with their source, approved before use. */
export function HolidaysPanel({ canManage, canApprove }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const holidays = useLoad((signal) => listHolidays(signal), []);
  const [form, setForm] = useState(EMPTY);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);

  const attempt = async (action: () => Promise<unknown>, done: MessageKey) => {
    setMessage(null);
    try {
      await action();
      setMessage({ key: done, failed: false });
      holidays.reload();
    } catch (error) {
      setMessage({ key: failureKey(error), failed: true });
    }
  };

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    if (form.holidayOn === '' || form.label.trim() === '' || form.legalSource.trim() === '') {
      setMessage({ key: 'wp3.error.fields', failed: true });
      return;
    }
    await attempt(async () => {
      await createHoliday(form);
      setForm(EMPTY);
    }, 'wp4.holidays.created');
  };

  return (
    <section aria-labelledby={`${id}-h`} className="wp3-section">
      <h2 id={`${id}-h`}>{t('wp4.holidays.title')}</h2>
      <p className="case-field-hint">{t('wp4.holidays.hint')}</p>
      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>{t(message.key)}</p>
      )}
      {holidays.status !== null ? <p className="case-alert" role="alert">{t('wp4.failed')}</p> : null}
      {holidays.data === null ? null : holidays.data.length === 0 ? (
        <p>{t('wp4.holidays.none')}</p>
      ) : (
        <ul className="wp3-list">
          {holidays.data.map((holiday) => (
            <li key={`${holiday.id}-${String(holiday.version)}`}>
              <p>
                <strong>{formatDate(holiday.holidayOn, locale)}</strong> — {holiday.label}{' '}
                <span className="wp3-badge" data-rule={holiday.status}>{t(`wp4.setting.${holiday.status}`)}</span>
              </p>
              <p className="case-field-hint">
                {holiday.legalSource}
                {holiday.approvedByName === null || holiday.approvedAt === null
                  ? ''
                  : ` · ${t('wp4.approvedBy')}: ${holiday.approvedByName} — ${formatDateTime(holiday.approvedAt, locale)}`}
              </p>
              {!canApprove ? null : (
                <div className="case-actions">
                  {holiday.status === 'DRAFT' ? (
                    <button type="button" className="primary" onClick={() => void attempt(() => approveHoliday(holiday), 'wp4.holidays.approved')}>
                      {t('wp4.approve')}
                    </button>
                  ) : null}
                  {holiday.status === 'APPROVED' ? (
                    <button type="button" onClick={() => void attempt(() => retireHoliday(holiday), 'wp4.holidays.retired')}>{t('wp4.retire')}</button>
                  ) : null}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      {!canManage ? null : (
        <form className="case-form" aria-label={t('wp4.holidays.new')} noValidate onSubmit={(e) => void submit(e)}>
          <h3>{t('wp4.holidays.new')}</h3>
          <div className="case-field">
            <label htmlFor={`${id}-on`}>{t('wp4.holidays.date')}</label>
            <input id={`${id}-on`} type="date" value={form.holidayOn} onChange={(e) => { setForm({ ...form, holidayOn: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-label`}>{t('wp4.holidays.label')}</label>
            <input id={`${id}-label`} type="text" maxLength={200} value={form.label} onChange={(e) => { setForm({ ...form, label: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-source`}>{t('wp4.rules.legalSource')}</label>
            <textarea id={`${id}-source`} rows={2} maxLength={1000} value={form.legalSource} onChange={(e) => { setForm({ ...form, legalSource: e.target.value }); }} />
          </div>
          <div className="case-actions">
            <button type="submit" className="primary">{t('wp4.rules.save')}</button>
          </div>
        </form>
      )}
    </section>
  );
}
