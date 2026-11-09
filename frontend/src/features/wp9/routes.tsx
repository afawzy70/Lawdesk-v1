import type { ReactElement } from 'react';
import { Route } from 'react-router';
import { DirectivesPage } from './DirectivesPage';
import { SearchPage } from './SearchPage';
import { TemplatesPage } from './TemplatesPage';

export const SEARCH_PATH = '/search';
export const DIRECTIVES_PATH = '/directives';
export const TEMPLATES_PATH = '/templates';

/** The routes of WP9 (LD-057): unified search, the directives register and the template library. Call it, do not render it. */
export function wp9Routes(): ReactElement[] {
  return [
    <Route key="search" path={SEARCH_PATH} element={<SearchPage />} />,
    <Route key="directives" path={DIRECTIVES_PATH} element={<DirectivesPage />} />,
    <Route key="templates" path={TEMPLATES_PATH} element={<TemplatesPage />} />,
  ];
}
