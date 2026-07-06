import { describe, it, expect, vi } from 'vitest';
import { contractTypeLabel } from './contractType';

describe('contractTypeLabel', () => {
  it('maps known types to their English fallback label', () => {
    expect(contractTypeLabel('SALE')).toBe('Sale');
    expect(contractTypeLabel('RENT')).toBe('Rental');
    expect(contractTypeLabel('CESSION_TEMPORARY')).toBe('Loan');
    expect(contractTypeLabel('CESSION_PERMANENT')).toBe('Donation');
  });

  it('returns an em dash for a missing type', () => {
    expect(contractTypeLabel(null)).toBe('—');
  });

  it('echoes an unknown type unchanged', () => {
    expect(contractTypeLabel('BARTER')).toBe('BARTER');
  });

  it('uses the i18n translator when provided', () => {
    const t = vi.fn(() => 'Alquiler');
    expect(contractTypeLabel('RENT', t)).toBe('Alquiler');
    expect(t).toHaveBeenCalledWith('contractType.RENT', { defaultValue: 'Rental' });
  });
});
