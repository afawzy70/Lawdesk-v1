import type { MessageKey } from '../../i18n/catalog';
import type { Classification, Direction, Finality, RelationType } from './api';

/** Message keys for the library's coded values; a code the screen does not know reads as "not specified". */
export const courtLevelKey = (level: string): MessageKey => `library.courtLevel.${level}` as MessageKey;
export const directionKey = (direction: Direction): MessageKey => `library.direction.${direction}`;
export const finalityKey = (finality: Finality): MessageKey => `library.finality.${finality}`;
export const relationKey = (type: RelationType): MessageKey => `library.relation.${type}`;
export const dimensionKey = (dimension: string): MessageKey => `library.dimension.${dimension}` as MessageKey;

/** A classification value in the language of the page (Arabic first; English when the page is English and has it). */
export function classificationLabel(item: Pick<Classification, 'labelAr' | 'labelEn'>, locale: string): string {
  return locale === 'en' && item.labelEn !== null && item.labelEn !== '' ? item.labelEn : item.labelAr;
}

/** "court, number/year" as a citation line; unknown parts are left out. */
export function citation(item: { courtName: string; caseNumber: string | null; caseYear: number | null }): string {
  const number =
    item.caseNumber === null ? '' : item.caseYear === null ? item.caseNumber : `${item.caseNumber}/${String(item.caseYear)}`;
  return number === '' ? item.courtName : `${item.courtName} - ${number}`;
}

/** The separator of a list of words in the language of the page. */
export const listSeparator = (locale: string): string => (locale === 'ar' ? '، ' : ', ');
