import { useId, useState } from 'react';
import type { MessageKey } from '../../../i18n/catalog';
import { useI18n } from '../../../i18n/I18nContext';
import { formatNumber } from '../../../i18n/format';
import type { FinancialImpact } from '../api';

/** The financial-impact choice and amount (LD-045), shared by the intake and the edit screens. */
export interface FinancialValue {
  impact: FinancialImpact | '';
  amount: string;
}

interface Props {
  value: FinancialValue;
  onChange: (next: FinancialValue) => void;
  impactError?: MessageKey | undefined;
  amountError?: MessageKey | undefined;
  /** Edit screens may leave the impact as it is; the intake needs an explicit choice. */
  legendRequired?: boolean;
}

/**
 * Two exclusive choices. "Yes" shows the amount (KWD, up to three decimals). "No" hides it and leaves nothing behind;
 * choosing "No" while an amount is typed first asks, so a typed amount is never lost without the user knowing.
 */
export function FinancialImpactFields({ value, onChange, impactError, amountError, legendRequired = true }: Props) {
  const { locale, t } = useI18n();
  const id = useId();
  const [confirming, setConfirming] = useState(false);

  const choose = (impact: FinancialImpact) => {
    if (impact === 'NO' && value.impact === 'YES' && value.amount.trim() !== '') {
      setConfirming(true);
      return;
    }
    setConfirming(false);
    onChange({ impact, amount: impact === 'NO' ? '' : value.amount });
  };
  const shownAmount = Number(value.amount.replace(',', '.'));
  const amountText = Number.isFinite(shownAmount)
    ? formatNumber(shownAmount, locale, { minimumFractionDigits: 0, maximumFractionDigits: 3 })
    : value.amount;

  return (
    <fieldset className="case-fieldset" aria-describedby={impactError === undefined ? undefined : `${id}-impact-error`}>
      <legend>
        {t('intake.financial.legend')}
        {legendRequired ? ' *' : ''}
      </legend>
      {(['YES', 'NO'] as const).map((option) => (
        <label key={option} className="case-radio">
          <input
            type="radio"
            name={`${id}-impact`}
            value={option}
            checked={value.impact === option}
            onChange={() => {
              choose(option);
            }}
          />
          {t(`intake.financial.${option}`)}
        </label>
      ))}
      {impactError === undefined ? null : (
        <p id={`${id}-impact-error`} className="case-field-error">
          {t(impactError)}
        </p>
      )}
      {confirming ? (
        <div className="case-alert" role="alert">
          <p>{t('intake.financial.discard', { amount: amountText })}</p>
          <button
            type="button"
            onClick={() => {
              setConfirming(false);
              onChange({ impact: 'NO', amount: '' });
            }}
          >
            {t('intake.financial.discardConfirm')}
          </button>{' '}
          <button
            type="button"
            onClick={() => {
              setConfirming(false);
            }}
          >
            {t('intake.financial.discardKeep')}
          </button>
        </div>
      ) : null}
      {value.impact === 'YES' ? (
        <div className="case-field">
          <label htmlFor={`${id}-amount`}>{t('intake.financial.amount')} *</label>
          <input
            id={`${id}-amount`}
            type="text"
            inputMode="decimal"
            autoComplete="off"
            aria-required="true"
            aria-invalid={amountError === undefined ? undefined : true}
            aria-describedby={`${id}-amount-hint${amountError === undefined ? '' : ` ${id}-amount-error`}`}
            value={value.amount}
            onChange={(event) => {
              onChange({ impact: 'YES', amount: event.target.value });
            }}
          />
          <p id={`${id}-amount-hint`} className="case-field-hint">
            {t('intake.financial.amountHint')}
          </p>
          {amountError === undefined ? null : (
            <p id={`${id}-amount-error`} className="case-field-error">
              {t(amountError)}
            </p>
          )}
        </div>
      ) : null}
    </fieldset>
  );
}
