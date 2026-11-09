import { type SyntheticEvent, useCallback, useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { formatDateTime, formatNumber } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { formatDate } from '../cases/format';
import {
  assess,
  listAssessments,
  REMEDIATION_STATUSES,
  RISK_ELEMENTS,
  RISK_LEVELS,
  RISK_RATINGS,
  RISK_TRIGGERS,
  setRemediationStatus,
  type NewAssessment,
  type Remediation,
  type RemediationStatus,
  type RiskAssessment,
  type RiskElementCode,
  type RiskLevel,
  type RiskRating,
  type RiskTrigger,
} from './api';
import { failureKey, isForbidden } from './errors';

function blank(): NewAssessment {
  const elements = {} as NewAssessment['elements'];
  for (const code of RISK_ELEMENTS) elements[code] = { rating: 'NOT_ASSESSED', reason: '' };
  return {
    level: 'MEDIUM',
    levelReasons: '',
    potentialLoss: '',
    trigger: 'MANUAL',
    triggerNote: '',
    elements,
    plan: { cause: '', action: '', unitToAddress: '', targetDate: '' },
  };
}

interface Props {
  caseId: string;
  /** Holds RISK_MANAGE and the case is not archived. */
  editable: boolean;
}

/** Section 19: the risk assessment of the case. A change is a new version; earlier versions stay as they were. */
export function RiskSection({ caseId, editable }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [versions, setVersions] = useState<RiskAssessment[] | null>(null);
  const [hidden, setHidden] = useState(false);
  const [form, setForm] = useState<NewAssessment>(blank);
  const [message, setMessage] = useState<{ text: MessageKey; failed: boolean } | null>(null);
  const [generation, setGeneration] = useState(0);
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    listAssessments(caseId, controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) setVersions(found);
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        if (isForbidden(error)) setHidden(true);
        else setMessage({ text: 'wp3.risk.failed', failed: true });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, generation]);

  if (hidden) return null;

  const high = form.level === 'HIGH' || form.level === 'VERY_HIGH';
  const amount = (value: string) =>
    formatNumber(Number(value), locale, { minimumFractionDigits: 3, maximumFractionDigits: 3 });

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setMessage(null);
    if (form.levelReasons.trim() === '' || (high && (form.plan.cause.trim() === '' || form.plan.action.trim() === ''))) {
      setMessage({ text: 'wp3.risk.invalid', failed: true });
      return;
    }
    try {
      await assess(caseId, { ...form, levelReasons: form.levelReasons.trim() });
      setForm(blank());
      setMessage({ text: 'wp3.risk.saved', failed: false });
      reload();
    } catch (error) {
      setMessage({ text: failureKey(error), failed: true });
    }
  };

  const step = async (item: Remediation, status: RemediationStatus) => {
    setMessage(null);
    try {
      await setRemediationStatus(item, status);
      setMessage({ text: 'wp3.risk.stepUpdated', failed: false });
      reload();
    } catch (error) {
      setMessage({ text: failureKey(error), failed: true });
    }
  };

  const setElement = (code: RiskElementCode, change: Partial<{ rating: RiskRating; reason: string }>) => {
    setForm((current) => ({ ...current, elements: { ...current.elements, [code]: { ...current.elements[code], ...change } } }));
  };

  const latest = versions?.[0];
  return (
    <section aria-labelledby={`${id}-h`} className="wp3-section">
      <h2 id={`${id}-h`}>{t('wp3.risk.title')}</h2>
      <p className="case-notice wp3-disclaimer">{t('wp3.risk.disclaimer')}</p>
      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>
          {t(message.text)}
        </p>
      )}
      {versions === null ? null : latest === undefined ? (
        <p>{t('wp3.risk.none')}</p>
      ) : (
        <>
          <p>
            <span className="wp3-badge" data-level={latest.level}>{t(`wp3.risk.level.${latest.level}`)}</span>{' '}
            {t('wp3.risk.current', { version: String(latest.versionNo) })}
            {latest.potentialLoss === null ? null : ` · ${t('wp3.risk.loss', { amount: amount(latest.potentialLoss) })}`}
          </p>
          <h3>{t('wp3.risk.history')}</h3>
          <ol className="wp3-list" reversed>
            {versions.map((v) => (
              <li key={v.id}>
                <details>
                  <summary>
                    {t('wp3.risk.version', { version: String(v.versionNo) })} — {t(`wp3.risk.level.${v.level}`)} —{' '}
                    {formatDateTime(v.assessedAt, locale)}
                    {v.assessedByName === null ? null : ` — ${v.assessedByName}`}
                  </summary>
                  <p>{v.levelReasons}</p>
                  <p className="case-field-hint">
                    {t(`wp3.risk.trigger.${v.trigger}`)}
                    {v.triggerNote === null ? null : ` — ${v.triggerNote}`}
                    {v.potentialLoss === null ? null : ` · ${t('wp3.risk.loss', { amount: amount(v.potentialLoss) })}`}
                  </p>
                  <table className="wp3-table">
                    <caption className="visually-hidden">{t('wp3.risk.elements')}</caption>
                    <thead>
                      <tr><th scope="col">{t('wp3.risk.element')}</th><th scope="col">{t('wp3.risk.rating')}</th><th scope="col">{t('wp3.risk.reason')}</th></tr>
                    </thead>
                    <tbody>
                      {v.elements.map((e) => (
                        <tr key={e.code}>
                          <th scope="row">{t(`wp3.risk.element.${e.code}`)}</th>
                          <td>{t(`wp3.risk.rating.${e.rating}`)}</td>
                          <td>{e.reason ?? '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {v.remediation.length === 0 ? null : (
                    <>
                      <h4>{t('wp3.risk.plan')}</h4>
                      <ul>
                        {v.remediation.map((r) => (
                          <li key={r.id}>
                            <strong>{r.action}</strong> — {r.cause}
                            {r.unitToAddress === null ? null : ` — ${r.unitToAddress}`}
                            {r.targetDate === null ? null : ` — ${t('wp3.risk.target', { date: formatDate(r.targetDate, locale) })}`}{' '}
                            {!editable ? (
                              <span className="wp3-badge">{t(`wp3.risk.step.${r.status}`)}</span>
                            ) : (
                              <select
                                aria-label={t('wp3.risk.stepStatus', { action: r.action })}
                                value={r.status}
                                onChange={(e) => void step(r, e.target.value as RemediationStatus)}
                              >
                                {REMEDIATION_STATUSES.map((status) => (
                                  <option key={status} value={status}>{t(`wp3.risk.step.${status}`)}</option>
                                ))}
                              </select>
                            )}
                          </li>
                        ))}
                      </ul>
                    </>
                  )}
                </details>
              </li>
            ))}
          </ol>
        </>
      )}
      {!editable ? null : (
        <form className="case-form" aria-label={t('wp3.risk.new')} noValidate onSubmit={(e) => void submit(e)}>
          <h3>{t('wp3.risk.new')}</h3>
          <fieldset className="case-fieldset">
            <legend>{t('wp3.risk.elements')}</legend>
            {RISK_ELEMENTS.map((code) => (
              <div className="case-field" key={code}>
                <label htmlFor={`${id}-r-${code}`}>{t(`wp3.risk.element.${code}`)}</label>
                <select id={`${id}-r-${code}`} value={form.elements[code].rating} onChange={(e) => { setElement(code, { rating: e.target.value as RiskRating }); }}>
                  {RISK_RATINGS.map((rating) => (
                    <option key={rating} value={rating}>{t(`wp3.risk.rating.${rating}`)}</option>
                  ))}
                </select>
                <input
                  type="text"
                  maxLength={2000}
                  aria-label={t('wp3.risk.reasonOf', { element: t(`wp3.risk.element.${code}`) })}
                  value={form.elements[code].reason}
                  onChange={(e) => { setElement(code, { reason: e.target.value }); }}
                />
              </div>
            ))}
          </fieldset>
          <div className="case-field">
            <label htmlFor={`${id}-level`}>{t('wp3.risk.overall')}</label>
            <select id={`${id}-level`} value={form.level} onChange={(e) => { setForm({ ...form, level: e.target.value as RiskLevel }); }}>
              {RISK_LEVELS.map((level) => (
                <option key={level} value={level}>{t(`wp3.risk.level.${level}`)}</option>
              ))}
            </select>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-why`}>{t('wp3.risk.levelReasons')}</label>
            <textarea id={`${id}-why`} rows={3} maxLength={4000} value={form.levelReasons} onChange={(e) => { setForm({ ...form, levelReasons: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-loss`}>{t('wp3.risk.lossField')}</label>
            <input id={`${id}-loss`} type="text" inputMode="decimal" dir="ltr" value={form.potentialLoss} onChange={(e) => { setForm({ ...form, potentialLoss: e.target.value }); }} />
            <span className="case-field-hint">{t('wp3.risk.lossHint')}</span>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-trigger`}>{t('wp3.risk.triggerField')}</label>
            <select id={`${id}-trigger`} value={form.trigger} onChange={(e) => { setForm({ ...form, trigger: e.target.value as RiskTrigger }); }}>
              {RISK_TRIGGERS.map((trigger) => (
                <option key={trigger} value={trigger}>{t(`wp3.risk.trigger.${trigger}`)}</option>
              ))}
            </select>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-tn`}>{t('wp3.risk.triggerNote')}</label>
            <input id={`${id}-tn`} type="text" maxLength={500} value={form.triggerNote} onChange={(e) => { setForm({ ...form, triggerNote: e.target.value }); }} />
          </div>
          {!high ? null : (
            <fieldset className="case-fieldset">
              <legend>{t('wp3.risk.planRequired')}</legend>
              <div className="case-field">
                <label htmlFor={`${id}-cause`}>{t('wp3.risk.cause')}</label>
                <input id={`${id}-cause`} type="text" maxLength={1000} value={form.plan.cause} onChange={(e) => { setForm({ ...form, plan: { ...form.plan, cause: e.target.value } }); }} />
              </div>
              <div className="case-field">
                <label htmlFor={`${id}-action`}>{t('wp3.risk.action')}</label>
                <input id={`${id}-action`} type="text" maxLength={1000} value={form.plan.action} onChange={(e) => { setForm({ ...form, plan: { ...form.plan, action: e.target.value } }); }} />
              </div>
              <div className="case-field">
                <label htmlFor={`${id}-unit`}>{t('wp3.risk.unit')}</label>
                <input id={`${id}-unit`} type="text" maxLength={300} value={form.plan.unitToAddress} onChange={(e) => { setForm({ ...form, plan: { ...form.plan, unitToAddress: e.target.value } }); }} />
              </div>
              <div className="case-field">
                <label htmlFor={`${id}-target`}>{t('wp3.risk.targetDate')}</label>
                <input id={`${id}-target`} type="date" value={form.plan.targetDate} onChange={(e) => { setForm({ ...form, plan: { ...form.plan, targetDate: e.target.value } }); }} />
              </div>
            </fieldset>
          )}
          <div className="case-actions">
            <button type="submit" className="primary">{t('wp3.risk.save')}</button>
          </div>
        </form>
      )}
    </section>
  );
}
