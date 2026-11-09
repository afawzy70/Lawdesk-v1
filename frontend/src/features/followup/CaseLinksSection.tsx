import { type SyntheticEvent, useCallback, useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { PagedSelect } from '../../components/PagedSelect';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { searchCases, type CaseListItem } from '../cases/api';
import { formatDate } from '../cases/format';
import { casePath } from '../cases/paths';
import {
  addLink,
  LINK_KINDS,
  LINK_STATUSES,
  listLinks,
  updateLink,
  type CaseLink,
  type CaseLinks,
  type LinkKind,
  type LinkStatus,
  type NewLink,
} from './api';
import { failureKey, isForbidden } from './errors';

const EMPTY: NewLink = {
  otherCaseId: '',
  kind: 'SUBJECT_LINK',
  reason: '',
  requestedBy: '',
  requestedOn: '',
  courtDecision: false,
  decisionDate: '',
  operative: '',
};

interface Props {
  caseId: string;
  /** Holds CASE_LINK_MANAGE and the case is not archived: the user may be allowed to add and end links. */
  editable: boolean;
}

/** Section 8: the cases linked to this one, shown in both files; the other case's details only if the user may see it. */
export function CaseLinksSection({ caseId, editable }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [links, setLinks] = useState<CaseLinks | null>(null);
  const [hidden, setHidden] = useState(false);
  const [form, setForm] = useState<NewLink>(EMPTY);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const [generation, setGeneration] = useState(0);
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    listLinks(caseId, controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) setLinks(found);
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        if (isForbidden(error)) setHidden(true);
        else setMessage({ key: 'wp3.links.failed', failed: true });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, generation]);

  if (hidden) return null;

  const set = <K extends keyof NewLink>(field: K, value: NewLink[K]) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setMessage(null);
    if (form.otherCaseId === '' || form.reason.trim() === '') {
      setMessage({ key: 'wp3.links.invalid', failed: true });
      return;
    }
    try {
      await addLink(caseId, { ...form, reason: form.reason.trim() });
      setForm(EMPTY);
      setMessage({ key: 'wp3.links.added', failed: false });
      reload();
    } catch (error) {
      setMessage({ key: failureKey(error), failed: true });
    }
  };

  const setStatus = async (link: CaseLink, status: LinkStatus) => {
    setMessage(null);
    try {
      await updateLink(link, { status });
      setMessage({ key: 'wp3.links.updated', failed: false });
      reload();
    } catch (error) {
      setMessage({ key: failureKey(error), failed: true });
    }
  };

  return (
    <section aria-labelledby={`${id}-h`} className="wp3-section">
      <h2 id={`${id}-h`}>{t('wp3.links.title')}</h2>
      <p className="case-field-hint">{t('wp3.links.hint')}</p>
      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>
          {t(message.key)}
        </p>
      )}
      {links === null ? null : links.items.length === 0 && links.hiddenCount === 0 ? (
        <p>{t('wp3.links.none')}</p>
      ) : (
        <ul className="wp3-list">
          {links.items.map((link) => (
            <li key={link.id}>
              <p>
                <strong>{t(`wp3.links.kind.${link.kind}`)}</strong> —{' '}
                <Link to={withLang(casePath(link.otherCaseId), locale)}>
                  {link.otherCaseReferenceNo} {link.otherCaseTitle}
                </Link>{' '}
                <span className="wp3-badge" data-status={link.status}>
                  {t(`wp3.links.status.${link.status}`)}
                </span>
                {' · '}
                {t(link.direction === 'OUTGOING' ? 'wp3.links.outgoing' : 'wp3.links.incoming')}
              </p>
              <p>{link.reason}</p>
              <p className="case-field-hint">
                {t('wp3.links.otherStatus', { status: t(`cases.status.${link.otherCaseStatus as 'OPEN'}`) })}
                {link.requestedBy === null ? null : ` · ${t('wp3.links.requestedBy', { name: link.requestedBy })}`}
                {link.requestedOn === null ? null : ` · ${formatDate(link.requestedOn, locale)}`}
              </p>
              {link.courtDecision ? (
                <p>
                  {t('wp3.links.decided')}
                  {link.decisionDate === null ? null : ` ${formatDate(link.decisionDate, locale)}`}
                  {link.operative === null ? null : ` — ${link.operative}`}
                </p>
              ) : null}
              {!editable ? null : (
                <p>
                  <label htmlFor={`${id}-s-${link.id}`}>{t('wp3.links.changeStatus')}</label>{' '}
                  <select
                    id={`${id}-s-${link.id}`}
                    value={link.status}
                    onChange={(e) => void setStatus(link, e.target.value as LinkStatus)}
                  >
                    {LINK_STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {t(`wp3.links.status.${status}`)}
                      </option>
                    ))}
                  </select>
                </p>
              )}
            </li>
          ))}
          {links.hiddenCount === 0 ? null : (
            <li className="wp3-hidden">{t('wp3.links.hidden', { count: String(links.hiddenCount) })}</li>
          )}
        </ul>
      )}
      {!editable ? null : (
        <form className="case-form" aria-label={t('wp3.links.add')} noValidate onSubmit={(e) => void submit(e)}>
          <div className="case-field">
            <label htmlFor={`${id}-other`}>{t('wp3.links.otherCase')}</label>
            <PagedSelect<CaseListItem>
              selectProps={{ id: `${id}-other` }}
              value={form.otherCaseId}
              onChange={(value) => {
                set('otherCaseId', value);
              }}
              load={(page, query, signal) => searchCases({ q: query, page }, signal)}
              optionLabel={(c) => `${c.referenceNo} — ${c.title}`}
              serverSearch
              searchLabel={t('wp3.links.searchCases')}
              chooseLabel={t('wp3.choose')}
            />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-kind`}>{t('wp3.links.kind')}</label>
            <select id={`${id}-kind`} value={form.kind} onChange={(e) => { set('kind', e.target.value as LinkKind); }}>
              {LINK_KINDS.map((kind) => (
                <option key={kind} value={kind}>
                  {t(`wp3.links.kind.${kind}`)}
                </option>
              ))}
            </select>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-reason`}>{t('wp3.links.reason')}</label>
            <textarea id={`${id}-reason`} rows={2} maxLength={2000} value={form.reason} onChange={(e) => { set('reason', e.target.value); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-by`}>{t('wp3.links.requester')}</label>
            <input id={`${id}-by`} type="text" maxLength={200} value={form.requestedBy} onChange={(e) => { set('requestedBy', e.target.value); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-on`}>{t('wp3.links.requestedOn')}</label>
            <input id={`${id}-on`} type="date" value={form.requestedOn} onChange={(e) => { set('requestedOn', e.target.value); }} />
          </div>
          <div className="case-field">
            <label>
              <input type="checkbox" checked={form.courtDecision} onChange={(e) => { set('courtDecision', e.target.checked); }} />{' '}
              {t('wp3.links.courtDecision')}
            </label>
          </div>
          {!form.courtDecision ? null : (
            <>
              <div className="case-field">
                <label htmlFor={`${id}-dd`}>{t('wp3.links.decisionDate')}</label>
                <input id={`${id}-dd`} type="date" value={form.decisionDate} onChange={(e) => { set('decisionDate', e.target.value); }} />
              </div>
              <div className="case-field">
                <label htmlFor={`${id}-op`}>{t('wp3.links.operative')}</label>
                <textarea id={`${id}-op`} rows={2} maxLength={2000} value={form.operative} onChange={(e) => { set('operative', e.target.value); }} />
              </div>
            </>
          )}
          <div className="case-actions">
            <button type="submit" className="primary">
              {t('wp3.links.add')}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
