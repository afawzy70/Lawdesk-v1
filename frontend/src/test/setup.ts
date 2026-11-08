import '@testing-library/jest-dom/vitest';
import { cleanup, configure } from '@testing-library/react';
import { afterEach } from 'vitest';

// A loaded machine (coverage, parallel workers, CI) can take longer than the 1 s default to settle a screen.
configure({ asyncUtilTimeout: 4000 });

afterEach(() => {
  cleanup();
});
