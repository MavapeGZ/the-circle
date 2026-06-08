import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '../services/api';
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
        setError('Could not load the contract.');
      } finally {
        setLoading(false);
      }
    })();
  }, [contractId]);

  const amount = contract?.type === 'SALE' ? contract?.price : contract?.guaranteeAmount;
  const amountLabel = contract?.type === 'RENT' ? 'Security deposit' : 'Amount due';

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
      const raw = (data.message || '').trim();
      const reason = (data.error || '').trim();
      const detail = raw || (reason && reason !== 'Bad Request' && reason !== 'Internal Server Error' ? reason : '');

      if (status === 422) {
        setError(detail || 'The seller has no payout account configured yet. Please try again later.');
      } else if (status === 400) {
        setError(detail
          ? `Payment was not completed: ${detail}`
          : 'Payment was not completed. Please check your card details and try again.');
      } else if (status === 403) {
        setError('Only the buyer of this contract can complete the payment.');
      } else if (status === 409) {
        setError('This contract is no longer accepting payments.');
      } else if (status === 401) {
        setError('Your session has expired. Please sign in again.');
      } else if (status >= 500) {
        setError('An unexpected error occurred. Payment was not completed. Please try again in a few minutes.');
      } else {
        setError('An unexpected error occurred. Payment was not completed. Please try again later.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">Loading checkout…</div>;
  if (!contract) return <div className="max-w-xl mx-auto mt-10 p-6 bg-red-50 text-red-700 rounded-lg">{error || 'Contract not found.'}</div>;

  return (
    <div className="max-w-3xl mx-auto mt-8 p-4">
      <h1 className="text-3xl font-extrabold text-gray-900 mb-2">Checkout</h1>
      <p className="text-gray-500 mb-6">
        This is a simulated payment for demo purposes. No real money is moved and no card data is stored.
      </p>

      {error && (
        <div className="mb-6 p-4 bg-red-50 text-red-700 rounded-lg border border-red-200">{error}</div>
      )}

      <div className="bg-blue-50 border border-blue-100 rounded-xl p-4 mb-6 text-sm text-blue-800">
        <p><span className="font-bold">Contract:</span> {contract.id}</p>
        <p><span className="font-bold">Type:</span> {contractTypeLabel(contract.type)}</p>
        <p><span className="font-bold">{amountLabel}:</span> {amount?.toFixed ? amount.toFixed(2) : amount} €</p>
        <p className="mt-2 text-xs text-blue-700">
          Funds are held in escrow and only released to the seller once they sign. If the seller does
          not sign within 7 days, the money is refunded automatically.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="bg-white p-6 rounded-2xl shadow-lg border border-gray-100 space-y-5">
        <div>
          <label className="block text-sm font-bold text-gray-700 mb-1">Cardholder name</label>
          <input
            type="text"
            value={card.holderName}
            onChange={(e) => setCard({ ...card, holderName: e.target.value })}
            className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-blue-500"
            placeholder="Name as it appears on the card"
            required
          />
        </div>

        <div>
          <label className="block text-sm font-bold text-gray-700 mb-1">Card number</label>
          <input
            type="text"
            inputMode="numeric"
            value={card.number}
            onChange={(e) => setCard({ ...card, number: formatCardNumber(e.target.value) })}
            className={`w-full border rounded-lg p-2.5 font-mono tracking-wider focus:ring-2 focus:ring-blue-500 ${
              card.number && !isLuhnValid ? 'border-red-300' : 'border-gray-300'
            }`}
            placeholder="4242 4242 4242 4242"
            required
          />
          {card.number && !isLuhnValid && (
            <p className="text-xs text-red-600 mt-1">Card number does not look valid.</p>
          )}
          <p className="text-xs text-gray-500 mt-1">
            Demo tip: any Luhn-valid number works. Cards ending in <code>0000</code> always fail.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-1">Expiry (MM/YY)</label>
            <input
              type="text"
              inputMode="numeric"
              value={card.expiry}
              onChange={(e) => setCard({ ...card, expiry: formatExpiry(e.target.value) })}
              maxLength={5}
              className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-blue-500"
              placeholder="MM/YY"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-bold text-gray-700 mb-1">CVC</label>
            <input
              type="text"
              inputMode="numeric"
              value={card.cvc}
              onChange={(e) => setCard({ ...card, cvc: e.target.value.replace(/\D/g, '').slice(0, 4) })}
              className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-blue-500"
              placeholder="123"
              required
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={!canSubmit}
          className={`w-full py-3 px-4 text-white font-bold rounded-lg shadow-md transition ${
            canSubmit ? 'bg-blue-600 hover:bg-blue-700' : 'bg-blue-300 cursor-not-allowed'
          }`}
        >
          {submitting ? 'Processing…' : `Pay ${amount?.toFixed ? amount.toFixed(2) : amount} €`}
        </button>
      </form>
    </div>
  );
}

export default Checkout;
