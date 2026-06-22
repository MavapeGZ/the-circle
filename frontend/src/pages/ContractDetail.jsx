import { useState, useEffect, useContext } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import api, { extractApiError } from '../services/api';
import { AuthContext } from '../context/AuthContext';
import { contractTypeLabel } from '../utils/contractType';
import ReviewForm from '../components/ReviewForm';

function ContractDetail() {
  const { contractId } = useParams();
  const navigate = useNavigate();
  const { user: currentUser } = useContext(AuthContext);

  const [contract, setContract] = useState(null);
  const [ownerName, setOwnerName] = useState('');
  const [receiverName, setReceiverName] = useState('');
  const [itemTitle, setItemTitle] = useState('');
  const [pdfUrl, setPdfUrl] = useState(null);
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [showReviewForm, setShowReviewForm] = useState(false);
  const [reviewDone, setReviewDone] = useState(false);

  useEffect(() => {
    let objectUrl;

    // Best-effort name lookup so the UI shows people, not raw ids.
    const fullName = (u) => [u?.firstName, u?.lastName].filter(Boolean).join(' ').trim();

    const load = async () => {
      let data = null;
      try {
        const res = await api.get(`/contracts/${contractId}`);
        data = res.data;
        setContract(data);
      } catch {
        setError('Could not load this contract.');
        setLoading(false);
        return;
      }

      // Resolve owner/receiver names and the item title in parallel; failures
      // fall back to the raw id so the page still renders.
      await Promise.all([
        data.ownerId && api.get(`/users/${data.ownerId}`)
          .then((r) => setOwnerName(fullName(r.data) || `User ${data.ownerId}`))
          .catch(() => setOwnerName(`User ${data.ownerId}`)),
        data.receiverId && api.get(`/users/${data.receiverId}`)
          .then((r) => setReceiverName(fullName(r.data) || `User ${data.receiverId}`))
          .catch(() => setReceiverName(`User ${data.receiverId}`)),
        data.itemId && api.get(`/catalog/articles/${data.itemId}`)
          .then((r) => setItemTitle(r.data?.title || data.itemId))
          .catch(() => setItemTitle(data.itemId)),
        api.get(`/contracts/${contractId}/payments`)
          .then((r) => setPayments(r.data || []))
          .catch(() => setPayments([])),
      ]);

      // Prefer the signed artifact; fall back to a freshly rendered preview.
      try {
        const pdfRes = data.storedContractId
          ? await api.get(`/contracts/joint-rental/download/${data.storedContractId}`, { responseType: 'blob' })
          : await api.get(`/contracts/${contractId}/pdf`, { responseType: 'blob' });
        objectUrl = URL.createObjectURL(pdfRes.data);
        setPdfUrl(objectUrl);
      } catch {
        /* preview is optional */
      } finally {
        setLoading(false);
      }
    };

    load();
    return () => { if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [contractId]);

  const downloadSigned = async () => {
    if (!contract?.storedContractId) return;
    try {
      const res = await api.get(`/contracts/joint-rental/download/${contract.storedContractId}`, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = `contract-${contract.storedContractId}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setError('Could not download the signed contract.');
    }
  };

  const statusLabel = (c) => {
    if (c.status === 'ACTIVE') return { text: 'Active (signed by both)', cls: 'bg-green-100 text-green-800' };
    if (c.status === 'DELIVERED') return { text: 'Delivered', cls: 'bg-emerald-100 text-emerald-800' };
    if (c.status === 'AWAITING_COUNTERPARTY') return { text: 'Awaiting counterparty', cls: 'bg-yellow-100 text-yellow-800' };
    if (c.status === 'COMPLETED') return { text: `Completed (deposit ${c.guaranteeStatus?.toLowerCase()})`, cls: 'bg-gray-200 text-gray-700' };
    if (c.status === 'CANCELLED') return { text: 'Cancelled', cls: 'bg-red-100 text-red-700' };
    return { text: 'Pending signatures', cls: 'bg-blue-100 text-blue-800' };
  };

  if (loading) return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">Loading contract...</div>;
  if (error) return <div className="text-center mt-20 text-xl text-red-600 font-bold">{error}</div>;
  if (!contract) return null;

  const label = statusLabel(contract);

  // Can the logged-in user still sign this contract? (their party hasn't signed
  // and the contract is not already fully signed / completed)
  const isOwner = currentUser && String(currentUser.id) === String(contract.ownerId);
  const isReceiver = currentUser && String(currentUser.id) === String(contract.receiverId);
  const myRole = isOwner ? 'OWNER' : isReceiver ? 'RECEIVER' : null;
  const iNeedToSign = myRole
    && contract.status !== 'ACTIVE' && contract.status !== 'COMPLETED' && contract.status !== 'CANCELLED'
    && (isOwner ? !contract.ownerSignedAt : !contract.receiverSignedAt);

  const goSign = () => {
    navigate(`/contracts/${contract.id}/sign`, {
      state: { contract, signerEmail: currentUser?.email, role: myRole, from: `/contracts/${contract.id}` },
    });
  };

  // Delivery hand-over: available once both parties have signed (ACTIVE/DELIVERED).
  // Owner confirms delivery, receiver confirms reception; both → DELIVERED → reviews.
  const canConfirmDelivery = myRole
    && (contract.status === 'ACTIVE' || contract.status === 'DELIVERED');
  const myConfirmedAt = isOwner ? contract.ownerDeliveredAt : contract.receiverReceivedAt;
  const bothConfirmed = contract.ownerDeliveredAt && contract.receiverReceivedAt;
  const otherPartyId = isOwner ? contract.receiverId : contract.ownerId;

  const confirmDelivery = async () => {
    setConfirming(true);
    setError('');
    try {
      const res = await api.post(`/contracts/${contract.id}/delivery/confirm`);
      setContract(res.data);
    } catch (err) {
      setError(extractApiError(err, 'Could not confirm delivery.'));
    } finally {
      setConfirming(false);
    }
  };

  const escrowed = payments.find((p) => p.status === 'ESCROWED') || null;
  const released = payments.find((p) => p.status === 'RELEASED') || null;
  const refunded = payments.find((p) => p.status === 'REFUNDED') || null;
  const lastFailed = payments.find((p) => p.status === 'FAILED') || null;
  const isPayable = (contract.type === 'SALE' && Number(contract.price) > 0)
    || (contract.type === 'RENT' && Number(contract.guaranteeAmount) > 0);
  const buyerStillNeedsToPay = isReceiver && isPayable && !escrowed && !released && contract.receiverSignedAt
    && contract.status !== 'CANCELLED';

  return (
    <div className="max-w-5xl mx-auto mt-8 p-4">
      <Link to="/contracts" className="inline-flex items-center text-blue-600 hover:text-blue-800 mb-6 font-semibold">
        &larr; Back to My Contracts
      </Link>

      <div className="flex flex-col lg:flex-row gap-8">
        {/* DETAILS */}
        <div className="lg:w-1/2 bg-white p-6 rounded-2xl shadow-lg border border-gray-100 h-fit">
          <div className="flex justify-between items-start mb-4">
            <h1 className="text-2xl font-extrabold text-gray-900">{contractTypeLabel(contract.type)}</h1>
            <span className={`text-xs font-bold px-3 py-1 rounded-full whitespace-nowrap ${label.cls}`}>
              {label.text}
            </span>
          </div>

          <dl className="text-sm text-gray-600 space-y-2">
            <Row term="Item" value={itemTitle || contract.itemId} />
            <Row term="Owner" value={ownerName || contract.ownerId} />
            <Row term="Receiver" value={receiverName || contract.receiverId} />
            <Row term="Created" value={contract.createdAt ? new Date(contract.createdAt).toLocaleString() : '—'} />
            <Row term="Owner signed" value={contract.ownerSignedAt ? new Date(contract.ownerSignedAt).toLocaleString() : 'Not yet'} />
            <Row term="Receiver signed" value={contract.receiverSignedAt ? new Date(contract.receiverSignedAt).toLocaleString() : 'Not yet'} />
            {contract.guaranteeStatus && contract.guaranteeStatus !== 'NONE' && (
              <Row term="Deposit" value={`${contract.guaranteeAmount} € (${contract.guaranteeStatus.toLowerCase()})`} />
            )}
            {contract.conditions && <Row term="Conditions" value={contract.conditions} />}
          </dl>

          {escrowed && (
            <div className="mt-6 p-4 bg-yellow-50 border border-yellow-200 rounded-lg text-sm text-yellow-900">
              <p className="font-bold">
                Payment held in escrow — {escrowed.amount} {escrowed.currency}
              </p>
              <p className="mt-1">
                {isOwner
                  ? 'Sign the contract before '
                  : 'The seller has until '}
                <span className="font-bold">{new Date(escrowed.escrowExpiresAt).toLocaleString()}</span>
                {isOwner ? ' to release the funds.' : ' to sign; otherwise the payment will be refunded automatically.'}
              </p>
              <Link
                to={`/contracts/${contract.id}/payments/${escrowed.id}/receipt`}
                className="inline-block mt-2 text-sm font-bold underline hover:no-underline"
              >
                View receipt
              </Link>
            </div>
          )}

          {released && (
            <div className="mt-6 p-4 bg-green-50 border border-green-100 rounded-lg text-sm text-green-800">
              Payment released to the seller on {new Date(released.releasedAt).toLocaleString()}.
            </div>
          )}

          {refunded && (
            <div className="mt-6 p-4 bg-red-50 border border-red-200 rounded-lg text-sm text-red-800">
              Payment refunded to the buyer. Contract cancelled.
            </div>
          )}

          {iNeedToSign && (
            <button
              onClick={goSign}
              className="mt-6 w-full bg-green-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-green-700 transition"
            >
              Sign contract
            </button>
          )}

          {buyerStillNeedsToPay && (
            <button
              onClick={() => navigate(`/contracts/${contract.id}/checkout`)}
              className="mt-3 w-full bg-yellow-500 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-yellow-600 transition"
            >
              {contract.type === 'RENT' ? 'Lock security deposit' : 'Complete payment'}
            </button>
          )}

          {lastFailed && !escrowed && !released && (
            <p className="mt-3 text-sm text-red-700">
              Last payment attempt failed: {lastFailed.failureReason || 'card declined.'}
            </p>
          )}

          {contract.storedContractId && (
            <button
              onClick={downloadSigned}
              className="mt-3 w-full bg-blue-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-blue-700 transition"
            >
              Download signed PDF
            </button>
          )}

          {canConfirmDelivery && (
            <div className="mt-6 p-4 bg-emerald-50 border border-emerald-100 rounded-lg text-sm text-emerald-900">
              <p className="font-bold mb-2">Hand-over</p>
              <ul className="space-y-1 mb-3">
                <li>Owner delivered: {contract.ownerDeliveredAt ? new Date(contract.ownerDeliveredAt).toLocaleString() : 'Not yet'}</li>
                <li>Receiver received: {contract.receiverReceivedAt ? new Date(contract.receiverReceivedAt).toLocaleString() : 'Not yet'}</li>
              </ul>
              {myConfirmedAt ? (
                <p className="text-emerald-700">
                  You confirmed {isOwner ? 'delivery' : 'reception'}.
                  {!bothConfirmed && ' Waiting for the other party.'}
                </p>
              ) : (
                <button
                  onClick={confirmDelivery}
                  disabled={confirming}
                  className={`w-full font-bold py-2.5 px-6 rounded-lg text-white ${confirming ? 'bg-emerald-400' : 'bg-emerald-600 hover:bg-emerald-700'}`}
                >
                  {confirming ? 'Saving…' : isOwner ? 'Mark as delivered' : 'Mark as received'}
                </button>
              )}
            </div>
          )}

          {bothConfirmed && myRole && !reviewDone && (
            showReviewForm ? (
              <ReviewForm
                targetUserId={otherPartyId}
                contractId={contract.id}
                onSubmitted={() => { setReviewDone(true); setShowReviewForm(false); }}
                onCancel={() => setShowReviewForm(false)}
              />
            ) : (
              <button
                onClick={() => setShowReviewForm(true)}
                className="mt-3 w-full bg-yellow-500 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-yellow-600 transition"
              >
                Leave a review
              </button>
            )
          )}

          {reviewDone && (
            <p className="mt-3 text-sm font-semibold text-emerald-700">Thanks! Your review was submitted.</p>
          )}
        </div>

        {/* PDF PREVIEW */}
        <div className="lg:w-1/2">
          <h2 className="text-xl font-bold text-gray-800 mb-3">Document</h2>
          {pdfUrl ? (
            <iframe
              title="Contract document"
              src={pdfUrl}
              className="w-full h-[600px] border border-gray-200 rounded-xl bg-gray-50"
            />
          ) : (
            <div className="w-full h-[600px] border border-gray-200 rounded-xl bg-gray-50 flex items-center justify-center text-gray-400">
              Preview not available
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function Row({ term, value }) {
  return (
    <div className="flex justify-between gap-4 border-b border-gray-50 pb-1">
      <dt className="font-bold text-gray-800 whitespace-nowrap">{term}</dt>
      <dd className="text-right break-all">{value}</dd>
    </div>
  );
}

export default ContractDetail;
