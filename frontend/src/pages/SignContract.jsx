import { useState, useEffect, useContext } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api, { extractApiError } from '../services/api';
import { AuthContext } from '../context/AuthContext';
import { usePreferences } from '../context/PreferencesContext';
import BadgeToast from '../components/BadgeToast';
import { contractTypeLabel } from '../utils/contractType';
import { contractStatusLabel } from '../utils/contractStatus';

const STEP = { REQUEST: 'request', CONFIRM: 'confirm', SUCCESS: 'success' };

function SignContract() {
  const { t } = useTranslation();
  const { formatDate } = usePreferences();
  const { contractId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { user: currentUser, refreshContracts } = useContext(AuthContext);

  // When reached from the catalog "Buy/Rent/Request" flow, the contract and the
  // buyer email arrive in router state, so we skip the lookups below.
  const passedContract = location.state?.contract ?? null;
  const passedEmail = location.state?.signerEmail ?? '';
  const passedRole = location.state?.role ?? null;
  // Where the back button returns to (the screen the user came from).
  const backTo = location.state?.from ?? `/contracts/${contractId}`;

  const [step, setStep] = useState(STEP.REQUEST);
  const [contract, setContract] = useState(passedContract);
  const [loadingContract, setLoadingContract] = useState(!passedContract);
  const [pdfUrl, setPdfUrl] = useState(null);

  const role = passedRole ?? (
    contract && currentUser?.id
      ? (String(currentUser.id) === String(contract.ownerId)
        ? 'OWNER'
        : String(currentUser.id) === String(contract.receiverId)
          ? 'RECEIVER'
          : null)
      : null
  );

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
          setBanner({ type: 'error', text: t('sign.loadError') });
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
      setBanner({ type: 'error', text: t('sign.enterEmail') });
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
        text: t('sign.codeSent', { email: signerEmail }),
      });
    } catch (err) {
      const status = err.response?.status;
      if (status === 502) {
        setBanner({ type: 'error', text: t('sign.sendFail') });
      } else if (status === 400) {
        setBanner({ type: 'error', text: extractApiError(err, t('sign.invalidData')) });
      } else {
        setBanner({ type: 'error', text: t('sign.requestError') });
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
      // My signature is done → navbar pending-action count should drop now.
      refreshContracts();

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
        setBanner({ type: 'error', text: t('sign.incorrectCode') });
      } else if (status === 410 || status === 404) {
        // 404 = session evicted/expired on the server; treat like an expired OTP.
        setBanner({ type: 'error', text: t('sign.expired') });
        setSessionId(null);
        setStep(STEP.REQUEST);
      } else if (status === 429) {
        setBanner({ type: 'error', text: t('sign.tooMany') });
        setSessionId(null);
        setStep(STEP.REQUEST);
      } else if (status === 409) {
        setBanner({ type: 'error', text: t('sign.alreadyUsed') });
        setSessionId(null);
        setStep(STEP.REQUEST);
      } else {
        setBanner({ type: 'error', text: extractApiError(err, t('sign.confirmError')) });
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
      setBanner({ type: 'error', text: t('sign.downloadError') });
    }
  };

  const bannerClasses = banner?.type === 'error'
    ? 'bg-red-100 text-red-700'
    : 'bg-indigo-100 text-indigo-700';

  return (
    <div className="max-w-5xl mx-auto mt-8 p-4">
      {result?.earnedBadges?.length > 0 && <BadgeToast badges={result.earnedBadges} to="/profile" />}
      <button
        type="button"
        onClick={() => navigate(backTo)}
        className="inline-flex items-center text-indigo-600 hover:text-indigo-800 mb-6 font-semibold"
      >
        &larr; {t('sign.back')}
      </button>
      <h1 className="text-3xl font-extrabold text-gray-900 mb-2">{t('sign.title')}</h1>
      <p className="text-gray-500 mb-8">{t('sign.subtitle')}</p>

      {banner && (
        <div className={`p-4 mb-6 rounded-lg text-sm font-bold ${bannerClasses}`}>
          {banner.text}
        </div>
      )}

      <div className="flex flex-col lg:flex-row gap-8">
        {/* PDF PREVIEW */}
        <div className="lg:w-1/2">
          <h2 className="text-xl font-bold text-gray-800 mb-3">{t('sign.preview')}</h2>
          {pdfUrl ? (
            <iframe
              title={t('sign.preview')}
              src={pdfUrl}
              className="w-full h-[480px] border border-gray-200 rounded-xl bg-gray-50"
            />
          ) : (
            <div className="w-full h-[480px] border border-gray-200 rounded-xl bg-gray-50 flex items-center justify-center text-gray-400">
              {loadingContract ? t('sign.loading') : t('sign.previewNa')}
            </div>
          )}
        </div>

        {/* FLOW */}
        <div className="lg:w-1/2 bg-white p-6 rounded-2xl shadow-lg border border-gray-100">
          {contract && (
            <div className="mb-6 text-sm text-gray-600 space-y-1 border-b pb-4">
              <p><span className="font-bold text-gray-800">{t('sign.contract')}</span> {contract.id || contractId}</p>
              {contract.type && <p><span className="font-bold text-gray-800">{t('sign.type')}</span> {contractTypeLabel(contract.type, t)}</p>}
              {contract.status && <p><span className="font-bold text-gray-800">{t('sign.status')}</span> {contractStatusLabel(contract.status, t)}</p>}
              {contract.conditions && <p><span className="font-bold text-gray-800">{t('sign.conditions')}</span> {contract.conditions}</p>}
            </div>
          )}

          {/* STEP 1 — REQUEST */}
          {step === STEP.REQUEST && (
            <form onSubmit={requestOtp} className="space-y-5">
              <h2 className="text-2xl font-bold text-gray-800">{t('sign.step1')}</h2>
              <div>
                <label className="block text-sm font-bold text-gray-700 mb-1">{t('sign.signerEmail')}</label>
                <input
                  type="email"
                  value={signerEmail}
                  onChange={(e) => setSignerEmail(e.target.value)}
                  data-testid="sign-signer-email"
                  className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500"
                  required
                />
              </div>
              <button
                type="submit"
                disabled={submitting || loadingContract || !contract}
                data-testid="sign-request-submit"
                className="bg-indigo-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-indigo-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {submitting ? t('sign.sending') : t('sign.sendCode')}
              </button>
            </form>
          )}

          {/* STEP 2 — CONFIRM */}
          {step === STEP.CONFIRM && (
            <form onSubmit={confirmOtp} className="space-y-5">
              <h2 className="text-2xl font-bold text-gray-800">{t('sign.step2')}</h2>
              <div>
                <label className="block text-sm font-bold text-gray-700 mb-1">{t('sign.codeLabel')}</label>
                <input
                  type="text"
                  inputMode="numeric"
                  maxLength={6}
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                  data-testid="sign-otp"
                  className="w-full border border-gray-300 rounded-lg p-2.5 text-center text-2xl tracking-[0.5em] font-mono focus:ring-2 focus:ring-indigo-500"
                  autoFocus
                  required
                />
              </div>
              <button
                type="submit"
                disabled={submitting || otp.length !== 6}
                data-testid="sign-confirm-submit"
                className="bg-indigo-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-indigo-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {submitting ? t('sign.verifying') : t('sign.signBtn')}
              </button>
              <button
                type="button"
                onClick={requestOtp}
                disabled={submitting}
                className="block text-sm text-indigo-600 font-bold hover:underline disabled:opacity-50"
              >
                {t('sign.resend')}
              </button>
            </form>
          )}

          {/* STEP 3 — SUCCESS */}
          {step === STEP.SUCCESS && result && (
            <div className="space-y-5">
              {result.fullySigned ? (
                <h2 className="text-2xl font-bold text-green-600">{t('sign.successBoth')}</h2>
              ) : (
                <h2 className="text-2xl font-bold text-green-600">{t('sign.successOne')}</h2>
              )}

              {!result.fullySigned && (
                <div className="bg-indigo-50 p-4 rounded-xl border border-indigo-100 text-sm text-indigo-700 font-bold">
                  {t('sign.recordedNote')}
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
                      ? t('sign.nextDeposit')
                      : t('sign.nextPayment')}
                  </p>
                  <button
                    type="button"
                    onClick={() => navigate(`/contracts/${contractId}/checkout`)}
                    className="bg-indigo-600 text-white font-bold py-2 px-5 rounded-lg hover:bg-indigo-700 transition"
                  >
                    {t('sign.goCheckout')}
                  </button>
                </div>
              )}

              <div className="bg-green-50 p-4 rounded-xl border border-green-100 text-sm text-gray-700 space-y-1">
                <p><span className="font-bold">{t('sign.document')}</span> {result.storedContractId}</p>
                {result.signedAt && (
                  <p><span className="font-bold">{t('sign.signed')}</span> {formatDate(result.signedAt, { dateStyle: 'medium', timeStyle: 'short' })}</p>
                )}
              </div>
              <button
                onClick={downloadSigned}
                data-testid="sign-download-signed"
                className="bg-indigo-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-indigo-700 transition"
              >
                {t('sign.downloadSigned')}
              </button>
              <button
                onClick={() => navigate('/contracts')}
                className="block text-sm text-gray-500 font-bold hover:underline"
              >
                {t('sign.viewContracts')}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default SignContract;
