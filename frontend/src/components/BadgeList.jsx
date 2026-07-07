import React from 'react';
import { useTranslation } from 'react-i18next';
import BadgeChip from './BadgeChip';

export default function BadgeList({ badges = [], emptyMessage }) {
  const { t } = useTranslation();
  if (!badges.length) {
    return <p className="text-sm text-gray-500">{emptyMessage ?? t('badge.none')}</p>;
  }

  return (
    <div className="flex flex-wrap gap-2">
      {badges.map((badge) => (
        <BadgeChip key={`${badge.code}-${badge.earnedAt || badge.name}`} badge={badge} />
      ))}
    </div>
  );
}