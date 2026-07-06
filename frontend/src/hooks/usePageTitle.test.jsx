import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook } from '@testing-library/react';

// Mock i18n: t echoes the key so we can assert the title wiring without a real
// translation bundle.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key) => key, i18n: { language: 'en' } }),
}));

import usePageTitle from './usePageTitle';

describe('usePageTitle', () => {
  beforeEach(() => {
    document.title = '';
  });

  it('shows just "The Circle" when no section key is given', () => {
    renderHook(() => usePageTitle());
    expect(document.title).toBe('The Circle');
  });

  it('appends the translated section with an en dash', () => {
    renderHook(() => usePageTitle('title.catalog'));
    expect(document.title).toBe('The Circle – title.catalog');
  });

  it('updates the title when the section key changes', () => {
    const { rerender } = renderHook(({ key }) => usePageTitle(key), {
      initialProps: { key: 'title.catalog' },
    });
    expect(document.title).toBe('The Circle – title.catalog');

    rerender({ key: 'title.profile' });
    expect(document.title).toBe('The Circle – title.profile');
  });
});
