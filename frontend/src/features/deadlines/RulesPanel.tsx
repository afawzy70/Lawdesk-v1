import { type SyntheticEvent, useId, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { formatDateTime } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import { useLoad } from '../pleadings/useLoad';
import {
  EMPTY_RULE,
  WEEKDAYS,
  approveRule,
  createRule,
  listRules,
  retireRule,
  type DeadlineRule,
  type NewRule,
} from './api';
import { failureKey } from './errors';

interface Props {
  canManage: boolean;
  canApprove: boolean;
}

/**
 * The periods the system may compute from (decision D6). The list starts empty and nothing is suggested: every rule is
 * typed by a specialist with its legal source and approved by a competent member before it can be used.
 */
export function RulesPanel({ canManage, canApprove }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const rules = useLoad((signal) => listRules(signal), []);
  const [form, setForm] = useState<NewRule>(EMPTY_RULE);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);

  const attempt = async (action: () => Promise<unknown>, done: MessageKey) => {
    setMessage(null);
    try {
      await action();
      setMessage({ key: done, failed: false });
      rules.reload();
    } catch (error) {
      setMessage({ key: failureKey(error), failed: true });
    }
  };

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    const days = Number(form.periodDays);
    if (form.code.trim() === '' || form.label.trim() === '' || form.legalSource.trim() === '' || form.effectiveFrom === ''
      || !Number.isInteger(days) || days < 1) {
      setMessage({ key: 'wp3.error.fields', failed: true });
      return;
    }
    await attempt(async () => {
      await createRule(form);
      setForm(EMPTY_RULE);
    }, 'wp4.rules.created');
  };

  const toggleDay = (day: number) => {
    setForm({
      ...form,
      closedWeekdays: form.closedWeekdays.includes(day) ? form.closedWeekdays.filter((d) => d !== day) : [...form.closedWeekdays, day].sort(),
    });
  };

  return (
    <section aria-labelledby={`${id}-h`} className="wp3-section">
      <h2 id={`${id}-h`}>{t('wp4.rules.title')}</h2>
      <p className="case-field-hint">{t('wp4.rules.hint')}</p>
      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>{t(message.key)}</p>
      )}
      {rules.status !== null ? <p className="case-alert" role="alert">{t('wp4.failed')}</p> : null}
      {rules.data === null ? null : rules.data.length === 0 ? (
        <p>{t('wp4.rules.none')}</p>
      ) : (
        <ul className="wp3-list">
          {rules.data.map((rule) => (
            <RuleItem key={`${rule.id}-${String(rule.version)}`} rule={rule} canApprove={canApprove} locale={locale}
              onApprove={() => void attempt(() => approveRule(rule), 'wp4.rules.approved')}
              onRetire={() => void attempt(() => retireRule(rule), 'wp4.rules.retired')} />
          ))}
        </ul>
      )}
      {!canManage ? null : (
        <form className="case-form" aria-label={t('wp4.rules.new')} noValidate onSubmit={(e) => void submit(e)}>
          <h3>{t('wp4.rules.new')}</h3>
          <div className="case-field">
            <label htmlFor={`${id}-code`}>{t('wp4.rules.code')}</label>
            <input id={`${id}-code`} type="text" maxLength={60} value={form.code} onChange={(e) => { setForm({ ...form, code: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-label`}>{t('wp4.rules.label')}</label>
            <input id={`${id}-label`} type="text" maxLength={300} value={form.label} onChange={(e) => { setForm({ ...form, label: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-days`}>{t('wp4.rules.periodDays')}</label>
            <input id={`${id}-days`} type="number" min={1} max={3650} value={form.periodDays} onChange={(e) => { setForm({ ...form, periodDays: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-method`}>{t('wp4.rules.method')}</label>
            <select id={`${id}-method`} value={form.countMethod} onChange={(e) => { setForm({ ...form, countMethod: e.target.value === 'WORKING' ? 'WORKING' : 'CALENDAR' }); }}>
              <option value="CALENDAR">{t('wp4.method.CALENDAR')}</option>
              <option value="WORKING">{t('wp4.method.WORKING')}</option>
            </select>
          </div>
          <label>
            <input type="checkbox" checked={form.countsStartDay} onChange={(e) => { setForm({ ...form, countsStartDay: e.target.checked }); }} />{' '}
            {t('wp4.rules.countsStartDay')}
          </label>
          <label>
            <input type="checkbox" checked={form.rollForward} onChange={(e) => { setForm({ ...form, rollForward: e.target.checked }); }} />{' '}
            {t('wp4.rules.rollForward')}
          </label>
          <fieldset className="case-fieldset">
            <legend>{t('wp4.rules.closedWeekdays')}</legend>
            {WEEKDAYS.map((day) => (
              <label key={day}>
                <input type="checkbox" checked={form.closedWeekdays.includes(day)} onChange={() => { toggleDay(day); }} />{' '}
                {t(`wp4.weekday.${String(day) as '1'}`)}
              </label>
            ))}
          </fieldset>
          <div className="case-field">
            <label htmlFor={`${id}-source`}>{t('wp4.rules.legalSource')}</label>
            <textarea id={`${id}-source`} rows={2} maxLength={1000} value={form.legalSource} onChange={(e) => { setForm({ ...form, legalSource: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-from`}>{t('wp4.rules.effectiveFrom')}</label>
            <input id={`${id}-from`} type="date" value={form.effectiveFrom} onChange={(e) => { setForm({ ...form, effectiveFrom: e.target.value }); }} />
          </div>
          <div className="case-actions">
            <button type="submit" className="primary">{t('wp4.rules.save')}</button>
          </div>
        </form>
      )}
    </section>
  );
}

function RuleItem({ rule, canApprove, locale, onApprove, onRetire }: {
  rule: DeadlineRule;
  canApprove: boolean;
  locale: 'ar' | 'en';
  onApprove: () => void;
  onRetire: () => void;
}) {
  const { t } = useI18n();
  return (
    <li>
      <p>
        <strong>{rule.code}</strong> — {rule.label} <span className="wp3-badge" data-rule={rule.status}>{t(`wp4.setting.${rule.status}`)}</span>
      </p>
      <dl className="case-facts">
        <dt>{t('wp4.rules.periodDays')}</dt>
        <dd>{t('wp4.calc.methodText', { days: String(rule.periodDays), method: t(`wp4.method.${rule.countMethod}`) })}</dd>
        <dt>{t('wp4.calc.counting')}</dt>
        <dd>
          {rule.countsStartDay ? t('wp4.calc.startCounts') : t('wp4.calc.startNotCounted')}
          {' · '}
          {rule.closedWeekdays.length === 0
            ? t('wp4.calc.noClosedWeekdays')
            : t('wp4.calc.closedWeekdays', { days: rule.closedWeekdays.map((d) => t(`wp4.weekday.${String(d) as '1'}`)).join('، ') })}
          {rule.rollForward ? ` · ${t('wp4.calc.rolls')}` : ''}
        </dd>
        <dt>{t('wp4.rules.legalSource')}</dt>
        <dd>{rule.legalSource}</dd>
        <dt>{t('wp4.rules.effectiveFrom')}</dt>
        <dd>{formatDate(rule.effectiveFrom, locale)}</dd>
        {rule.approvedByName === null || rule.approvedAt === null ? null : (
          <>
            <dt>{t('wp4.approvedBy')}</dt>
            <dd>{rule.approvedByName} — {formatDateTime(rule.approvedAt, locale)}</dd>
          </>
        )}
      </dl>
      {!canApprove ? null : (
        <div className="case-actions">
          {rule.status === 'DRAFT' ? <button type="button" className="primary" onClick={onApprove}>{t('wp4.approve')}</button> : null}
          {rule.status === 'APPROVED' ? <button type="button" onClick={onRetire}>{t('wp4.retire')}</button> : null}
        </div>
      )}
    </li>
  );
}
