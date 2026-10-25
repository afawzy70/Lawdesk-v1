import type { Messages } from './en';
import { authAr } from './auth.ar';
import { adminAr } from './admin.ar';
import { casesAr } from './cases.ar';
import { courtsAr } from './courts.ar';
import { proceedingsAr } from './proceedings.ar';
import { operationsAr } from './operations.ar';
import { reportsAr } from './reports.ar';

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
  'shell.caseSearch': 'البحث في القضايا بالرقم أو العنوان',
  'shell.caseSearchPlaceholder': 'رقم القضية أو عنوانها',
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
  ...casesAr,
  ...courtsAr,
  ...proceedingsAr,
  ...operationsAr,
  ...reportsAr,
};
