import { useEffect, useId, useState, type SyntheticEvent } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { useI18n } from '../../i18n/I18nContext';
import {
  addCaseParty,
  createParty,
  getCaseParties,
  PARTY_KINDS,
  PARTY_ROLES,
  SIDES,
  removeCaseParty,
  searchParties,
  type CaseParty,
  type Party,
  type PartyKind,
  type PartyRole,
  type Side,
} from './api';
import { PartyCases } from './PartyCases';
import { isStatus, problemMessage } from './problems';

function explain(error: unknown): MessageKey {
  if (isStatus(error, 409)) return 'cases.parties.conflict';
  if (isStatus(error, 400) || isStatus(error, 422, 'PARTY_KIND_MISMATCH')) return 'cases.parties.invalid';
  return problemMessage(error, 'cases.parties.failed');
}

interface Props {
  caseId: string;
  /** Set on cases registered through the intake: parties then have a side and the role follows from it. */
  authorityPosition: Side | null;
  generation: number;
  /** False for an archived case: its parties are shown but cannot change. */
  editable: boolean;
  onChanged: () => void;
}

interface Choice {
  role: PartyRole;
  side: Side;
}

/** The role of a party on an older case, or its side on a case registered through the intake (LD-045). */
function RoleOrSide({
  position,
  value,
  onChange,
}: {
  position: Side | null;
  value: Choice;
  onChange: (next: Choice) => void;
}) {
  const { t } = useI18n();
  const id = useId();
  return (
    <div className="case-field">
      <label htmlFor={id}>{t(position === null ? 'cases.parties.role' : 'intake.parties.side')}</label>
      {position === null ? (
        <select
          id={id}
          value={value.role}
          onChange={(e) => {
            onChange({ ...value, role: e.target.value as PartyRole });
          }}
        >
          {PARTY_ROLES.map((role) => (
            <option key={role} value={role}>
              {t(`cases.role.${role}`)}
            </option>
          ))}
        </select>
      ) : (
        <select
          id={id}
          value={value.side}
          onChange={(e) => {
            onChange({ ...value, side: e.target.value as Side });
          }}
        >
          {SIDES.map((side) => (
            <option key={side} value={side}>
              {t(`intake.position.${side}`)}
            </option>
          ))}
        </select>
      )}
    </div>
  );
}

function Failure({ message }: { message: MessageKey | null }) {
  const { t } = useI18n();
  return message === null ? null : (
    <p className="case-alert" role="alert">
      {t(message)}
    </p>
  );
}

/** Creates a party and links it to the case in one step. */
function NewPartyForm({ caseId, position, onChanged }: { caseId: string; position: Side | null; onChanged: () => void }) {
  const { t } = useI18n();
  const id = useId();
  const [kind, setKind] = useState<PartyKind>('PERSON');
  const [name, setName] = useState('');
  const [identifier, setIdentifier] = useState('');
  const [choice, setChoice] = useState<Choice>({ role: 'CLIENT', side: position === 'PLAINTIFF' ? 'DEFENDANT' : 'PLAINTIFF' });
  const [failure, setFailure] = useState<MessageKey | null>(null);

  const submit = async (event: SyntheticEvent) => {
    event.preventDefault();
    setFailure(null);
    try {
      const party = await createParty({ kind, name, identifier });
      await addCaseParty(caseId, party.id, choice.role, position === null ? undefined : choice.side);
      onChanged();
    } catch (error) {
      setFailure(explain(error));
    }
  };

  return (
    <form className="case-form" aria-label={t('cases.parties.new')} noValidate onSubmit={(e) => void submit(e)}>
      <h3>{t('cases.parties.new')}</h3>
      <Failure message={failure} />
      <div className="case-field">
        <label htmlFor={`${id}-kind`}>{t('cases.parties.kind')}</label>
        <select
          id={`${id}-kind`}
          value={kind}
          onChange={(e) => {
            setKind(e.target.value as PartyKind);
          }}
        >
          {PARTY_KINDS.map((option) => (
            <option key={option} value={option}>
              {t(`cases.kind.${option}`)}
            </option>
          ))}
        </select>
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-name`}>{t('cases.parties.name')}</label>
        <input
          id={`${id}-name`}
          type="text"
          maxLength={200}
          aria-required="true"
          value={name}
          onChange={(e) => {
            setName(e.target.value);
          }}
        />
      </div>
      <div className="case-field">
        <label htmlFor={`${id}-identifier`}>
          {t(kind === 'PERSON' ? 'cases.parties.nationalId' : 'cases.parties.registrationNo')}
        </label>
        <input
          id={`${id}-identifier`}
          type="text"
                    maxLength={50}
          value={identifier}
          onChange={(e) => {
            setIdentifier(e.target.value);
          }}
        />
      </div>
      <RoleOrSide position={position} value={choice} onChange={setChoice} />
      <div className="case-actions">
        <button type="submit" className="primary">
          {t('cases.parties.add')}
        </button>
      </div>
    </form>
  );
}

/** Finds an existing party by name or identifier and links it to the case. */
function FindPartyForm({ caseId, position, onChanged }: { caseId: string; position: Side | null; onChanged: () => void }) {
  const { t } = useI18n();
  const id = useId();
  const [q, setQ] = useState('');
  const [choice, setChoice] = useState<Choice>({ role: 'CLIENT', side: position === 'PLAINTIFF' ? 'DEFENDANT' : 'PLAINTIFF' });
  const [found, setFound] = useState<Party[] | null>(null);
  const [failure, setFailure] = useState<MessageKey | null>(null);

  const find = async (event: SyntheticEvent) => {
    event.preventDefault();
    setFailure(null);
    try {
      setFound((await searchParties(q)).items);
    } catch (error) {
      setFailure(problemMessage(error, 'cases.list.failed'));
    }
  };

  const add = async (party: Party) => {
    setFailure(null);
    try {
      await addCaseParty(caseId, party.id, choice.role, position === null ? undefined : choice.side);
      onChanged();
    } catch (error) {
      setFailure(explain(error));
    }
  };

  return (
    <form role="search" aria-label={t('cases.parties.find')} className="case-form" onSubmit={(e) => void find(e)}>
      <h3>{t('cases.parties.find')}</h3>
      <Failure message={failure} />
      <div className="case-field">
        <label htmlFor={`${id}-q`}>{t('cases.parties.query')}</label>
        <input
          id={`${id}-q`}
          type="search"
          maxLength={100}
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
          }}
        />
      </div>
      <RoleOrSide position={position} value={choice} onChange={setChoice} />
      <div className="case-actions">
        <button type="submit">{t('cases.parties.findButton')}</button>
      </div>
      {found !== null && found.length === 0 ? <p>{t('cases.parties.noMatch')}</p> : null}
      {found !== null && found.length > 0 ? (
        <ul className="case-found">
          {found.map((party) => (
            <li key={party.id}>
              <button
                type="button"
                onClick={() => {
                  void add(party);
                }}
              >
                {t('cases.parties.addNamed', { name: party.name })}
              </button>{' '}
              {t(`cases.kind.${party.kind}`)}
            </li>
          ))}
        </ul>
      ) : null}
    </form>
  );
}

/** LD-016: the parties of a case with their roles; adding and removing them is recorded in the timeline. */
export function CasePartiesSection({ caseId, authorityPosition, generation, editable, onChanged }: Props) {
  const { t } = useI18n();
  const headingId = useId();
  const key = `${caseId}#${String(generation)}`;
  const [result, setResult] = useState<{ key: string; parties: CaseParty[] | null; failure: MessageKey | null }>();
  const [removeFailure, setRemoveFailure] = useState<MessageKey | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getCaseParties(caseId, controller.signal).then(
      (parties) => {
        if (!controller.signal.aborted) setResult({ key, parties, failure: null });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setResult({ key, parties: null, failure: problemMessage(error, 'cases.edit.loadFailed') });
        }
      },
    );
    return () => {
      controller.abort();
    };
  }, [caseId, key]);

  const remove = async (party: CaseParty) => {
    setRemoveFailure(null);
    try {
      await removeCaseParty(caseId, party.partyId);
      onChanged();
    } catch (error) {
      setRemoveFailure(explain(error));
    }
  };

  const current = result?.key === key ? result : undefined;
  return (
    <section aria-labelledby={headingId} className="case-section">
      <h2 id={headingId}>{t('cases.parties.title')}</h2>
      {authorityPosition === null ? null : (
        <p className="case-position">
          <strong>{t('intake.details.position')}:</strong> {t(`intake.position.${authorityPosition}`)}
        </p>
      )}
      <Failure message={current?.failure ?? removeFailure} />
      {current?.parties?.length === 0 ? <p>{t('cases.parties.empty')}</p> : null}
      {current?.parties !== undefined && current.parties !== null && current.parties.length > 0 ? (
        <div className="table-scroll" role="region" tabIndex={0} aria-labelledby={headingId}>
          <table className="case-table">
            <caption className="visually-hidden">{t('cases.parties.title')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('cases.parties.name')}</th>
                <th scope="col">{t('cases.parties.kind')}</th>
                <th scope="col">{t(authorityPosition === null ? 'cases.parties.role' : 'intake.parties.side')}</th>
                {editable ? (
                  <th scope="col">
                    <span className="visually-hidden">{t('cases.parties.remove')}</span>
                  </th>
                ) : null}
              </tr>
            </thead>
            <tbody>
              {current.parties.map((party) => (
                <tr key={party.partyId}>
                  <td>
                    {party.name}
                    {party.authority === true ? null : <PartyCases partyId={party.partyId} currentCaseId={caseId} />}
                  </td>
                  <td>{t(`cases.kind.${party.kind}`)}</td>
                  <td>
                    {party.side == null ? t(`cases.role.${party.role}`) : t(`intake.position.${party.side}`)}
                    {party.authority === true ? <> <span className="case-tag">{t('intake.authority.tag')}</span></> : null}
                  </td>
                  {editable ? (
                    <td>
                      {party.authority === true ? null : <button
                        type="button"
                        aria-label={t('cases.parties.removeNamed', { name: party.name })}
                        onClick={() => {
                          void remove(party);
                        }}
                      >
                        {t('cases.parties.remove')}
                      </button>}
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
      {editable ? (
        <div className="case-party-forms">
          <NewPartyForm key={key} caseId={caseId} position={authorityPosition} onChanged={onChanged} />
          <FindPartyForm key={key} caseId={caseId} position={authorityPosition} onChanged={onChanged} />
        </div>
      ) : null}
    </section>
  );
}
