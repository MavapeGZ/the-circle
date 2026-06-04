import { useState, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

const STEP_CREDENTIALS = 'credentials';
const STEP_OTP = 'otp';

const FLOW_LOGIN_OTP = 'login-otp';
const FLOW_EMAIL_VERIFICATION = 'email-verification';

function Login() {
  const { login, verifyLoginOtp, verifyEmail } = useContext(AuthContext);
  const navigate = useNavigate();

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
        navigate('/');
      } else if (data.requiresOtp && data.sessionId) {
        setFlow(FLOW_LOGIN_OTP);
        setSessionId(data.sessionId);
        setOtpMessage(data.message || 'Enter the sign-in code we sent to your email.');
        setStep(STEP_OTP);
      } else if (data.requiresEmailVerification && data.sessionId) {
        // Account never verified; resume signup verification flow.
        setFlow(FLOW_EMAIL_VERIFICATION);
        setSessionId(data.sessionId);
        setOtpMessage(data.message || 'Your email is not verified yet. Enter the code we just sent.');
        setStep(STEP_OTP);
      } else {
        setError('Unexpected server response. Please try again.');
      }
    } catch (err) {
      const status = err?.response?.status;
      if (status === 404) {
        setAccountNotFound(true);
        setError('No account is registered with this email.');
      } else if (status === 401) {
        setError('Incorrect password.');
      } else {
        setError('Could not sign in. Please try again.');
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
      navigate('/');
    } catch (err) {
      setError('Invalid or expired code. Try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex justify-center items-center mt-20">
      <div className="bg-white p-8 rounded-lg shadow-lg w-full max-w-md border">
        <h2 className="text-3xl font-bold text-center text-blue-600 mb-6">
          {step === STEP_CREDENTIALS ? 'Sign In' : 'Verify Sign-in'}
        </h2>

        {error && <p className="bg-red-100 text-red-600 p-3 rounded mb-4 text-center">{error}</p>}

        {accountNotFound && (
          <div className="bg-blue-50 text-blue-800 p-3 rounded mb-4 text-center border border-blue-200">
            <p className="mb-2">Don't have an account yet?</p>
            <Link to="/register"
                  className="inline-block bg-blue-600 text-white font-bold px-4 py-2 rounded hover:bg-blue-700 transition">
              Create an account
            </Link>
          </div>
        )}

        {step === STEP_CREDENTIALS && (
          <form onSubmit={submitCredentials} className="flex flex-col gap-4">
            <div>
              <label className="block text-gray-700 font-semibold mb-2">Email</label>
              <input
                type="email" required
                className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-blue-500"
                value={email} onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div>
              <label className="block text-gray-700 font-semibold mb-2">Password</label>
              <input
                type="password" required
                className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-blue-500"
                value={password} onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            <button type="submit" disabled={submitting}
                    className="bg-blue-600 text-white font-bold p-3 rounded hover:bg-blue-700 transition disabled:opacity-60">
              {submitting ? 'Signing in…' : 'Sign In'}
            </button>
            <p className="mt-2 text-center text-gray-600">
              Don't have an account? <Link to="/register" className="text-blue-500 hover:underline">Sign Up</Link>
            </p>
          </form>
        )}

        {step === STEP_OTP && (
          <form onSubmit={submitOtp} className="flex flex-col gap-4">
            <p className="text-gray-600 text-center">{otpMessage}</p>
            <input
              type="text" inputMode="numeric" autoComplete="one-time-code" maxLength={6} required
              className="w-full p-3 border rounded text-center text-2xl tracking-widest focus:outline-none focus:ring-2 focus:ring-blue-500"
              value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
            />
            <button type="submit" disabled={submitting}
                    className="bg-blue-600 text-white font-bold p-3 rounded hover:bg-blue-700 transition disabled:opacity-60">
              {submitting ? 'Verifying…' : 'Verify'}
            </button>
            <button type="button"
                    onClick={() => { setStep(STEP_CREDENTIALS); setOtp(''); setSessionId(''); }}
                    className="text-sm text-gray-500 hover:underline">
              Back to sign-in
            </button>
          </form>
        )}
      </div>
    </div>
  );
}

export default Login;
