// Display-currency conversion. Prices are stored in EUR (the platform's base and
// the legal/contract currency); the user's chosen currency only changes how
// amounts are *shown*. Rates are static approximations kept here on purpose —
// good enough for a symbolic-price marketplace and dependency-free. Update as
// needed; contract/payment amounts are never converted (see backend PDF).

export const BASE_CURRENCY = 'EUR';

export const SUPPORTED_CURRENCIES = ['EUR', 'USD', 'GBP'];

// Units of the target currency per 1 EUR.
export const EUR_RATES = {
  EUR: 1,
  USD: 1.08,
  GBP: 0.85,
};

const LOCALE_BY_LANG = { es: 'es-ES', en: 'en-GB' };

/** Converts an amount in EUR to the target currency using the static rate. */
export function convertFromEur(amountEur, currency) {
  const rate = EUR_RATES[currency] ?? 1;
  return amountEur * rate;
}

/**
 * Formats an EUR-denominated amount in the user's currency and locale.
 * Returns null for null/undefined input so callers can render a "Free" label.
 */
export function formatPrice(amountEur, currency = BASE_CURRENCY, language = 'en') {
  if (amountEur == null) return null;
  const code = SUPPORTED_CURRENCIES.includes(currency) ? currency : BASE_CURRENCY;
  const locale = LOCALE_BY_LANG[language] || 'en-GB';
  const value = convertFromEur(Number(amountEur), code);
  try {
    return new Intl.NumberFormat(locale, { style: 'currency', currency: code }).format(value);
  } catch {
    return `${value.toFixed(2)} ${code}`;
  }
}
