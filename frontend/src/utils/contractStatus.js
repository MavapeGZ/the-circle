// Maps backend ContractStatus enum values to user-facing labels.
const CONTRACT_STATUS_LABELS = {
  DRAFT: 'Draft',
  PENDING_SIGNATURES: 'Pending signature',
  ACTIVE: 'Signed',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

export function contractStatusLabel(status) {
  if (!status) return '—';
  return CONTRACT_STATUS_LABELS[status] || status;
}
