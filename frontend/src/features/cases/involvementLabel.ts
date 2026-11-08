import type { Involvement } from './api';

/** Sector, department and section as the case recorded them, most general first. */
export function unitLabel(involvement: Involvement): string {
  return [involvement.sectorName, involvement.departmentName, involvement.sectionName]
    .filter((part): part is string => part !== null)
    .join(' / ');
}
