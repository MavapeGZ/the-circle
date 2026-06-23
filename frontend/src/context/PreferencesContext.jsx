import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import i18n, { SUPPORTED_LANGUAGES } from '../i18n';
import api from '../services/api';
import { formatPrice as formatPriceUtil, SUPPORTED_CURRENCIES, BASE_CURRENCY } from '../utils/currency';

export const PreferencesContext = createContext();

const STORAGE_KEY = 'prefs';
const DEFAULTS = { language: 'es', currency: 'EUR', timezone: 'Europe/Madrid' };

// Detection order for anonymous visitors: a manual choice saved in localStorage,
// then the browser's Accept-Language (navigator.language), then the default.
function detectInitial() {
  try {
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
    if (stored?.language) return { ...DEFAULTS, ...stored };
  } catch { /* ignore corrupt storage */ }
  const navLang = (navigator.language || 'es').slice(0, 2).toLowerCase();
  const language = SUPPORTED_LANGUAGES.includes(navLang) ? navLang : DEFAULTS.language;
  return { ...DEFAULTS, language };
}

export const PreferencesProvider = ({ children }) => {
  const [prefs, setPrefs] = useState(detectInitial);

  // Keep i18next in sync with the active language.
  useEffect(() => {
    if (i18n.language !== prefs.language) i18n.changeLanguage(prefs.language);
  }, [prefs.language]);

  // For a logged-in user, the account is the source of truth: pull saved
  // preferences once a token exists and reconcile local state with them.
  useEffect(() => {
    if (!localStorage.getItem('token')) return;
    let active = true;
    (async () => {
      try {
        const { data } = await api.get('/users/me/settings', { skipAuthRedirect: true });
        if (!active || !data) return;
        const next = {
          language: SUPPORTED_LANGUAGES.includes(data.language) ? data.language : prefs.language,
          currency: SUPPORTED_CURRENCIES.includes(data.currency) ? data.currency : prefs.currency,
          timezone: data.timezone || prefs.timezone,
        };
        setPrefs(next);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      } catch { /* keep detected prefs on failure */ }
    })();
    return () => { active = false; };
  }, []);

  // Apply a partial change locally + to storage, and persist to the account when
  // the user is logged in. Persisting is best-effort: the UI updates regardless.
  const updatePreferences = useCallback(async (partial) => {
    setPrefs((prev) => {
      const next = { ...prev, ...partial };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      return next;
    });
    if (localStorage.getItem('token')) {
      await api.patch('/users/me', partial);
    }
  }, []);

  const formatPrice = useCallback(
    (amountEur) => formatPriceUtil(amountEur, prefs.currency, prefs.language),
    [prefs.currency, prefs.language],
  );

  // Locale-aware date/time formatting in the user's chosen time zone. Uses
  // Intl.DateTimeFormat (not toLocaleDateString) so callers can pass either
  // field options (day/month/year) or dateStyle/timeStyle without the
  // "invalid option timeStyle" error toLocaleDateString throws for time fields.
  const formatDate = useCallback(
    (value, options = { day: '2-digit', month: 'short', year: 'numeric' }) => {
      if (!value) return '';
      const date = new Date(value);
      const locale = prefs.language === 'es' ? 'es-ES' : 'en-GB';
      try {
        return new Intl.DateTimeFormat(locale, { timeZone: prefs.timezone, ...options }).format(date);
      } catch {
        return new Intl.DateTimeFormat(locale, options).format(date);
      }
    },
    [prefs.language, prefs.timezone],
  );

  return (
    <PreferencesContext.Provider value={{
      ...prefs,
      baseCurrency: BASE_CURRENCY,
      updatePreferences,
      formatPrice,
      formatDate,
    }}>
      {children}
    </PreferencesContext.Provider>
  );
};

export const usePreferences = () => useContext(PreferencesContext);
