import React from 'react';

function isImageUrl(value) {
  return typeof value === 'string' && /^(https?:|data:|\/)/.test(value);
}

function formatTooltip(badge) {
  const parts = [badge?.description];
  if (badge?.earnedAt) {
    parts.push(`Earned on ${new Date(badge.earnedAt).toLocaleDateString()}`);
  }
  if (badge?.tier) {
    parts.push(`Tier: ${badge.tier}`);
  }
  return parts.filter(Boolean).join(' · ');
}

export default function BadgeChip({ badge }) {
  if (!badge) return null;

  const tooltip = formatTooltip(badge);
  const icon = badge.iconUrl || '🏅';

  return (
    <span
      title={tooltip}
      className="inline-flex items-center gap-2 rounded-full border border-gray-200 bg-white px-3 py-1.5 text-sm font-semibold text-gray-700 shadow-sm"
    >
      {isImageUrl(icon) ? (
        <img src={icon} alt={badge.name} loading="lazy" className="h-5 w-5 rounded-full object-cover" />
      ) : (
        <span className="text-base leading-none" aria-hidden="true">{icon}</span>
      )}
      <span className="whitespace-nowrap">{badge.name}</span>
    </span>
  );
}