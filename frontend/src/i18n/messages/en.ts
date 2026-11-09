import { authEn } from './auth.en';
import { adminEn } from './admin.en';
import { accessEn } from './access.en';
import { casesEn } from './cases.en';
import { courtsEn } from './courts.en';
import { proceedingsEn } from './proceedings.en';
import { intakeEn } from './intake.en';
import { selectorEn } from './selector.en';
import { operationsEn } from './operations.en';
import { reportsEn } from './reports.en';
import { libraryEn } from './library.en';
import { wp3En } from './wp3.en';
import { deadlinesEn } from './deadlines.en';
import { pleadingsEn } from './pleadings.en';
import { wp9En } from './wp9.en';
import { wp5En } from './wp5.en';

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
  'shell.caseSearch': 'Search cases, parties, hearings, judgments, documents and more',
  'shell.caseSearchPlaceholder': 'Words, case or party name',
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
  'nav.directives': 'Decisions and assignments',
  'nav.templates': 'Legal templates',
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
  ...accessEn,
  ...casesEn,
  ...courtsEn,
  ...proceedingsEn,
  ...intakeEn,
  ...selectorEn,
  ...operationsEn,
  ...reportsEn,
  ...libraryEn,
  ...wp3En,
  ...deadlinesEn,
  ...pleadingsEn,
  ...wp9En,
  ...wp5En,
};

export type MessageKey = keyof typeof en;
export type Messages = Record<MessageKey, string>;
