import { useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import type { MessageKey } from '../../i18n/catalog';
import { formatDate } from '../cases/format';
import { listCaseHearings } from '../operations/hearings/hearingsApi';
import { listCaseTasks } from '../operations/tasks/tasksApi';
import { listClaims, listDefenses } from '../pleadings/api';
import { ChoiceField, Notice, TextField } from '../pleadings/ui';
import { useLoad } from '../pleadings/useLoad';
import { listJudgments } from '../proceedings/proceedingsApi';
import { addDocumentLink, asList, LINK_TYPES, listDocumentLinks, removeDocumentLink, type LinkType } from './api';
import { archiveError } from './errors';

interface Props {
  caseId: string;
  documentId: string;
  canManage: boolean;
}

type Option = readonly [string, string];

/** The claim, defence, hearing, judgment or task a document concerns (requirement 13, last item). */
export function DocumentLinks({ caseId, documentId, canManage }: Props) {
  const { t, locale } = useI18n();
  const links = useLoad((signal) => listDocumentLinks(documentId, signal), [documentId]);
  const [type, setType] = useState<LinkType>('CLAIM');
  const [target, setTarget] = useState('');
  const [note, setNote] = useState('');
  const [message, setMessage] = useState<{ failed: boolean; key: MessageKey } | null>(null);

  const targets = useLoad<Option[]>(
    async (signal) => {
      switch (type) {
        case 'CLAIM':
          return (await listClaims(caseId, signal)).map((c) => [c.id, t('wp5.link.claim', { number: String(c.seq), text: c.text.slice(0, 60) })] as const);
        case 'DEFENSE':
          return (await listDefenses(caseId, signal)).map((d) => [d.id, d.title] as const);
        case 'HEARING':
          return (await listCaseHearings(caseId, signal)).map((h) => [h.id, t('wp5.link.hearing', { date: formatDate(h.hearingDate, locale) })] as const);
        case 'JUDGMENT':
          return (await listJudgments(caseId, signal)).map((j) => [j.id, t('wp5.link.judgment', { date: formatDate(j.judgmentDate, locale) })] as const);
        case 'TASK':
          return (await listCaseTasks(caseId, signal)).map((x) => [x.id, x.title] as const);
      }
    },
    [caseId, type],
  );

  const add = async () => {
    setMessage(null);
    try {
      await addDocumentLink(documentId, type, target, note);
      setTarget('');
      setNote('');
      setMessage({ failed: false, key: 'wp5.link.added' });
      links.reload();
    } catch (error) {
      setMessage({ failed: true, key: archiveError(error) });
    }
  };

  const remove = async (id: string) => {
    setMessage(null);
    try {
      await removeDocumentLink(id);
      setMessage({ failed: false, key: 'wp5.link.removed' });
      links.reload();
    } catch (error) {
      setMessage({ failed: true, key: archiveError(error) });
    }
  };

  const options = asList(targets.data);
  return (
    <section aria-label={t('wp5.link.title')}>
      <h5>{t('wp5.link.title')}</h5>
      {links.data !== null && asList(links.data).length === 0 ? <p>{t('wp5.link.none')}</p> : null}
      <ul className="archive-list">
        {asList(links.data).map((l) => (
          <li key={l.id} className="archive-item">
            <p>
              <strong>{t(`wp5.link.${l.targetType}`)}</strong>: {l.targetLabel ?? l.targetId}
            </p>
            {l.note === null ? null : <p className="archive-meta">{l.note}</p>}
            {canManage ? (
              <button
                type="button"
                aria-label={t('wp5.link.removeLabel', { label: l.targetLabel ?? l.targetId })}
                onClick={() => {
                  void remove(l.id);
                }}
              >
                {t('wp5.link.remove')}
              </button>
            ) : null}
          </li>
        ))}
      </ul>
      {message === null ? null : <Notice failed={message.failed}>{t(message.key)}</Notice>}
      {canManage ? (
        <form
          className="case-form"
          aria-label={t('wp5.link.add')}
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void add();
          }}
        >
          <ChoiceField
            label={t('wp5.link.type')}
            value={type}
            onChange={(value) => {
              setType(value as LinkType);
              setTarget('');
            }}
            options={LINK_TYPES.map((x) => [x, t(`wp5.link.${x}`)] as const)}
          />
          {targets.data !== null && options.length === 0 ? <p className="archive-meta">{t('wp5.link.noTargets')}</p> : null}
          <ChoiceField label={t('wp5.link.target')} value={target} onChange={setTarget} options={options} empty={t('wp5.choose')} />
          <TextField label={t('wp5.link.note')} value={note} onChange={setNote} max={500} />
          <button type="submit" disabled={target === ''}>
            {t('wp5.link.add')}
          </button>
        </form>
      ) : null}
    </section>
  );
}
