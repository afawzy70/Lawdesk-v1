import { type SyntheticEvent, useId, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import type { CaseParty } from '../cases/api';
import { valueLabel, type ListValue, type SettlementInput } from './api';

interface Props {
  initial: SettlementInput;
  kinds: readonly ListValue[];
  parties: readonly CaseParty[];
  submitLabel: string;
  onSubmit: (input: SettlementInput) => Promise<void>;
  onCancel?: () => void;
}

/** The terms of a settlement (section 23): kind, who offered, the settlement's own amount in KWD, grounds, impact, opinion, authority. */
export function SettlementForm({ initial, kinds, parties, submitLabel, onSubmit, onCancel }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [form, setForm] = useState<SettlementInput>(initial);
  const [invalid, setInvalid] = useState(false);
  const set = <K extends keyof SettlementInput>(field: K, value: SettlementInput[K]) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    // The amount is dinars with at most three decimals; the server checks it again.
    const amountOk = form.settlementAmount.trim() === '' || /^\d{1,12}(\.\d{1,3})?$/.test(form.settlementAmount.trim());
    if (form.kind === '' || !amountOk) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    await onSubmit({ ...form, settlementAmount: form.settlementAmount.trim() });
  };

  const text = (field: 'legalGrounds' | 'financialImpactNote' | 'legalOpinion', label: string) => (
    <div className="case-field">
      <label htmlFor={`${id}-${field}`}>{label}</label>
      <textarea
        id={`${id}-${field}`}
        rows={3}
        maxLength={20000}
        value={form[field]}
        onChange={(e) => {
          set(field, e.target.value);
        }}
      />
    </div>
  );

  return (
    <form className="case-form" aria-label={submitLabel} noValidate onSubmit={(e) => void submit(e)}>
      {!invalid ? null : (
        <p className="case-alert" role="alert">
          {t('wp9.settlement.invalid')}
        </p>
      )}
      <div className="case-field">
        <label htmlFor={`${id}-kind`}>{t('wp9.settlement.kind')}</label>
        <select
          id={`${id}-kind`}
          value={form.kind}
          onChange={(e) => {
            set('kind', e.target.value);
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
        <label htmlFor={`${id}-party`}>{t('wp9.settlement.party')}</label>
        <select
          id={`${id}-party`}
          value={form.offeringPartyId}
          onChange={(e) => {
            set('offeringPartyId', e.target.value);
          }}
        >
          <option value="">{t('wp9.settlement.partyNone')}</option>
          {parties.map((party) => (
            <option key={party.partyId} value={party.partyId}>
              {party.name}
            </option>
          ))}
        </select>
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-partyText`}>{t('wp9.settlement.partyText')}</label>
        <input
          id={`${id}-partyText`}
          type="text"
          maxLength={300}
          value={form.offeringPartyText}
          onChange={(e) => {
            set('offeringPartyText', e.target.value);
          }}
        />
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-amount`}>{t('wp9.settlement.amount')}</label>
        <input
          id={`${id}-amount`}
          type="text"
          inputMode="decimal"
          dir="ltr"
          aria-describedby={`${id}-amount-hint`}
          value={form.settlementAmount}
          onChange={(e) => {
            set('settlementAmount', e.target.value);
          }}
        />
        <small id={`${id}-amount-hint`}>{t('wp9.settlement.amountHint')}</small>
      </div>
      {text('legalGrounds', t('wp9.settlement.grounds'))}
      {text('financialImpactNote', t('wp9.settlement.impact'))}
      {text('legalOpinion', t('wp9.settlement.opinion'))}
      <div className="case-field">
        <label htmlFor={`${id}-authority`}>{t('wp9.settlement.authority')}</label>
        <input
          id={`${id}-authority`}
          type="text"
          maxLength={300}
          value={form.approvingAuthority}
          onChange={(e) => {
            set('approvingAuthority', e.target.value);
          }}
        />
      </div>
      <div className="case-actions">
        <button type="submit" className="primary">
          {submitLabel}
        </button>
        {onCancel === undefined ? null : (
          <button type="button" onClick={onCancel}>
            {t('wp9.cancel')}
          </button>
        )}
      </div>
    </form>
  );
}
