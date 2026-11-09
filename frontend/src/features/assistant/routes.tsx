import type { ReactElement } from 'react';
import { Route } from 'react-router';
import { AssistantStatusPage } from './AssistantStatusPage';

export const ASSISTANT_PATH = '/assistant';

/** The route of WP10 (LD-059): the status of the legal assistant. Call it, do not render it. */
export function assistantRoutes(): ReactElement[] {
  return [<Route key="assistant" path={ASSISTANT_PATH} element={<AssistantStatusPage />} />];
}
