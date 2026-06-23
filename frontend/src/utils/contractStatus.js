// Maps backend ContractStatus enum values to user-facing labels.
const CONTRACT_STATUS_LABELS = {
  DRAFT: 'Draft',
  PENDING_SIGNATURES: 'Pending signature',
  AWAITING_COUNTERPARTY: 'Awaiting counterparty',
  ACTIVE: 'Signed',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

// Pass the i18next `t` to get a localized label; without it, falls back to the
// English map (keeps non-React callers working).
export function contractStatusLabel(status, t) {
  if (!status) return '—';
  const fallback = CONTRACT_STATUS_LABELS[status] || status;
  return t ? t(`contractStatus.${status}`, { defaultValue: fallback }) : fallback;
}
