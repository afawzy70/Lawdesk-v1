import { useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../../i18n/catalog';
import { useI18n } from '../../../i18n/I18nContext';
import { listOrgUnits, type OrgUnit } from '../../access/api';
import { arrange } from '../../access/orgTree';
import { type DetailsInput, IMPORTANCE_LEVELS } from '../api';

interface Props {
  value: DetailsInput;
  onChange: (next: DetailsInput) => void;
  /** The key of the message under the claimed amount when it is malformed. */
  amountError?: MessageKey | undefined;
}

/**
 * The case record beyond its numbers (requirements section 1): chamber, when the legal department received the case,
 * who referred it, the facts, the amount claimed, the importance and internal notes. All of it is optional; what is left
 * empty is stored as "not specified", never filled in.
 */
export function CaseDetailsFields({ value, onChange, amountError }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [units, setUnits] = useState<OrgUnit[]>([]);

  useEffect(() => {
    let current = true;
    listOrgUnits('AUTHORITY', false).then(
      (found) => {
        if (current) setUnits(found);
      },
      () => {
        // Without the structure the referrer can still be typed.
      },
    );
    return () => {
      current = false;
    };
  }, []);

  const set = (field: keyof DetailsInput) => (next: string) => {
    onChange({ ...value, [field]: next });
  };
  const name = (unit: OrgUnit) => (locale === 'en' && unit.nameEn !== null ? unit.nameEn : unit.nameAr);

  return (
    <fieldset className="case-details-fields">
      <legend>{t('details.legend')}</legend>
      <div className="case-field">
        <label htmlFor={`${id}-chamber`}>{t('details.chamber')}</label>
        <input id={`${id}-chamber`} type="text" maxLength={100} value={value.chamber} onChange={(e) => { set('chamber')(e.target.value); }} />
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-received`}>{t('details.receivedOn')}</label>
        <input id={`${id}-received`} type="date" value={value.receivedOn} onChange={(e) => { set('receivedOn')(e.target.value); }} />
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-unit`}>{t('details.referringUnit')}</label>
        <select id={`${id}-unit`} value={value.referringUnitId} onChange={(e) => { set('referringUnitId')(e.target.value); }}>
          <option value="">{t('details.notSpecified')}</option>
          {arrange(units).map(({ unit }) => (
            <option key={unit.id} value={unit.id}>
              {name(unit)}
            </option>
          ))}
        </select>
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-by`}>{t('details.referredBy')}</label>
        <input id={`${id}-by`} type="text" maxLength={200} value={value.referredBy} onChange={(e) => { set('referredBy')(e.target.value); }} />
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-facts`}>{t('details.facts')}</label>
        <textarea id={`${id}-facts`} rows={5} maxLength={8000} value={value.facts} onChange={(e) => { set('facts')(e.target.value); }} />
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-claimed`}>{t('details.claimedAmount')}</label>
        <input
          id={`${id}-claimed`}
          type="text"
          inputMode="decimal"
          autoComplete="off"
          value={value.claimedAmount}
          aria-invalid={amountError === undefined ? undefined : true}
          aria-describedby={`${id}-claimed-hint`}
          onChange={(e) => { set('claimedAmount')(e.target.value); }}
        />
        <p id={`${id}-claimed-hint`} className="case-field-hint">
          {t('details.claimedAmountHint')}
        </p>
        {amountError === undefined ? null : <p className="case-field-error">{t(amountError)}</p>}
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-importance`}>{t('details.importance')}</label>
        <select id={`${id}-importance`} value={value.importance} onChange={(e) => { set('importance')(e.target.value); }}>
          <option value="">{t('details.notSpecified')}</option>
          {IMPORTANCE_LEVELS.map((level) => (
            <option key={level} value={level}>
              {t(`details.importance.${level}`)}
            </option>
          ))}
        </select>
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-notes`}>{t('details.internalNotes')}</label>
        <textarea id={`${id}-notes`} rows={3} maxLength={4000} value={value.internalNotes} onChange={(e) => { set('internalNotes')(e.target.value); }} />
      </div>
    </fieldset>
  );
}
