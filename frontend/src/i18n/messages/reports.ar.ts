import type { reportsEn } from './reports.en';

/** Arabic reports messages; exactly the keys of reports.en.ts. */
export const reportsAr: Record<keyof typeof reportsEn, string> = {
  'reports.title': 'التقارير',

  'reports.dashboard.intro': 'تُحسب الأرقام من السجلات المحفوظة في LawDesk عند فتح الصفحة.',
  'reports.dashboard.noPermission': 'تظهر الإحصاءات للمستخدمين الذين لديهم صلاحية التقارير.',
  'reports.dashboard.loading': 'جارٍ تحميل الأرقام…',
  'reports.dashboard.failed': 'تعذّر تحميل الأرقام.',
  'reports.dashboard.retry': 'إعادة المحاولة',
  'reports.dashboard.cases': 'القضايا',
  'reports.dashboard.cases.open': 'القضايا المفتوحة',
  'reports.dashboard.cases.closed': 'القضايا المغلقة',
  'reports.dashboard.cases.total': 'جميع القضايا',
  'reports.dashboard.cases.newThisMonth': 'فُتحت هذا الشهر',
  'reports.dashboard.byStatus': 'القضايا حسب الحالة',
  'reports.dashboard.byType': 'القضايا حسب النوع',
  'reports.dashboard.stages': 'الدعاوى حسب درجة التقاضي',
  'reports.dashboard.stageValue': '{total} دعوى، منها {awaiting} في انتظار الحكم',
  'reports.dashboard.awaitingJudgment': 'في انتظار الحكم',
  'reports.dashboard.judgments': 'الأحكام من {from} إلى {to}',
  'reports.dashboard.judgments.recorded': 'الأحكام المسجلة',
  'reports.dashboard.judgments.final': 'الأحكام النهائية',
  'reports.dashboard.operations': 'العمليات القانونية',
  'reports.dashboard.notAvailable': 'غير متاح بعد',
  'reports.dashboard.notAvailableHint': 'تظهر هذه الأرقام بعد إنشاء الجلسات والخبراء والمهام.',
  'reports.pending.HEARINGS_THIS_WEEK': 'جلسات هذا الأسبوع',
  'reports.pending.EXPERT_REPORTS_DUE': 'تقارير الخبراء المستحقة',
  'reports.pending.OVERDUE_TASKS': 'المهام المتأخرة',
} as const;
