import { describe, expect, it } from 'vitest';
import { searchQuery, EMPTY_FILTERS } from './api';
import { citation, classificationLabel, listSeparator } from './labels';

describe('library helpers', () => {
  it('names a classification in the page language and falls back to Arabic', () => {
    const both = { labelAr: 'عقود', labelEn: 'Contracts' };
    expect(classificationLabel(both, 'ar')).toBe('عقود');
    expect(classificationLabel(both, 'en')).toBe('Contracts');
    expect(classificationLabel({ labelAr: 'عقود', labelEn: null }, 'en')).toBe('عقود');
    expect(classificationLabel({ labelAr: 'عقود', labelEn: '' }, 'en')).toBe('عقود');
  });

  it('cites a judgment with the parts that are known', () => {
    expect(citation({ courtName: 'محكمة', caseNumber: '5', caseYear: 2020 })).toBe('محكمة - 5/2020');
    expect(citation({ courtName: 'محكمة', caseNumber: '5', caseYear: null })).toBe('محكمة - 5');
    expect(citation({ courtName: 'محكمة', caseNumber: null, caseYear: 2020 })).toBe('محكمة');
  });

  it('separates words with the comma of the language', () => {
    expect(listSeparator('ar')).toBe('، ');
    expect(listSeparator('en')).toBe(', ');
  });

  it('leaves out empty filters and always pages', () => {
    expect(searchQuery(EMPTY_FILTERS)).toBe('page=0&size=20');
    expect(searchQuery({ ...EMPTY_FILTERS, q: ' عقد ', includeWithdrawn: true, page: 2 })).toBe(
      'q=%D8%B9%D9%82%D8%AF&includeWithdrawn=true&page=2&size=20',
    );
  });
});
