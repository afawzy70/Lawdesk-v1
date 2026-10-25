import { authEn } from './auth.en';
import { adminEn } from './admin.en';
import { casesEn } from './cases.en';
import { courtsEn } from './courts.en';
import { proceedingsEn } from './proceedings.en';
import { operationsEn } from './operations.en';
import { reportsEn } from './reports.en';

/**
 * English message catalog. This object defines the set of message keys: every other catalog is typed as
 * `Messages`, so a missing or extra key fails type-checking (and `catalog.test.ts` checks it at runtime too).
 */
export const en = {
  'app.name': 'LawDesk',
  'footer.version': 'LawDesk v{version}',

  'preview.banner':
    'UAT test environment — enter test data only, never real client data. Areas marked “coming later” are not built yet.',
  'preview.bannerLabel': 'Preview notice',
  'preview.titleSuffix': '(Preview)',

  'shell.skipToContent': 'Skip to main content',
  'shell.primaryNav': 'Primary',
  'shell.menu': 'Menu',
  'shell.switchLanguage': 'العربية',
  'shell.caseSearch': 'Search cases by reference or title',
  'shell.caseSearchPlaceholder': 'Case reference or title',
  'shell.search': 'Search',

  'nav.dashboard': 'Dashboard',
  'nav.cases': 'Cases',
  'nav.courts': 'Courts',
  'nav.hearings': 'Hearings',
  'nav.judgments': 'Judgments',
  'nav.experts': 'Experts',
  'nav.documents': 'Documents',
  'nav.tasks': 'Tasks',
  'nav.reports': 'Reports',
  'nav.admin': 'Administration',

  'apiStatus.checking': 'Checking API…',
  'apiStatus.online': 'API online',
  'apiStatus.unavailable': 'API unavailable',

  'comingSoon.badge': 'Coming in a later phase',
  'comingSoon.body':
    'This feature is not available in this preview. It will be built and tested in a later phase of LawDesk.',

  'notFound.title': 'Page not found',
  'notFound.body': 'We could not find that page. Check the address, or go back to the home page.',
  'notFound.homeLink': 'Go to the home page',

  // Area messages, one file per workstream (architecture/CONTRACTS.md §6).
  ...authEn,
  ...adminEn,
  ...casesEn,
  ...courtsEn,
  ...proceedingsEn,
  ...operationsEn,
  ...reportsEn,
};

export type MessageKey = keyof typeof en;
export type Messages = Record<MessageKey, string>;
