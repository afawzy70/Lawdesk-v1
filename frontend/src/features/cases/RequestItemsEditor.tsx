import { useId, useState, type KeyboardEvent } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { MAX_REQUEST_ITEMS, SIDES, type CaseRequests, type Side } from './api';

interface Props {
  value: CaseRequests;
  onChange: (next: CaseRequests) => void;
}

const keyOf = (side: Side): 'plaintiff' | 'defendant' => (side === 'PLAINTIFF' ? 'plaintiff' : 'defendant');

/**
 * «الطلبات»: the request items of the plaintiff side and of the defendant side, each a short text. Enter inside the
 * input adds the item and never submits the surrounding form.
 */
export function RequestItemsEditor({ value, onChange }: Props) {
  return (
    <div className="intake-sides">
      {SIDES.map((side) => (
        <SideItems key={side} side={side} items={value[keyOf(side)]} onItems={(next) => { onChange({ ...value, [keyOf(side)]: next }); }} />
      ))}
    </div>
  );
}

function SideItems({ side, items, onItems }: { side: Side; items: string[]; onItems: (next: string[]) => void }) {
  const { t } = useI18n();
  const id = useId();
  const [text, setText] = useState('');
  const full = items.length >= MAX_REQUEST_ITEMS;
  const add = () => {
    const trimmed = text.trim();
    if (trimmed === '' || full) return;
    onItems([...items, trimmed]);
    setText('');
  };
  const onEnter = (event: KeyboardEvent) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      add();
    }
  };
  const sideLabel = t(`intake.side.${side}`);
  return (
    <section className="intake-side" aria-labelledby={`${id}-title`}>
      <h3 id={`${id}-title`}>{t('requests.sideTitle', { side: sideLabel })}</h3>
      <ol>
        {items.length === 0 ? <li className="case-field-hint">{t('requests.none')}</li> : null}
        {items.map((item, index) => (
          <li key={`${String(index)}-${item}`}>
            {item}{' '}
            <button
              type="button"
              aria-label={t('requests.remove', { side: sideLabel, text: item })}
              onClick={() => {
                onItems(items.filter((_, position) => position !== index));
              }}
            >
              {t('requests.removeShort')}
            </button>
          </li>
        ))}
      </ol>
      <div className="case-field">
        <label htmlFor={`${id}-text`}>{t('requests.newLabel', { side: sideLabel })}</label>
        <input id={`${id}-text`} type="text" maxLength={2000} value={text} onKeyDown={onEnter} onChange={(event) => { setText(event.target.value); }} />
      </div>
      <button type="button" disabled={full || text.trim() === ''} onClick={add}>
        {t('requests.add')}
      </button>
    </section>
  );
}
