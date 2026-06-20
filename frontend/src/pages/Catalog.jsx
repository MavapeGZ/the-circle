import { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import api from '../services/api';
import ArticleCard from '../components/ArticleCard';
import { ZONE_OPTIONS } from '../constants/zones';

function Catalog() {
  const [articles, setArticles] = useState([]);
  const [sellerProfiles, setSellerProfiles] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [query, setQuery] = useState('');
  const [productType, setProductType] = useState('');
  const [zone, setZone] = useState('');

  const abortControllerRef = useRef(null);

  const fetchArticles = async (searchQuery = '', searchProductType = '', searchZone = '') => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    const controller = new AbortController();
    abortControllerRef.current = controller;

    setLoading(true);
    setError('');

    try {
      const params = {};
      if (searchQuery) params.q = searchQuery;
      if (searchProductType) params.productType = searchProductType;
      if (searchZone) params.zone = searchZone;

      const response = await api.get('/catalog/articles/search', { params, signal: controller.signal });

      setArticles(response.data.content || []);
    } catch (err) {
      if (axios.isCancel(err)) {
        console.log('Previous search request cancelled', err.message);
        return; 
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

  useEffect(() => {
    fetchArticles();

    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
    };
  }, []);

  useEffect(() => {
    const authorIds = [...new Set(articles.map((article) => article.authorId).filter(Boolean))];

    if (authorIds.length === 0) {
      setSellerProfiles({});
      return;
    }

    const controller = new AbortController();

    const loadProfiles = async () => {
      try {
        const { data } = await api.get('/users/public', {
          params: { ids: authorIds.join(',') },
          signal: controller.signal,
        });

        if (!controller.signal.aborted) {
          const profilesById = Object.fromEntries((data || []).map((profile) => [String(profile.id), profile]));
          setSellerProfiles(profilesById);
        }
      } catch (err) {
        if (axios.isCancel(err) || err?.name === 'CanceledError') {
          return;
        }
        console.error('Error loading seller profiles:', err);
        setSellerProfiles({});
      }
    };

    loadProfiles();

    return () => {
      controller.abort();
    };
  }, [articles]);

  const handleSearch = (e) => {
    e.preventDefault();
    fetchArticles(query, productType, zone);
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
          aria-label="Filter by product type"
          className="p-3 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
          value={productType}
          onChange={(e) => setProductType(e.target.value)}
        >
          <option value="">All Types</option>
          <option value="DONATION">🎁 Only Donations</option>
          <option value="SYMBOLIC_SALE">Sales</option>
          <option value="SYMBOLIC_RENTAL">Rentals</option>
          <option value="DEMAND">Demands</option>
        </select>

        <select
          aria-label="Filter by area"
          className="p-3 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
          value={zone}
          onChange={(e) => setZone(e.target.value)}
        >
          <option value="">All areas</option>
          {ZONE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
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
          Searching in catalog...
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
            <ArticleCard
              key={article.id}
              article={article}
              sellerProfile={sellerProfiles[String(article.authorId)]}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default Catalog;