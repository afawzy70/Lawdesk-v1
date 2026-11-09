import { useState, type SyntheticEvent } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AdminTabs } from '../admin/AdminTabs';
import {
  approveLookup,
  createLookup,
  listLookup,
  LOOKUP_LISTS,
  lookupHistory,
  updateLookup,
  type LookupValue,
} from './api';
import { ChoiceField, Notice, TextField } from './ui';
import { flaggedFields, orNull, saveMessage, useLoad } from './useLoad';
import './pleadings.css';

/**
 * Managed lists (owner decision D10): values that come from the requirements document are told apart from the ones the
 * implementers proposed, which wait for approval here. Values are deactivated, never deleted; every change is kept.
 */
export function LookupsPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('pl.lookups.title'));
  const [list, setList] = useState<string>(LOOKUP_LISTS[0]);
  const values = useLoad((signal) => listLookup(list, true, signal), [list]);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const [flagged, setFlagged] = useState<string[]>([]);
  const [form, setForm] = useState({ code: '', labelAr: '', labelEn: '' });
  const [shown, setShown] = useState<LookupValue | null>(null);
  const history = useLoad(
    (signal) => (shown === null ? Promise.resolve([]) : lookupHistory(shown.id, signal)),
    [shown?.id, shown?.version],
  );

  const guarded = async (action: () => Promise<unknown>, done: MessageKey) => {
    try {
      await action();
      setMessage({ key: done, failed: false });
      setFlagged([]);
      values.reload();
    } catch (error) {
      setFlagged(flaggedFields(error));
      setMessage({ key: saveMessage(error), failed: true });
    }
  };

  const add = (event: SyntheticEvent) => {
    event.preventDefault();
    void guarded(async () => {
      await createLookup(list, { code: form.code.trim(), labelAr: form.labelAr, labelEn: orNull(form.labelEn) });
      setForm({ code: '', labelAr: '', labelEn: '' });
    }, 'pl.lookups.added');
  };

  const label = (v: LookupValue) => (locale === 'ar' ? v.labelAr : (v.labelEn ?? v.labelAr));
  return (
    <>
      <h1>{t('pl.lookups.title')}</h1>
      <AdminTabs />
      <p className="case-field-hint">{t('pl.lookups.hint')}</p>
      <ChoiceField
        label={t('pl.lookups.list')}
        value={list}
        onChange={(value) => {
          setList(value);
          setShown(null);
        }}
        options={LOOKUP_LISTS.map((l) => [l, t(`pl.lookups.list.${l}`)] as const)}
      />
      {message === null ? null : <Notice failed={message.failed}>{t(message.key)}</Notice>}
      {values.status !== null ? <Notice failed>{t('pl.error.load')}</Notice> : null}
      <table className="case-table">
        <thead>
          <tr>
            <th scope="col">{t('pl.lookups.code')}</th>
            <th scope="col">{t('pl.lookups.label')}</th>
            <th scope="col">{t('pl.lookups.origin')}</th>
            <th scope="col">{t('pl.lookups.state')}</th>
            <th scope="col">{t('pl.actions')}</th>
          </tr>
        </thead>
        <tbody>
          {(values.data ?? []).map((v) => (
            <tr key={v.id}>
              <td dir="ltr">{v.code}</td>
              <td>{label(v)}</td>
              <td>
                {t(`pl.lookups.origin.${v.origin}`)}
                {v.needsApproval ? <span className="case-tag">{t('pl.needsApproval')}</span> : null}
              </td>
              <td>{t(v.active ? 'pl.lookups.active' : 'pl.lookups.inactive')}</td>
              <td>
                {v.needsApproval ? (
                  <button
                    type="button"
                    aria-label={t('pl.lookups.approveNamed', { name: label(v) })}
                    onClick={() => void guarded(() => approveLookup(v.id), 'pl.lookups.approved')}
                  >
                    {t('pl.lookups.approve')}
                  </button>
                ) : null}{' '}
                <button
                  type="button"
                  aria-label={t(v.active ? 'pl.lookups.deactivateNamed' : 'pl.lookups.activateNamed', { name: label(v) })}
                  onClick={() =>
                    void guarded(
                      () => updateLookup(v.id, { labelAr: v.labelAr, labelEn: v.labelEn, active: !v.active, version: v.version }),
                      'pl.lookups.changed',
                    )
                  }
                >
                  {t(v.active ? 'pl.lookups.deactivate' : 'pl.lookups.activate')}
                </button>{' '}
                <button
                  type="button"
                  aria-label={t('pl.lookups.historyNamed', { name: label(v) })}
                  onClick={() => {
                    setShown(shown?.id === v.id ? null : v);
                  }}
                >
                  {t('pl.claims.history')}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {shown === null ? null : (
        <ul className="pleadings-list" aria-label={t('pl.lookups.historyNamed', { name: label(shown) })}>
          {(history.data ?? []).map((h) => (
            <li key={h.rev}>
              {h.rev}. {t(`pl.lookups.change.${h.change}`)} — {locale === 'ar' ? h.labelAr : (h.labelEn ?? h.labelAr)}
            </li>
          ))}
        </ul>
      )}
      <form className="case-form" aria-label={t('pl.lookups.add')} noValidate onSubmit={add}>
        <TextField
          label={t('pl.lookups.code')}
          value={form.code}
          dir="ltr"
          max={60}
          invalid={flagged.includes('code')}
          onChange={(code) => {
            setForm((c) => ({ ...c, code }));
          }}
        />
        <TextField
          label={t('pl.lookups.labelAr')}
          value={form.labelAr}
          max={200}
          invalid={flagged.includes('labelAr')}
          onChange={(labelAr) => {
            setForm((c) => ({ ...c, labelAr }));
          }}
        />
        <TextField
          label={t('pl.lookups.labelEn')}
          value={form.labelEn}
          max={200}
          onChange={(labelEn) => {
            setForm((c) => ({ ...c, labelEn }));
          }}
        />
        <div className="case-actions">
          <button type="submit" className="primary">
            {t('pl.lookups.add')}
          </button>
        </div>
      </form>
    </>
  );
}
