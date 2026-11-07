import { useEffect, useId, useState } from 'react';
import type { MessageKey } from '../../../i18n/catalog';
import { useI18n } from '../../../i18n/I18nContext';
import { listCaseMembers, type MemberRef } from '../api';

interface Props {
  primary: string;
  alternate: string;
  onChange: (next: { primary: string; alternate: string }) => void;
  primaryError?: MessageKey | undefined;
  alternateError?: MessageKey | undefined;
}

/**
 * The specialist (required) and the alternate (optional) from the active members of the legal department. The same
 * person cannot be both: each list disables the other's choice. The server checks eligibility again.
 */
export function MemberFields({ primary, alternate, onChange, primaryError, alternateError }: Props) {
  const { t } = useI18n();
  const id = useId();
  const [members, setMembers] = useState<MemberRef[] | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    listCaseMembers(controller.signal).then(
      (list) => {
        if (!controller.signal.aborted) setMembers(list);
      },
      () => {
        if (!controller.signal.aborted) setFailed(true);
      },
    );
    return () => {
      controller.abort();
    };
  }, []);

  const list = members ?? [];
  const select = (
    name: 'primary' | 'alternate',
    label: MessageKey,
    value: string,
    other: string,
    error: MessageKey | undefined,
    required: boolean,
  ) => (
    <div className="case-field">
      <label htmlFor={`${id}-${name}`}>
        {t(label)}
        {required ? ' *' : ''}
      </label>
      <select
        id={`${id}-${name}`}
        aria-required={required ? 'true' : undefined}
        aria-invalid={error === undefined ? undefined : true}
        aria-describedby={error === undefined ? `${id}-hint` : `${id}-${name}-error ${id}-hint`}
        value={value}
        onChange={(event) => {
          onChange(
            name === 'primary'
              ? { primary: event.target.value, alternate: event.target.value === alternate ? '' : alternate }
              : { primary, alternate: event.target.value },
          );
        }}
      >
        <option value="">{t(name === 'primary' ? 'intake.choose' : 'intake.members.none')}</option>
        {list.map((member) => (
          <option key={member.id} value={member.id} disabled={member.id === other}>
            {member.displayName}
          </option>
        ))}
      </select>
      {error === undefined ? null : (
        <p id={`${id}-${name}-error`} className="case-field-error">
          {t(error)}
        </p>
      )}
    </div>
  );

  return (
    <>
      {failed ? (
        <p className="case-alert" role="alert">
          {t('intake.members.failed')}
        </p>
      ) : null}
      {select('primary', 'intake.members.primary', primary, alternate, primaryError, true)}
      {select('alternate', 'intake.members.alternate', alternate, primary, alternateError, false)}
      <p id={`${id}-hint`} className="case-field-hint">
        {t('intake.members.hint')}
      </p>
    </>
  );
}
