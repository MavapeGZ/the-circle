import { useEffect } from 'react';

// Sets the browser tab title for a page. The home page passes nothing and shows
// just "The Circle"; every other section shows "The Circle – <Section>" using an
// en dash (guión medio), e.g. usePageTitle('Catalog') -> "The Circle – Catalog".
export default function usePageTitle(section) {
  useEffect(() => {
    document.title = section ? `The Circle – ${section}` : 'The Circle';
  }, [section]);
}
