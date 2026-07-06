// Registers the jest-dom matchers (toBeInTheDocument, toHaveTextContent, ...)
// and clears the DOM between tests so component renders stay isolated.
import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

afterEach(() => {
  cleanup();
});
