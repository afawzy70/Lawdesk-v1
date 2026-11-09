import { Link } from 'react-router';
import { useI18n } from '../../i18n/I18nContext';
import { withLang } from '../../i18n/locale';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AdminTabs } from '../admin/AdminTabs';
import { useAuth } from '../auth/AuthContext';
import { HolidaysPanel } from './HolidaysPanel';
import { RemindersPanel } from './RemindersPanel';
import { RulesPanel } from './RulesPanel';
import '../followup/followup.css';

/** The settings of the legal deadlines (section 10): periods, official holidays and reminder lead times. */
export function DeadlineSettingsPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('wp4.settings.title'));
  const { state } = useAuth();
  const granted = new Set(state.status === 'signedIn' ? state.me.permissions : []);
  return (
    <>
      <AdminTabs />
      <h1>{t('wp4.settings.title')}</h1>
      <p className="case-notice">{t('wp4.settings.disclaimer')}</p>
      <p><Link to={withLang('/deadlines', locale)}>{t('wp4.settings.toRegister')}</Link></p>
      <RulesPanel canManage={granted.has('DEADLINE_MANAGE')} canApprove={granted.has('DEADLINE_APPROVE')} />
      <HolidaysPanel canManage={granted.has('DEADLINE_MANAGE')} canApprove={granted.has('DEADLINE_APPROVE')} />
      <RemindersPanel canApprove={granted.has('DEADLINE_APPROVE')} />
    </>
  );
}
