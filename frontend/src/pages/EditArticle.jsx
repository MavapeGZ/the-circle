import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import api from '../services/api';

function EditArticle() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    title: '',
    description: '',
    productType: 'SYMBOLIC_SALE',
    price: 0
  });
  
  const [imagePreview, setImagePreview] = useState(null);
  const [imageBase64, setImageBase64] = useState('');
  
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchArticle = async () => {
      try {
        const response = await api.get(`/catalog/articles/${id}`);
        const article = response.data;
        
        setFormData({
          title: article.title || '',
          description: article.description || '',
          productType: article.productType || 'SYMBOLIC_SALE',
          price: article.price || 0
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
    
    if (name === 'productType') {
      const isDonation = value === 'DONATION';
      setFormData(prev => ({
        ...prev,
        productType: value,
        price: isDonation ? 0 : prev.price
      }));
    } else {
      setFormData(prev => ({ ...prev, [name]: value }));
    }
  };

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

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');

    try {
      const payload = {
        title: formData.title,
        description: formData.description,
        productType: formData.productType,
        category: 'General',
        imageBase64: imageBase64,
        price: formData.productType === 'DONATION' ? 0.0 : parseFloat(formData.price)
      };

      await api.put(`/catalog/articles/${id}`, payload);
      
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
        {/* TITLE */}
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

        {/* DESCRIPTION */}
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

        {/* PRODUCT TYPE */}
        <div>
          <label htmlFor="productType" className="block text-sm font-semibold text-gray-700 mb-1">
            Product Type <span className="text-red-500">*</span>
          </label>
          <select
            id="productType"
            name="productType"
            className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none bg-white"
            value={formData.productType}
            onChange={handleChange}
          >
            <option value="SYMBOLIC_SALE">Symbolic Sale</option>
            <option value="SYMBOLIC_RENTAL">Symbolic Rental</option>
            <option value="DONATION">Donation (Free)</option>
            <option value="DEMAND">Demand</option>
          </select>
        </div>

        {/* PRICE (Renderizado Condicional) */}
        {formData.productType !== 'DONATION' && (
          <div>
            <label htmlFor="price" className="block text-sm font-semibold text-gray-700 mb-1">
              Price (€) <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              id="price"
              name="price"
              min="0"
              step="0.01"
              required
              className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none"
              value={formData.price}
              onChange={handleChange}
            />
          </div>
        )}

        {/* IMAGE UPLOAD */}
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