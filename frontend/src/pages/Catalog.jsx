import { useState, useEffect, useRef } from 'react';
import axios from 'axios'; // Import axios for proving isCancel
import api from '../services/api';
import ArticleCard from '../components/ArticleCard';

function Catalog() {
  const [articles, setArticles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Search states
  const [query, setQuery] = useState('');
  const [type, setType] = useState(''); // '' = All, 'OFFER' = Offers, 'DEMAND' = Demands

  // We use a ref to store the current AbortController, so we can cancel it if needed
  const abortControllerRef = useRef(null);

  // Function that calls your API Gateway / OpenSearch
  const fetchArticles = async (searchQuery = '', searchType = '') => {
    // If there is already a request in progress, we cancel it before launching the new one
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    // Create a new AbortController for the new request
    const controller = new AbortController();
    abortControllerRef.current = controller;

    setLoading(true);
    setError('');

    try {
      // We build the parameters. Axios converts them to: ?q=bike&type=OFFER
      const params = {};
      if (searchQuery) params.q = searchQuery;
      if (searchType) params.type = searchType;

      const response = await api.get('/catalog/articles/search', { params, signal: controller.signal });

      // OpenSearch returns { content: [...], totalElements, ... }
      setArticles(response.data.content || []);
    } catch (err) {
      if (axios.isCancel(err)) {
        console.log('Previous search request cancelled', err.message);
        return; // Don't set error if the request was cancelled
      } else {
        console.error(err);
        setError('Error loading catalog. Please try again later.');
      }
    } finally {
      if (abortControllerRef.current === controller) {
        setLoading(false);
      }
    }
  };

  // Load articles on page entry (empty search)
  useEffect(() => {
    fetchArticles();

    // Cleanup: If the user navigates away or component unmounts, we cancel any ongoing request
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  // Handler for search button
  const handleSearch = (e) => {
    e.preventDefault();
    fetchArticles(query, type);
  };

  return (
    <div className="max-w-6xl mx-auto mt-4">
      <div className="flex flex-col items-center mb-10">
        <h1 className="text-4xl font-extrabold text-gray-800 mb-2">Solidary Catalog</h1>
        <p className="text-gray-500">Find or request what you need in the community.</p>
      </div>

      {/* SEARCH BAR */}
      <form onSubmit={handleSearch} className="bg-white p-4 rounded-xl shadow-md border border-gray-200 flex flex-col md:flex-row gap-4 mb-10">
        <input
          type="text"
          aria-label="Search catalog"
          placeholder="Search for bikes, books, clothes..."
          className="flex-grow p-3 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />

        <select
          aria-label="Filter by type"
          className="p-3 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
          value={type}
          onChange={(e) => setType(e.target.value)}
        >
          <option value="">All types</option>
          <option value="OFFER">Only Offers</option>
          <option value="DEMAND">Only Demands</option>
        </select>

        <button
          type="submit"
          className="bg-blue-600 text-white font-bold py-3 px-8 rounded hover:bg-blue-700 transition"
        >
          Search
        </button>
      </form>

      {/* STATE: LOADING / ERROR / RESULTS */}
      {loading ? (
        <div className="text-center py-20 text-gray-500 text-xl font-semibold animate-pulse">
          Searching in OpenSearch...
        </div>
      ) : error ? (
        <div className="bg-red-100 text-red-700 p-4 rounded-lg text-center font-semibold">
          {error}
        </div>
      ) : articles.length === 0 ? (
        <div className="text-center py-20 text-gray-500 text-xl">
          No results found.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {articles.map((article) => (
            <ArticleCard key={article.id} article={article} />
          ))}
        </div>
      )}
    </div>
  );
}

export default Catalog;