import { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import api, { extractApiError } from '../services/api';
import BadgeToast from '../components/BadgeToast';
import { contractTypeLabel } from '../utils/contractType';
import { contractStatusLabel } from '../utils/contractStatus';

const STEP = { REQUEST: 'request', CONFIRM: 'confirm', SUCCESS: 'success' };

function SignContract() {
  const { contractId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  // When reached from the catalog "Buy/Rent/Request" flow, the contract and the
  // buyer email arrive in router state, so we skip the lookups below.
  const passedContract = location.state?.contract ?? null;
  const passedEmail = location.state?.signerEmail ?? '';
  const role = location.state?.role ?? 'RECEIVER'; // RECEIVER (buyer) or OWNER (seller)
  // Where the back button returns to (the screen the user came from).
  const backTo = location.state?.from ?? `/contracts/${contractId}`;

  const [step, setStep] = useState(STEP.REQUEST);
  const [contract, setContract] = useState(passedContract);
  const [loadingContract, setLoadingContract] = useState(!passedContract);
  const [pdfUrl, setPdfUrl] = useState(null);

  const [signerEmail, setSignerEmail] = useState(passedEmail);
  const [sessionId, setSessionId] = useState(null);
  const [otp, setOtp] = useState('');
  const [result, setResult] = useState(null);

  const [submitting, setSubmitting] = useState(false);
  const [banner, setBanner] = useState(null); // { type: 'info' | 'error', text }

  // Load contract details, a PDF preview, and a best-effort signer email.
  useEffect(() => {
    let objectUrl;

    const load = async () => {
      // Contract may already be supplied by the catalog flow via router state.
      if (!passedContract) {
        try {
          const res = await api.get(`/contracts/${contractId}`);
          setContract(res.data);
        } catch {
          setBanner({ type: 'error', text: 'Could not load the contract.' });
        } finally {
          setLoadingContract(false);
        }
      }

      // Fetch through axios so the Authorization header is attached; an <iframe src>
      // would hit the gateway without the JWT.
      try {
        const pdf = await api.get(`/contracts/${contractId}/pdf`, { responseType: 'blob' });
        objectUrl = URL.createObjectURL(pdf.data);
        setPdfUrl(objectUrl);
      } catch {
        /* preview is optional */
      }

      // Only prefill the email if the catalog flow did not already provide it.
      if (!passedEmail) {
        try {
          const me = await api.get('/users/me');
          if (me.data?.email) setSignerEmail(me.data.email);
        } catch {
          /* prefill is optional */
        }
      }
    };

    load();
    return () => { if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [contractId, passedContract, passedEmail]);

  const requestOtp = async (e) => {
    if (e) e.preventDefault();
    if (!signerEmail) {
      setBanner({ type: 'error', text: 'Enter a signer email.' });
      return;
    }
    setSubmitting(true);
    setBanner(null);
    try {
      const res = await api.post('/contracts/joint-rental/sign/request', {
        signerEmail,
        signatureMode: 'ADVANCED',
        signerRole: role,
        contract,
      });
      setSessionId(res.data.sessionId);
      setOtp('');
      setStep(STEP.CONFIRM);
      setBanner({
        type: 'info',
        text: `We've sent a code to ${signerEmail}. It may take up to a minute.`,
      });
    } catch (err) {
      const status = err.response?.status;
      if (status === 502) {
        setBanner({ type: 'error', text: 'We could not send the code, please try again.' });
      } else if (status === 400) {
        setBanner({ type: 'error', text: extractApiError(err, 'Invalid signature data.') });
      } else {
        setBanner({ type: 'error', text: 'Error requesting the code. Please try again.' });
      }
    } finally {
      setSubmitting(false);
    }
  };

  const confirmOtp = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setBanner(null);
    try {
      const res = await api.post('/contracts/joint-rental/sign/confirm', { sessionId, otp });
      setResult(res.data);
      setStep(STEP.SUCCESS);

      // Buyer signing a rental locks the security deposit right after signing.
      if (role === 'RECEIVER' && contract?.type === 'RENT' && Number(contract?.guaranteeAmount) > 0) {
        try {
          await api.post(`/contracts/${contractId}/guarantee/deposit`);
        } catch {
          /* deposit is best-effort; the owner can still review the contract */
        }
      }
    } catch (err) {
      const status = err.response?.status;
      if (status === 401) {
        // Keep the code populated so the signer can correct it.
        setBanner({ type: 'error', text: 'Incorrect code. Check it and try again.' });
      } else if (status === 410 || status === 404) {
        // 404 = session evicted/expired on the server; treat like an expired OTP.
        setBanner({ type: 'error', text: 'The code has expired. Request a new one.' });
        setSessionId(null);
        setStep(STEP.REQUEST);
      } else if (status === 429) {
        setBanner({ type: 'error', text: 'Too many attempts. Request a new code.' });
        setSessionId(null);
        setStep(STEP.REQUEST);
      } else if (status === 409) {
        setBanner({ type: 'error', text: 'This signing session has already been used.' });
        setSessionId(null);
        setStep(STEP.REQUEST);
      } else {
        setBanner({ type: 'error', text: extractApiError(err, 'Could not confirm the signature. Please try again.') });
      }
    } finally {
      setSubmitting(false);
    }
  };

  const downloadSigned = async () => {
    if (!result?.storedContractId) return;
    try {
      const res = await api.get(
        `/contracts/joint-rental/download/${result.storedContractId}`,
        { responseType: 'blob' }
      );
      const url = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = `contract-${result.storedContractId}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setBanner({ type: 'error', text: 'Could not download the signed contract.' });
    }
  };

  const bannerClasses = banner?.type === 'error'
    ? 'bg-red-100 text-red-700'
    : 'bg-blue-100 text-blue-700';

  return (
    <div className="max-w-5xl mx-auto mt-8 p-4">
      {result?.earnedBadges?.length > 0 && <BadgeToast badges={result.earnedBadges} to="/profile" />}
      <button
        type="button"
        onClick={() => navigate(backTo)}
        className="inline-flex items-center text-blue-600 hover:text-blue-800 mb-6 font-semibold"
      >
        &larr; Back
      </button>
      <h1 className="text-3xl font-extrabold text-gray-900 mb-2">Sign contract</h1>
      <p className="text-gray-500 mb-8">eIDAS advanced electronic signature via OTP code.</p>

      {banner && (
        <div className={`p-4 mb-6 rounded-lg text-sm font-bold ${bannerClasses}`}>
          {banner.text}
        </div>
      )}

      <div className="flex flex-col lg:flex-row gap-8">
        {/* PDF PREVIEW */}
        <div className="lg:w-1/2">
          <h2 className="text-xl font-bold text-gray-800 mb-3">Preview</h2>
          {pdfUrl ? (
            <iframe
              title="Contract preview"
              src={pdfUrl}
              className="w-full h-[480px] border border-gray-200 rounded-xl bg-gray-50"
            />
          ) : (
            <div className="w-full h-[480px] border border-gray-200 rounded-xl bg-gray-50 flex items-center justify-center text-gray-400">
              {loadingContract ? 'Loading…' : 'Preview not available'}
            </div>
          )}
        </div>

        {/* FLOW */}
        <div className="lg:w-1/2 bg-white p-6 rounded-2xl shadow-lg border border-gray-100">
          {contract && (
            <div className="mb-6 text-sm text-gray-600 space-y-1 border-b pb-4">
              <p><span className="font-bold text-gray-800">Contract:</span> {contract.id || contractId}</p>
              {contract.type && <p><span className="font-bold text-gray-800">Type:</span> {contractTypeLabel(contract.type)}</p>}
              {contract.status && <p><span className="font-bold text-gray-800">Status:</span> {contractStatusLabel(contract.status)}</p>}
              {contract.conditions && <p><span className="font-bold text-gray-800">Conditions:</span> {contract.conditions}</p>}
            </div>
          )}

          {/* STEP 1 — REQUEST */}
          {step === STEP.REQUEST && (
            <form onSubmit={requestOtp} className="space-y-5">
              <h2 className="text-2xl font-bold text-gray-800">1. Request code</h2>
              <div>
                <label className="block text-sm font-bold text-gray-700 mb-1">Signer email</label>
                <input
                  type="email"
                  value={signerEmail}
                  onChange={(e) => setSignerEmail(e.target.value)}
                  className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-blue-500"
                  required
                />
              </div>
              <button
                type="submit"
                disabled={submitting || loadingContract || !contract}
                className="bg-blue-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-blue-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {submitting ? 'Sending…' : 'Send code'}
              </button>
            </form>
          )}

          {/* STEP 2 — CONFIRM */}
          {step === STEP.CONFIRM && (
            <form onSubmit={confirmOtp} className="space-y-5">
              <h2 className="text-2xl font-bold text-gray-800">2. Enter code</h2>
              <div>
                <label className="block text-sm font-bold text-gray-700 mb-1">6-digit code</label>
                <input
                  type="text"
                  inputMode="numeric"
                  maxLength={6}
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                  className="w-full border border-gray-300 rounded-lg p-2.5 text-center text-2xl tracking-[0.5em] font-mono focus:ring-2 focus:ring-blue-500"
                  autoFocus
                  required
                />
              </div>
              <button
                type="submit"
                disabled={submitting || otp.length !== 6}
                className="bg-blue-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-blue-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {submitting ? 'Verifying…' : 'Sign contract'}
              </button>
              <button
                type="button"
                onClick={requestOtp}
                disabled={submitting}
                className="block text-sm text-blue-600 font-bold hover:underline disabled:opacity-50"
              >
                Resend code
              </button>
            </form>
          )}

          {/* STEP 3 — SUCCESS */}
          {step === STEP.SUCCESS && result && (
            <div className="space-y-5">
              {result.fullySigned ? (
                <h2 className="text-2xl font-bold text-green-600">Contract signed by both parties!</h2>
              ) : (
                <h2 className="text-2xl font-bold text-green-600">Signature recorded</h2>
              )}

              {!result.fullySigned && (
                <div className="bg-blue-50 p-4 rounded-xl border border-blue-100 text-sm text-blue-700 font-bold">
                  Your signature has been recorded. The contract will become active once the other party signs.
                </div>
              )}

              {/* Buyer + paid contract → prompt for checkout next. */}
              {role === 'RECEIVER' && contract && (
                ((contract.type === 'SALE' && Number(contract.price) > 0) ||
                 (contract.type === 'RENT' && Number(contract.guaranteeAmount) > 0))
              ) && (
                <div className="bg-yellow-50 p-4 rounded-xl border border-yellow-200 text-sm text-yellow-900 space-y-3">
                  <p className="font-bold">
                    {contract.type === 'RENT'
                      ? 'Next step: lock the security deposit.'
                      : 'Next step: complete the payment.'}
                  </p>
                  <button
                    type="button"
                    onClick={() => navigate(`/contracts/${contractId}/checkout`)}
                    className="bg-blue-600 text-white font-bold py-2 px-5 rounded-lg hover:bg-blue-700 transition"
                  >
                    Go to checkout
                  </button>
                </div>
              )}

              <div className="bg-green-50 p-4 rounded-xl border border-green-100 text-sm text-gray-700 space-y-1">
                <p><span className="font-bold">Document:</span> {result.storedContractId}</p>
                {result.signedAt && (
                  <p><span className="font-bold">Signed:</span> {new Date(result.signedAt).toLocaleString()}</p>
                )}
              </div>
              <button
                onClick={downloadSigned}
                className="bg-blue-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-blue-700 transition"
              >
                Download signed PDF
              </button>
              <button
                onClick={() => navigate('/contracts')}
                className="block text-sm text-gray-500 font-bold hover:underline"
              >
                View my contracts
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default SignContract;
