import React from 'react';
import BadgeChip from './BadgeChip';

export default function BadgeList({ badges = [], emptyMessage = 'No badges yet.' }) {
  if (!badges.length) {
    return <p className="text-sm text-gray-500">{emptyMessage}</p>;
  }

  return (
    <div className="flex flex-wrap gap-2">
      {badges.map((badge) => (
        <BadgeChip key={`${badge.code}-${badge.earnedAt || badge.name}`} badge={badge} />
      ))}
    </div>
  );
}