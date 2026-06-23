import { useState } from 'react';
import api, { extractApiError } from '../services/api';
import StarRating from './StarRating';

// Inline form to leave a 1–5 (half-step) star review with an optional comment for
// the other party of a delivered contract. Calls onSubmitted(review) on success.
export default function ReviewForm({ targetUserId, contractId, onSubmitted, onCancel }) {
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    if (rating < 1) {
      setError('Please select a rating.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      const res = await api.post(`/users/${targetUserId}/reviews`, { contractId, rating, comment });
      onSubmitted?.(res.data);
    } catch (err) {
      setError(extractApiError(err, 'Could not submit your review.'));
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={submit} className="mt-4 p-4 bg-gray-50 border border-gray-200 rounded-xl space-y-3">
      <h3 className="font-bold text-gray-800">Leave a review</h3>
      <div className="flex items-center gap-3">
        <StarRating value={rating} onChange={setRating} size={28} />
        <span className="text-sm text-gray-500">{rating ? `${rating} / 5` : 'Tap to rate'}</span>
      </div>
      <textarea
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        maxLength={1000}
        rows={3}
        placeholder="Share how the transaction went (optional)"
        className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 text-sm"
      />
      {error && <p className="text-sm text-red-600">{error}</p>}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={submitting}
          className={`px-4 py-2 rounded-lg font-bold text-white ${submitting ? 'bg-indigo-400' : 'bg-indigo-600 hover:bg-indigo-700'}`}
        >
          {submitting ? 'Submitting...' : 'Submit review'}
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel} className="px-4 py-2 rounded-lg font-bold text-gray-600 hover:bg-gray-200">
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}
