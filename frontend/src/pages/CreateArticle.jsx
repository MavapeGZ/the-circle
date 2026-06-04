import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

function CreateArticle() {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    title: '',
    description: '',
    category: 'DONATION', // Product type: DONATION | SELL | RENTAL | DEMAND
    type: 'OFFER',
    price: '', // Symbolic amount for SELL / RENTAL
    rentalTimeUnit: 'DAY' // Only used for RENTAL
  });

  const showPrice = formData.category === 'SELL' || formData.category === 'RENTAL';
  const showTimeUnit = formData.category === 'RENTAL';

  const [imagePreview, setImagePreview] = useState(null);
  const [imageBase64, setImageBase64] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Handler for text fields
  const handleChange = (e) => {
    const { name, value } = e.target;

    // If the category changes to "DEMAND", we automatically adjust the type
    if (name === 'category') {
      const isDemand = value === 'DEMAND';
      setFormData(prev => ({
        ...prev,
        category: value,
        type: isDemand ? 'DEMAND' : 'OFFER'
      }));
    } else {
      setFormData(prev => ({ ...prev, [name]: value }));
    }
  };

  // Handler to convert the image to Base64
  const handleImageChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      // Validate the size (e.g. maximum 2MB to avoid overloading the JSON)
      if (file.size > 2 * 1024 * 1024) {
        setError('The image is too large. Maximum size is 2MB.');
        return;
      }

      setError('');
      const reader = new FileReader();

      reader.onloadend = () => {
        setImagePreview(reader.result);
        setImageBase64(reader.result); // Store the base64 string
      };

      reader.readAsDataURL(file);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
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
        setLoading(false);
        return;
      }
      if (showPrice && symbolicPrice > 10) {
        setError('The symbolic amount cannot exceed 10 €.');
        setLoading(false);
        return;
      }

      const payload = {
        title: formData.title,
        description: formData.description,
        type: formData.type, // 'OFFER' o 'DEMAND'
        transactionMode,
        category: 'General', // Default category for now, we can enhance this later to allow users to select from predefined categories
        imageBase64: imageBase64,
        price: showPrice ? symbolicPrice : 0.0,
        rentalTimeUnit: showTimeUnit ? formData.rentalTimeUnit : null
      };

      await api.post('/catalog/articles', payload);

      // If successful, redirect the user to the catalog so they can see their article
      navigate('/catalog');
    } catch (err) {
      console.error('Error uploading article:', err);
      setError('Failed to publish the article. Please check your connection and try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto mt-10 p-6 bg-white rounded-xl shadow-lg border border-gray-100">
      <h1 className="text-3xl font-extrabold text-gray-800 mb-6 text-center">
        Publish an Article
      </h1>

      {error && (
        <div className="mb-6 p-4 bg-red-50 text-red-700 rounded-lg border border-red-200" role="alert">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">

        {/* TITLE */}
        <div>
          <label htmlFor="title" className="block text-sm font-semibold text-gray-700 mb-1">
            Title <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            id="title"
            name="title"
            required
            placeholder="e.g., Mountain Bike in good condition"
            className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none"
            value={formData.title}
            onChange={handleChange}
          />
        </div>

        {/* DESCRIPTION */}
        <div>
          <label htmlFor="description" className="block text-sm font-semibold text-gray-700 mb-1">
            Description <span className="text-red-500">*</span>
          </label>
          <textarea
            id="description"
            name="description"
            required
            rows="4"
            placeholder="Describe the item, its condition, and any other relevant details..."
            className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none resize-none"
            value={formData.description}
            onChange={handleChange}
          />
        </div>

        {/* CATEGORY */}
        <div>
          <label htmlFor="category" className="block text-sm font-semibold text-gray-700 mb-1">
            Category <span className="text-red-500">*</span>
          </label>
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

        {/* SYMBOLIC PRICE (Sale / Rental) */}
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
              placeholder={showTimeUnit ? 'e.g., 2.00 per period (max 10 €)' : 'e.g., 3.00 (max 10 €)'}
              className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none"
              value={formData.price}
              onChange={handleChange}
            />
          </div>
        )}

        {/* RENTAL TIME UNIT */}
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

        {/* IMAGE UPLOAD */}
        <div>
          <label htmlFor="image" className="block text-sm font-semibold text-gray-700 mb-1">
            Image (Max 2MB)
          </label>
          <input
            type="file"
            id="image"
            name="image"
            accept="image/*"
            onChange={handleImageChange}
            className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
          />

          {/* Image preview */}
          {imagePreview && (
            <div className="mt-4">
              <p className="text-xs text-gray-500 mb-2">Image Preview:</p>
              <img
                src={imagePreview}
                alt="Preview"
                className="h-48 w-auto object-cover rounded shadow-sm border border-gray-200"
              />
            </div>
          )}
        </div>

        {/* SUBMIT BUTTON */}
        <button
          type="submit"
          disabled={loading}
          className={`w-full py-3 px-4 text-white font-bold rounded shadow-md transition ${loading ? 'bg-blue-400 cursor-not-allowed' : 'bg-blue-600 hover:bg-blue-700'
            }`}
        >
          {loading ? 'Publishing...' : 'Publish Article'}
        </button>
      </form>
    </div>
  );
}

export default CreateArticle;