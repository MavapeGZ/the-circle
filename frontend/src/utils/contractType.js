// Maps backend ContractType enum values to user-facing labels.
const CONTRACT_TYPE_LABELS = {
  SALE: 'Sale',
  RENT: 'Rental',
  CESSION_TEMPORARY: 'Loan',
  CESSION_PERMANENT: 'Donation',
};

// Pass the i18next `t` to get a localized label; without it, falls back to the
// English map (keeps non-React callers working).
export function contractTypeLabel(type, t) {
  if (!type) return '—';
  const fallback = CONTRACT_TYPE_LABELS[type] || type;
  return t ? t(`contractType.${type}`, { defaultValue: fallback }) : fallback;
}
