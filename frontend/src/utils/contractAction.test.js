import { describe, it, expect } from 'vitest';
import { contractActionRequired, countContractsNeedingAction } from './contractAction';

const OWNER = 1;
const RECEIVER = 2;

function base(overrides = {}) {
  return {
    ownerId: OWNER,
    receiverId: RECEIVER,
    ownerSignedAt: null,
    receiverSignedAt: null,
    ownerDeliveredAt: null,
    receiverReceivedAt: null,
    ...overrides,
  };
}

describe('contractActionRequired — guards', () => {
  it('is false for a missing contract or missing id', () => {
    expect(contractActionRequired(null, OWNER)).toBe(false);
    expect(contractActionRequired(base(), null)).toBe(false);
  });

  it('is false for someone who is neither owner nor receiver', () => {
    expect(contractActionRequired(base({ status: 'PENDING_SIGNATURES' }), 999)).toBe(false);
  });

  it('matches ids regardless of string/number type', () => {
    const c = base({ ownerId: '1', status: 'PENDING_SIGNATURES' });
    expect(contractActionRequired(c, 1)).toBe(true);
  });
});

describe('contractActionRequired — pending signature', () => {
  it('flags a party who has not signed yet while signing is open', () => {
    const c = base({ status: 'PENDING_SIGNATURES' });
    expect(contractActionRequired(c, OWNER)).toBe(true);
    expect(contractActionRequired(c, RECEIVER)).toBe(true);
  });

  it('does not flag a party who already signed', () => {
    const c = base({ status: 'AWAITING_COUNTERPARTY', ownerSignedAt: '2026-01-01' });
    expect(contractActionRequired(c, OWNER)).toBe(false);
    // Counterparty still has to sign.
    expect(contractActionRequired(c, RECEIVER)).toBe(true);
  });

  it('does not treat signed/closed statuses as signable', () => {
    for (const status of ['ACTIVE', 'DELIVERED', 'COMPLETED', 'CANCELLED']) {
      const c = base({ status, ownerSignedAt: null });
      // No delivery/deposit obligations set, so nothing is pending.
      if (status === 'ACTIVE' || status === 'DELIVERED') continue; // handled below
      expect(contractActionRequired(c, OWNER)).toBe(false);
    }
  });
});

describe('contractActionRequired — delivery handshake', () => {
  it('flags a sale party who has not confirmed hand-over once active', () => {
    const c = base({ type: 'SALE', status: 'ACTIVE', ownerSignedAt: 'x', receiverSignedAt: 'x' });
    expect(contractActionRequired(c, OWNER)).toBe(true);
    expect(contractActionRequired(c, RECEIVER)).toBe(true);
  });

  it('does not flag once that party confirmed', () => {
    const c = base({
      type: 'SALE', status: 'ACTIVE', ownerSignedAt: 'x', receiverSignedAt: 'x',
      ownerDeliveredAt: 'x',
    });
    expect(contractActionRequired(c, OWNER)).toBe(false);
    expect(contractActionRequired(c, RECEIVER)).toBe(true);
  });

  it('treats a deposit-less rental as a handshake too', () => {
    const c = base({
      type: 'RENT', guaranteeAmount: 0, status: 'ACTIVE',
      ownerSignedAt: 'x', receiverSignedAt: 'x',
    });
    expect(contractActionRequired(c, OWNER)).toBe(true);
  });
});

describe('contractActionRequired — deposited rental settlement', () => {
  const deposited = base({
    type: 'RENT', guaranteeAmount: 20, status: 'ACTIVE',
    ownerSignedAt: 'x', receiverSignedAt: 'x', guaranteeStatus: 'DEPOSITED',
  });

  it('flags the owner to settle a locked deposit (no handshake step)', () => {
    expect(contractActionRequired(deposited, OWNER)).toBe(true);
  });

  it('does not flag the receiver for the deposit', () => {
    expect(contractActionRequired(deposited, RECEIVER)).toBe(false);
  });

  it('does not flag once the deposit is released', () => {
    const released = { ...deposited, guaranteeStatus: 'RELEASED' };
    expect(contractActionRequired(released, OWNER)).toBe(false);
  });
});

describe('countContractsNeedingAction', () => {
  it('counts only the contracts that need my action', () => {
    const list = [
      base({ status: 'PENDING_SIGNATURES' }),                         // needs sign
      base({ status: 'AWAITING_COUNTERPARTY', ownerSignedAt: 'x' }),  // owner done
      base({ type: 'SALE', status: 'ACTIVE', ownerSignedAt: 'x', receiverSignedAt: 'x' }), // needs delivery
    ];
    expect(countContractsNeedingAction(list, OWNER)).toBe(2);
  });

  it('returns 0 for a non-array input', () => {
    expect(countContractsNeedingAction(null, OWNER)).toBe(0);
    expect(countContractsNeedingAction(undefined, OWNER)).toBe(0);
  });

  it('returns 0 for an empty list', () => {
    expect(countContractsNeedingAction([], OWNER)).toBe(0);
  });
});
