import { type SyntheticEvent, useCallback, useEffect, useId, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AccessDeniedPage } from '../auth/AccessDeniedPage';
import { AdminTabs } from '../admin/AdminTabs';
import {
  createOrgUnit,
  listOrgUnits,
  ORG_LEVELS,
  type OrgLevel,
  type OrgTree,
  type OrgUnit,
  updateOrgUnit,
} from './api';
import { arrange } from './orgTree';

function explain(error: unknown): MessageKey {
  if (error instanceof ApiError && error.status === 409) return 'access.org.conflict';
  if (error instanceof ApiError && (error.status === 400 || error.status === 422)) return 'access.org.invalid';
  return 'access.org.failed';
}

/** LD-050: the organisation tree. Units are deactivated, never deleted, so cases keep a valid reference. */
export function OrgUnitsPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('access.org.title'));
  const id = useId();
  const [tree, setTree] = useState<OrgTree>('LEGAL');
  const [units, setUnits] = useState<OrgUnit[] | null>(null);
  const [denied, setDenied] = useState(false);
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [notice, setNotice] = useState<MessageKey | null>(null);
  const [level, setLevel] = useState<OrgLevel>('DEPARTMENT');
  const [parentId, setParentId] = useState('');
  const [nameAr, setNameAr] = useState('');
  const [nameEn, setNameEn] = useState('');
  const [generation, setGeneration] = useState(0);

  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);

  useEffect(() => {
    let current = true;
    listOrgUnits(tree).then(
      (list) => {
        if (current) setUnits(list);
      },
      (error: unknown) => {
        if (!current) return;
        if (error instanceof ApiError && error.status === 403) setDenied(true);
        else setFailure('access.org.failed');
      },
    );
    return () => {
      current = false;
    };
  }, [tree, generation]);

  if (denied) return <AccessDeniedPage />;

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setFailure(null);
    setNotice(null);
    try {
      await createOrgUnit({
        tree,
        parentId: parentId === '' ? null : parentId,
        level,
        nameAr,
        nameEn: nameEn === '' ? null : nameEn,
      });
      setNameAr('');
      setNameEn('');
      setNotice('access.org.added');
      reload();
    } catch (error) {
      setFailure(explain(error));
    }
  };

  const toggle = async (unit: OrgUnit) => {
    setFailure(null);
    setNotice(null);
    try {
      await updateOrgUnit(unit, { active: !unit.active });
      setNotice('access.org.saved');
      reload();
    } catch (error) {
      setFailure(explain(error));
    }
  };

  const rows = arrange(units ?? []);
  const name = (unit: OrgUnit) => (locale === 'en' && unit.nameEn !== null ? unit.nameEn : unit.nameAr);
  return (
    <section>
      <AdminTabs />
      <h1>{t('access.org.title')}</h1>
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
      <div className="case-field">
        <label htmlFor={`${id}-tree`}>{t('access.org.tree')}</label>
        <select
          id={`${id}-tree`}
          value={tree}
          onChange={(e) => {
            setUnits(null);
            setTree(e.target.value as OrgTree);
            setParentId('');
          }}
        >
          <option value="LEGAL">{t('access.org.tree.LEGAL')}</option>
          <option value="AUTHORITY">{t('access.org.tree.AUTHORITY')}</option>
        </select>
      </div>
      {units === null ? null : rows.length === 0 ? (
        <p>{t('access.org.none')}</p>
      ) : (
        <ul aria-label={t('access.org.list')} className="org-tree">
          {rows.map(({ unit, depth }) => (
            <li key={unit.id} style={{ paddingInlineStart: `${String(depth * 1.5)}rem` }}>
              <span>{name(unit)}</span> <small>({t(`access.org.level.${unit.level}`)})</small>
              {unit.active ? null : <small> — {t('access.org.inactive')}</small>}{' '}
              <button type="button" onClick={() => void toggle(unit)}>
                {t(unit.active ? 'access.org.deactivate' : 'access.org.activate')}
              </button>
            </li>
          ))}
        </ul>
      )}
      <form className="case-form" aria-label={t('access.org.add')} noValidate onSubmit={(e) => void submit(e)}>
        <h2>{t('access.org.add')}</h2>
        <div className="case-field">
          <label htmlFor={`${id}-level`}>{t('access.org.level')}</label>
          <select
            id={`${id}-level`}
            value={level}
            onChange={(e) => {
              setLevel(e.target.value as OrgLevel);
            }}
          >
            {ORG_LEVELS.map((option) => (
              <option key={option} value={option}>
                {t(`access.org.level.${option}`)}
              </option>
            ))}
          </select>
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-parent`}>{t('access.org.parent')}</label>
          <select
            id={`${id}-parent`}
            value={parentId}
            onChange={(e) => {
              setParentId(e.target.value);
            }}
          >
            <option value="">{t('access.org.noParent')}</option>
            {rows
              .filter(({ unit }) => unit.active)
              .map(({ unit }) => (
                <option key={unit.id} value={unit.id}>
                  {name(unit)}
                </option>
              ))}
          </select>
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-ar`}>{t('access.org.nameAr')}</label>
          <input
            id={`${id}-ar`}
            type="text"
            maxLength={200}
            aria-required="true"
            value={nameAr}
            onChange={(e) => {
              setNameAr(e.target.value);
            }}
          />
        </div>
        <div className="case-field">
          <label htmlFor={`${id}-en`}>{t('access.org.nameEn')}</label>
          <input
            id={`${id}-en`}
            type="text"
            maxLength={200}
            value={nameEn}
            onChange={(e) => {
              setNameEn(e.target.value);
            }}
          />
        </div>
        <div className="case-actions">
          <button type="submit" className="primary">
            {t('access.org.add')}
          </button>
        </div>
      </form>
    </section>
  );
}
