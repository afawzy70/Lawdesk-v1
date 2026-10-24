import { describe, expect, it } from 'vitest';
import { ar } from './messages/ar';
import { en } from './messages/en';
import { catalogs, translate } from './catalog';

const ARABIC_LETTER = /[؀-ۿ]/;
/**
 * Brand name and version string stay in Latin script in every language; the language toggle names the *other*
 * language in its own script ("English" in the Arabic catalog, "العربية" in the English one).
 */
const LATIN_ONLY_KEYS = new Set(['app.name', 'footer.version', 'shell.switchLanguage']);

describe('message catalogs', () => {
  it('registers a catalog for every supported locale', () => {
    expect(Object.keys(catalogs).sort()).toEqual(['ar', 'en']);
  });

  it('English and Arabic have exactly the same keys', () => {
    expect(Object.keys(ar).sort()).toEqual(Object.keys(en).sort());
  });

  it('has no empty messages', () => {
    for (const catalog of [en, ar]) {
      for (const [key, value] of Object.entries(catalog)) {
        expect(value.trim(), key).not.toBe('');
      }
    }
  });

  it('writes Arabic messages in Arabic script, not transliteration', () => {
    for (const [key, value] of Object.entries(ar)) {
      if (LATIN_ONLY_KEYS.has(key)) continue;
      expect(value, key).toMatch(ARABIC_LETTER);
    }
  });

  it('uses the same placeholders in both languages', () => {
    const placeholders = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
    for (const key of Object.keys(en) as (keyof typeof en)[]) {
      expect(placeholders(ar[key]), key).toEqual(placeholders(en[key]));
    }
  });

  // LD-030: the dashboard shows real figures, so the sample-data messages of the preview are gone.
  it('keeps no sample-data messages of the retired dashboard preview', () => {
    const retired = Object.keys(en).filter((key) =>
      /^(dashboard|kpi|hearings|stage|stages|sample)\./.test(key) || key === 'preview.sampleData',
    );

    expect(retired).toEqual([]);
  });

  it('carries the agreed preview notice in both languages', () => {
    expect(en['preview.banner']).toBe(
      'UAT test environment — enter test data only, never real client data. Areas marked “coming later” are not built yet.',
    );
    expect(ar['preview.banner']).toBe(
      'بيئة اختبار UAT — أدخل بيانات تجريبية فقط، ولا تُدخل بيانات موكلين حقيقية. الأقسام المعلَّمة «لاحقًا» لم تُبنَ بعد.',
    );
    expect(en['preview.titleSuffix']).toBe('(Preview)');
    expect(ar['preview.titleSuffix']).toBe('(معاينة)');
  });
});

describe('translate', () => {
  it('looks up a message for the locale', () => {
    expect(translate('en', 'nav.cases')).toBe('Cases');
    expect(translate('ar', 'nav.cases')).toBe('القضايا');
  });

  it('interpolates named placeholders', () => {
    expect(translate('en', 'footer.version', { version: '1.2.3' })).toBe('LawDesk v1.2.3');
    expect(translate('ar', 'footer.version', { version: '1.2.3' })).toBe(ar['footer.version'].replace('{version}', '1.2.3'));
  });

  it('leaves missing placeholders visible instead of failing silently', () => {
    expect(translate('en', 'footer.version')).toBe('LawDesk v{version}');
    expect(translate('en', 'footer.version', { other: 'x' })).toBe('LawDesk v{version}');
  });

  it('fills the placeholders it is given and leaves the others visible', () => {
    expect(translate('en', 'courts.pageOf', { page: '1' })).toBe('Page 1 of {pages}');
  });
});
