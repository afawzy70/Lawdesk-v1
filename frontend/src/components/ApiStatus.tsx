import { useEffect, useState } from 'react';
import { fetchApiHealth, type ApiHealth } from '../api/health';
import type { MessageKey } from '../i18n/catalog';
import { useI18n } from '../i18n/I18nContext';

type State = 'checking' | ApiHealth;

const LABELS: Record<State, MessageKey> = {
  checking: 'apiStatus.checking',
  online: 'apiStatus.online',
  unavailable: 'apiStatus.unavailable',
};

/** Shows whether the backend answers its health check. Announced politely to assistive technology. */
export function ApiStatus() {
  const { t } = useI18n();
  const [state, setState] = useState<State>('checking');

  useEffect(() => {
    const controller = new AbortController();
    void fetchApiHealth(controller.signal).then((health) => {
      if (!controller.signal.aborted) {
        setState(health);
      }
    });
    return () => {
      controller.abort();
    };
  }, []);

  return (
    <p className="api-status" role="status" aria-live="polite" data-state={state}>
      {t(LABELS[state])}
    </p>
  );
}
