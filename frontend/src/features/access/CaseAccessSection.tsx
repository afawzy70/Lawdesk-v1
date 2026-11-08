import { type SyntheticEvent, useCallback, useContext, useEffect, useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { formatDateTime } from '../../i18n/format';
import { useI18n } from '../../i18n/I18nContext';
import { AuthContext } from '../auth/AuthContext';
import { listAssignees, type DirectoryUser } from '../operations/tasks/tasksApi';
import { grantAccess, type CaseGrant, type GrantLevel, listGrants, revokeGrant, setConfidentiality } from './api';

interface Props {
  caseId: string;
  confidentiality: 'NORMAL' | 'RESTRICTED';
  version: number;
  /** Called after a change that alters what the case page shows. */
  onChanged: () => void;
}

/** LD-050: marks a case restricted and manages its explicit grants; for holders of CASE_GRANT_MANAGE. */
export function CaseAccessSection({ caseId, confidentiality, version, onChanged }: Props) {
  const { t, locale } = useI18n();
  const state = useContext(AuthContext)?.state;
  const id = useId();
  const allowed = state?.status === 'signedIn' && state.me.permissions.includes('CASE_GRANT_MANAGE');
  const [grants, setGrants] = useState<CaseGrant[] | null>(null);
  const [users, setUsers] = useState<DirectoryUser[]>([]);
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [notice, setNotice] = useState<MessageKey | null>(null);
  const [level, setLevel] = useState(confidentiality);
  const [reason, setReason] = useState('');
  const [user, setUser] = useState('');
  const [grantLevel, setGrantLevel] = useState<GrantLevel>('VIEW');
  const [grantReason, setGrantReason] = useState('');
  const [generation, setGeneration] = useState(0);
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);

  useEffect(() => {
    if (!allowed) return;
    const controller = new AbortController();
    Promise.all([listGrants(caseId), listAssignees(controller.signal)]).then(
      ([found, directory]) => {
        if (controller.signal.aborted) return;
        setGrants(found);
        setUsers(directory.filter((candidate) => candidate.enabled));
      },
      () => {
        if (!controller.signal.aborted) setFailure('access.case.failed');
      },
    );
    return () => {
      controller.abort();
    };
  }, [allowed, caseId, generation]);

  if (!allowed) return null;

  const explain = (error: unknown): MessageKey =>
    error instanceof ApiError && error.status === 422 ? 'access.case.refused' : 'access.case.failed';

  const saveLevel = async (event: SyntheticEvent) => {
    event.preventDefault();
    setNotice(null);
    if (reason.trim() === '') {
      setFailure('access.case.needReason');
      return;
    }
    setFailure(null);
    try {
      await setConfidentiality(caseId, level, reason.trim(), version);
      setReason('');
      setNotice('access.case.saved');
      onChanged();
    } catch (error) {
      setFailure(explain(error));
    }
  };

  const grant = async (event: SyntheticEvent) => {
    event.preventDefault();
    setNotice(null);
    if (user === '' || grantReason.trim() === '') {
      setFailure('access.case.needReason');
      return;
    }
    setFailure(null);
    try {
      await grantAccess(caseId, {
        userId: user,
        level: grantLevel,
        reason: grantReason.trim(),
      });
      setGrantReason('');
      setNotice('access.case.granted');
      reload();
    } catch (error) {
      setFailure(explain(error));
    }
  };

  const revoke = async (item: CaseGrant) => {
    setFailure(null);
    setNotice(null);
    try {
      await revokeGrant(caseId, item.id);
      setNotice('access.case.revoked');
      reload();
    } catch (error) {
      setFailure(explain(error));
    }
  };

  return (
    <section aria-labelledby={`${id}-h`} className="case-access">
      <h2 id={`${id}-h`}>{t('access.case.title')}</h2>
      {failure === null ? null : (
        <p className="case-alert" role="alert">
          {t(failure)}
        </p>
      )}
      {notice === null ? null : (
        <p className="case-notice" role="status">
          {t(notice)}
        </p>
      )}
      <form
        className="case-form"
        aria-label={t('access.case.confidentiality')}
        noValidate
        onSubmit={(e) => void saveLevel(e)}
      >
        <div className="case-field">
          <label htmlFor={`${id}-level`}>{t('access.case.confidentiality')}</label>
          <select
            id={`${id}-level`}
            value={level}
            onChange={(e) => {
              setLevel(e.target.value as 'NORMAL' | 'RESTRICTED');
            }}
          >
            <option value="NORMAL">{t('access.case.NORMAL')}</option>
            <option value="RESTRICTED">{t('access.case.RESTRICTED')}</option>
          </select>
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-reason`}>{t('access.case.reason')}</label>
          <input
            id={`${id}-reason`}
            type="text"
            maxLength={500}
            value={reason}
            onChange={(e) => {
              setReason(e.target.value);
            }}
          />
        </div>
        <div className="case-actions">
          <button type="submit">{t('access.case.save')}</button>
        </div>
      </form>
      <h3>{t('access.case.grants')}</h3>
      {grants === null ? null : grants.length === 0 ? (
        <p>{t('access.case.noGrants')}</p>
      ) : (
        <ul>
          {grants.map((item) => (
            <li key={item.id}>
              <strong>{item.userName}</strong> — {t(`access.case.level.${item.level}`)} — {item.reason} (
              {formatDateTime(item.grantedAt, locale)})
              {item.revokedAt === null ? (
                <>
                  {' '}
                  <button type="button" onClick={() => void revoke(item)}>
                    {t('access.case.revoke')}
                  </button>
                </>
              ) : (
                <small> — {t('access.case.withdrawn')}</small>
              )}
            </li>
          ))}
        </ul>
      )}
      <form className="case-form" aria-label={t('access.case.grant')} noValidate onSubmit={(e) => void grant(e)}>
        <div className="case-field">
          <label htmlFor={`${id}-user`}>{t('access.case.user')}</label>
          <select
            id={`${id}-user`}
            value={user}
            onChange={(e) => {
              setUser(e.target.value);
            }}
          >
            <option value="" />
            {users.map((option) => (
              <option key={option.id} value={option.id}>
                {option.displayName}
              </option>
            ))}
          </select>
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-glevel`}>{t('access.case.level')}</label>
          <select
            id={`${id}-glevel`}
            value={grantLevel}
            onChange={(e) => {
              setGrantLevel(e.target.value as GrantLevel);
            }}
          >
            <option value="VIEW">{t('access.case.level.VIEW')}</option>
            <option value="CONTRIBUTE">{t('access.case.level.CONTRIBUTE')}</option>
          </select>
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-greason`}>{t('access.case.reason')}</label>
          <input
            id={`${id}-greason`}
            type="text"
            maxLength={500}
            value={grantReason}
            onChange={(e) => {
              setGrantReason(e.target.value);
            }}
          />
        </div>
        <div className="case-actions">
          <button type="submit" className="primary">
            {t('access.case.grant')}
          </button>
        </div>
      </form>
    </section>
  );
}
