import { useContext, useEffect, useState } from 'react';
import { useI18n } from '../../i18n/I18nContext';
import { usePageTitle } from '../../i18n/usePageTitle';
import { AuthContext } from '../auth/AuthContext';
import { getStatus, listIntegrations, type Integration, type Status } from './api';
import { keyFor } from './labels';
import './assistant.css';

/**
 * Settings/status of the legal assistant (owner decision D8): in plain words, not enabled because no provider is approved, with
 * what is and is not connected. Also lists the future integrations of section 34, all disabled (for those who may see users).
 */
export function AssistantStatusPage() {
  const { t, locale } = useI18n();
  usePageTitle(t('assistant.status.title'));
  const auth = useContext(AuthContext)?.state;
  const mayListIntegrations = auth?.status === 'signedIn' && auth.me.permissions.includes('USER_VIEW');
  const [status, setStatus] = useState<Status | 'failed' | null>(null);
  const [integrations, setIntegrations] = useState<Integration[]>([]);

  useEffect(() => {
    const controller = new AbortController();
    getStatus(controller.signal).then(
      (found) => {
        if (!controller.signal.aborted) setStatus(found);
      },
      () => {
        if (!controller.signal.aborted) setStatus('failed');
      },
    );
    if (mayListIntegrations) {
      listIntegrations(controller.signal).then(
        (found) => {
          if (!controller.signal.aborted) setIntegrations(found);
        },
        () => undefined,
      );
    }
    return () => {
      controller.abort();
    };
  }, [mayListIntegrations]);

  return (
    <>
      <h1>{t('assistant.status.title')}</h1>
      <p>{t('assistant.status.intro')}</p>
      {status === null && <p role="status">{t('assistant.status.loading')}</p>}
      {status === 'failed' && (
        <p role="alert" className="case-alert">
          {t('assistant.status.failed')}
        </p>
      )}
      {status !== null && status !== 'failed' && <StatusDetails status={status} />}
      {integrations.length > 0 && (
        <section aria-labelledby="assistant-integrations">
          <h2 id="assistant-integrations">{t('assistant.integrations.title')}</h2>
          <p>{t('assistant.integrations.intro')}</p>
          <ul className="assistant-integrations">
            {integrations.map((integration) => {
              const direction = keyFor('assistant.integrations.direction', integration.direction);
              return (
                <li key={integration.code}>
                  <strong>{locale === 'ar' ? integration.nameAr : integration.nameEn}</strong> — {t('assistant.integrations.disabled')}
                  {direction !== null && <span> · {t(direction)}</span>}
                  <div>
                    {t('assistant.integrations.operations')}: {integration.operations.join('، ')}
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </>
  );
}

function StatusDetails({ status }: { status: Status }) {
  const { t } = useI18n();
  const reason = keyFor('assistant.reason', status.reason);
  const mode = keyFor('assistant.mode', status.mode);
  return (
    <>
      <p role="status" className="case-alert assistant-off">
        {t('assistant.notOperational')}
      </p>
      <dl className="assistant-fields">
        <div>
          <dt>{t('assistant.status.operational')}</dt>
          <dd>{status.operational ? '✓' : t('assistant.status.operationalNo')}</dd>
        </div>
        <div>
          <dt>{t('assistant.status.reason')}</dt>
          <dd>{reason === null ? status.reason : t(reason)}</dd>
        </div>
        <div>
          <dt>{t('assistant.status.mode')}</dt>
          <dd>{mode === null ? status.mode : t(mode)}</dd>
        </div>
        <div>
          <dt>{t('assistant.status.provider')}</dt>
          <dd>{status.provider.nonProduction ? t('assistant.status.providerTest') : status.provider.code}</dd>
        </div>
        <div>
          <dt>{t('assistant.status.approved')}</dt>
          <dd>{status.approvedProviders === 0 ? t('assistant.status.none') : String(status.approvedProviders)}</dd>
        </div>
        <div>
          <dt>{t('assistant.status.external')}</dt>
          <dd>{status.externalCalls ? '!' : t('assistant.status.none')}</dd>
        </div>
        <div>
          <dt>{t('assistant.status.leaves')}</dt>
          <dd>{status.caseDataLeavesTheSystem ? '!' : t('assistant.status.none')}</dd>
        </div>
      </dl>
      <p className="case-notice">{t('assistant.status.testNote')}</p>
    </>
  );
}
