import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

// Per the project decision, frontend translations live in Java-style .properties
// files (one fallback `messages.properties` plus `messages_<lang>.properties`),
// the same format the backend uses. Vite's `?raw` import gives us the file text,
// which we parse into the flat key/value maps i18next expects.
import baseProps from './messages.properties?raw';
import enProps from './messages_en.properties?raw';
import esProps from './messages_es.properties?raw';

/**
 * Minimal .properties parser: one `key=value` per line, `#`/`!` comment lines and
 * blank lines ignored, only the first `=` splits the pair. Java unicode escapes
 * are not needed because the files are authored and read as UTF-8.
 */
export function parseProperties(text) {
  const out = {};
  if (!text) return out;
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#') || line.startsWith('!')) continue;
    const eq = line.indexOf('=');
    if (eq === -1) continue;
    const key = line.slice(0, eq).trim();
    const value = line.slice(eq + 1).trim();
    if (key) out[key] = value;
  }
  return out;
}

export const SUPPORTED_LANGUAGES = ['es', 'en'];

// `base` mirrors messages.properties and backs the fallback language, so a key
// missing from a localized bundle still resolves to the English fallback text.
const resources = {
  en: { translation: parseProperties(enProps) },
  es: { translation: parseProperties(esProps) },
  base: { translation: parseProperties(baseProps) },
};

i18n
  .use(initReactI18next)
  .init({
    resources,
    // Resolution order on a miss: requested lang -> en -> base (messages.properties).
    fallbackLng: ['en', 'base'],
    supportedLngs: [...SUPPORTED_LANGUAGES, 'base'],
    // Keys use '.'; values use {{var}} interpolation. Disable nesting so flat
    // dotted keys (e.g. "tz.Europe/Madrid") are matched verbatim.
    keySeparator: false,
    nsSeparator: false,
    interpolation: { escapeValue: false },
    returnEmptyString: false,
  });

export default i18n;
