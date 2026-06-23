import { useState, useEffect, useContext } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import api, { resolveAssetUrl, extractApiError } from '../services/api';
import { AuthContext } from '../context/AuthContext';
import BadgeList from '../components/BadgeList';
import { ZONE_OPTIONS } from '../constants/zones';

function zoneLabel(zone) {
  return ZONE_OPTIONS.find((option) => option.value === zone)?.label || zone || 'Not shared';
}

// Strip angle brackets when embedding a (possibly legacy) article title into the
// contract conditions, so a title saved before input validation — e.g.
// "<strong>hola</strong>" — does not trip the server-side NO_ANGLE check and
// block the deal. New titles can no longer contain markup anyway.
function cleanTitle(title) {
  return (title || '').replace(/[<>]/g, '');
}

function ArticleDetail() {
  const { id } = useParams();
  const navigate = useNavigate();

  const { user: currentUser } = useContext(AuthContext);

  const [article, setArticle] = useState(null);
  const [owner, setOwner] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);
  const [rentDays, setRentDays] = useState(7);
  const [deposit, setDeposit] = useState('');
  const [acquiring, setAcquiring] = useState(false);
  const [contacting, setContacting] = useState(false);

  useEffect(() => {
    const fetchArticleAndOwner = async () => {
      try {
        const articleRes = await api.get(`/catalog/articles/${id}`);
        const articleData = articleRes.data;
        setArticle(articleData);

        if (articleData.authorId) {
          try {
            // Use the public batch lookup (not the numeric profile endpoint, which
            // is now authenticated): this page is viewable while logged out, and the
            // result carries the owner's opaque publicId for the profile link.
            const ownerRes = await api.get('/users/public', { params: { ids: String(articleData.authorId) } });
            setOwner(ownerRes.data?.[0] || null);
          } catch (ownerErr) {
            console.error('Error fetching owner profile:', ownerErr);
            setOwner(null);
          }
        }
      } catch (err) {
        console.error(err);
        setError('Article not found or error loading details.');
      } finally {
        setLoading(false);
      }
    };

    fetchArticleAndOwner();
  }, [id]);

  const handleDelete = async () => {
    const confirmed = globalThis.confirm('Are you sure you want to delete this article? This action cannot be undone.');
    if (confirmed) {
      setIsDeleting(true);
      try {
        await api.delete(`/catalog/articles/${id}`);
        navigate('/catalog');
      } catch (err) {
        console.error('Error deleting:', err);
        alert('Failed to delete the article. Please try again.');
        setIsDeleting(false);
      }
    }
  };

  // Maps a catalog product type to its action label and the contract type the
  // backend expects when the deal is created. DEMAND listings are not acquirable.
  const ACQUIRE = {
    SYMBOLIC_SALE: { label: 'Buy', contractType: 'SALE' },
    SYMBOLIC_RENTAL: { label: 'Rent', contractType: 'RENT' },
    DONATION: { label: 'Request', contractType: 'CESSION_PERMANENT' },
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
    if (!currentUser?.id) {
      alert('You must be logged in to acquire this article.');
      navigate('/login');
      return;
    }

    const cfg = ACQUIRE[article.productType];
    if (!cfg) return;

    setAcquiring(true);
    const isRent = article.productType === 'SYMBOLIC_RENTAL';
    const returnDate = isRent
      ? new Date(Date.now() + rentDays * 86400000).toISOString().slice(0, 19)
      : null;
    // Security deposit only applies to rentals; default to the listing price.
    const guaranteeAmount = isRent ? Number(deposit || article.price || 0) : null;

    try {
      const res = await api.post('/contracts', {
        itemId: article.id,
        ownerId: String(article.authorId),
        receiverId: String(currentUser.id),
        type: cfg.contractType,
        price: article.price ?? 0,
        guaranteeAmount,
        conditions: `${cfg.label}: ${cleanTitle(article.title)}`
          + (isRent ? ` (${rentDays} days, deposit ${guaranteeAmount} €)` : '')
          + (article.price ? ` - ${article.price} €` : ''),
        returnDate,
      });
      navigate(`/contracts/${res.data.id}/sign`, {
        state: { contract: res.data, signerEmail: currentUser.email, role: 'RECEIVER', from: `/catalog/${article.id}` },
      });
    } catch (err) {
      console.error('Error creating contract:', err);
      alert(extractApiError(err, 'Could not start the deal. Please try again.'));
      setAcquiring(false);
    }
  };

  // Fulfilling a DEMAND flips the roles: the current user provides (owns) the item
  // and the demand's author receives it. We sign first as the OWNER party.
  const handleFulfillDemand = async () => {
    if (!currentUser?.id) {
      alert('You must be logged in to offer this item.');
      navigate('/login');
      return;
    }

    setAcquiring(true);
    try {
      const res = await api.post('/contracts', {
        itemId: article.id,
        ownerId: String(currentUser.id),
        receiverId: String(article.authorId),
        type: 'CESSION_PERMANENT',
        price: article.price ?? 0,
        guaranteeAmount: null,
        conditions: `Fulfill demand: ${cleanTitle(article.title)}`,
        returnDate: null,
      });
      navigate(`/contracts/${res.data.id}/sign`, {
        state: { contract: res.data, signerEmail: currentUser.email, role: 'OWNER', from: `/catalog/${article.id}` },
      });
    } catch (err) {
      console.error('Error creating contract:', err);
      alert(extractApiError(err, 'Could not start the deal. Please try again.'));
      setAcquiring(false);
    }
  };

  // Opens (or reuses) a chat with the article owner about this item. A contract is
  // not required: users can ask questions before any deal.
  const handleContact = async () => {
    if (!currentUser?.id) {
      navigate('/login');
      return;
    }
    setContacting(true);
    try {
      const res = await api.post('/chat/conversations', { articleId: article.id });
      navigate(`/messages/${res.data.id}`);
    } catch (err) {
      console.error('Error starting conversation:', err);
      alert(extractApiError(err, 'Could not start the conversation. Please try again.'));
      setContacting(false);
    }
  };

  if (loading) return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">Loading article...</div>;
  if (error) return <div className="text-center mt-20 text-xl text-red-600 font-bold">{error}</div>;
  if (!article) return null;

  const renderBadge = () => {
    switch (article.productType) {
      case 'DONATION':
        return (
          <span className="bg-purple-100 text-purple-800 text-xs font-extrabold px-3 py-1 rounded-full border border-purple-300 shadow-sm">
            🎁 Donation — Free
          </span>
        );
      case 'DEMAND':
        return (
          <span className="bg-blue-100 text-blue-800 text-xs font-bold px-3 py-1 rounded-full border border-blue-300">
            DEMAND
          </span>
        );
      case 'SYMBOLIC_SALE':
        return (
          <span className="bg-green-100 text-green-800 text-xs font-bold px-3 py-1 rounded-full border border-green-300">
            SALE
          </span>
        );
      case 'SYMBOLIC_RENTAL':
        return (
          <span className="bg-green-100 text-green-800 text-xs font-bold px-3 py-1 rounded-full border border-green-300">
            RENTAL
          </span>
        );
      default:
        return null;
    }
  };

  const formattedDate = new Date(article.createdAt).toLocaleDateString('en-US', {
    day: '2-digit', month: 'long', year: 'numeric'
  });

  const isOwner = currentUser && String(currentUser.id) === String(article.authorId);
  // Legacy articles have no status; treat them as available.
  const isAvailable = !article.status || article.status === 'AVAILABLE';
  const acquireCfg = ACQUIRE[article.productType];
  // A visitor (not the owner) can acquire any non-DEMAND listing via OTP signing.
  const canAcquire = isAvailable && !isOwner && acquireCfg;
  // For a DEMAND, a visitor can instead offer to provide the item being requested.
  const canFulfill = isAvailable && !isOwner && article.productType === 'DEMAND';

  return (
    <div className="max-w-5xl mx-auto mt-8 p-4 grid grid-cols-1 lg:grid-cols-3 gap-8">

      {/* LEFT COLUMN */}
      <div className="lg:col-span-2">
        <Link to="/catalog" className="inline-flex items-center text-blue-600 hover:text-blue-800 mb-6 font-semibold">
          &larr; Back to Catalog
        </Link>

        <div className="bg-white rounded-2xl shadow-lg border border-gray-200 overflow-hidden">
          {article.imageBase64 ? (
            <img src={article.imageBase64} alt={article.title} className="w-full h-96 object-contain bg-gray-100" />
          ) : (
            <div className="w-full h-64 bg-gray-100 flex items-center justify-center text-gray-400">
              <span>No image available</span>
            </div>
          )}

          <div className="p-8">
            <div className="flex justify-between items-center mb-4">
              <div className="flex gap-2">
                {renderBadge()}
                <span className="bg-gray-100 text-gray-700 text-xs font-semibold px-3 py-1 rounded-full border border-gray-200">
                  {zoneLabel(article.zone)}
                </span>
                {article.category && (
                  <span className="bg-gray-200 text-gray-700 text-xs font-semibold px-3 py-1 rounded-full">
                    {article.category}
                  </span>
                )}
                {article.status === 'RESERVED' && (
                  <span className="bg-yellow-100 text-yellow-800 text-xs font-bold px-3 py-1 rounded-full border border-yellow-300">
                    Reserved
                  </span>
                )}
                {article.status === 'SOLD' && (
                  <span className="bg-gray-800 text-white text-xs font-bold px-3 py-1 rounded-full">
                    Sold
                  </span>
                )}
              </div>
              <span className="text-gray-500 text-sm">Published on {formattedDate}</span>
            </div>

            <div className="flex justify-between items-start mb-6">
              <h1 className="text-3xl font-extrabold text-gray-900 w-3/4">{article.title}</h1>
              <span className="text-2xl font-black text-gray-800">
                {article.price === 0 ? 'FREE' : `${article.price} €`}
              </span>
            </div>

            <div className="mb-8">
              <h3 className="text-lg font-bold text-gray-800 mb-2">Description</h3>
              <p className="text-gray-600 whitespace-pre-wrap leading-relaxed">{article.description}</p>
            </div>
          </div>
        </div>
      </div>

      {/* RIGHT COLUMN */}
      <div className="space-y-6 lg:mt-12">

        <div className="bg-white rounded-2xl shadow-lg border border-gray-200 p-6">
          <h3 className="text-sm font-bold text-gray-400 uppercase tracking-wider mb-4">About the owner</h3>

          {owner ? (
            <Link to={`/users/${owner.publicId}`} className="block mb-6 group">
              <div className="flex items-center gap-4">
                <div className="w-14 h-14 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center font-bold text-xl overflow-hidden shrink-0">
                  {owner.avatarUrl ? (
                    <img src={resolveAssetUrl(owner.avatarUrl)} alt={owner.displayName} className="h-full w-full object-cover" />
                  ) : (
                    <span>{(owner.displayName || 'User').split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0].toUpperCase()).join('')}</span>
                  )}
                </div>
                <div className="min-w-0">
                  <h4 className="text-lg font-bold text-gray-900 group-hover:text-blue-700 transition-colors">
                    {owner.displayName}
                  </h4>
                  <div className="flex flex-wrap items-center gap-2 mt-1 text-xs font-semibold text-gray-500">
                    <span className="px-2 py-1 rounded-full bg-gray-100">{zoneLabel(owner.approximateZone)}</span>
                    <span className="px-2 py-1 rounded-full bg-gray-100">{owner.points} points</span>
                    <span className="px-2 py-1 rounded-full bg-gray-100">
                      Joined {owner.memberSince ? new Date(owner.memberSince).toLocaleDateString() : 'Unknown'}
                    </span>
                  </div>
                </div>
              </div>
            </Link>
          ) : (
            <div className="text-gray-500 text-sm mb-6 animate-pulse">Loading owner profile...</div>
          )}

          {owner?.badges?.length ? (
            <div className="mb-6">
              <BadgeList badges={owner.badges} emptyMessage="This seller has no badges yet." />
            </div>
          ) : owner ? (
            <p className="text-sm text-gray-500 mb-6">This seller has not earned any badges yet.</p>
          ) : null}

          <hr className="border-gray-100 mb-6" />

          {!isOwner && (
            <button
              onClick={handleContact}
              disabled={contacting}
              className={`w-full mb-3 py-3 font-bold rounded-lg shadow-sm transition border ${contacting ? 'bg-gray-100 text-gray-400 border-gray-200' : 'bg-white text-blue-700 border-blue-300 hover:bg-blue-50'}`}
            >
              {contacting ? 'Opening chat…' : '💬 Contact'}
            </button>
          )}

          {isOwner ? (
            <div className="flex flex-col gap-3">
              <button
                onClick={() => navigate(`/catalog/edit/${article.id}`)}
                className="w-full py-3 bg-gray-100 text-gray-700 font-bold rounded-lg shadow-sm hover:bg-gray-200 transition"
              >
                Edit My Article
              </button>
              <button
                onClick={handleDelete}
                disabled={isDeleting}
                className={`w-full py-3 font-bold rounded-lg shadow-sm transition text-white ${isDeleting ? 'bg-red-400 cursor-not-allowed' : 'bg-red-500 hover:bg-red-600'}`}
              >
                {isDeleting ? 'Deleting...' : 'Delete Article'}
              </button>
            </div>
          ) : canAcquire ? (
            <div className="flex flex-col gap-3">
              {/* Rentals expose duration + deposit before the deal is created */}
              {article.productType === 'SYMBOLIC_RENTAL' && (
                <>
                  <select
                    value={rentDays}
                    onChange={(e) => setRentDays(Number(e.target.value))}
                    disabled={acquiring}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg shadow-sm bg-white focus:ring-2 focus:ring-blue-500"
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
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-2 focus:ring-blue-500"
                  />
                </>
              )}
              <button
                onClick={handleAcquire}
                disabled={acquiring}
                className={`w-full py-4 font-extrabold rounded-lg shadow-md transition text-white text-lg ${acquiring ? 'bg-blue-400 cursor-not-allowed' : 'bg-blue-600 hover:bg-blue-700 hover:scale-[1.02]'}`}
              >
                {acquiring ? 'Processing...' : `${acquireCfg.label} & Sign`}
              </button>
            </div>
          ) : canFulfill ? (
            <button
              onClick={handleFulfillDemand}
              disabled={acquiring}
              className={`w-full py-4 font-extrabold rounded-lg shadow-md transition text-white text-lg ${acquiring ? 'bg-blue-400 cursor-not-allowed' : 'bg-green-600 hover:bg-green-700 hover:scale-[1.02]'}`}
            >
              {acquiring ? 'Processing...' : 'Offer this item & Sign'}
            </button>
          ) : !isAvailable ? (
            <p className="text-gray-500 text-sm font-semibold">
              {article.status === 'SOLD'
                ? 'This article has already been sold.'
                : 'This article is currently reserved.'}
            </p>
          ) : (
            <p className="text-gray-500 text-sm">This article is not available for acquisition.</p>
          )}
        </div>

        {!isOwner && (canAcquire || canFulfill) && (
          <div className="bg-blue-50 p-4 rounded-xl border border-blue-100 text-sm text-blue-800">
            <p className="font-bold mb-1">Safe Transaction</p>
            <p>Your request will initiate a digital agreement secured by an OTP electronic signature.</p>
          </div>
        )}
      </div>
    </div>
  );
}

export default ArticleDetail;
