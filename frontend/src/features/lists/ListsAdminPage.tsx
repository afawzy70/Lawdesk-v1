import { useState, type SyntheticEvent } from 'react';
import { Link } from 'react-router';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AdminTabs } from '../admin/AdminTabs';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { usePermissions } from '../archive/usePermissions';
import { ApiError } from '../../api/http';
import { ChoiceField, CheckField, Notice, TextField } from '../pleadings/ui';
import { flaggedFields, orNull, useLoad } from '../pleadings/useLoad';
import '../pleadings/pleadings.css';
import {
  addOption,
  listCatalogue,
  listOptions,
  saveOption,
  type ListOption,
  type ListSummary,
} from './listsApi';

type Message = { key: MessageKey; failed: boolean } | null;

function failure(error: unknown): MessageKey {
  if (!(error instanceof ApiError)) return 'lists.error.save';
  if (error.status === 400) return 'lists.error.fields';
  if (error.status === 403) return 'lists.error.forbidden';
  if (error.status === 409) return 'lists.error.stale';
  if (error.status === 422) return 'lists.error.rule';
  return 'lists.error.save';
}

const toOrder = (text: string): number | null => (text.trim() === '' || Number.isNaN(Number(text)) ? null : Number(text));

/**
 * The administrator's dropdown lists (LD-DOC-r5): every list the screens offer, with add and edit of its options.
 * A list that system logic depends on takes label and order edits only. The backend enforces LIST_ADMIN.
 */
export function ListsAdminPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('lists.title'));
  const granted = usePermissions();
  const catalogue = useLoad((signal) => listCatalogue(signal), []);
  const [code, setCode] = useState<string | null>(null);
  const lists = catalogue.data ?? [];
  const selected = lists.find((l) => l.code === (code ?? lists[0]?.code)) ?? null;

  if (!granted.has('LIST_ADMIN') || catalogue.status === 403) return <AccessDeniedPage />;
  const title = (l: ListSummary) => (locale === 'ar' ? l.titleAr : l.titleEn);
  return (
    <>
      <h1>{t('lists.title')}</h1>
      <AdminTabs />
      <p className="case-field-hint">{t('lists.intro')}</p>
      {catalogue.status !== null ? <Notice failed>{t('lists.loadFailed')}</Notice> : null}
      {lists.length === 0 ? null : (
        <ChoiceField
          label={t('lists.list')}
          value={selected?.code ?? ''}
          onChange={setCode}
          options={lists.map((l) => [l.code, title(l)] as const)}
        />
      )}
      {selected === null ? null : <ListPanel key={selected.code} list={selected} onChanged={catalogue.reload} />}
    </>
  );
}

function ListPanel({ list, onChanged }: { list: ListSummary; onChanged: () => void }) {
  const { t, locale } = useI18n();
  const options = useLoad((signal) => (list.source === 'COURT' ? Promise.resolve([]) : listOptions(list, signal)), [list.code]);
  const [message, setMessage] = useState<Message>(null);
  const [flagged, setFlagged] = useState<string[]>([]);
  const [editing, setEditing] = useState<ListOption | null>(null);
  const [form, setForm] = useState({ labelAr: '', labelEn: '', order: '', active: true });
  const [add, setAdd] = useState({ code: '', labelAr: '', labelEn: '', order: '' });
  const note = locale === 'ar' ? list.noteAr : list.noteEn;
  const label = (o: ListOption) => (locale === 'ar' ? o.labelAr : (o.labelEn ?? o.labelAr));

  const guarded = async (action: () => Promise<unknown>, done: MessageKey) => {
    try {
      await action();
      setMessage({ key: done, failed: false });
      setFlagged([]);
      setEditing(null);
      options.reload();
      onChanged();
    } catch (error) {
      setFlagged(flaggedFields(error));
      setMessage({ key: failure(error), failed: true });
    }
  };

  const startEdit = (option: ListOption) => {
    setEditing(option);
    setMessage(null);
    setForm({ labelAr: option.labelAr, labelEn: option.labelEn ?? '', order: String(option.sortOrder), active: option.active });
  };
  const submitEdit = (event: SyntheticEvent) => {
    event.preventDefault();
    if (editing === null) return;
    void guarded(
      () =>
        saveOption(list, editing, {
          labelAr: form.labelAr.trim(),
          labelEn: orNull(form.labelEn),
          sortOrder: toOrder(form.order),
          active: editing.system === true ? true : form.active,
        }),
      'lists.saved',
    );
  };
  const submitAdd = (event: SyntheticEvent) => {
    event.preventDefault();
    void guarded(async () => {
      await addOption(list, {
        code: add.code.trim(),
        labelAr: add.labelAr.trim(),
        labelEn: orNull(add.labelEn),
        sortOrder: toOrder(add.order),
      });
      setAdd({ code: '', labelAr: '', labelEn: '', order: '' });
    }, 'lists.added');
  };

  return (
    <section aria-label={locale === 'ar' ? list.titleAr : list.titleEn}>
      <p className="case-field-hint">
        {t(list.addable ? 'lists.mode.add' : 'lists.mode.labelOnly')} · {t('lists.optionsCount', { count: String(list.options) })}
      </p>
      {note === '' ? null : <p className="case-field-hint">{note}</p>}
      {message === null ? null : <Notice failed={message.failed}>{t(message.key)}</Notice>}
      {list.source === 'COURT' ? (
        <>
          <p>{t('lists.courts.intro')}</p>
          <p>
            <Link to={withLang('/courts', locale)}>{t('lists.courts.open')}</Link>
          </p>
        </>
      ) : (
        <>
          {list.source === 'MANAGED' ? <p className="case-field-hint">{t('lists.managed.hint')}</p> : null}
          {options.status !== null ? <Notice failed>{t('lists.optionsFailed')}</Notice> : null}
          <div className="table-scroll" role="region" tabIndex={0} aria-label={locale === 'ar' ? list.titleAr : list.titleEn}>
            <table className="case-table">
              <thead>
                <tr>
                  <th scope="col">{t('lists.col.code')}</th>
                  <th scope="col">{t('lists.col.labelAr')}</th>
                  <th scope="col">{t('lists.col.labelEn')}</th>
                  <th scope="col">{t('lists.col.order')}</th>
                  <th scope="col">{t('lists.col.state')}</th>
                  <th scope="col">{t('lists.col.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {(options.data ?? []).map((o) => (
                  <tr key={o.id}>
                    <td dir="ltr">{o.code}</td>
                    <td>{o.labelAr}</td>
                    <td dir="ltr">{o.labelEn ?? '—'}</td>
                    <td>{o.sortOrder}</td>
                    <td>
                      {t(o.active ? 'lists.state.active' : 'lists.state.inactive')}
                      {o.system === true ? <span className="case-tag" title={t('lists.systemHint')}>{t('lists.system')}</span> : null}
                    </td>
                    <td>
                      <button
                        type="button"
                        aria-label={t('lists.editNamed', { name: label(o) })}
                        onClick={() => {
                          startEdit(o);
                        }}
                      >
                        {t('lists.edit')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {editing === null ? null : (
            <form className="case-form" aria-label={t('lists.editTitle', { code: editing.code })} noValidate onSubmit={submitEdit}>
              <h2>{t('lists.editTitle', { code: editing.code })}</h2>
              {editing.system === true ? <p className="case-field-hint">{t('lists.systemHint')}</p> : null}
              <TextField label={t('lists.col.labelAr')} value={form.labelAr} max={200} invalid={flagged.includes('labelAr')}
                onChange={(labelAr) => { setForm((c) => ({ ...c, labelAr })); }} />
              <TextField label={t('lists.col.labelEn')} value={form.labelEn} max={200} dir="ltr"
                onChange={(labelEn) => { setForm((c) => ({ ...c, labelEn })); }} />
              <TextField label={t('lists.col.order')} value={form.order} type="number" max={6}
                onChange={(order) => { setForm((c) => ({ ...c, order })); }} />
              {editing.system === true ? null : (
                <CheckField label={t('lists.active')} checked={form.active} onChange={(active) => { setForm((c) => ({ ...c, active })); }} />
              )}
              <div className="case-actions">
                <button type="submit" className="primary">{t('lists.save')}</button>{' '}
                <button type="button" onClick={() => { setEditing(null); }}>{t('lists.cancel')}</button>
              </div>
            </form>
          )}
          {list.addable ? (
            <form className="case-form" aria-label={t('lists.add')} noValidate onSubmit={submitAdd}>
              <h2>{t('lists.addTitle')}</h2>
              <TextField label={t('lists.col.code')} value={add.code} dir="ltr" max={60} invalid={flagged.includes('code')}
                onChange={(code) => { setAdd((c) => ({ ...c, code })); }} />
              <TextField label={t('lists.col.labelAr')} value={add.labelAr} max={200} invalid={flagged.includes('labelAr')}
                onChange={(labelAr) => { setAdd((c) => ({ ...c, labelAr })); }} />
              <TextField label={t('lists.col.labelEn')} value={add.labelEn} max={200} dir="ltr"
                onChange={(labelEn) => { setAdd((c) => ({ ...c, labelEn })); }} />
              <TextField label={t('lists.col.order')} value={add.order} type="number" max={6}
                onChange={(order) => { setAdd((c) => ({ ...c, order })); }} />
              <div className="case-actions">
                <button type="submit" className="primary">{t('lists.add')}</button>
              </div>
            </form>
          ) : (
            <p className="case-field-hint">{t('lists.labelOnlyHint')}</p>
          )}
        </>
      )}
    </section>
  );
}
