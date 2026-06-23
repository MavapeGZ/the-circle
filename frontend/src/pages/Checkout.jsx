import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api, { extractApiError } from '../services/api';
import { usePreferences } from '../context/PreferencesContext';
import { contractTypeLabel } from '../utils/contractType';

const luhn = (raw) => {
  const digits = (raw || '').replace(/\D/g, '');
  if (digits.length < 12) return false;
  let sum = 0;
  let dbl = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let n = parseInt(digits[i], 10);
    if (dbl) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
    dbl = !dbl;
  }
  return sum % 10 === 0;
};

const formatCardNumber = (raw) => (raw || '').replace(/\D/g, '').slice(0, 19).replace(/(.{4})/g, '$1 ').trim();

// MM auto-completes with a `/` so the user types 4 digits and gets MM/YY for free.
// We keep at most 4 digits (MMYY); the slash is inserted after the first 2.
const formatExpiry = (raw) => {
  const digits = (raw || '').replace(/\D/g, '').slice(0, 4);
  if (digits.length <= 2) return digits;
  return `${digits.slice(0, 2)}/${digits.slice(2)}`;
};

function Checkout() {
  const { t } = useTranslation();
  const { formatPrice } = usePreferences();
  const { contractId } = useParams();
  const navigate = useNavigate();

  const [contract, setContract] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [card, setCard] = useState({ number: '', expiry: '', cvc: '', holderName: '' });

  useEffect(() => {
    (async () => {
      try {
        const res = await api.get(`/contracts/${contractId}`);
        setContract(res.data);
      } catch {
        setError(t('checkout.loadError'));
      } finally {
        setLoading(false);
      }
    })();
  }, [contractId]);

  const amount = contract?.type === 'SALE' ? contract?.price : contract?.guaranteeAmount;
  const amountLabel = contract?.type === 'RENT' ? t('checkout.deposit') : t('checkout.amountDue');

  const isLuhnValid = luhn(card.number);
  const isExpiryValid = /^(0[1-9]|1[0-2])\/(\d{2}|\d{4})$/.test(card.expiry.trim());
  const isCvcValid = /^\d{3,4}$/.test(card.cvc.trim());
  const canSubmit = !submitting && isLuhnValid && isExpiryValid && isCvcValid && card.holderName.trim().length > 0;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError('');
    try {
      const res = await api.post(`/contracts/${contractId}/payments`, {
        cardNumber: card.number.replace(/\s/g, ''),
        expiry: card.expiry.trim(),
        cvc: card.cvc.trim(),
        holderName: card.holderName.trim(),
      });
      if (res.data?.status === 'FAILED') {
        navigate(`/contracts/${contractId}/payments/${res.data.id}/failure`, { state: { payment: res.data } });
      } else {
        navigate(`/contracts/${contractId}/payments/${res.data.id}/receipt`, { state: { payment: res.data } });
      }
    } catch (err) {
      const status = err?.response?.status;
      const data = err?.response?.data || {};
      // Spring returns `message` when include-message=always; falls back to `error` (reason
      // phrase) otherwise. The reason phrase ("Bad Request") is useless on its own, so we
      // only surface it when it actually carries explanatory text.
      const fieldMsg = Array.isArray(data.fields)
        ? data.fields.map((f) => f?.message).filter(Boolean).join(' ')
        : '';
      const raw = (data.message || fieldMsg || '').trim();
      const reason = (data.error || '').trim();
      const detail = raw || (reason && reason !== 'Bad Request' && reason !== 'Internal Server Error' ? reason : '');

      if (status === 422) {
        setError(detail || t('checkout.err.noPayout'));
      } else if (status === 400) {
        setError(detail
          ? t('checkout.err.notCompleted', { detail })
          : t('checkout.err.checkCard'));
      } else if (status === 403) {
        setError(t('checkout.err.onlyBuyer'));
      } else if (status === 409) {
        setError(t('checkout.err.noLonger'));
      } else if (status === 401) {
        setError(t('checkout.err.sessionExpired'));
      } else if (status >= 500) {
        setError(t('checkout.err.unexpected'));
      } else {
        setError(t('checkout.err.unexpectedLater'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">{t('checkout.loading')}</div>;
  if (!contract) return <div className="max-w-xl mx-auto mt-10 p-6 bg-red-50 text-red-700 rounded-lg">{error || t('checkout.notFound')}</div>;

  return (
    <div className="max-w-3xl mx-auto mt-8 p-4">
      <h1 className="text-3xl font-extrabold text-gray-900 mb-2">{t('checkout.title')}</h1>
      <p className="text-gray-500 mb-6">
        {t('checkout.intro')}
      </p>

      {error && (
        <div className="mb-6 p-4 bg-red-50 text-red-700 rounded-lg border border-red-200">{error}</div>
      )}

      <div className="bg-indigo-50 border border-indigo-100 rounded-xl p-4 mb-6 text-sm text-indigo-800">
        <p><span className="font-bold">{t('checkout.contract')}</span> {contract.id}</p>
        <p><span className="font-bold">{t('checkout.type')}</span> {contractTypeLabel(contract.type, t)}</p>
        <p><span className="font-bold">{amountLabel}:</span> {formatPrice(amount)}</p>
        <p className="mt-2 text-xs text-indigo-700">
          {t('checkout.escrowNote')}
        </p>
      </div>

      <form onSubmit={handleSubmit} className="bg-white p-6 rounded-2xl shadow-lg border border-gray-100 space-y-5">
        <div>
          <label className="block text-sm font-bold text-gray-700 mb-1">{t('checkout.cardholder')}</label>
          <input
            type="text"
            value={card.holderName}
            onChange={(e) => setCard({ ...card, holderName: e.target.value })}
            className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500"
            placeholder={t('checkout.cardholderPlaceholder')}
            required
          />
        </div>

        <div>
          <label className="block text-sm font-bold text-gray-700 mb-1">{t('checkout.cardNumber')}</label>
          <input
            type="text"
            inputMode="numeric"
            value={card.number}
            onChange={(e) => setCard({ ...card, number: formatCardNumber(e.target.value) })}
            className={`w-full border rounded-lg p-2.5 font-mono tracking-wider focus:ring-2 focus:ring-indigo-500 ${
              card.number && !isLuhnValid ? 'border-red-300' : 'border-gray-300'
            }`}
            placeholder="4242 4242 4242 4242"
            required
          />
          {card.number && !isLuhnValid && (
            <p className="text-xs text-red-600 mt-1">{t('checkout.cardInvalid')}</p>
          )}
          <p className="text-xs text-gray-500 mt-1">
            {t('checkout.demoTip')}
          </p>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-1">{t('checkout.expiry')}</label>
            <input
              type="text"
              inputMode="numeric"
              value={card.expiry}
              onChange={(e) => setCard({ ...card, expiry: formatExpiry(e.target.value) })}
              maxLength={5}
              className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500"
              placeholder="MM/YY"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-1">{t('checkout.cvc')}</label>
            <input
              type="text"
              inputMode="numeric"
              value={card.cvc}
              onChange={(e) => setCard({ ...card, cvc: e.target.value.replace(/\D/g, '').slice(0, 4) })}
              className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500"
              placeholder="123"
              required
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={!canSubmit}
          className={`w-full py-3 px-4 text-white font-bold rounded-lg shadow-md transition ${
            canSubmit ? 'bg-indigo-600 hover:bg-indigo-700' : 'bg-indigo-300 cursor-not-allowed'
          }`}
        >
          {submitting ? t('checkout.processing') : t('checkout.pay', { amount: formatPrice(amount) })}
        </button>
      </form>
    </div>
  );
}

export default Checkout;
