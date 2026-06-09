import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../services/api';

const STEP_EMAIL = 'email';
const STEP_OTP = 'otp';
const STEP_PASSWORD = 'password';
const STEP_DONE = 'done';

function ForgotPassword() {
  const navigate = useNavigate();

  const [step, setStep] = useState(STEP_EMAIL);
  const [email, setEmail] = useState('');
  const [sessionId, setSessionId] = useState('');
  const [otp, setOtp] = useState('');
  const [resetToken, setResetToken] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const requestCode = async (e) => {
    e.preventDefault();
    setError('');
    setNotice('');
    setSubmitting(true);
    try {
      const res = await api.post('/auth/forgot-password', { email });
      // Backend always returns 200 + sessionId (real or fake) to avoid leaking
      // which addresses are registered. Message stays neutral on purpose.
      setSessionId(res.data.sessionId);
      setNotice(res.data.message || 'If that email is registered, a reset code is on its way.');
      setStep(STEP_OTP);
    } catch (err) {
      const status = err?.response?.status;
      if (status === 429) {
        setError(err.response?.data?.message
          || 'Too many password reset requests. Please wait a few minutes and try again.');
      } else {
        setError('Something went wrong. Please try again later.');
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
      const res = await api.post('/auth/verify-reset-otp', { sessionId, otp });
      // Backend rides the opaque reset token in `sessionId`. Step 3 sends it
      // back as `resetToken`.
      setResetToken(res.data.sessionId);
      setNotice('');
      setStep(STEP_PASSWORD);
    } catch (err) {
      const status = err?.response?.status;
      const msg = err?.response?.data?.message;
      if (status === 400) {
        setError(msg || 'The reset code does not match or has expired.');
      } else {
        setError('Something went wrong. Please try again later.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const submitNewPassword = async (e) => {
    e.preventDefault();
    setError('');
    if (newPassword.length < 6) {
      setError('New password must be at least 6 characters long.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('The two passwords do not match.');
      return;
    }
    setSubmitting(true);
    try {
      await api.post('/auth/reset-password', { resetToken, newPassword });
      setStep(STEP_DONE);
    } catch (err) {
      const status = err?.response?.status;
      const msg = err?.response?.data?.message;
      if (status === 400) {
        setError(msg || 'Your reset session has expired. Please start over.');
      } else {
        setError('Something went wrong. Please try again later.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const restart = () => {
    setStep(STEP_EMAIL);
    setOtp('');
    setResetToken('');
    setNewPassword('');
    setConfirmPassword('');
    setSessionId('');
    setNotice('');
    setError('');
  };

  const heading = step === STEP_DONE ? 'Password updated' : 'Reset your password';

  return (
    <div className="flex justify-center items-center mt-20">
      <div className="bg-white p-8 rounded-lg shadow-lg w-full max-w-md border">
        <h2 className="text-3xl font-bold text-center text-blue-600 mb-6">{heading}</h2>

        {error && (
          <p className="bg-red-100 text-red-600 p-3 rounded mb-4 text-center">{error}</p>
        )}
        {notice && step === STEP_OTP && (
          <p className="bg-blue-50 text-blue-700 p-3 rounded mb-4 text-center text-sm">{notice}</p>
        )}

        {step === STEP_EMAIL && (
          <form onSubmit={requestCode} className="flex flex-col gap-4">
            <p className="text-gray-600 text-sm text-center">
              Enter the email associated with your account and we'll send you a 6-digit code to
              choose a new password.
            </p>
            <div>
              <label className="block text-gray-700 font-semibold mb-2">Email</label>
              <input
                type="email" required autoFocus
                className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-blue-500"
                value={email} onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <button type="submit" disabled={submitting}
              className="bg-blue-600 text-white font-bold p-3 rounded hover:bg-blue-700 transition disabled:opacity-60">
              {submitting ? 'Sending…' : 'Send reset code'}
            </button>
            <p className="text-center text-sm text-gray-600">
              <Link to="/login" className="text-blue-500 hover:underline">Back to sign in</Link>
            </p>
          </form>
        )}

        {step === STEP_OTP && (
          <form onSubmit={submitOtp} className="flex flex-col gap-4">
            <p className="text-gray-600 text-sm text-center">
              Enter the 6-digit code we sent to your inbox.
            </p>
            <input
              type="text" inputMode="numeric" autoComplete="one-time-code" maxLength={6} required autoFocus
              className="w-full p-3 border rounded text-center text-2xl tracking-widest focus:outline-none focus:ring-2 focus:ring-blue-500"
              value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
            />
            <button type="submit" disabled={submitting || otp.length !== 6}
              className="bg-blue-600 text-white font-bold p-3 rounded hover:bg-blue-700 transition disabled:opacity-60">
              {submitting ? 'Verifying…' : 'Verify code'}
            </button>
            <button type="button" onClick={restart} className="text-sm text-gray-500 hover:underline">
              Use a different email
            </button>
          </form>
        )}

        {step === STEP_PASSWORD && (
          <form onSubmit={submitNewPassword} className="flex flex-col gap-4">
            <p className="text-gray-600 text-sm text-center">
              Choose a new password. You'll be signed out from every trusted device after the change.
            </p>
            <div>
              <label className="block text-gray-700 font-semibold mb-2">New password</label>
              <input
                type="password" required minLength={6} autoFocus
                className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-blue-500"
                value={newPassword} onChange={(e) => setNewPassword(e.target.value)}
              />
            </div>
            <div>
              <label className="block text-gray-700 font-semibold mb-2">Confirm new password</label>
              <input
                type="password" required minLength={6}
                className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-blue-500"
                value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)}
              />
            </div>
            <button type="submit" disabled={submitting}
              className="bg-blue-600 text-white font-bold p-3 rounded hover:bg-blue-700 transition disabled:opacity-60">
              {submitting ? 'Updating…' : 'Update password'}
            </button>
          </form>
        )}

        {step === STEP_DONE && (
          <div className="flex flex-col gap-4 text-center">
            <p className="text-green-700 bg-green-50 border border-green-100 rounded p-4">
              Your password has been updated. For your security, all trusted devices have been
              signed out — you'll need to sign in again everywhere.
            </p>
            <button type="button"
              onClick={() => navigate('/login')}
              className="bg-blue-600 text-white font-bold p-3 rounded hover:bg-blue-700 transition">
              Go to sign in
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default ForgotPassword;
