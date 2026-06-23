// Display-currency conversion. EUR is the platform's stored base (and the legal /
// contract currency); the user's chosen currency is a thin presentation layer on
// top: amounts read from the backend are converted EUR -> chosen for display, and
// amounts the user types are converted chosen -> EUR before being sent, so the
// backend always stores EUR. Rates are static approximations kept here on purpose —
// good enough for a symbolic-price marketplace and dependency-free.

export const BASE_CURRENCY = 'EUR';

export const SUPPORTED_CURRENCIES = ['EUR', 'USD', 'GBP'];

// Units of the target currency per 1 EUR.
export const EUR_RATES = {
  EUR: 1,
  USD: 1.08,
  GBP: 0.85,
};

export const CURRENCY_SYMBOLS = {
  EUR: '€',
  USD: '$',
  GBP: '£',
};

const LOCALE_BY_LANG = { es: 'es-ES', en: 'en-GB' };

function rateOf(currency) {
  const code = SUPPORTED_CURRENCIES.includes(currency) ? currency : BASE_CURRENCY;
  return EUR_RATES[code] ?? 1;
}

/** Symbol for a currency code, defaulting to the base (€). */
export function currencySymbol(currency) {
  return CURRENCY_SYMBOLS[currency] || CURRENCY_SYMBOLS[BASE_CURRENCY];
}

/** Converts an amount in EUR to the target currency, rounded to cents. */
export function convertFromEur(amountEur, currency) {
  if (amountEur == null || amountEur === '') return null;
  const n = Number(amountEur);
  if (Number.isNaN(n)) return null;
  return Math.round(n * rateOf(currency) * 100) / 100;
}

/**
 * Converts an amount the user typed in their currency back to EUR (the stored
 * base), rounded to cents so currency caps / validation compare cleanly. Returns
 * null for null/blank/NaN input.
 */
export function convertToEur(amount, currency) {
  if (amount == null || amount === '') return null;
  const n = Number(amount);
  if (Number.isNaN(n)) return null;
  return Math.round((n / rateOf(currency)) * 100) / 100;
}

/**
 * Formats an EUR-denominated amount in the user's currency and locale.
 * Returns null for null/undefined input so callers can render a "Free" label.
 */
export function formatPrice(amountEur, currency = BASE_CURRENCY, language = 'en') {
  if (amountEur == null) return null;
  const code = SUPPORTED_CURRENCIES.includes(currency) ? currency : BASE_CURRENCY;
  const locale = LOCALE_BY_LANG[language] || 'en-GB';
  const value = Number(amountEur) * rateOf(code);
  try {
    return new Intl.NumberFormat(locale, { style: 'currency', currency: code }).format(value);
  } catch {
    return `${value.toFixed(2)} ${code}`;
  }
}
