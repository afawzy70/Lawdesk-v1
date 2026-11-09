import type { Messages } from './en';
import { authAr } from './auth.ar';
import { adminAr } from './admin.ar';
import { accessAr } from './access.ar';
import { casesAr } from './cases.ar';
import { courtsAr } from './courts.ar';
import { proceedingsAr } from './proceedings.ar';
import { intakeAr } from './intake.ar';
import { selectorAr } from './selector.ar';
import { operationsAr } from './operations.ar';
import { reportsAr } from './reports.ar';
import { libraryAr } from './library.ar';
import { wp3Ar } from './wp3.ar';
import { deadlinesAr } from './deadlines.ar';
import { pleadingsAr } from './pleadings.ar';
import { wp9Ar } from './wp9.ar';
import { wp5Ar } from './wp5.ar';

/** Arabic (Modern Standard Arabic) message catalog. Keys must match `en.ts` exactly. */
export const ar: Messages = {
  'app.name': 'LawDesk',
  'footer.version': 'LawDesk v{version}',

  'preview.banner':
    'بيئة اختبار UAT — أدخل بيانات تجريبية فقط، ولا تُدخل بيانات موكلين حقيقية. الأقسام المعلَّمة «لاحقًا» لم تُبنَ بعد.',
  'preview.bannerLabel': 'تنبيه المعاينة',
  'preview.titleSuffix': '(معاينة)',

  'shell.skipToContent': 'انتقل إلى المحتوى الرئيسي',
  'shell.primaryNav': 'التنقل الرئيسي',
  'shell.menu': 'القائمة',
  'shell.switchLanguage': 'English',
  'shell.caseSearch': 'البحث في القضايا والأطراف والجلسات والأحكام والمستندات وغيرها',
  'shell.caseSearchPlaceholder': 'كلمات أو رقم قضية أو اسم طرف',
  'shell.search': 'بحث',

  'nav.dashboard': 'لوحة المعلومات',
  'nav.cases': 'القضايا',
  'nav.courts': 'المحاكم',
  'nav.hearings': 'الجلسات',
  'nav.judgments': 'الأحكام',
  'nav.experts': 'الخبراء',
  'nav.documents': 'المستندات',
  'nav.tasks': 'المهام',
  'nav.reports': 'التقارير',
  'nav.directives': 'القرارات والتكليفات',
  'nav.templates': 'النماذج القانونية',
  'nav.admin': 'الإدارة',

  'apiStatus.checking': 'جارٍ التحقق من الواجهة البرمجية…',
  'apiStatus.online': 'الواجهة البرمجية متصلة',
  'apiStatus.unavailable': 'الواجهة البرمجية غير متاحة',

  'comingSoon.badge': 'قادمة في مرحلة لاحقة',
  'comingSoon.body': 'هذه الميزة غير متاحة في هذه المعاينة. سيتم بناؤها واختبارها في مرحلة لاحقة من LawDesk.',

  'notFound.title': 'الصفحة غير موجودة',
  'notFound.body': 'تعذّر العثور على هذه الصفحة. تحقّق من العنوان، أو عُد إلى الصفحة الرئيسية.',
  'notFound.homeLink': 'الذهاب إلى الصفحة الرئيسية',

  // Area messages, one file per workstream (architecture/CONTRACTS.md §6).
  ...authAr,
  ...adminAr,
  ...accessAr,
  ...casesAr,
  ...courtsAr,
  ...proceedingsAr,
  ...intakeAr,
  ...selectorAr,
  ...operationsAr,
  ...reportsAr,
  ...libraryAr,
  ...wp3Ar,
  ...deadlinesAr,
  ...pleadingsAr,
  ...wp9Ar,
  ...wp5Ar,
};
