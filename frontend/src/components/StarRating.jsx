import { useState } from 'react';
import { useTranslation } from 'react-i18next';

// Renders five stars supporting half-star granularity. In read-only mode it just
// displays `value` (e.g. 3.5). When `onChange` is provided it becomes an input:
// hovering/clicking the left half of a star picks x.5, the right half picks x.0.
export default function StarRating({ value = 0, onChange, size = 24, className = '' }) {
  const { t } = useTranslation();
  const [hover, setHover] = useState(null);
  const readOnly = typeof onChange !== 'function';
  const shown = hover != null ? hover : value;

  const stars = [1, 2, 3, 4, 5].map((i) => {
    const fill = Math.max(0, Math.min(1, shown - (i - 1))); // 0, 0.5 or 1 of this star
    return (
      <span
        key={i}
        className="relative inline-block"
        style={{ width: size, height: size, lineHeight: 0 }}
      >
        <Star size={size} className="text-gray-300" />
        <span className="absolute inset-0 overflow-hidden" style={{ width: `${fill * 100}%` }}>
          <Star size={size} className="text-yellow-400" />
        </span>
        {!readOnly && (
          <>
            <button
              type="button"
              aria-label={t('starRating.aria', { n: i - 0.5 })}
              className="absolute inset-y-0 left-0 w-1/2 cursor-pointer"
              onMouseEnter={() => setHover(i - 0.5)}
              onMouseLeave={() => setHover(null)}
              onClick={() => onChange(i - 0.5)}
            />
            <button
              type="button"
              aria-label={t('starRating.aria', { n: i })}
              className="absolute inset-y-0 right-0 w-1/2 cursor-pointer"
              onMouseEnter={() => setHover(i)}
              onMouseLeave={() => setHover(null)}
              onClick={() => onChange(i)}
            />
          </>
        )}
      </span>
    );
  });

  return <span className={`inline-flex items-center gap-0.5 ${className}`}>{stars}</span>;
}

function Star({ size, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" className={className}>
      <path d="M12 2l2.9 6.26 6.9.6-5.2 4.52 1.55 6.74L12 17.27 5.85 20.6l1.55-6.74L2.2 8.86l6.9-.6L12 2z" />
    </svg>
  );
}
