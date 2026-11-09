import { type SyntheticEvent, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { usePermissions } from '../archive/usePermissions';
import { useLoad } from '../pleadings/useLoad';
import {
  createWording,
  DEFENSE_KINDS,
  listWording,
  TEMPLATE_STATUSES,
  updateWording,
  type TemplateInput,
  type WordingTemplate,
} from './api';

const EMPTY: TemplateInput = { title: '', kind: 'SUBSTANTIVE', text: null, legalBasis: null, status: 'DRAFT' };

/** The defence wording library (requirements section 17): reusable wording a member reads and adapts; nothing is copied automatically. */
export function DefenseWordingPage() {
  const { t } = useI18n();
  usePageTitle(t('gap.wording.title'));
  const manage = usePermissions().has('DEFENSE_MANAGE');
  const [q, setQ] = useState('');
  const [applied, setApplied] = useState('');
  const load = useLoad((signal) => listWording(applied, signal), [applied]);
  const [editing, setEditing] = useState<WordingTemplate | null>(null);
  const [draft, setDraft] = useState<TemplateInput>(EMPTY);
  const [failed, setFailed] = useState(false);

  if (load.status === 403) return <AccessDeniedPage />;

  const edit = (template: WordingTemplate | null) => {
    setEditing(template);
    setDraft(template === null ? EMPTY : { title: template.title, kind: template.kind, text: template.text, legalBasis: template.legalBasis, status: template.status });
  };
  const submit = (event: SyntheticEvent) => {
    event.preventDefault();
    const request = editing === null ? createWording(draft) : updateWording(editing.id, { ...draft, version: editing.version });
    request.then(
      () => { setFailed(false); edit(null); load.reload(); },
      () => { setFailed(true); },
    );
  };

  return (
    <>
      <h1>{t('gap.wording.title')}</h1>
      <form role="search" onSubmit={(e) => { e.preventDefault(); setApplied(q); }}>
        <label>
          {t('gap.wording.search')} <input value={q} onChange={(e) => { setQ(e.target.value); }} />
        </label>{' '}
        <button type="submit">{t('gap.search')}</button>
      </form>
      {load.status !== null ? <p role="alert">{t('gap.failed')}</p> : null}
      {load.data === null ? null : load.data.length === 0 ? <p>{t('gap.wording.none')}</p> : (
        <ul>
          {load.data.map((template) => (
            <li key={template.id} aria-label={template.title}>
              <strong>{template.title}</strong> ({template.kind}, {template.status})
              {template.text === null ? null : <p>{template.text}</p>}
              {template.legalBasis === null ? null : <p>{template.legalBasis}</p>}
              {manage ? <button type="button" onClick={() => { edit(template); }}>{t('gap.edit')}</button> : null}
            </li>
          ))}
        </ul>
      )}
      {manage ? (
        <form aria-label={t(editing === null ? 'gap.wording.add' : 'gap.wording.edit')} onSubmit={submit}>
          <h2>{t(editing === null ? 'gap.wording.add' : 'gap.wording.edit')}</h2>
          <p><label>{t('gap.wording.titleField')} <input required value={draft.title} onChange={(e) => { setDraft({ ...draft, title: e.target.value }); }} /></label></p>
          <p>
            <label>{t('gap.wording.kind')}{' '}
              <select value={draft.kind} onChange={(e) => { setDraft({ ...draft, kind: DEFENSE_KINDS.find((k) => k === e.target.value) ?? 'OTHER' }); }}>
                {DEFENSE_KINDS.map((k) => <option key={k} value={k}>{k}</option>)}
              </select>
            </label>
          </p>
          <p><label>{t('gap.wording.text')} <textarea value={draft.text ?? ''} onChange={(e) => { setDraft({ ...draft, text: e.target.value === '' ? null : e.target.value }); }} /></label></p>
          <p><label>{t('gap.wording.basis')} <textarea value={draft.legalBasis ?? ''} onChange={(e) => { setDraft({ ...draft, legalBasis: e.target.value === '' ? null : e.target.value }); }} /></label></p>
          <p>
            <label>{t('gap.wording.status')}{' '}
              <select value={draft.status} onChange={(e) => { setDraft({ ...draft, status: TEMPLATE_STATUSES.find((s) => s === e.target.value) ?? 'DRAFT' }); }}>
                {TEMPLATE_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </label>
          </p>
          <button type="submit">{t('gap.save')}</button>{' '}
          {editing === null ? null : <button type="button" onClick={() => { edit(null); }}>{t('gap.cancel')}</button>}
          {failed ? <p role="alert">{t('gap.failed')}</p> : null}
        </form>
      ) : null}
    </>
  );
}
