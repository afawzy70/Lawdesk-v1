import type { ReactElement } from 'react';
import { Route } from 'react-router';
import { CalendarPage } from './CalendarPage';

/** The path of the judicial calendar (LD-056). */
export const CALENDAR_PATH = '/calendar';

/** The routes of the calendar, mounted inside the application layout by App.tsx. Call it, do not render it. */
export function calendarRoutes(): ReactElement[] {
  return [<Route key={CALENDAR_PATH} path={CALENDAR_PATH} element={<CalendarPage />} />];
}
