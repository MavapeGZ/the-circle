import { useState, useContext } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AuthContext } from '../context/AuthContext';
import { extractApiError } from '../services/api';

const STEP_CREDENTIALS = 'credentials';
const STEP_OTP = 'otp';

const FLOW_LOGIN_OTP = 'login-otp';
const FLOW_EMAIL_VERIFICATION = 'email-verification';

function Login() {
  const { t } = useTranslation();
  const { login, verifyLoginOtp, verifyEmail } = useContext(AuthContext);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const sessionExpired = searchParams.get('expired') === '1';
  const authRequired = searchParams.get('authRequired');
  // Where to land after a successful sign-in (set by ProtectedRoute). Restricted
  // to internal paths so the param can't be used as an open redirect.
  const nextParam = searchParams.get('next');
  const redirectTo = nextParam && nextParam.startsWith('/') ? nextParam : '/';

  // Message shown when the user was redirected here from a guarded action.
  const authRequiredMessage = authRequired
    ? t(`auth.required.${authRequired}`, { defaultValue: t('auth.required.default') })
    : '';

  const [step, setStep] = useState(STEP_CREDENTIALS);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [accountNotFound, setAccountNotFound] = useState(false);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [sessionId, setSessionId] = useState('');
  const [otp, setOtp] = useState('');
  const [otpMessage, setOtpMessage] = useState('');
  const [flow, setFlow] = useState(FLOW_LOGIN_OTP);

  const submitCredentials = async (e) => {
    e.preventDefault();
    setError('');
    setAccountNotFound(false);
    setSubmitting(true);
    try {
      const data = await login(email, password);
      if (data.token) {
        navigate(redirectTo);
      } else if (data.requiresOtp && data.sessionId) {
        setFlow(FLOW_LOGIN_OTP);
        setSessionId(data.sessionId);
        setOtpMessage(data.message || t('auth.otp.enterLogin'));
        setStep(STEP_OTP);
      } else if (data.requiresEmailVerification && data.sessionId) {
        // Account never verified; resume signup verification flow.
        setFlow(FLOW_EMAIL_VERIFICATION);
        setSessionId(data.sessionId);
        setOtpMessage(data.message || t('auth.otp.enterVerify'));
        setStep(STEP_OTP);
      } else {
        setError(t('auth.error.unexpected'));
      }
    } catch (err) {
      const status = err?.response?.status;

      // Keep a single combined message for both "no such account" and "wrong
      // password". Splitting them would let anyone probe which emails are
      // registered (user enumeration) — the backend deliberately returns one
      // 401 for both cases, and forgot-password hides existence the same way.
      if (status === 400) {
        // Format-level validation (blank/invalid email, missing password). Safe to
        // surface — it says nothing about whether the account exists.
        setError(extractApiError(err, t('auth.error.checkCreds')));
      } else if (status === 404 || status === 401) {
        setError(t('auth.error.badCreds'));
      } else {
        setError(t('auth.error.generic'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const submitOtp = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      if (flow === FLOW_EMAIL_VERIFICATION) {
        await verifyEmail(sessionId, otp);
      } else {
        await verifyLoginOtp(sessionId, otp);
      }
      navigate(redirectTo);
    } catch (err) {
      setError(t('auth.error.invalidCode'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex justify-center items-center mt-20">
      <div className="bg-white p-8 rounded-lg shadow-lg w-full max-w-md border">
        <h2 className="text-3xl font-bold text-center text-indigo-600 mb-6">
          {step === STEP_CREDENTIALS ? t('auth.login.title') : t('auth.verify.title')}
        </h2>

        {sessionExpired && !error && (
          <p className="bg-amber-100 text-amber-700 p-3 rounded mb-4 text-center">
            {t('auth.sessionExpired')}
          </p>
        )}

        {authRequiredMessage && !error && !sessionExpired && (
          <p className="bg-amber-100 text-amber-700 p-3 rounded mb-4 text-center">
            {authRequiredMessage}
          </p>
        )}

        {error && <p className="bg-red-100 text-red-600 p-3 rounded mb-4 text-center">{error}</p>}

        {accountNotFound && (
          <div className="bg-indigo-50 text-indigo-800 p-3 rounded mb-4 text-center border border-indigo-200">
            <p className="mb-2">{t('auth.noAccountYet')}</p>
            <Link to="/register"
              className="inline-block bg-indigo-600 text-white font-bold px-4 py-2 rounded hover:bg-indigo-700 transition">
              {t('auth.createAccount')}
            </Link>
          </div>
        )}

        {step === STEP_CREDENTIALS && (
          <form onSubmit={submitCredentials} className="flex flex-col gap-4">
            <div>
              <label className="block text-gray-700 font-semibold mb-2">{t('auth.email')}</label>
              <input
                type="email" required
                className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-indigo-500"
                value={email} onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div>
              <label className="block text-gray-700 font-semibold mb-2">{t('auth.password')}</label>
              <input
                type="password" required
                className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-indigo-500"
                value={password} onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            <button type="submit" disabled={submitting}
              className="bg-indigo-600 text-white font-bold p-3 rounded hover:bg-indigo-700 transition disabled:opacity-60">
              {submitting ? t('auth.signingIn') : t('auth.signIn')}
            </button>
            <p className="text-center text-sm text-gray-600">
              <Link to="/forgot-password" className="text-indigo-500 hover:underline">{t('auth.forgot')}</Link>
            </p>
            <p className="mt-2 text-center text-gray-600">
              {t('auth.noAccount')} <Link to="/register" className="text-indigo-500 hover:underline">{t('auth.signUp')}</Link>
            </p>
          </form>
        )}

        {step === STEP_OTP && (
          <form onSubmit={submitOtp} className="flex flex-col gap-4">
            <p className="text-gray-600 text-center">{otpMessage}</p>
            <input
              type="text" inputMode="numeric" autoComplete="one-time-code" maxLength={6} required
              className="w-full p-3 border rounded text-center text-2xl tracking-widest focus:outline-none focus:ring-2 focus:ring-indigo-500"
              value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
            />
            <button type="submit" disabled={submitting}
              className="bg-indigo-600 text-white font-bold p-3 rounded hover:bg-indigo-700 transition disabled:opacity-60">
              {submitting ? t('auth.verifying') : t('auth.verify')}
            </button>
            <button type="button"
              onClick={() => { setStep(STEP_CREDENTIALS); setOtp(''); setSessionId(''); }}
              className="text-sm text-gray-500 hover:underline">
              {t('auth.backToSignIn')}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}

export default Login;
