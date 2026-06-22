// Whether a contract is waiting on *my* action right now. Shared by the navbar
// Contracts badge (count) and the per-row marker in My Contracts so both agree.
//
// An action is pending when, as a party to the contract, I still have to:
//   1. sign it (it is not yet fully signed and my signature is missing),
//   2. confirm the delivery hand-over (goods + deposit-less rentals, once both
//      signed, and I have not confirmed my side yet), or
//   3. settle a deposited rental guarantee (owner releases/claims it).
export function contractActionRequired(contract, myId) {
  if (!contract || myId == null) return false;
  const me = String(myId);
  const isOwner = me === String(contract.ownerId);
  const isReceiver = me === String(contract.receiverId);
  if (!isOwner && !isReceiver) return false;

  // 1) Pending signature on my side.
  const signed = isOwner ? contract.ownerSignedAt : contract.receiverSignedAt;
  const signable = contract.status !== 'ACTIVE' && contract.status !== 'DELIVERED'
    && contract.status !== 'COMPLETED' && contract.status !== 'CANCELLED';
  if (signable && !signed) return true;

  // 2) Pending delivery confirmation (hand-over flow only).
  const isRental = contract.type === 'RENT';
  const rentalHasDeposit = isRental && Number(contract.guaranteeAmount) > 0;
  const usesHandshake = !isRental || !rentalHasDeposit;
  if (usesHandshake && (contract.status === 'ACTIVE' || contract.status === 'DELIVERED')) {
    const confirmed = isOwner ? contract.ownerDeliveredAt : contract.receiverReceivedAt;
    if (!confirmed) return true;
  }

  // 3) Owner has to settle a locked deposit.
  if (isOwner && contract.status === 'ACTIVE' && contract.guaranteeStatus === 'DEPOSITED') return true;

  return false;
}

// Count of contracts in a list that need my action.
export function countContractsNeedingAction(contracts, myId) {
  if (!Array.isArray(contracts)) return 0;
  return contracts.reduce((n, c) => n + (contractActionRequired(c, myId) ? 1 : 0), 0);
}
