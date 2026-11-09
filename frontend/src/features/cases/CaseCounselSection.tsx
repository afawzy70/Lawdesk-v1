import { type SyntheticEvent, useCallback, useEffect, useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { addCounsel, type CaseParty, type Counsel, getCaseParties, listCounsel, removeCounsel } from './api';

const EMPTY = { kind: 'LAWYER' as 'LAWYER' | 'AGENT', name: '', phone: '', email: '', notes: '' };

/** Section 2: the lawyer or agent of each party on this case. */
export function CaseCounselSection({ caseId, editable, generation }: { caseId: string; editable: boolean; generation: number }) {
  const { t } = useI18n();
  const id = useId();
  const [items, setItems] = useState<Counsel[] | null>(null);
  const [parties, setParties] = useState<CaseParty[]>([]);
  const [partyId, setPartyId] = useState('');
  const [form, setForm] = useState(EMPTY);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const [round, setRound] = useState(0);
  const reload = useCallback(() => {
    setRound((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([listCounsel(caseId, controller.signal), getCaseParties(caseId, controller.signal)]).then(
      ([found, list]) => {
        if (controller.signal.aborted) return;
        setItems(found);
        setParties(list);
      },
      () => {
        if (!controller.signal.aborted) setMessage({ key: 'counsel.failed', failed: true });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, generation, round]);

  const nameOf = (party: string) => parties.find((candidate) => candidate.partyId === party)?.name ?? '';

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setMessage(null);
    if (partyId === '' || form.name.trim() === '') {
      setMessage({ key: 'counsel.invalid', failed: true });
      return;
    }
    try {
      await addCounsel(caseId, partyId, form);
      setForm(EMPTY);
      setMessage({ key: 'counsel.added', failed: false });
      reload();
    } catch (error) {
      setMessage({ key: error instanceof ApiError && error.status === 400 ? 'counsel.invalid' : 'counsel.failed', failed: true });
    }
  };

  const remove = async (item: Counsel) => {
    setMessage(null);
    try {
      await removeCounsel(caseId, item.id);
      setMessage({ key: 'counsel.removed', failed: false });
      reload();
    } catch {
      setMessage({ key: 'counsel.failed', failed: true });
    }
  };

  return (
    <section aria-labelledby={`${id}-h`} className="case-counsel">
      <h2 id={`${id}-h`}>{t('counsel.title')}</h2>
      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>
          {t(message.key)}
        </p>
      )}
      {items === null ? null : items.length === 0 ? (
        <p>{t('counsel.none')}</p>
      ) : (
        <ul>
          {items.map((item) => (
            <li key={item.id}>
              <strong>{item.name}</strong> — {t(`counsel.kind.${item.kind}`)} — {nameOf(item.partyId)}
              {item.phone === null ? null : <> — {item.phone}</>}
              {editable ? (
                <>
                  {' '}
                  <button type="button" onClick={() => void remove(item)}>
                    {t('counsel.remove')}
                  </button>
                </>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {!editable ? null : (
        <form className="case-form" aria-label={t('counsel.add')} noValidate onSubmit={(e) => void submit(e)}>
          <div className="case-field">
            <label htmlFor={`${id}-party`}>{t('counsel.party')}</label>
            <select id={`${id}-party`} value={partyId} onChange={(e) => { setPartyId(e.target.value); }}>
              <option value="" />
              {parties.map((party) => (
                <option key={party.partyId} value={party.partyId}>
                  {party.name}
                </option>
              ))}
            </select>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-kind`}>{t('counsel.kind')}</label>
            <select id={`${id}-kind`} value={form.kind} onChange={(e) => { setForm({ ...form, kind: e.target.value as 'LAWYER' | 'AGENT' }); }}>
              <option value="LAWYER">{t('counsel.kind.LAWYER')}</option>
              <option value="AGENT">{t('counsel.kind.AGENT')}</option>
            </select>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-name`}>{t('counsel.name')}</label>
            <input id={`${id}-name`} type="text" maxLength={200} value={form.name} onChange={(e) => { setForm({ ...form, name: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-phone`}>{t('counsel.phone')}</label>
            <input id={`${id}-phone`} type="text" maxLength={30} value={form.phone} onChange={(e) => { setForm({ ...form, phone: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-email`}>{t('counsel.email')}</label>
            <input id={`${id}-email`} type="text" maxLength={254} value={form.email} onChange={(e) => { setForm({ ...form, email: e.target.value }); }} />
          </div>
          <div className="case-actions">
            <button type="submit" className="primary">
              {t('counsel.add')}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
