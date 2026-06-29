import { describe, it, expect, vi } from 'vitest';
import { contractStatusLabel } from './contractStatus';

describe('contractStatusLabel', () => {
  it('maps known statuses to their English fallback label', () => {
    expect(contractStatusLabel('ACTIVE')).toBe('Signed');
    expect(contractStatusLabel('PENDING_SIGNATURES')).toBe('Pending signature');
    expect(contractStatusLabel('AWAITING_COUNTERPARTY')).toBe('Awaiting counterparty');
  });

  it('returns an em dash for a missing status', () => {
    expect(contractStatusLabel(null)).toBe('—');
    expect(contractStatusLabel(undefined)).toBe('—');
  });

  it('echoes an unknown status unchanged', () => {
    expect(contractStatusLabel('WEIRD')).toBe('WEIRD');
  });

  it('uses the i18n translator when provided', () => {
    const t = vi.fn(() => 'Firmado');
    expect(contractStatusLabel('ACTIVE', t)).toBe('Firmado');
    expect(t).toHaveBeenCalledWith('contractStatus.ACTIVE', { defaultValue: 'Signed' });
  });
});
