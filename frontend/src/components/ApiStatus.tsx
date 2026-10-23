import { useEffect, useState } from 'react';
import { fetchApiHealth, type ApiHealth } from '../api/health';

type State = 'checking' | ApiHealth;

const LABELS: Record<State, string> = {
  checking: 'Checking API…',
  online: 'API online',
  unavailable: 'API unavailable',
};

/** Shows whether the backend answers its health check. Announced politely to assistive technology. */
export function ApiStatus() {
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
      {LABELS[state]}
    </p>
  );
}
