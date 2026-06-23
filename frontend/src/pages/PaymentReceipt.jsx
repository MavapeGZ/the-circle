import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../services/api';
import { usePreferences } from '../context/PreferencesContext';

function PaymentReceipt() {
  const { t } = useTranslation();
  const { formatDate, formatPrice } = usePreferences();
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
          setError(t('receipt.notFound'));
        } else {
          setPayment(found);
        }
      } catch {
        setError(t('receipt.loadError'));
      } finally {
        setLoading(false);
      }
    })();
  }, [contractId, paymentId, payment]);

  if (loading) return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">{t('receipt.loading')}</div>;
  if (error || !payment) return <div className="max-w-xl mx-auto mt-10 p-6 bg-red-50 text-red-700 rounded-lg">{error || t('receipt.notAvailable')}</div>;

  const inEscrow = payment.status === 'ESCROWED';
  const released = payment.status === 'RELEASED';
  const refunded = payment.status === 'REFUNDED';
  const failed = payment.status === 'FAILED';

  return (
    <div className="max-w-2xl mx-auto mt-10 p-6 bg-white rounded-2xl shadow-lg border border-gray-100">
      <h1 className="text-3xl font-extrabold text-gray-900 mb-2">
        {failed ? t('receipt.failedTitle') : t('receipt.title')}
      </h1>
      <p className="text-gray-500 mb-6">{t('receipt.subtitle')}</p>

      {inEscrow && (
        <div className="p-4 mb-6 bg-indigo-50 border border-indigo-100 rounded-lg text-sm text-indigo-800">
          {t('receipt.escrowMsg', { date: formatDate(payment.escrowExpiresAt, { dateStyle: 'medium', timeStyle: 'short' }) })}
        </div>
      )}
      {released && (
        <div className="p-4 mb-6 bg-green-50 border border-green-100 rounded-lg text-sm text-green-800">
          {t('receipt.releasedMsg', { date: formatDate(payment.releasedAt, { dateStyle: 'medium', timeStyle: 'short' }) })}
        </div>
      )}
      {refunded && (
        <div className="p-4 mb-6 bg-yellow-50 border border-yellow-200 rounded-lg text-sm text-yellow-800">
          {t('receipt.refundedMsg')}
        </div>
      )}
      {failed && (
        <div className="p-4 mb-6 bg-red-50 border border-red-200 rounded-lg text-sm text-red-800">
          {payment.failureReason || t('receipt.cardDeclined')}
        </div>
      )}

      <dl className="space-y-3 text-sm">
        <div className="flex justify-between border-b pb-2">
          <dt className="text-gray-500">{t('receipt.id')}</dt>
          <dd className="font-mono text-gray-800">{payment.id}</dd>
        </div>
        <div className="flex justify-between border-b pb-2">
          <dt className="text-gray-500">{t('receipt.contract')}</dt>
          <dd className="font-mono text-gray-800">{payment.contractId}</dd>
        </div>
        <div className="flex justify-between border-b pb-2">
          <dt className="text-gray-500">{t('receipt.amount')}</dt>
          <dd className="text-gray-800 font-bold">{formatPrice(payment.amount)}</dd>
        </div>
        <div className="flex justify-between border-b pb-2">
          <dt className="text-gray-500">{t('receipt.card')}</dt>
          <dd className="text-gray-800">{payment.cardBrand} •••• {payment.cardLast4}</dd>
        </div>
        {payment.payoutIbanLast4 && (
          <div className="flex justify-between border-b pb-2">
            <dt className="text-gray-500">{t('receipt.sellerIban')}</dt>
            <dd className="text-gray-800 font-mono">•••• {payment.payoutIbanLast4}</dd>
          </div>
        )}
        <div className="flex justify-between border-b pb-2">
          <dt className="text-gray-500">{t('receipt.simulatedAt')}</dt>
          <dd className="text-gray-800">{formatDate(payment.simulatedAt, { dateStyle: 'medium', timeStyle: 'short' })}</dd>
        </div>
        <div className="flex justify-between border-b pb-2">
          <dt className="text-gray-500">{t('receipt.status')}</dt>
          <dd className="text-gray-800 font-bold">{payment.status}</dd>
        </div>
      </dl>

      <div className="mt-6 flex gap-3">
        <Link
          to={`/contracts/${payment.contractId}`}
          className="bg-indigo-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-indigo-700 transition"
        >
          {t('receipt.backToContract')}
        </Link>
        {failed && (
          <Link
            to={`/contracts/${payment.contractId}/checkout`}
            className="bg-gray-100 text-gray-800 font-bold py-2.5 px-6 rounded-lg hover:bg-gray-200 transition"
          >
            {t('receipt.retry')}
          </Link>
        )}
      </div>
    </div>
  );
}

export default PaymentReceipt;
