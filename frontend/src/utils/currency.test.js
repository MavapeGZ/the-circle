import { describe, it, expect } from 'vitest';
import {
  BASE_CURRENCY,
  SUPPORTED_CURRENCIES,
  currencySymbol,
  convertFromEur,
  convertToEur,
  formatPrice,
} from './currency';

describe('currencySymbol', () => {
  it('returns the symbol for a known currency', () => {
    expect(currencySymbol('USD')).toBe('$');
    expect(currencySymbol('GBP')).toBe('£');
  });

  it('falls back to the base symbol for unknown currencies', () => {
    expect(currencySymbol('XYZ')).toBe('€');
    expect(currencySymbol(undefined)).toBe('€');
  });
});

describe('convertFromEur', () => {
  it('keeps EUR amounts unchanged', () => {
    expect(convertFromEur(10, 'EUR')).toBe(10);
  });

  it('applies the rate and rounds to cents', () => {
    expect(convertFromEur(10, 'USD')).toBe(10.8);
    expect(convertFromEur(2, 'GBP')).toBe(1.7);
  });

  it('treats an unsupported currency as the base (no conversion)', () => {
    expect(convertFromEur(10, 'XYZ')).toBe(10);
  });

  it('returns null for null, blank or NaN input', () => {
    expect(convertFromEur(null, 'USD')).toBeNull();
    expect(convertFromEur('', 'USD')).toBeNull();
    expect(convertFromEur('abc', 'USD')).toBeNull();
  });
});

describe('convertToEur', () => {
  it('keeps EUR amounts unchanged', () => {
    expect(convertToEur(10, 'EUR')).toBe(10);
  });

  it('divides by the rate and rounds to cents', () => {
    expect(convertToEur(10.8, 'USD')).toBe(10);
    expect(convertToEur(1.7, 'GBP')).toBe(2);
  });

  it('round-trips with convertFromEur within cent precision', () => {
    const eur = 7.5;
    expect(convertToEur(convertFromEur(eur, 'USD'), 'USD')).toBeCloseTo(eur, 1);
  });

  it('returns null for null, blank or NaN input', () => {
    expect(convertToEur(null, 'USD')).toBeNull();
    expect(convertToEur('', 'USD')).toBeNull();
    expect(convertToEur('abc', 'USD')).toBeNull();
  });
});

describe('formatPrice', () => {
  it('returns null for null/undefined so callers can render a Free label', () => {
    expect(formatPrice(null)).toBeNull();
    expect(formatPrice(undefined)).toBeNull();
  });

  it('formats an EUR amount with the euro symbol by default', () => {
    expect(formatPrice(10)).toMatch(/10/);
    expect(formatPrice(10)).toMatch(/€/);
  });

  it('converts and formats in the requested currency', () => {
    const usd = formatPrice(10, 'USD', 'en');
    expect(usd).toMatch(/\$/);
    expect(usd).toMatch(/10\.80/);
  });

  it('falls back to the base currency for an unsupported code', () => {
    expect(formatPrice(10, 'XYZ', 'en')).toMatch(/€/);
  });
});

describe('module constants', () => {
  it('exposes EUR as the base and the three supported currencies', () => {
    expect(BASE_CURRENCY).toBe('EUR');
    expect(SUPPORTED_CURRENCIES).toEqual(['EUR', 'USD', 'GBP']);
  });
});
