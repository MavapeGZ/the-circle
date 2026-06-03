import { useState, useEffect, useContext } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';

function ArticleDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  
  const { user: currentUser } = useContext(AuthContext);
  
  const [article, setArticle] = useState(null);
  const [owner, setOwner] = useState(null); 
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [isDeleting, setIsDeleting] = useState(false);
  const [isRequesting, setIsRequesting] = useState(false);

  useEffect(() => {
    const fetchArticleAndOwner = async () => {
      try {
        const articleRes = await api.get(`/catalog/articles/${id}`);
        const articleData = articleRes.data;
        setArticle(articleData);

        if (articleData.authorId) {
          try {
          } catch (ownerErr) {
            console.error('Error fetching owner profile:', ownerErr);
            setOwner({ firstName: 'Unknown', lastName: '', kycStatus: 'UNKNOWN' });
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
    const confirmed = window.confirm('Are you sure you want to delete this article? This action cannot be undone.');
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

  const handleRequestExchange = async () => {
    if (!currentUser?.id) {
      alert('You must be logged in to request this article.');
      navigate('/login');
      return;
    }

    setIsRequesting(true);
    try {
      const payload = {
        articleId: article.id,
        providerId: article.authorId,
        requesterId: currentUser.id,
        transactionMode: article.transactionMode
      };
      await api.post('/contracts/requests', payload);
      alert('Request sent successfully! The owner will be notified.');
      navigate('/catalog');
    } catch (err) {
      console.error('Error requesting exchange:', err);
      alert('Error processing the request. Please try again.');
    } finally {
      setIsRequesting(false);
    }
  };

  if (loading) return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">Loading article...</div>;
  if (error) return <div className="text-center mt-20 text-xl text-red-600 font-bold">{error}</div>;
  if (!article) return null;

  const isOffer = article.type === 'OFFER';
  const formattedDate = new Date(article.createdAt).toLocaleDateString('en-US', {
    day: '2-digit', month: 'long', year: 'numeric'
  });

  const isOwner = currentUser && String(currentUser.id) === String(article.authorId);

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
                <span className={`text-xs font-bold px-3 py-1 rounded-full border ${isOffer ? 'bg-green-100 text-green-800 border-green-300' : 'bg-blue-100 text-blue-800 border-blue-300'}`}>
                  {isOffer ? 'OFFER' : 'DEMAND'}
                </span>
                <span className="bg-gray-200 text-gray-700 text-xs font-semibold px-3 py-1 rounded-full">
                  {article.category}
                </span>
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
            <div className="flex items-center gap-4 mb-6">
              <div className="w-14 h-14 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center font-bold text-xl">
                {owner.firstName.charAt(0)}{owner.lastName.charAt(0)}
              </div>
              <div>
                <h4 className="text-lg font-bold text-gray-900">
                  {owner.firstName} {owner.lastName}
                </h4>
                <div className="flex items-center mt-1">
                  <span className={`text-xs font-semibold px-2 py-1 rounded-full ${owner.kycStatus === 'VERIFIED' ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'}`}>
                    {owner.kycStatus}
                  </span>
                </div>
              </div>
            </div>
          ) : (
            <div className="text-gray-500 text-sm mb-6 animate-pulse">Loading owner profile...</div>
          )}

          <hr className="border-gray-100 mb-6" />

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
          ) : (
            <button 
              onClick={handleRequestExchange}
              disabled={isRequesting}
              className={`w-full py-4 font-extrabold rounded-lg shadow-md transition text-white text-lg ${isRequesting ? 'bg-blue-400 cursor-not-allowed' : 'bg-blue-600 hover:bg-blue-700 hover:scale-[1.02]'}`}
            >
              {isRequesting ? 'Processing...' : 'Request Exchange'}
            </button>
          )}
        </div>

        {!isOwner && (
          <div className="bg-blue-50 p-4 rounded-xl border border-blue-100 text-sm text-blue-800">
            <p className="font-bold mb-1">Safe Transaction</p>
            <p>Your request will initiate a digital agreement governed by the platform's trust framework.</p>
          </div>
        )}
      </div>
    </div>
  );
}

export default ArticleDetail;