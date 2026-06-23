import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../services/api';
import StarRating from './StarRating';

// Shows the reviews a user has received, with the average up top. Used on both the
// owner's profile (their own received reviews) and other users' public profiles.
export default function ReviewsSection({ userId, average, count }) {
  const { t } = useTranslation();
  const [reviews, setReviews] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    if (!userId) return undefined;
    setLoading(true);
    api.get(`/users/${userId}/reviews`)
      .then((r) => { if (mounted) setReviews(r.data || []); })
      .catch(() => { if (mounted) setReviews([]); })
      .finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, [userId]);

  return (
    <article className="bg-white rounded-3xl shadow-lg border border-gray-100 p-6">
      <div className="flex items-center justify-between gap-4 mb-5">
        <div>
          <h2 className="text-2xl font-extrabold text-gray-900">{t('reviews.title')}</h2>
          <p className="text-sm text-gray-500">{t('reviews.subtitle')}</p>
        </div>
        {count > 0 && (
          <span className="flex flex-col items-center justify-center shrink-0 rounded-2xl bg-yellow-50 border border-yellow-100 px-4 py-2 text-center shadow-sm">
            <span className="text-2xl font-extrabold leading-none text-yellow-600">{average?.toFixed(1)}</span>
            <span className="mt-0.5 text-[11px] font-semibold uppercase tracking-wide text-yellow-500">{t('reviews.count', { count })}</span>
          </span>
        )}
      </div>

      {loading ? (
        <p className="text-gray-400 animate-pulse">{t('reviews.loading')}</p>
      ) : reviews.length === 0 ? (
        <p className="text-gray-500">{t('reviews.none')}</p>
      ) : (
        <ul className="space-y-4">
          {reviews.map((r) => (
            <li key={r.id} className="border-b border-gray-100 pb-4 last:border-0 last:pb-0">
              <div className="flex items-center justify-between gap-3">
                {r.reviewerPublicId ? (
                  <Link to={`/users/${r.reviewerPublicId}`} className="font-bold text-gray-800 hover:text-indigo-700">
                    {r.reviewerName || t('reviews.user', { id: r.reviewerId })}
                  </Link>
                ) : (
                  <span className="font-bold text-gray-800">{r.reviewerName || t('reviews.user', { id: r.reviewerId })}</span>
                )}
                <StarRating value={r.rating} size={16} />
              </div>
              {r.comment && <p className="mt-1 text-gray-600 text-sm whitespace-pre-wrap">{r.comment}</p>}
              <p className="mt-1 text-xs text-gray-400">
                {r.createdAt ? new Date(r.createdAt).toLocaleDateString() : ''}
              </p>
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}
