import { useId, useState, type KeyboardEvent } from 'react';
import { api } from '../../../api/http';
import { PagedSelect, type PagedResult } from '../../../components/PagedSelect';
import { useI18n } from '../../../i18n/I18nContext';
import { PARTY_KINDS, SIDES, type IntakeParty, type Party, type PartyKind, type Side } from '../api';
import { catalogCodes } from '../../../catalog/catalogStore';

/** A party the user added on the intake screen, with what the list shows and what the request will carry. */
export interface DraftParty {
  key: string;
  name: string;
  kind: PartyKind;
  entry: IntakeParty;
}

interface Props {
  position: Side | '';
  onPosition: (position: Side) => void;
  parties: DraftParty[];
  onParties: (parties: DraftParty[]) => void;
  /** True after the position changed while other parties were listed: they must be reviewed before saving. */
  reviewPending: boolean;
  onReviewed: () => void;
  onSwap: () => void;
  positionError?: string | undefined;
}

function listParties(page: number, q: string, signal: AbortSignal): Promise<PagedResult<Party>> {
  const query = new URLSearchParams({ page: String(page), size: '20' });
  if (q.trim() !== '') query.set('q', q.trim());
  return api<PagedResult<Party>>(`/api/v1/parties?${query.toString()}`, { signal }).then((result) => ({
    ...result,
    items: result.items.filter((party) => party.authority !== true),
  }));
}

let counter = 0;
const nextKey = () => `party-${String((counter += 1))}`;

/**
 * The authority's position (مدعي / مدعى عليه) and the parties of both sides. The authority is a party in its own right,
 * shown on the side of its position; other parties are added to either side, new or already registered. A party's
 * capacity is its side, so it cannot contradict it. Changing the position never flips or drops anyone silently: the
 * list asks for a review before the case can be saved.
 */
export function IntakePartiesSection(props: Props) {
  const { position, onPosition, parties, onParties, reviewPending, onReviewed, onSwap, positionError } = props;
  const { t } = useI18n();
  const id = useId();

  const column = (side: Side) => {
    const others = parties.filter((party) => party.entry.side === side);
    return (
      <section className="intake-side" aria-labelledby={`${id}-${side}`} key={side}>
        <h3 id={`${id}-${side}`}>{t(`intake.side.${side}`)}</h3>
        <ul>
          {position === side ? (
            <li className="intake-authority">
              <strong>{t('intake.authority')}</strong> <span className="case-tag">{t('intake.authority.tag')}</span>
            </li>
          ) : null}
          {others.map((party) => (
            <li key={party.key}>
              {party.name}{' '}
              <button
                type="button"
                aria-label={t('intake.party.remove', { name: party.name })}
                onClick={() => {
                  onParties(parties.filter((other) => other.key !== party.key));
                }}
              >
                {t('cases.parties.remove')}
              </button>
            </li>
          ))}
          {position !== side && others.length === 0 ? <li className="case-field-hint">{t('intake.side.none')}</li> : null}
        </ul>
        <AddParty side={side} parties={parties} onParties={onParties} />
      </section>
    );
  };

  return (
    <div>
      <fieldset className="case-fieldset" aria-describedby={positionError === undefined ? undefined : `${id}-pos-error`}>
        <legend>{t('intake.position.legend')} *</legend>
        {catalogCodes('PARTY_SIDE_GROUP', SIDES).map((side) => (
          <label key={side} className="case-radio">
            <input
              type="radio"
              name={`${id}-position`}
              value={side}
              checked={position === side}
              onChange={() => {
                onPosition(side);
              }}
            />
            {t(`intake.position.${side}`)}
          </label>
        ))}
        {positionError === undefined ? null : (
          <p id={`${id}-pos-error`} className="case-field-error">
            {positionError}
          </p>
        )}
      </fieldset>

      {reviewPending ? (
        <div className="case-alert" role="alert">
          <p>{t('intake.review.changed')}</p>
          <button type="button" onClick={onSwap}>
            {t('intake.review.swap')}
          </button>{' '}
          <button type="button" className="primary" onClick={onReviewed}>
            {t('intake.review.confirm')}
          </button>
        </div>
      ) : null}

      {position === '' ? null : <div className="intake-sides">{catalogCodes('PARTY_SIDE_GROUP', SIDES).map(column)}</div>}
    </div>
  );
}

/** Adds a party to one side only: each side of the case has its own form, so a party cannot land on the wrong side. */
function AddParty({
  side,
  parties,
  onParties,
}: {
  side: Side;
  parties: DraftParty[];
  onParties: (parties: DraftParty[]) => void;
}) {
  const { t } = useI18n();
  const id = useId();
  const [mode, setMode] = useState<'new' | 'registered'>('new');
  const [kind, setKind] = useState<PartyKind>('PERSON');
  const [name, setName] = useState('');
  const [number, setNumber] = useState('');
  const [phone, setPhone] = useState('');
  const [registered, setRegistered] = useState<Party | null>(null);
  const [registeredId, setRegisteredId] = useState('');
  const [problem, setProblem] = useState<'name' | 'party' | 'already' | null>(null);

  const add = () => {
    if (mode === 'new') {
      const trimmed = name.trim();
      if (trimmed === '') {
        setProblem('name');
        return;
      }
      const identifier = number.trim() === '' ? null : number.trim();
      onParties([
        ...parties,
        {
          key: nextKey(),
          name: trimmed,
          kind,
          entry: {
            side,
            newParty: {
              kind,
              name: trimmed,
              nationalId: kind === 'PERSON' ? identifier : null,
              registrationNo: kind === 'ORGANIZATION' ? identifier : null,
              phone: phone.trim() === '' ? null : phone.trim(),
            },
          },
        },
      ]);
      setName('');
      setNumber('');
      setPhone('');
    } else {
      if (registered === null) {
        setProblem('party');
        return;
      }
      if (parties.some((party) => party.entry.partyId === registered.id)) {
        setProblem('already');
        return;
      }
      onParties([
        ...parties,
        { key: nextKey(), name: registered.name, kind: registered.kind, entry: { side, partyId: registered.id } },
      ]);
      setRegistered(null);
      setRegisteredId('');
    }
    setProblem(null);
  };

  // Enter inside these fields adds the party; it must not submit the whole registration.
  const onEnter = (event: KeyboardEvent) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      add();
    }
  };

  const message = problem === null ? null : t(
    problem === 'name' ? 'intake.party.needName' : problem === 'party' ? 'intake.party.needParty' : 'intake.party.already',
  );

  return (
    <div className="intake-add-party" role="group" aria-labelledby={`${id}-title`}>
      <h4 id={`${id}-title`}>{t('intake.party.addToSide', { side: t(`intake.side.${side}`) })}</h4>
      <div role="radiogroup" aria-label={t('intake.party.addToSide', { side: t(`intake.side.${side}`) })} className="case-radios">
        {(['new', 'registered'] as const).map((option) => (
          <label key={option} className="case-radio">
            <input
              type="radio"
              name={`${id}-mode`}
              checked={mode === option}
              onChange={() => {
                setMode(option);
                setProblem(null);
              }}
            />
            {t(`intake.party.mode.${option}`)}
          </label>
        ))}
      </div>
      {mode === 'new' ? (
        <>
          <div className="case-field">
            <label htmlFor={`${id}-kind`}>{t('intake.party.kind')}</label>
            <select
              id={`${id}-kind`}
              value={kind}
              onChange={(event) => {
                setKind(event.target.value as PartyKind);
              }}
            >
              {catalogCodes('PARTY_KIND', PARTY_KINDS).map((option) => (
                <option key={option} value={option}>
                  {t(`cases.kind.${option}`)}
                </option>
              ))}
            </select>
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-name`}>{t('intake.party.name')}</label>
            <input
              id={`${id}-name`}
              type="text"
              maxLength={200}
              value={name}
              onKeyDown={onEnter}
              onChange={(event) => {
                setName(event.target.value);
              }}
            />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-number`}>
              {t(kind === 'PERSON' ? 'intake.party.number' : 'intake.party.registrationNo')}
            </label>
            <input
              id={`${id}-number`}
              type="text"
              maxLength={50}
              value={number}
              onKeyDown={onEnter}
              onChange={(event) => {
                setNumber(event.target.value);
              }}
            />
          </div>
          <div className="case-field">
            <label htmlFor={`${id}-phone`}>{t('intake.party.phone')}</label>
            <input
              id={`${id}-phone`}
              type="tel"
              maxLength={30}
              value={phone}
              onKeyDown={onEnter}
              onChange={(event) => {
                setPhone(event.target.value);
              }}
            />
          </div>
        </>
      ) : (
        <div className="case-field">
          <label htmlFor={`${id}-registered`}>{t('intake.party.findLabel')}</label>
          <PagedSelect<Party>
            selectProps={{ id: `${id}-registered` }}
            value={registeredId}
            onChange={setRegisteredId}
            onSelect={setRegistered}
            load={listParties}
            optionLabel={(party) => `${party.name} (${t(`cases.kind.${party.kind}`)})`}
            serverSearch
            searchLabel={t('intake.party.find')}
            chooseLabel={t('intake.choose')}
          />
        </div>
      )}
      {message === null ? null : (
        <p className="case-field-error" role="alert">
          {message}
        </p>
      )}
      <button type="button" onClick={add}>
        {t('intake.party.add')}
      </button>
    </div>
  );
}
