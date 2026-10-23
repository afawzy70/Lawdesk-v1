import type { Messages } from './en';

/** Arabic (Modern Standard Arabic) message catalog. Keys must match `en.ts` exactly. */
export const ar: Messages = {
  'app.name': 'LawDesk',
  'footer.version': 'LawDesk v{version}',

  'preview.banner': 'معاينة — بيانات نموذجية فقط. هذا ليس نظامًا فعّالًا لإدارة القضايا.',
  'preview.bannerLabel': 'تنبيه المعاينة',
  'preview.titleSuffix': '(معاينة)',
  'preview.sampleData': 'بيانات نموذجية',

  'shell.skipToContent': 'انتقل إلى المحتوى الرئيسي',
  'shell.primaryNav': 'التنقل الرئيسي',
  'shell.menu': 'القائمة',
  'shell.switchLanguage': 'English',

  'nav.dashboard': 'لوحة المعلومات',
  'nav.cases': 'القضايا',
  'nav.hearings': 'الجلسات',
  'nav.judgments': 'الأحكام',
  'nav.experts': 'الخبراء',
  'nav.documents': 'المستندات',
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

  'dashboard.intro':
    'تعرض هذه المعاينة تصورًا مبدئيًا للوحة المعلومات. جميع الأرقام والسجلات في هذه الصفحة بيانات نموذجية مُختلَقة.',
  'dashboard.kpis': 'مؤشرات رئيسية',
  'kpi.openCases': 'القضايا المفتوحة',
  'kpi.hearingsThisWeek': 'جلسات هذا الأسبوع',
  'kpi.pendingJudgments': 'أحكام قيد الانتظار',
  'kpi.expertReportsDue': 'تقارير خبراء مستحقة',
  'dashboard.hearings': 'الجلسات القادمة',
  'hearings.case': 'القضية',
  'hearings.court': 'المحكمة',
  'hearings.stage': 'درجة التقاضي',
  'hearings.when': 'التاريخ والوقت',
  'dashboard.stages': 'القضايا حسب درجة التقاضي',
  'stages.value': 'عدد القضايا: {count} ({percent})',
  'stage.firstInstance': 'الدرجة الأولى',
  'stage.appeal': 'الاستئناف',
  'stage.cassation': 'النقض',

  'sample.caseRef': 'قضية نموذجية {number}',
};
