import { useState, useEffect } from 'react';
import api from '../services/api';
import ArticleCard from '../components/ArticleCard';

function Catalog() {
  const [articles, setArticles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Search states
  const [query, setQuery] = useState('');
  const [type, setType] = useState(''); // '' = All, 'OFFER' = Offers, 'DEMAND' = Demands

  // Function that calls your API Gateway / OpenSearch
  const fetchArticles = async (searchQuery = '', searchType = '') => {
    setLoading(true);
    setError('');
    
    try {
      // We build the parameters. Axios converts them to: ?q=bike&type=OFFER
      const params = {};
      if (searchQuery) params.q = searchQuery;
      if (searchType) params.type = searchType;

      const response = await api.get('/catalog/articles/search', { params });
      
      // OpenSearch returns { content: [...], totalElements, ... }
      setArticles(response.data.content || []);
    } catch (err) {
      console.error(err);
      setError('Error loading catalog. Please try again later.');
    } finally {
      setLoading(false);
    }
  };

  // Load articles on page entry (empty search)
  useEffect(() => {
    fetchArticles();
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
          placeholder="Search for bikes, books, clothes..." 
          className="flex-grow p-3 border border-gray-300 rounded focus:outline-none focus:ring-2 focus:ring-blue-500"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        
        <select 
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