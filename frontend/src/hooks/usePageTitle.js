import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';

// Sets the browser tab title for a page. Pass an i18n key (e.g. 'title.catalog');
// the home page passes nothing and shows just "The Circle". Every other section
// shows "The Circle – <Section>" with an en dash, localized to the active
// language and re-applied whenever the language changes.
export default function usePageTitle(sectionKey) {
  const { t, i18n } = useTranslation();
  useEffect(() => {
    const section = sectionKey ? t(sectionKey) : '';
    document.title = section ? `The Circle – ${section}` : 'The Circle';
  }, [sectionKey, t, i18n.language]);
}
