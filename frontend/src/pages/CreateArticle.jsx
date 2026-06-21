import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api, { extractApiError } from '../services/api';

// Product types that have no price (free / priority). DEMAND is priority, so no price selection.
const PRICELESS_TYPES = ['DONATION', 'DEMAND'];
const GUARANTEE_MAX = 20.0;

function CreateArticle() {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    title: '',
    description: '',
    productType: 'SYMBOLIC_SALE', // Default to SYMBOLIC_SALE, but can be changed to DONATION, SYMBOLIC_RENTAL, or DEMAND
    price: 0,
    guaranteeAmount: ''
  });

  const [imagePreview, setImagePreview] = useState(null);
  const [imageBase64, setImageBase64] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [missingIban, setMissingIban] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;

    if (name === 'productType') {
      const isPriceless = PRICELESS_TYPES.includes(value);
      setFormData(prev => ({
        ...prev,
        productType: value,
        price: isPriceless ? 0 : prev.price,
        guaranteeAmount: value === 'SYMBOLIC_RENTAL' ? prev.guaranteeAmount : ''
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
    setLoading(true);
    setError('');

    try {
      const isRental = formData.productType === 'SYMBOLIC_RENTAL';
      let guaranteeAmount = null;
      if (isRental) {
        guaranteeAmount = parseFloat(formData.guaranteeAmount);
        if (Number.isNaN(guaranteeAmount) || guaranteeAmount <= 0 || guaranteeAmount > GUARANTEE_MAX) {
          setError(`Security deposit must be between 0.01€ and ${GUARANTEE_MAX}€.`);
          setLoading(false);
          return;
        }
      }
      const payload = {
        title: formData.title,
        description: formData.description,
        productType: formData.productType,
        category: 'General',
        imageBase64: imageBase64,
        price: PRICELESS_TYPES.includes(formData.productType) ? 0.0 : parseFloat(formData.price),
        guaranteeAmount
      };

      await api.post('/catalog/articles', payload);
      navigate('/catalog');
    } catch (err) {
      console.error('Error uploading article:', err);
      const status = err?.response?.status;
      if (status === 401 || status === 403) {
        setError('You must be logged in to publish an article.');
      } else if (status === 422) {
        setMissingIban(true);
        setError(extractApiError(err, 'Missing payout information. Please add an IBAN in Settings before publishing paid items.'));
      } else {
        setError(extractApiError(err, 'Failed to publish the article. Please check your connection and try again.'));
      }
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
          <p>{error}</p>
          {missingIban && (
            <Link
              to="/settings"
              state={{ tab: 'payments' }}
              className="inline-block mt-3 text-sm font-bold underline hover:no-underline"
            >
              Go to Settings → Payments
            </Link>
          )}
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
            maxLength={140}
            placeholder="e.g., Mountain Bike in good condition"
            className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none"
            value={formData.title}
            onChange={handleChange}
          />
          <p className="text-xs text-gray-400 mt-1 text-right">{formData.title.length}/140</p>
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
            maxLength={4000}
            placeholder="Describe the item, its condition, and any other relevant details..."
            className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none resize-none"
            value={formData.description}
            onChange={handleChange}
          />
          <p className="text-xs text-gray-400 mt-1 text-right">{formData.description.length}/4000</p>
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

        {/* PRICE */}
        {!PRICELESS_TYPES.includes(formData.productType) && (
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
              placeholder="0.00"
              className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none"
              value={formData.price}
              onChange={handleChange}
            />
          </div>
        )}

        {/* SECURITY DEPOSIT (rentals only) */}
        {formData.productType === 'SYMBOLIC_RENTAL' && (
          <div>
            <label htmlFor="guaranteeAmount" className="block text-sm font-semibold text-gray-700 mb-1">
              Security deposit (€) <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              id="guaranteeAmount"
              name="guaranteeAmount"
              min="0.01"
              max={GUARANTEE_MAX}
              step="0.01"
              required
              placeholder="e.g. 10.00"
              className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 focus:outline-none"
              value={formData.guaranteeAmount}
              onChange={handleChange}
            />
            <p className="text-xs text-gray-500 mt-1">
              Refundable deposit held during the rental. Capped at {GUARANTEE_MAX}€.
            </p>
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
          className={`w-full py-3 px-4 text-white font-bold rounded shadow-md transition ${
            loading ? 'bg-blue-400 cursor-not-allowed' : 'bg-blue-600 hover:bg-blue-700'
          }`}
        >
          {loading ? 'Publishing...' : 'Publish Article'}
        </button>
      </form>
    </div>
  );
}

export default CreateArticle;