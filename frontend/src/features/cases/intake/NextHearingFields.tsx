import { useId } from 'react';
import type { MessageKey } from '../../../i18n/catalog';
import { useI18n } from '../../../i18n/I18nContext';
import type { NextHearingValue } from './nextHearing';

/** The optional date of the next hearing, given when a case is registered (section 15). The date alone is enough. */
export function NextHearingFields({
  value,
  onChange,
  dateError,
}: {
  value: NextHearingValue;
  onChange: (next: NextHearingValue) => void;
  dateError?: MessageKey | undefined;
}) {
  const { t } = useI18n();
  const id = useId();
  const set = (field: keyof NextHearingValue) => (text: string) => {
    onChange({ ...value, [field]: text });
  };
  return (
    <fieldset className="case-fieldset">
      <legend>{t('intake.section.nextHearing')}</legend>
      <p className="case-field-hint">{t('intake.nextHearing.hint')}</p>
      <div className="case-field">
        <label htmlFor={`${id}-date`}>{t('intake.nextHearing.date')}</label>
        <input
          id={`${id}-date`}
          type="date"
          value={value.date}
          aria-invalid={dateError === undefined ? undefined : true}
          aria-describedby={dateError === undefined ? undefined : `${id}-date-error`}
          onChange={(e) => { set('date')(e.target.value); }}
        />
        {dateError === undefined ? null : <p id={`${id}-date-error`} className="case-field-error">{t(dateError)}</p>}
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-time`}>{t('intake.nextHearing.time')}</label>
        <input id={`${id}-time`} type="time" value={value.time} onChange={(e) => { set('time')(e.target.value); }} />
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-notes`}>{t('intake.nextHearing.notes')}</label>
        <textarea id={`${id}-notes`} rows={2} maxLength={2000} value={value.notes} onChange={(e) => { set('notes')(e.target.value); }} />
      </div>
    </fieldset>
  );
}
