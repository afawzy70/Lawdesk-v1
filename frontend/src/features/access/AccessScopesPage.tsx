import { type SyntheticEvent, useCallback, useEffect, useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { AdminTabs } from '../admin/AdminTabs';
import { listRoles, type RoleView } from '../admin/api';
import {
  addScope,
  type AccessScope,
  listOrgUnits,
  listScopes,
  type OrgUnit,
  removeScope,
  SCOPE_TYPES,
  type ScopeType,
} from './api';

const CASE_TYPES = ['CIVIL', 'CRIMINAL', 'COMMERCIAL', 'LABOR', 'FAMILY', 'ADMINISTRATIVE'] as const;

/** LD-050: which cases each role sees beyond the ones its users are members of. */
export function AccessScopesPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('access.scopes.title'));
  const id = useId();
  const [scopes, setScopes] = useState<AccessScope[] | null>(null);
  const [roles, setRoles] = useState<RoleView[]>([]);
  const [units, setUnits] = useState<OrgUnit[]>([]);
  const [denied, setDenied] = useState(false);
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [notice, setNotice] = useState<MessageKey | null>(null);
  const [role, setRole] = useState('');
  const [type, setType] = useState<ScopeType>('ALL');
  const [ref, setRef] = useState('');
  const [generation, setGeneration] = useState(0);
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);

  useEffect(() => {
    let current = true;
    Promise.all([listScopes(), listRoles(), listOrgUnits('LEGAL', false)]).then(
      ([found, roleList, unitList]) => {
        if (!current) return;
        setScopes(found);
        setRoles(roleList);
        setUnits(unitList);
        setRole((chosen) => (chosen === '' ? (roleList[0]?.code ?? '') : chosen));
      },
      (error: unknown) => {
        if (!current) return;
        if (error instanceof ApiError && error.status === 403) setDenied(true);
        else setFailure('access.scopes.failed');
      },
    );
    return () => {
      current = false;
    };
  }, [generation]);

  if (denied) return <AccessDeniedPage />;

  const needsRef = type === 'ORG_UNIT' || type === 'CASE_TYPE';
  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setFailure(null);
    setNotice(null);
    try {
      await addScope({
        subjectType: 'ROLE',
        subjectRef: role,
        scopeType: type,
        scopeRef: needsRef ? ref : null,
      });
      setNotice('access.scopes.added');
      reload();
    } catch (error) {
      setFailure(error instanceof ApiError && error.status === 422 ? 'access.scopes.refused' : 'access.scopes.failed');
    }
  };

  const remove = async (scope: AccessScope) => {
    setFailure(null);
    setNotice(null);
    try {
      await removeScope(scope.id);
      setNotice('access.scopes.removed');
      reload();
    } catch {
      setFailure('access.scopes.failed');
    }
  };

  const unitName = (unitId: string) => {
    const unit = units.find((candidate) => candidate.id === unitId);
    return unit === undefined ? unitId : locale === 'en' && unit.nameEn !== null ? unit.nameEn : unit.nameAr;
  };
  const describe = (scope: AccessScope) =>
    scope.scopeRef === null
      ? t(`access.scopes.type.${scope.scopeType}`)
      : `${t(`access.scopes.type.${scope.scopeType}`)}: ${scope.scopeType === 'ORG_UNIT' ? unitName(scope.scopeRef) : scope.scopeRef}`;

  return (
    <section>
      <AdminTabs />
      <h1>{t('access.scopes.title')}</h1>
      <p>{t('access.scopes.intro')}</p>
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
      {scopes === null ? null : scopes.length === 0 ? (
        <p>{t('access.scopes.none')}</p>
      ) : (
        <ul>
          {scopes.map((scope) => (
            <li key={scope.id}>
              <strong>{scope.subjectRef}</strong> — {describe(scope)}{' '}
              <button type="button" onClick={() => void remove(scope)}>
                {t('access.scopes.remove')}
              </button>
            </li>
          ))}
        </ul>
      )}
      <form className="case-form" aria-label={t('access.scopes.add')} noValidate onSubmit={(e) => void submit(e)}>
        <div className="case-field">
          <label htmlFor={`${id}-role`}>{t('access.scopes.subject')}</label>
          <select
            id={`${id}-role`}
            value={role}
            onChange={(e) => {
              setRole(e.target.value);
            }}
          >
            {roles.map((option) => (
              <option key={option.id} value={option.code}>
                {option.code}
              </option>
            ))}
          </select>
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-type`}>{t('access.scopes.type')}</label>
          <select
            id={`${id}-type`}
            value={type}
            onChange={(e) => {
              setType(e.target.value as ScopeType);
              setRef('');
            }}
          >
            {SCOPE_TYPES.map((option) => (
              <option key={option} value={option}>
                {t(`access.scopes.type.${option}`)}
              </option>
            ))}
          </select>
        </div>
        {!needsRef ? null : (
          <div className="case-field">
            <label htmlFor={`${id}-ref`}>{t('access.scopes.ref')}</label>
            <select
              id={`${id}-ref`}
              value={ref}
              onChange={(e) => {
                setRef(e.target.value);
              }}
            >
              <option value="" />
              {type === 'ORG_UNIT'
                ? units.map((unit) => (
                    <option key={unit.id} value={unit.id}>
                      {unitName(unit.id)}
                    </option>
                  ))
                : CASE_TYPES.map((caseType) => (
                    <option key={caseType} value={caseType}>
                      {t(`cases.type.${caseType}`)}
                    </option>
                  ))}
            </select>
          </div>
        )}
        <div className="case-actions">
          <button type="submit" className="primary">
            {t('access.scopes.add')}
          </button>
        </div>
      </form>
    </section>
  );
}
