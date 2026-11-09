import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../../api/http';
import type { MessageKey } from '../../i18n/catalog';

export type Loaded<T> = { data: T | null; status: number | null; reload: () => void };

/** Loads once and again on `reload()`; `status` is the HTTP status of a failure (0 when the API cannot be reached). */
export function useLoad<T>(load: (signal: AbortSignal) => Promise<T>, deps: readonly unknown[]): Loaded<T> {
  const [state, setState] = useState<{ data: T | null; status: number | null }>({ data: null, status: null });
  const [generation, setGeneration] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setState({ data, status: null });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setState({ data: null, status: error instanceof ApiError ? error.status : 0 });
      },
    );
    return () => {
      controller.abort();
    };
    // `load` is a closure over `deps`.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, generation]);
  const reload = useCallback(() => {
    setGeneration((n) => n + 1);
  }, []);
  return { data: state.data, status: state.status, reload };
}

/** What a refused save means, in the user's words. */
export function saveMessage(error: unknown): MessageKey {
  if (!(error instanceof ApiError)) return 'pl.error.save';
  switch (error.status) {
    case 400:
      return 'pl.error.fields';
    case 403:
      return 'pl.error.forbidden';
    case 404:
      return 'pl.error.gone';
    case 409:
      return 'pl.error.stale';
    case 422:
      return 'pl.error.rule';
    default:
      return 'pl.error.save';
  }
}

/** Field names the backend flagged in a 400 answer. */
export function flaggedFields(error: unknown): string[] {
  return error instanceof ApiError ? (error.problem?.errors ?? []).map((e) => e.field) : [];
}

/** Empty or blank text is "not specified" on the wire. */
export const orNull = (value: string): string | null => (value.trim() === '' ? null : value.trim());

/** A KWD amount typed with a dot or an Arabic comma, as the string the API takes; null when blank. */
export function amountText(value: string): string | null {
  const text = value.trim().replace(',', '.');
  return text === '' ? null : text;
}
