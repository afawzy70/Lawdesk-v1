import { type SyntheticEvent, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { useLoad } from '../pleadings/useLoad';
import { linkPrecedent, listPrecedents, searchLibrary, unlinkPrecedent } from './api';

type Hit = { id: string; courtName: string; caseNumber: string | null; caseYear: number | null; subject: string };
const cite = (h: { courtName: string; caseNumber: string | null; caseYear: number | null }) =>
  `${h.courtName} ${h.caseNumber ?? ''}${h.caseYear === null ? '' : `/${String(h.caseYear)}`}`.trim();

/** The judgments and precedents that support a defence (requirements section 17): linked by the member, removable, never copied. */
export function DefensePrecedents({ defenseId, canManage }: { defenseId: string; canManage: boolean }) {
  const { t } = useI18n();
  const list = useLoad((signal) => listPrecedents(defenseId, signal), [defenseId]);
  const [q, setQ] = useState('');
  const [hits, setHits] = useState<Hit[]>([]);
  const [failed, setFailed] = useState(false);

  const search = (event: SyntheticEvent) => {
    event.preventDefault();
    searchLibrary(q).then(
      (page) => { setFailed(false); setHits(page.items); },
      () => { setFailed(true); },
    );
  };
  const link = (id: string) => {
    linkPrecedent(defenseId, id, null).then(
      () => { setFailed(false); setHits([]); list.reload(); },
      () => { setFailed(true); },
    );
  };
  const unlink = (id: string) => {
    unlinkPrecedent(defenseId, id).then(
      () => { setFailed(false); list.reload(); },
      () => { setFailed(true); },
    );
  };

  return (
    <section aria-label={t('gap.precedents.title')}>
      <h4>{t('gap.precedents.title')}</h4>
      {list.data === null || list.data.length === 0 ? <p>{t('gap.precedents.none')}</p> : (
        <ul>
          {list.data.map((p) => (
            <li key={p.id}>
              {cite(p)} - {p.subject}
              {canManage ? <> <button type="button" onClick={() => { unlink(p.id); }}>{t('gap.precedents.remove')}</button></> : null}
            </li>
          ))}
        </ul>
      )}
      {canManage ? (
        <form role="search" aria-label={t('gap.precedents.add')} onSubmit={search}>
          <label>{t('gap.precedents.add')} <input value={q} onChange={(e) => { setQ(e.target.value); }} /></label>{' '}
          <button type="submit">{t('gap.search')}</button>
          <ul>
            {hits.map((h) => (
              <li key={h.id}>
                {cite(h)} - {h.subject} <button type="button" onClick={() => { link(h.id); }}>{t('gap.precedents.link')}</button>
              </li>
            ))}
          </ul>
        </form>
      ) : null}
      {failed ? <p role="alert">{t('gap.failed')}</p> : null}
    </section>
  );
}
