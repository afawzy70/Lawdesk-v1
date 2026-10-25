import { useEffect, useState } from 'react';
import type { MessageKey } from '../../i18n/catalog';
import { failureMessage, isForbidden } from './common';

export type ReportLoad<T> =
  | { kind: 'loading' }
  | { kind: 'forbidden' }
  | { kind: 'failed'; message: MessageKey }
  | { kind: 'loaded'; report: T };

/**
 * Loads a report whenever `key` (the serialised request) changes; an answer that `valid` rejects is a failed load,
 * a 403 shows the not-permitted page, other failures a message that never repeats what the server sent.
 */
export function useReport<T>(
  key: string,
  load: (signal: AbortSignal) => Promise<T>,
  valid: (report: T) => boolean,
): ReportLoad<T> {
  const [result, setResult] = useState<{ key: string; load: ReportLoad<T> } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).then(
      (report) => {
        if (controller.signal.aborted) return;
        setResult({
          key,
          load: valid(report) ? { kind: 'loaded', report } : { kind: 'failed', message: 'reports.list.failed' },
        });
      },
      (error: unknown) => {
        if (controller.signal.aborted) return;
        setResult({
          key,
          load: isForbidden(error) ? { kind: 'forbidden' } : { kind: 'failed', message: failureMessage(error) },
        });
      },
    );
    return () => {
      controller.abort();
    };
    // `load` and `valid` are derived from `key`; reloading on their identity would refetch on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  return result?.key === key ? result.load : { kind: 'loading' };
}

export function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
