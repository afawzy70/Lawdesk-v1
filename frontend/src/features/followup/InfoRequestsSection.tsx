import { type SyntheticEvent, useCallback, useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { listOrgUnits, type OrgUnit } from '../access/api';
import { arrange } from '../access/orgTree';
import { listCaseMembers, type MemberRef } from '../cases/api';
import { createRequest, listCaseRequests, type InfoRequest, type NewRequest } from './api';
import { failureKey, isForbidden } from './errors';
import { InfoRequestCard } from './InfoRequestCard';

const EMPTY: NewRequest = { subject: '', unitId: '', details: '', requiredDocuments: '', dueOn: '', addressee: '' };

interface Props {
  caseId: string;
  /** Holds INFO_REQUEST_MANAGE and the case is not archived. */
  editable: boolean;
  canEscalate: boolean;
}

/** Section 18: the requests the legal department addressed to the authority's units for this case, with replies and follow-up. */
export function InfoRequestsSection({ caseId, editable, canEscalate }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [requests, setRequests] = useState<InfoRequest[] | null>(null);
  const [hidden, setHidden] = useState(false);
  const [units, setUnits] = useState<OrgUnit[]>([]);
  const [members, setMembers] = useState<MemberRef[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const [generation, setGeneration] = useState(0);
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    listCaseRequests(caseId, controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) setRequests(found);
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        if (isForbidden(error)) setHidden(true);
        else setMessage({ key: 'wp3.requests.failed', failed: true });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, generation]);

  useEffect(() => {
    if (!editable) return undefined;
    let current = true;
    listOrgUnits('AUTHORITY', false).then(
      (found) => {
        if (current) setUnits(found);
      },
      () => {
        // The unit can stay unspecified.
      },
    );
    listCaseMembers().then(
      (found) => {
        if (current) setMembers(found);
      },
      () => {
        // Escalation then has no one to choose.
      },
    );
    return () => {
      current = false;
    };
  }, [editable]);

  if (hidden) return null;

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setMessage(null);
    if (form.subject.trim() === '') {
      setMessage({ key: 'wp3.requests.invalid', failed: true });
      return;
    }
    try {
      await createRequest(caseId, { ...form, subject: form.subject.trim() });
      setForm(EMPTY);
      setMessage({ key: 'wp3.requests.created', failed: false });
      reload();
    } catch (error) {
      setMessage({ key: failureKey(error), failed: true });
    }
  };

  const name = (unit: OrgUnit) => (locale === 'en' && unit.nameEn !== null ? unit.nameEn : unit.nameAr);
  return (
    <section aria-labelledby={`${id}-h`} className="wp3-section">
      <h2 id={`${id}-h`}>{t('wp3.requests.title')}</h2>
      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>
          {t(message.key)}
        </p>
      )}
      {requests === null ? null : requests.length === 0 ? (
        <p>{t('wp3.requests.none')}</p>
      ) : (
        <ul className="wp3-list">
          {requests.map((request) => (
            <InfoRequestCard
              key={`${request.id}-${String(request.version)}`}
              request={request}
              editable={editable}
              canEscalate={canEscalate}
              members={members}
              onChanged={(key) => {
                setMessage({ key, failed: false });
                reload();
              }}
              onFailed={(key) => {
                setMessage({ key, failed: true });
              }}
            />
          ))}
        </ul>
      )}
      {!editable ? null : (
        <form className="case-form" aria-label={t('wp3.requests.new')} noValidate onSubmit={(e) => void submit(e)}>
          <div className="case-field">
            <label htmlFor={`${id}-subject`}>{t('wp3.requests.subject')}</label>
            <input id={`${id}-subject`} type="text" maxLength={300} value={form.subject} onChange={(e) => { setForm({ ...form, subject: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-unit`}>{t('wp3.requests.unit')}</label>
            <select id={`${id}-unit`} value={form.unitId} onChange={(e) => { setForm({ ...form, unitId: e.target.value }); }}>
              <option value="">{t('wp3.notSpecified')}</option>
              {arrange(units).map(({ unit }) => (
                <option key={unit.id} value={unit.id}>{name(unit)}</option>
              ))}
            </select>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-details`}>{t('wp3.requests.details')}</label>
            <textarea id={`${id}-details`} rows={3} maxLength={4000} value={form.details} onChange={(e) => { setForm({ ...form, details: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-docs`}>{t('wp3.requests.requiredDocuments')}</label>
            <textarea id={`${id}-docs`} rows={2} maxLength={2000} value={form.requiredDocuments} onChange={(e) => { setForm({ ...form, requiredDocuments: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-due`}>{t('wp3.requests.dueOn')}</label>
            <input id={`${id}-due`} type="date" value={form.dueOn} onChange={(e) => { setForm({ ...form, dueOn: e.target.value }); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-to`}>{t('wp3.requests.addressee')}</label>
            <input id={`${id}-to`} type="text" maxLength={200} value={form.addressee} onChange={(e) => { setForm({ ...form, addressee: e.target.value }); }} />
          </div>
          <div className="case-actions">
            <button type="submit" className="primary">{t('wp3.requests.new')}</button>
          </div>
        </form>
      )}
    </section>
  );
}
