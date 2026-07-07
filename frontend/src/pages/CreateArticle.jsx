import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api, { extractApiError } from '../services/api';
import { usePreferences } from '../context/PreferencesContext';
import usePageTitle from '../hooks/usePageTitle';

// Product types that have no price (free / priority). DEMAND is priority, so no price selection.
const PRICELESS_TYPES = ['DONATION', 'DEMAND'];
// Deposit cap, in EUR (the stored base). Shown to the user in their currency.
const GUARANTEE_MAX_EUR = 20.0;

function CreateArticle() {
  usePageTitle('title.publish');
  const { t } = useTranslation();
  const { formatPrice, toEur, fromEur, currencySymbol } = usePreferences();
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
        setError(t('article.error.imageTooLarge'));
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
        // Inputs are typed in the user's currency; store EUR (the base) and
        // validate the cap in EUR.
        guaranteeAmount = toEur(formData.guaranteeAmount);
        if (guaranteeAmount == null || guaranteeAmount <= 0 || guaranteeAmount > GUARANTEE_MAX_EUR) {
          setError(t('create.error.deposit', {
            min: formatPrice(0.01),
            max: formatPrice(GUARANTEE_MAX_EUR),
          }));
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
        price: PRICELESS_TYPES.includes(formData.productType) ? 0.0 : (toEur(formData.price) ?? 0),
        guaranteeAmount
      };

      await api.post('/catalog/articles', payload);
      navigate('/catalog');
    } catch (err) {
      console.error('Error uploading article:', err);
      const status = err?.response?.status;
      if (status === 401 || status === 403) {
        setError(t('create.error.loginRequired'));
      } else if (status === 422) {
        setMissingIban(true);
        setError(extractApiError(err, t('create.error.missingIban')));
      } else {
        setError(extractApiError(err, t('create.error.generic')));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto mt-10 p-6 bg-white rounded-xl shadow-lg border border-gray-100">
      <h1 className="text-3xl font-extrabold text-gray-800 mb-6 text-center">
        {t('create.title')}
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
              {t('create.goPayments')}
            </Link>
          )}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6" data-testid="article-create-form">

        {/* TITLE */}
        <div>
          <label htmlFor="title" className="block text-sm font-semibold text-gray-700 mb-1">
            {t('article.field.title')} <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            id="title"
            name="title"
            required
            maxLength={140}
            data-testid="article-title"
            placeholder={t('article.field.titlePlaceholder')}
            className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-indigo-500 focus:outline-none"
            value={formData.title}
            onChange={handleChange}
          />
          <p className="text-xs text-gray-400 mt-1 text-right">{formData.title.length}/140</p>
        </div>

        {/* DESCRIPTION */}
        <div>
          <label htmlFor="description" className="block text-sm font-semibold text-gray-700 mb-1">
            {t('article.field.description')} <span className="text-red-500">*</span>
          </label>
          <textarea
            id="description"
            name="description"
            required
            rows="4"
            maxLength={4000}
            data-testid="article-description"
            placeholder={t('article.field.descriptionPlaceholder')}
            className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-indigo-500 focus:outline-none resize-none"
            value={formData.description}
            onChange={handleChange}
          />
          <p className="text-xs text-gray-400 mt-1 text-right">{formData.description.length}/4000</p>
        </div>

        {/* PRODUCT TYPE */}
        <div>
          <label htmlFor="productType" className="block text-sm font-semibold text-gray-700 mb-1">
            {t('article.field.productType')} <span className="text-red-500">*</span>
          </label>
          <select
            id="productType"
            name="productType"
            data-testid="article-product-type"
            className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-indigo-500 focus:outline-none bg-white"
            value={formData.productType}
            onChange={handleChange}
          >
            <option value="SYMBOLIC_SALE">{t('article.type.sale')}</option>
            <option value="SYMBOLIC_RENTAL">{t('article.type.rental')}</option>
            <option value="DONATION">{t('article.type.donation')}</option>
            <option value="DEMAND">{t('article.type.demand')}</option>
          </select>
        </div>

        {/* PRICE */}
        {!PRICELESS_TYPES.includes(formData.productType) && (
          <div>
            <label htmlFor="price" className="block text-sm font-semibold text-gray-700 mb-1">
              {t('article.field.price', { currency: currencySymbol })} <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              id="price"
              name="price"
              min="0"
              step="0.01"
              required
              data-testid="article-price"
              placeholder="0.00"
              className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-indigo-500 focus:outline-none"
              value={formData.price}
              onChange={handleChange}
            />
          </div>
        )}

        {/* SECURITY DEPOSIT (rentals only) */}
        {formData.productType === 'SYMBOLIC_RENTAL' && (
          <div>
            <label htmlFor="guaranteeAmount" className="block text-sm font-semibold text-gray-700 mb-1">
              {t('create.field.deposit', { currency: currencySymbol })} <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              id="guaranteeAmount"
              name="guaranteeAmount"
              min="0.01"
              max={fromEur(GUARANTEE_MAX_EUR)}
              step="0.01"
              required
              data-testid="article-deposit"
              placeholder={t('create.depositPlaceholder')}
              className="w-full p-3 border border-gray-300 rounded focus:ring-2 focus:ring-indigo-500 focus:outline-none"
              value={formData.guaranteeAmount}
              onChange={handleChange}
            />
            <p className="text-xs text-gray-500 mt-1">
              {t('create.depositHelp', { max: formatPrice(GUARANTEE_MAX_EUR) })}
            </p>
          </div>
        )}

        {/* IMAGE UPLOAD */}
        <div>
          <label htmlFor="image" className="block text-sm font-semibold text-gray-700 mb-1">
            {t('create.field.image')}
          </label>
          <input
            type="file"
            id="image"
            name="image"
            accept="image/*"
            data-testid="article-image"
            onChange={handleImageChange}
            className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 file:text-sm file:font-semibold file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100"
          />

          {imagePreview && (
            <div className="mt-4">
              <p className="text-xs text-gray-500 mb-2">{t('create.imagePreview')}</p>
              <img
                src={imagePreview}
                alt={t('article.imageAlt')}
                className="h-48 w-auto object-cover rounded shadow-sm border border-gray-200"
              />
            </div>
          )}
        </div>

        {/* SUBMIT BUTTON */}
        <button
          type="submit"
          disabled={loading}
          data-testid="article-submit"
          className={`w-full py-3 px-4 text-white font-bold rounded shadow-md transition ${
            loading ? 'bg-indigo-400 cursor-not-allowed' : 'bg-indigo-600 hover:bg-indigo-700'
          }`}
        >
          {loading ? t('create.publishing') : t('create.publish')}
        </button>
      </form>
    </div>
  );
}

export default CreateArticle;