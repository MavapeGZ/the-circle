import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import api from '../services/api';

function ArticleDetail() {
  const { id } = useParams(); // Extract ID from URL
  const navigate = useNavigate();
  
  const [article, setArticle] = useState(null);
  const [me, setMe] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);
  const [rentDays, setRentDays] = useState(7);
  const [deposit, setDeposit] = useState('');
  const [acquiring, setAcquiring] = useState(false);

  // Load article on mount
  useEffect(() => {
    const fetchArticle = async () => {
      try {
        const response = await api.get(`/catalog/articles/${id}`);
        setArticle(response.data);
      } catch (err) {
        console.error(err);
        setError('Article not found or error loading details.');
      } finally {
        setLoading(false);
      }
    };

    fetchArticle();
  }, [id]);

  // Load the current user so we can tell owner from visitor.
  useEffect(() => {
    api.get('/users/me')
      .then((res) => setMe(res.data))
      .catch(() => setMe(null)); // not logged in: acquire/owner actions stay hidden
  }, []);

  // Delete logic
  const handleDelete = async () => {
    // Native browser confirmation to prevent accidental deletions
    const confirmed = window.confirm('Are you sure you want to delete this article? This action cannot be undone.');
    
    if (confirmed) {
      setIsDeleting(true);
      try {
        await api.delete(`/catalog/articles/${id}`);
        navigate('/catalog'); // Return to catalog after deletion
      } catch (err) {
        console.error('Error deleting:', err);
        alert('Failed to delete the article. Please try again.');
        setIsDeleting(false);
      }
    }
  };

  // Maps a catalog transaction mode to its action label and the contract type
  // the backend expects when the deal is created.
  const ACQUIRE = {
    SELL: { label: 'Buy', contractType: 'SALE' },
    RENT: { label: 'Rent', contractType: 'RENT' },
    DONATE: { label: 'Request', contractType: 'CESSION_PERMANENT' },
    GIFT: { label: 'Request', contractType: 'CESSION_PERMANENT' },
  };

  const RENT_OPTIONS = [
    { days: 7, label: '1 week' },
    { days: 14, label: '2 weeks' },
    { days: 30, label: '1 month' },
    { days: 90, label: '3 months' },
  ];

  // Creates the contract, then sends the buyer to the OTP signing screen with the
  // freshly created contract so it does not have to be re-fetched.
  const handleAcquire = async () => {
    const cfg = ACQUIRE[article.transactionMode];
    if (!cfg || !me) return;

    setAcquiring(true);
    const isRent = article.transactionMode === 'RENT';
    const returnDate = isRent
      ? new Date(Date.now() + rentDays * 86400000).toISOString().slice(0, 19)
      : null;
    // Security deposit only applies to rentals; default to the listing price.
    const guaranteeAmount = isRent ? Number(deposit || article.price || 0) : null;

    try {
      const res = await api.post('/contracts', {
        itemId: article.id,
        ownerId: String(article.authorId),
        receiverId: String(me.id),
        type: cfg.contractType,
        guaranteeAmount,
        conditions: `${cfg.label}: ${article.title}`
          + (isRent ? ` (${rentDays} days, deposit ${guaranteeAmount} €)` : '')
          + (article.price ? ` - ${article.price} €` : ''),
        returnDate,
      });
      navigate(`/contracts/${res.data.id}/sign`, {
        state: { contract: res.data, signerEmail: me.email, role: 'RECEIVER' },
      });
    } catch (err) {
      console.error('Error creating contract:', err);
      alert('Could not start the deal. Please try again.');
      setAcquiring(false);
    }
  };

  // Loading and error states
  if (loading) return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">Loading article...</div>;
  if (error) return <div className="text-center mt-20 text-xl text-red-600 font-bold">{error}</div>;
  if (!article) return null;

  const isOffer = article.type === 'OFFER';
  const isOwner = me != null && String(me.id) === String(article.authorId);
  const acquireCfg = ACQUIRE[article.transactionMode];
  // Only a logged-in visitor (not the owner) can acquire an OFFER.
  const canAcquire = isOffer && !isOwner && me != null && acquireCfg;
  const formattedDate = new Date(article.createdAt).toLocaleDateString('en-US', {
    day: '2-digit', month: 'long', year: 'numeric'
  });

  return (
    <div className="max-w-4xl mx-auto mt-8 p-4">
      {/* Back button */}
      <Link to="/catalog" className="inline-flex items-center text-blue-600 hover:text-blue-800 mb-6 font-semibold">
        &larr; Back to Catalog
      </Link>

      <div className="bg-white rounded-2xl shadow-lg border border-gray-200 overflow-hidden">
        {/* Large image */}
        {article.imageBase64 ? (
          <img src={article.imageBase64} alt={article.title} className="w-full h-96 object-contain bg-gray-100" />
        ) : (
          <div className="w-full h-64 bg-gray-100 flex items-center justify-center text-gray-400">
            <span>No image available</span>
          </div>
        )}

        <div className="p-8">
          {/* Header: Tags and Date */}
          <div className="flex justify-between items-center mb-4">
            <div className="flex gap-2">
              <span className={`text-xs font-bold px-3 py-1 rounded-full border ${isOffer ? 'bg-green-100 text-green-800 border-green-300' : 'bg-blue-100 text-blue-800 border-blue-300'}`}>
                {isOffer ? 'OFFER' : 'DEMAND'}
              </span>
              <span className="bg-gray-200 text-gray-700 text-xs font-semibold px-3 py-1 rounded-full">
                {article.category}
              </span>
            </div>
            <span className="text-gray-500 text-sm">Published on {formattedDate}</span>
          </div>

          {/* Title and Price */}
          <div className="flex justify-between items-start mb-6">
            <h1 className="text-3xl font-extrabold text-gray-900 w-3/4">{article.title}</h1>
            <span className="text-2xl font-black text-gray-800">
              {article.price === 0 ? 'FREE' : `${article.price} €`}
            </span>
          </div>

          {/* Full Description */}
          <div className="mb-8">
            <h3 className="text-lg font-bold text-gray-800 mb-2">Description</h3>
            <p className="text-gray-600 whitespace-pre-wrap leading-relaxed">{article.description}</p>
          </div>

          <hr className="my-6 border-gray-200" />

          {/* Action area */}
          <div className="flex justify-end items-center gap-4">
            {/* Owner: manage the article */}
            {isOwner && (
              <>
                <button
                  className="px-6 py-2 bg-gray-100 text-gray-700 font-bold rounded shadow hover:bg-gray-200 transition"
                  onClick={() => navigate(`/catalog/edit/${article.id}`)}
                >
                  Edit
                </button>
                <button
                  onClick={handleDelete}
                  disabled={isDeleting}
                  className={`px-6 py-2 font-bold rounded shadow transition text-white ${isDeleting ? 'bg-red-400 cursor-not-allowed' : 'bg-red-600 hover:bg-red-700'}`}
                >
                  {isDeleting ? 'Deleting...' : 'Delete'}
                </button>
              </>
            )}

            {/* Visitor: acquire the item (a contract + OTP signature follow) */}
            {canAcquire && (
              <>
                {article.transactionMode === 'RENT' && (
                  <>
                    <select
                      value={rentDays}
                      onChange={(e) => setRentDays(Number(e.target.value))}
                      disabled={acquiring}
                      className="px-4 py-2 border border-gray-300 rounded shadow-sm bg-white focus:ring-2 focus:ring-blue-500"
                    >
                      {RENT_OPTIONS.map((opt) => (
                        <option key={opt.days} value={opt.days}>{opt.label}</option>
                      ))}
                    </select>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      value={deposit}
                      onChange={(e) => setDeposit(e.target.value)}
                      disabled={acquiring}
                      placeholder={`Deposit (${article.price || 0} €)`}
                      className="w-40 px-4 py-2 border border-gray-300 rounded shadow-sm focus:ring-2 focus:ring-blue-500"
                    />
                  </>
                )}
                <button
                  onClick={handleAcquire}
                  disabled={acquiring}
                  className={`px-8 py-2 font-bold rounded shadow transition text-white ${acquiring ? 'bg-blue-400 cursor-not-allowed' : 'bg-blue-600 hover:bg-blue-700'}`}
                >
                  {acquiring ? 'Processing...' : acquireCfg.label}
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default ArticleDetail;