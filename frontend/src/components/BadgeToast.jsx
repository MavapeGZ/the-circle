import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { resolveAssetUrl } from '../services/api';

function isImageUrl(value) {
  return typeof value === 'string' && /^(https?:|data:|\/)/.test(value);
}

/**
 * Transient notification shown when the user unlocks one or more badges (e.g.
 * after completing a donation/rental signature). Auto-dismisses, and offers a
 * direct link to the user's badges on their profile. Self-contained — no toast
 * library — so it can be dropped onto any page.
 */
export default function BadgeToast({ badges = [], to = '/profile', autoDismissMs = 9000 }) {
  const [open, setOpen] = useState(true);

  useEffect(() => {
    if (!autoDismissMs) return undefined;
    const timer = setTimeout(() => setOpen(false), autoDismissMs);
    return () => clearTimeout(timer);
  }, [autoDismissMs]);

  if (!open || !badges.length) return null;

  const plural = badges.length > 1;

  return (
    <div className="fixed bottom-6 right-6 z-50 w-80 max-w-[calc(100vw-3rem)] animate-[fadeIn_0.2s_ease-out]">
      <div className="rounded-2xl bg-white shadow-2xl border border-gray-100 overflow-hidden">
        <div className="bg-gradient-to-br from-blue-600 to-indigo-600 px-4 py-3 flex items-center justify-between">
          <p className="font-extrabold text-white">{plural ? 'New badges unlocked!' : 'Badge unlocked!'}</p>
          <button
            type="button"
            aria-label="Dismiss"
            onClick={() => setOpen(false)}
            className="text-white/80 hover:text-white text-lg leading-none"
          >
            &times;
          </button>
        </div>
        <div className="p-4 space-y-2">
          {badges.map((badge) => (
            <div key={badge.code || badge.name} className="flex items-center gap-3">
              <span className="h-9 w-9 rounded-full bg-blue-50 flex items-center justify-center text-lg shrink-0">
                {isImageUrl(badge.iconUrl) ? (
                  <img src={resolveAssetUrl(badge.iconUrl)} alt={badge.name} className="h-6 w-6 rounded-full object-cover" />
                ) : (
                  <span aria-hidden="true">{badge.iconUrl || '🏅'}</span>
                )}
              </span>
              <span className="font-semibold text-gray-800">{badge.name}</span>
            </div>
          ))}
          <Link
            to={to}
            onClick={() => setOpen(false)}
            className="mt-2 inline-flex items-center font-bold text-blue-600 hover:text-blue-800"
          >
            View my badges &rarr;
          </Link>
        </div>
      </div>
    </div>
  );
}
