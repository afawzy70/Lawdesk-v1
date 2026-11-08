import type { OrgUnit } from './api';

/** The units in tree order with their depth, so a section shows under its department. */
export function arrange(units: readonly OrgUnit[]): { unit: OrgUnit; depth: number }[] {
  const byParent = new Map<string | null, OrgUnit[]>();
  const ids = new Set(units.map((unit) => unit.id));
  for (const unit of units) {
    const key = unit.parentId !== null && ids.has(unit.parentId) ? unit.parentId : null;
    byParent.set(key, [...(byParent.get(key) ?? []), unit]);
  }
  const ordered: { unit: OrgUnit; depth: number }[] = [];
  const visit = (parent: string | null, depth: number) => {
    for (const unit of byParent.get(parent) ?? []) {
      ordered.push({ unit, depth });
      visit(unit.id, depth + 1);
    }
  };
  visit(null, 0);
  return ordered;
}
