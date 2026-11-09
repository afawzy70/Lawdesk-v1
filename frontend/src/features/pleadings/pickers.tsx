import { getCaseParties, type CaseParty } from '../cases/api';
import { listCaseDocuments, type CaseDocument } from '../operations/documents/documentsApi';
import { numberOf } from '../proceedings/dates';
import { listProceedings, type Proceeding } from '../proceedings/proceedingsApi';
import type { MessageKey } from '../../i18n/catalog';
import { useLoad } from './useLoad';

export interface Pickers {
  parties: CaseParty[];
  documents: CaseDocument[];
  stages: Proceeding[];
}

/**
 * What a claim, defence or evidence form chooses from: the case's own parties, stored documents and proceedings. A list
 * that cannot be read is empty; the form still works without it.
 */
export function usePickers(caseId: string): Pickers {
  const parties = useLoad((signal) => getCaseParties(caseId, signal), [caseId]);
  const documents = useLoad((signal) => listCaseDocuments(caseId, signal), [caseId]);
  const stages = useLoad((signal) => listProceedings(caseId, signal), [caseId]);
  return { parties: parties.data ?? [], documents: documents.data ?? [], stages: stages.data ?? [] };
}

export const partyOptions = (parties: readonly CaseParty[]): (readonly [string, string])[] =>
  parties.map((p) => [p.partyId, p.name] as const);

export const documentOptions = (documents: readonly CaseDocument[]): (readonly [string, string])[] =>
  documents.map((d) => [d.id, d.title] as const);

export function stageOptions(
  stages: readonly Proceeding[],
  t: (key: MessageKey) => string,
): (readonly [string, string])[] {
  return stages.map((p) => [p.id, `${t(`proceedings.stage.${p.stage}`)} ${numberOf(p)}`] as const);
}
