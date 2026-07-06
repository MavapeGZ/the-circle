import { describe, it, expect } from 'vitest';
import { extractApiError, resolveAssetUrl, apiOrigin } from './api';

describe('extractApiError', () => {
  it('prefers an explicit backend message', () => {
    const err = { response: { data: { message: 'Email already in use' } } };
    expect(extractApiError(err, 'fallback')).toBe('Email already in use');
  });

  it('joins per-field bean-validation messages when there is no top message', () => {
    const err = {
      response: {
        data: {
          error: 'Invalid request',
          fields: [
            { field: 'title', message: 'Title is required' },
            { field: 'price', message: 'Price cannot be negative' },
          ],
        },
      },
    };
    expect(extractApiError(err, 'fallback'))
      .toBe('Title is required Price cannot be negative');
  });

  it('ignores empty field messages', () => {
    const err = { response: { data: { fields: [{ field: 'x' }, { message: '' }] } } };
    expect(extractApiError(err, 'fallback')).toBe('fallback');
  });

  it('returns a connection hint for network errors', () => {
    const err = { code: 'ERR_NETWORK' };
    expect(extractApiError(err, 'fallback')).toMatch(/could not connect/i);
  });

  it('falls back when nothing usable is present', () => {
    expect(extractApiError({}, 'Something went wrong')).toBe('Something went wrong');
    expect(extractApiError(null, 'Something went wrong')).toBe('Something went wrong');
  });
});

describe('resolveAssetUrl', () => {
  it('returns null for an empty path', () => {
    expect(resolveAssetUrl(null)).toBeNull();
    expect(resolveAssetUrl('')).toBeNull();
  });

  it('passes through absolute URLs unchanged', () => {
    const url = 'https://cdn.example.com/a.png';
    expect(resolveAssetUrl(url)).toBe(url);
  });

  it('prefixes gateway-relative paths with the API origin', () => {
    expect(resolveAssetUrl('/api/users/5/avatar')).toBe(`${apiOrigin}/api/users/5/avatar`);
  });

  it('inserts a slash when the path is missing one', () => {
    expect(resolveAssetUrl('api/users/5/avatar')).toBe(`${apiOrigin}/api/users/5/avatar`);
  });
});

describe('apiOrigin', () => {
  it('strips the trailing /api from the configured base URL', () => {
    expect(apiOrigin).not.toMatch(/\/api\/?$/);
  });
});
