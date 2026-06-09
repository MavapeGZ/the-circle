import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import api from '../services/api';

function PaymentReceipt() {
  const { contractId, paymentId } = useParams();
  const location = useLocation();

  const [payment, setPayment] = useState(location.state?.payment || null);
  const [loading, setLoading] = useState(!payment);
  const [error, setError] = useState('');

  useEffect(() => {
    if (payment) return;
    (async () => {
      try {
        const res = await api.get(`/contracts/${contractId}/payments`);
        const found = res.data.find((p) => p.id === paymentId) || null;
        if (!found) {
          setError('Receipt not found.');
        } else {
          setPayment(found);
        }
      } catch {
        setError('Could not load the receipt.');
      } finally {
        setLoading(false);
      }
    })();
  }, [contractId, paymentId, payment]);

  if (loading) return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">Loading receipt…</div>;
  if (error || !payment) return <div className="max-w-xl mx-auto mt-10 p-6 bg-red-50 text-red-700 rounded-lg">{error || 'Receipt not available.'}</div>;

  const inEscrow = payment.status === 'ESCROWED';
  const released = payment.status === 'RELEASED';
  const refunded = payment.status === 'REFUNDED';
  const failed = payment.status === 'FAILED';

  return (
    <div className="max-w-2xl mx-auto mt-10 p-6 bg-white rounded-2xl shadow-lg border border-gray-100">
      <h1 className="text-3xl font-extrabold text-gray-900 mb-2">
        {failed ? 'Payment failed' : 'Payment receipt'}
      </h1>
      <p className="text-gray-500 mb-6">Simulated payment — no real funds were moved.</p>

      {inEscrow && (
        <div className="p-4 mb-6 bg-blue-50 border border-blue-100 rounded-lg text-sm text-blue-800">
          Funds are held in escrow. They will be released to the seller as soon as they sign the
          contract. If they do not sign by <span className="font-bold">{new Date(payment.escrowExpiresAt).toLocaleString()}</span>,
          the payment will be refunded automatically.
        </div>
      )}
      {released && (
        <div className="p-4 mb-6 bg-green-50 border border-green-100 rounded-lg text-sm text-green-800">
          Funds released to the seller on {new Date(payment.releasedAt).toLocaleString()}.
        </div>
      )}
      {refunded && (
        <div className="p-4 mb-6 bg-yellow-50 border border-yellow-200 rounded-lg text-sm text-yellow-800">
          The seller did not sign in time. Your payment has been refunded.
        </div>
      )}
      {failed && (
        <div className="p-4 mb-6 bg-red-50 border border-red-200 rounded-lg text-sm text-red-800">
          {payment.failureReason || 'The card was declined.'}
        </div>
      )}

      <dl className="space-y-3 text-sm">
        <div className="flex justify-between border-b pb-2">
          <dt className="text-gray-500">Receipt ID</dt>
          <dd className="font-mono text-gray-800">{payment.id}</dd>
        </div>
        <div className="flex justify-between border-b pb-2">
          <dt className="text-gray-500">Contract</dt>
          <dd className="font-mono text-gray-800">{payment.contractId}</dd>
        </div>
        <div className="flex justify-between border-b pb-2">
          <dt className="text-gray-500">Amount</dt>
          <dd className="text-gray-800 font-bold">{payment.amount} {payment.currency}</dd>
        </div>
        <div className="flex justify-between border-b pb-2">
          <dt className="text-gray-500">Card</dt>
          <dd className="text-gray-800">{payment.cardBrand} •••• {payment.cardLast4}</dd>
        </div>
        {payment.payoutIbanLast4 && (
          <div className="flex justify-between border-b pb-2">
            <dt className="text-gray-500">Seller payout IBAN</dt>
            <dd className="text-gray-800 font-mono">•••• {payment.payoutIbanLast4}</dd>
          </div>
        )}
        <div className="flex justify-between border-b pb-2">
          <dt className="text-gray-500">Simulated at</dt>
          <dd className="text-gray-800">{new Date(payment.simulatedAt).toLocaleString()}</dd>
        </div>
        <div className="flex justify-between border-b pb-2">
          <dt className="text-gray-500">Status</dt>
          <dd className="text-gray-800 font-bold">{payment.status}</dd>
        </div>
      </dl>

      <div className="mt-6 flex gap-3">
        <Link
          to={`/contracts/${payment.contractId}`}
          className="bg-blue-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-blue-700 transition"
        >
          Back to contract
        </Link>
        {failed && (
          <Link
            to={`/contracts/${payment.contractId}/checkout`}
            className="bg-gray-100 text-gray-800 font-bold py-2.5 px-6 rounded-lg hover:bg-gray-200 transition"
          >
            Retry payment
          </Link>
        )}
      </div>
    </div>
  );
}

export default PaymentReceipt;
