import { type SyntheticEvent, useCallback, useEffect, useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { listOrgUnits, type OrgUnit } from '../access/api';
import { arrange } from '../access/orgTree';
import { addInvolvement, type Involvement, listInvolvements, type NewInvolvement, removeInvolvement } from './api';
import { formatDate } from './format';
import { unitLabel } from './involvementLabel';

const EMPTY: NewInvolvement = {
  orgUnitId: '',
  sectorName: '',
  departmentName: '',
  sectionName: '',
  primary: false,
  contactName: '',
  contactInfo: '',
  referralDate: '',
  requestedFromUnit: '',
  notes: '',
};

interface Props {
  caseId: string;
  /** False for an archived case: its units are shown but cannot change. */
  editable: boolean;
}

/** Section 6: the administrative units the case concerns, one primary and any number of supporting. */
export function CaseInvolvementsSection({ caseId, editable }: Props) {
  const { t, locale } = useI18n();
  const id = useId();
  const [items, setItems] = useState<Involvement[] | null>(null);
  const [units, setUnits] = useState<OrgUnit[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [message, setMessage] = useState<{ key: MessageKey; failed: boolean } | null>(null);
  const [generation, setGeneration] = useState(0);
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    listInvolvements(caseId, controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) setItems(found);
      },
      () => {
        if (!controller.signal.aborted) setMessage({ key: 'involve.failed', failed: true });
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, generation]);

  useEffect(() => {
    if (!editable) return;
    let current = true;
    listOrgUnits('AUTHORITY', false).then(
      (found) => {
        if (current) setUnits(found);
      },
      () => {
        // The names can still be typed.
      },
    );
    return () => {
      current = false;
    };
  }, [editable]);

  const set = (field: keyof NewInvolvement) => (value: string | boolean) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setMessage(null);
    if (form.orgUnitId === '' && form.sectorName.trim() === '' && form.departmentName.trim() === '' && form.sectionName.trim() === '') {
      setMessage({ key: 'involve.invalid', failed: true });
      return;
    }
    try {
      await addInvolvement(caseId, form);
      setForm(EMPTY);
      setMessage({ key: 'involve.added', failed: false });
      reload();
    } catch (error) {
      const refused = error instanceof ApiError && error.status === 422;
      setMessage({ key: refused ? 'involve.rule' : 'involve.failed', failed: true });
    }
  };

  const remove = async (item: Involvement) => {
    setMessage(null);
    try {
      await removeInvolvement(caseId, item.id);
      setMessage({ key: 'involve.removed', failed: false });
      reload();
    } catch {
      setMessage({ key: 'involve.failed', failed: true });
    }
  };

  const name = (unit: OrgUnit) => (locale === 'en' && unit.nameEn !== null ? unit.nameEn : unit.nameAr);
  return (
    <section aria-labelledby={`${id}-h`} className="case-involvements">
      <h2 id={`${id}-h`}>{t('involve.title')}</h2>
      {message === null ? null : (
        <p className={message.failed ? 'case-alert' : 'case-notice'} role={message.failed ? 'alert' : 'status'}>
          {t(message.key)}
        </p>
      )}
      {items === null ? null : items.length === 0 ? (
        <p>{t('involve.none')}</p>
      ) : (
        <ul>
          {items.map((item) => (
            <li key={item.id}>
              <strong>{unitLabel(item)}</strong> — {t(item.primary ? 'involve.primary' : 'involve.supporting')}
              {item.contactName === null ? null : <> — {item.contactName}</>}
              {item.referralDate === null ? null : <> — {formatDate(item.referralDate, locale)}</>}
              {item.requestedFromUnit === null ? null : <> — {item.requestedFromUnit}</>}
              {editable ? (
                <>
                  {' '}
                  <button type="button" onClick={() => void remove(item)}>
                    {t('involve.remove')}
                  </button>
                </>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {!editable ? null : (
        <form className="case-form" aria-label={t('involve.add')} noValidate onSubmit={(e) => void submit(e)}>
          <div className="case-field">
            <label htmlFor={`${id}-unit`}>{t('involve.unit')}</label>
            <select id={`${id}-unit`} value={form.orgUnitId} onChange={(e) => { set('orgUnitId')(e.target.value); }}>
              <option value="">{t('involve.unitOther')}</option>
              {arrange(units).map(({ unit }) => (
                <option key={unit.id} value={unit.id}>
                  {name(unit)}
                </option>
              ))}
            </select>
          </div>
          {form.orgUnitId !== '' ? null : (
            <>
              <div className="case-field">
                <label htmlFor={`${id}-sector`}>{t('access.org.level.SECTOR')}</label>
                <input id={`${id}-sector`} type="text" maxLength={200} value={form.sectorName} onChange={(e) => { set('sectorName')(e.target.value); }} />
              </div>
              <div className="case-field">
                <label htmlFor={`${id}-dept`}>{t('access.org.level.DEPARTMENT')}</label>
                <input id={`${id}-dept`} type="text" maxLength={200} value={form.departmentName} onChange={(e) => { set('departmentName')(e.target.value); }} />
              </div>
              <div className="case-field">
                <label htmlFor={`${id}-section`}>{t('access.org.level.SECTION')}</label>
                <input id={`${id}-section`} type="text" maxLength={200} value={form.sectionName} onChange={(e) => { set('sectionName')(e.target.value); }} />
              </div>
            </>
          )}
          <div className="case-field">
            <label>
              <input type="checkbox" checked={form.primary} onChange={(e) => { set('primary')(e.target.checked); }} />{' '}
              {t('involve.makePrimary')}
            </label>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-contact`}>{t('involve.contact')}</label>
            <input id={`${id}-contact`} type="text" maxLength={200} value={form.contactName} onChange={(e) => { set('contactName')(e.target.value); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-info`}>{t('involve.contactInfo')}</label>
            <input id={`${id}-info`} type="text" maxLength={300} value={form.contactInfo} onChange={(e) => { set('contactInfo')(e.target.value); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-date`}>{t('involve.referralDate')}</label>
            <input id={`${id}-date`} type="date" value={form.referralDate} onChange={(e) => { set('referralDate')(e.target.value); }} />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-req`}>{t('involve.requested')}</label>
            <input id={`${id}-req`} type="text" maxLength={1000} value={form.requestedFromUnit} onChange={(e) => { set('requestedFromUnit')(e.target.value); }} />
          </div>
          <div className="case-actions">
            <button type="submit" className="primary">
              {t('involve.add')}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
