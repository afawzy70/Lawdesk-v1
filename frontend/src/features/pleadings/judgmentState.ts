import { useState } from 'react';
import type { Judgment } from '../proceedings/proceedingsApi';
import {
  listJudgmentClaimResults,
  listJudgmentDefensePositions,
  saveClaimResults,
  saveDefensePositions,
  type ClaimResultInput,
  type DefensePositionInput,
  type JudgmentDetailsInput,
  type JudgmentStatus,
} from './api';
import { amountText, orNull, useLoad } from './useLoad';

/** The extended judgment (section 9) and the court's result on every claim and position on every defence (5 and 17). */

export interface DetailsState {
  chamber: string;
  typeId: string;
  natureId: string;
  operative: string;
  reasons: string;
  costs: string;
  fees: string;
  appealable: string;
  notifiedOn: string;
  status: string;
  copyDocumentId: string;
}

export const EMPTY_DETAILS: DetailsState = {
  chamber: '',
  typeId: '',
  natureId: '',
  operative: '',
  reasons: '',
  costs: '',
  fees: '',
  appealable: '',
  notifiedOn: '',
  status: '',
  copyDocumentId: '',
};

export function detailsOf(judgment: Judgment | undefined): DetailsState {
  const d = judgment?.details;
  if (d === undefined) return EMPTY_DETAILS;
  return {
    chamber: d.chamber ?? '',
    typeId: d.judgmentTypeId ?? '',
    natureId: d.natureId ?? '',
    operative: d.operativeText ?? '',
    reasons: d.reasons ?? '',
    costs: d.costsAmount ?? '',
    fees: d.lawyerFeesAmount ?? '',
    appealable: d.appealable === null ? '' : String(d.appealable),
    notifiedOn: d.notifiedOn ?? '',
    status: d.status ?? '',
    copyDocumentId: d.copyDocumentId ?? '',
  };
}

/** The request's {@code details}; for a new judgment with nothing filled in it is left out, so the legacy request is unchanged. */
export function detailsPayload(d: DetailsState, always: boolean): JudgmentDetailsInput | undefined {
  const payload: JudgmentDetailsInput = {
    chamber: orNull(d.chamber),
    judgmentTypeId: orNull(d.typeId),
    natureId: orNull(d.natureId),
    operativeText: orNull(d.operative),
    reasons: orNull(d.reasons),
    costsAmount: amountText(d.costs),
    lawyerFeesAmount: amountText(d.fees),
    appealable: d.appealable === '' ? null : d.appealable === 'true',
    notifiedOn: orNull(d.notifiedOn),
    status: d.status === '' ? null : (d.status as JudgmentStatus),
    copyDocumentId: orNull(d.copyDocumentId),
  };
  return always || Object.values(payload).some((v) => v !== null) ? payload : undefined;
}

export interface Entry {
  value: string;
  amount: string;
  reasons: string;
}

export const BLANK: Entry = { value: '', amount: '', reasons: '' };

export interface Extras {
  details: DetailsState;
  setDetails: (next: DetailsState) => void;
  claims: Record<string, Entry>;
  defenses: Record<string, Entry>;
  setEntry: (kind: 'claims' | 'defenses', id: string, entry: Entry) => void;
  /** Writes the chosen results and positions of the judgment; nothing is sent when none is chosen. */
  save: (judgmentId: string) => Promise<void>;
}

/** The state of the extended fields of one judgment form, prefilled from the judgment's recorded results when it has some. */
export function useExtras(initial: DetailsState, judgmentId: string | null): Extras {
  const [details, setDetails] = useState(initial);
  // What the user changed on screen; everything else comes from the results already recorded for the judgment.
  const [edits, setEdits] = useState<{ claims: Record<string, Entry>; defenses: Record<string, Entry> }>({
    claims: {},
    defenses: {},
  });
  const recordedClaims = useLoad(
    (signal) => (judgmentId === null ? Promise.resolve([]) : listJudgmentClaimResults(judgmentId, signal)),
    [judgmentId],
  );
  const recordedDefenses = useLoad(
    (signal) => (judgmentId === null ? Promise.resolve([]) : listJudgmentDefensePositions(judgmentId, signal)),
    [judgmentId],
  );
  const claims: Record<string, Entry> = {
    ...Object.fromEntries(
      (recordedClaims.data ?? []).map((r) => [r.claimId, { value: r.result ?? '', amount: r.awardedAmount ?? '', reasons: r.reasons ?? '' }]),
    ),
    ...edits.claims,
  };
  const defenses: Record<string, Entry> = {
    ...Object.fromEntries(
      (recordedDefenses.data ?? []).map((r) => [r.defenseId, { value: r.position ?? '', amount: '', reasons: r.reasons ?? '' }]),
    ),
    ...edits.defenses,
  };

  return {
    details,
    setDetails,
    claims,
    defenses,
    setEntry: (kind, id, entry) => {
      setEdits((current) => ({ ...current, [kind]: { ...current[kind], [id]: entry } }));
    },
    save: async (id) => {
      const results: ClaimResultInput[] = Object.entries(claims)
        .filter(([, e]) => e.value !== '')
        .map(([claimId, e]) => ({
          claimId,
          result: e.value as ClaimResultInput['result'],
          operative: null,
          reasons: orNull(e.reasons),
          awardedAmount: amountText(e.amount),
          notes: null,
        }));
      if (results.length > 0) await saveClaimResults(id, results);
      const positions: DefensePositionInput[] = Object.entries(defenses)
        .filter(([, e]) => e.value !== '')
        .map(([defenseId, e]) => ({
          defenseId,
          position: e.value as DefensePositionInput['position'],
          reasons: orNull(e.reasons),
          operative: null,
        }));
      if (positions.length > 0) await saveDefensePositions(id, positions);
    },
  };
}

