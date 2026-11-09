import { useLocation } from 'react-router';

/** Test-only: shows the current address so a test can see where a screen navigated to. */
export function Where() {
  const location = useLocation();
  return <p data-testid="where">{`${location.pathname}${location.search}`}</p>;
}
