// Maps backend ContractType enum values to user-facing labels.
const CONTRACT_TYPE_LABELS = {
  SALE: 'Sale',
  RENT: 'Rental',
  CESSION_TEMPORARY: 'Loan',
  CESSION_PERMANENT: 'Donation',
};

export function contractTypeLabel(type) {
  if (!type) return '—';
  return CONTRACT_TYPE_LABELS[type] || type;
}
