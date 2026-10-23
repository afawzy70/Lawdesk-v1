import { describe, expect, it } from 'vitest';
import { DEFAULT_LOCALE, applyDocumentLocale, directionFor, resolveLocale, withLang } from './locale';

describe('locale', () => {
  it('defaults to English, left-to-right', () => {
    expect(DEFAULT_LOCALE).toBe('en');
    expect(directionFor(DEFAULT_LOCALE)).toBe('ltr');
  });

  it('maps Arabic to right-to-left', () => {
    expect(directionFor('ar')).toBe('rtl');
  });

  it.each([
    ['en', 'en'],
    ['ar', 'ar'],
    ['ar-EG', 'ar'],
    ['EN-us', 'en'],
    ['fr', 'en'],
    ['', 'en'],
    [undefined, 'en'],
    ['<script>', 'en'],
  ])('resolves %j to the supported locale %j', (input, expected) => {
    expect(resolveLocale(input)).toBe(expected);
  });

  it('sets lang and dir on the document element', () => {
    // A detached document has its own HTMLElement realm, so read attributes directly instead of jest-dom.
    const doc = document.implementation.createHTMLDocument('t');
    const attrs = () => [doc.documentElement.getAttribute('lang'), doc.documentElement.getAttribute('dir')];

    applyDocumentLocale('ar', doc);
    expect(attrs()).toEqual(['ar', 'rtl']);

    applyDocumentLocale('en', doc);
    expect(attrs()).toEqual(['en', 'ltr']);
  });

  it.each([
    ['/', 'en', '/?lang=en'],
    ['/cases', 'ar', '/cases?lang=ar'],
  ] as const)('builds the in-app link %j for %j as %j', (path, locale, expected) => {
    expect(withLang(path, locale)).toBe(expected);
  });

  it('targets the current document by default', () => {
    applyDocumentLocale('ar');
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    applyDocumentLocale('en');
    expect(document.documentElement).toHaveAttribute('dir', 'ltr');
  });
});
