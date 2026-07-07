import React from 'react';
import { useTranslation } from 'react-i18next';
import { resolveAssetUrl } from '../services/api';

function isImageUrl(value) {
  return typeof value === 'string' && /^(https?:|data:|\/)/.test(value);
}

function formatTooltip(badge, t) {
  const parts = [badge?.description];
  if (badge?.earnedAt) {
    parts.push(t('badge.tooltip.earnedOn', { date: new Date(badge.earnedAt).toLocaleDateString() }));
  } else if (badge?.earned === false) {
    parts.push(t('badge.tooltip.notEarned'));
  }
  if (badge?.tier) {
    parts.push(t('badge.tooltip.tier', { tier: badge.tier }));
  }
  return parts.filter(Boolean).join(' · ');
}

export default function BadgeChip({ badge }) {
  const { t } = useTranslation();
  if (!badge) return null;

  const tooltip = formatTooltip(badge, t);
  const icon = badge.iconUrl || '🏅';
  // When a catalogue badge has not been earned, render it faded and de-saturated
  // so the user can still see what is available to unlock. `earned === undefined`
  // (legacy callers passing only earned badges) keeps the full-opacity styling.
  const locked = badge.earned === false;

  return (
    <span
      title={tooltip}
      className={`inline-flex items-center gap-2 rounded-full border border-gray-200 bg-white px-3 py-1.5 text-sm font-semibold text-gray-700 shadow-sm transition-opacity ${
        locked ? 'opacity-40 grayscale' : ''
      }`}
    >
      {isImageUrl(icon) ? (
        <img src={resolveAssetUrl(icon)} alt={badge.name} loading="lazy" className="h-5 w-5 rounded-full object-cover" />
      ) : (
        <span className="text-base leading-none" aria-hidden="true">{icon}</span>
      )}
      <span className="whitespace-nowrap">{badge.name}</span>
    </span>
  );
}