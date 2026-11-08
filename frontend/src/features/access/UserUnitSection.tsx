import { type SyntheticEvent, useContext, useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import { AuthContext } from '../auth/AuthContext';
import { listOrgUnits, type OrgUnit, placeUser } from './api';

/** LD-050: the legal-department unit a user works in; a section head's scope follows it. For holders of ORG_MANAGE. */
export function UserUnitSection({ userId, orgUnitId }: { userId: string; orgUnitId: string | null }) {
  const { t, locale } = useI18n();
  const state = useContext(AuthContext)?.state;
  const id = useId();
  const allowed = state?.status === 'signedIn' && state.me.permissions.includes('ORG_MANAGE');
  const [units, setUnits] = useState<OrgUnit[]>([]);
  const [chosen, setChosen] = useState(orgUnitId ?? '');
  const [message, setMessage] = useState<{
    key: MessageKey;
    failed: boolean;
  } | null>(null);

  useEffect(() => {
    if (!allowed) return;
    let current = true;
    listOrgUnits('LEGAL', false).then(
      (found) => {
        if (current) setUnits(found);
      },
      () => {
        // The list stays empty: a user can still be taken out of a unit.
      },
    );
    return () => {
      current = false;
    };
  }, [allowed]);

  if (!allowed) return null;

  const save = async (event: SyntheticEvent) => {
    event.preventDefault();
    try {
      await placeUser(userId, chosen === '' ? null : chosen);
      setMessage({ key: 'access.org.saved', failed: false });
    } catch {
      setMessage({ key: 'access.org.failed', failed: true });
    }
  };

  return (
    <form aria-label={t('access.user.unit')} noValidate onSubmit={(e) => void save(e)}>
      <h2>{t('access.user.unit')}</h2>
      {message === null ? null : <p role={message.failed ? 'alert' : 'status'}>{t(message.key)}</p>}
      <label htmlFor={id}>{t('access.user.unit')}</label>
      <select
        id={id}
        value={chosen}
        onChange={(e) => {
          setChosen(e.target.value);
        }}
      >
        <option value="">{t('access.org.noParent')}</option>
        {units.map((unit) => (
          <option key={unit.id} value={unit.id}>
            {locale === 'en' && unit.nameEn !== null ? unit.nameEn : unit.nameAr}
          </option>
        ))}
      </select>
      <button type="submit">{t('access.user.save')}</button>
    </form>
  );
}
