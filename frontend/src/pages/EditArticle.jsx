import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import api from '../services/api';

function EditArticle() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    title: '',
    description: '',
    category: '', // DONATION | SELL | RENTAL | DEMAND
    type: '',
    price: '',
    rentalTimeUnit: 'DAY'
  });

  const showPrice = formData.category === 'SELL' || formData.category === 'RENTAL';
  const showTimeUnit = formData.category === 'RENTAL';
  
  const [imagePreview, setImagePreview] = useState(null);
  const [imageBase64, setImageBase64] = useState('');
  
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  // Load the current article data to prefill the form
  useEffect(() => {
    const fetchArticle = async () => {
      try {
        const response = await api.get(`/catalog/articles/${id}`);
        const article = response.data;

        const category =
          article.type === 'DEMAND' ? 'DEMAND'
          : article.transactionMode === 'SELL' ? 'SELL'
          : article.transactionMode === 'RENT' ? 'RENTAL'
          : 'DONATION'; // DONATE / GIFT / unset

        setFormData({
          title: article.title || '',
          description: article.description || '',
          category,
          type: article.type || 'OFFER',
          price: article.price != null && article.price > 0 ? String(article.price) : '',
          rentalTimeUnit: article.rentalTimeUnit || 'DAY'
        });
        
        if (article.imageBase64) {
          setImagePreview(article.imageBase64);
          setImageBase64(article.imageBase64);
        }
      } catch (err) {
        console.error(err);
        setError('Error loading article data.');
      } finally {
        setLoading(false);
      }
    };

    fetchArticle();
  }, [id]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    if (name === 'category') {
      const isDemand = value === 'DEMAND';
      setFormData(prev => ({ ...prev, category: value, type: isDemand ? 'DEMAND' : 'OFFER' }));
    } else {
      setFormData(prev => ({ ...prev, [name]: value }));
    }
  };

  // Convert the new image to Base64 if it is changed
  const handleImageChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      if (file.size > 2 * 1024 * 1024) {
        setError('The image is too large. Maximum size is 2MB.');
        return;
      }
      setError('');
      const reader = new FileReader();
      reader.onloadend = () => {
        setImagePreview(reader.result);
        setImageBase64(reader.result);
      };
      reader.readAsDataURL(file);
    }
  };

  // Send the modified data to the Java PUT endpoint
  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');

    try {
      const transactionMode =
        formData.category === 'DONATION' ? 'DONATE'
        : formData.category === 'SELL' ? 'SELL'
        : formData.category === 'RENTAL' ? 'RENT'
        : null; // DEMAND

      const symbolicPrice = showPrice ? parseFloat(formData.price) : 0.0;
      if (showPrice && (!Number.isFinite(symbolicPrice) || symbolicPrice <= 0)) {
        setError('Please enter a valid symbolic amount greater than 0.');
        setSaving(false);
        return;
      }
      if (showPrice && symbolicPrice > 10) {
        setError('The symbolic amount cannot exceed 10 €.');
        setSaving(false);
        return;
      }

      const payload = {
        title: formData.title,
        description: formData.description,
        type: formData.type,
        transactionMode,
        category: 'General',
        imageBase64: imageBase64,
        price: showPrice ? symbolicPrice : 0.0,
        rentalTimeUnit: showTimeUnit ? formData.rentalTimeUnit : null
      };

      await api.put(`/catalog/articles/${id}`, payload);
      
      // If everything goes well, return to the detail view to see the changes
      navigate(`/catalog/${id}`);
    } catch (err) {
      console.error('Error updating article:', err);
      setError('Failed to update the article. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="text-center mt-20 text-xl text-gray-500 animate-pulse">Loading article data...</div>;

  return (
    <div className="max-w-3xl mx-auto mt-10 p-6 bg-white rounded-xl shadow-lg border border-gray-100">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-3xl font-extrabold text-gray-800">Edit Article</h1>
        <Link to={`/catalog/${id}`} className="text-sm text-gray-500 hover:text-gray-700">Cancel</Link>
      </div>
      
      {error && (
        <div className="mb-6 p-4 bg-red-50 text-red-700 rounded-lg border border-red-200" role="alert">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
        <div>
          <label htmlFor="title" className="block text-sm font-semibold text-gray-700 mb-1">Title</label>
          <input
            type="text"
            id="title"
            name="title"
            required
            className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none"
            value={formData.title}
            onChange={handleChange}
          />
        </div>

        <div>
          <label htmlFor="description" className="block text-sm font-semibold text-gray-700 mb-1">Description</label>
          <textarea
            id="description"
            name="description"
            required
            rows="4"
            className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none resize-none"
            value={formData.description}
            onChange={handleChange}
          />
        </div>

        <div>
          <label htmlFor="category" className="block text-sm font-semibold text-gray-700 mb-1">Product type</label>
          <select
            id="category"
            name="category"
            className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none bg-white"
            value={formData.category}
            onChange={handleChange}
          >
            <option value="DONATION">Offer: Donation (Free)</option>
            <option value="SELL">Offer: Symbolic Sale</option>
            <option value="RENTAL">Offer: Symbolic Rental</option>
            <option value="DEMAND">Demand: I am looking for something</option>
          </select>
        </div>

        {showPrice && (
          <div>
            <label htmlFor="price" className="block text-sm font-semibold text-gray-700 mb-1">
              Symbolic amount (€) <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              id="price"
              name="price"
              required
              min="0.01"
              max="10"
              step="0.01"
              className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none"
              value={formData.price}
              onChange={handleChange}
            />
          </div>
        )}

        {showTimeUnit && (
          <div>
            <label htmlFor="rentalTimeUnit" className="block text-sm font-semibold text-gray-700 mb-1">
              Rental period <span className="text-red-500">*</span>
            </label>
            <select
              id="rentalTimeUnit"
              name="rentalTimeUnit"
              className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none bg-white"
              value={formData.rentalTimeUnit}
              onChange={handleChange}
            >
              <option value="HOUR">Per hour</option>
              <option value="DAY">Per day</option>
              <option value="WEEK">Per week</option>
              <option value="MONTH">Per month</option>
            </select>
          </div>
        )}

        <div>
          <label htmlFor="image" className="block text-sm font-semibold text-gray-700 mb-1">Change Image (Optional)</label>
          <input
            type="file"
            id="image"
            accept="image/*"
            onChange={handleImageChange}
            className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
          />
          {imagePreview && (
            <div className="mt-4">
              <img src={imagePreview} alt="Preview" className="h-48 w-auto object-cover rounded border border-gray-200" />
            </div>
          )}
        </div>

        <button
          type="submit"
          disabled={saving}
          className={`w-full py-3 px-4 text-white font-bold rounded shadow-md transition ${
            saving ? 'bg-blue-400 cursor-not-allowed' : 'bg-blue-600 hover:bg-blue-700'
          }`}
        >
          {saving ? 'Saving changes...' : 'Save Changes'}
        </button>
      </form>
    </div>
  );
}

export default EditArticle;