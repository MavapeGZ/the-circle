import { useState, useContext } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

const STEP_ACCOUNT = 'account';
const STEP_OTP = 'otp';
const STEP_KYC = 'kyc';
const STEP_DONE = 'done';

function Register() {
  const { register, verifyEmail, fetchMe, uploadKycDocuments } = useContext(AuthContext);
  const navigate = useNavigate();

  const [step, setStep] = useState(STEP_ACCOUNT);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const [formData, setFormData] = useState({ firstName: '', lastName: '', email: '', password: '' });
  const [sessionId, setSessionId] = useState('');
  const [otp, setOtp] = useState('');
  const [userId, setUserId] = useState(null);
  const [frontFile, setFrontFile] = useState(null);
  const [backFile, setBackFile] = useState(null);
  const [kycMessage, setKycMessage] = useState('');

  const onAccountChange = (e) => setFormData({ ...formData, [e.target.name]: e.target.value });

  const submitAccount = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const data = await register(formData);
      setSessionId(data.sessionId);
      setStep(STEP_OTP);
    } catch (err) {
      setError(err?.response?.status === 400
        ? 'Email already registered or invalid data.'
        : 'Error registering. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  const submitOtp = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await verifyEmail(sessionId, otp);
      const me = await fetchMe();
      setUserId(me.id);
      setStep(STEP_KYC);
    } catch (err) {
      setError('Invalid or expired code. Check your email and try again.');
    } finally {
      setSubmitting(false);
    }
  };

  const submitKyc = async (e) => {
    e.preventDefault();
    setError('');
    if (!frontFile || !backFile) {
      setError('Both front and back of your ID are required.');
      return;
    }
    setSubmitting(true);
    try {
      const result = await uploadKycDocuments(userId, frontFile, backFile);
      setKycMessage(result?.message || 'Documents received.');
      setStep(STEP_DONE);
    } catch (err) {
      setError('Could not upload your documents. Try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex justify-center items-center mt-16">
      <div className="bg-white p-8 rounded-lg shadow-lg w-full max-w-lg border">
        <h2 className="text-3xl font-bold text-center text-green-600 mb-2">Create Account</h2>
        <StepIndicator step={step} />

        {error && <p className="bg-red-100 text-red-600 p-3 rounded my-3 text-center">{error}</p>}

        {step === STEP_ACCOUNT && (
          <form onSubmit={submitAccount} className="flex flex-col gap-4">
            <Field label="First Name" name="firstName" value={formData.firstName} onChange={onAccountChange} />
            <Field label="Last Name" name="lastName" value={formData.lastName} onChange={onAccountChange} />
            <Field label="Email" name="email" type="email" value={formData.email} onChange={onAccountChange} />
            <Field label="Password" name="password" type="password" value={formData.password} onChange={onAccountChange} />
            <SubmitButton disabled={submitting}>{submitting ? 'Sending code…' : 'Continue'}</SubmitButton>
            <p className="text-center text-gray-600 text-sm">
              Already have an account? <Link to="/login" className="text-green-500 hover:underline">Sign In</Link>
            </p>
          </form>
        )}

        {step === STEP_OTP && (
          <form onSubmit={submitOtp} className="flex flex-col gap-4">
            <p className="text-gray-600 text-center">
              We sent a 6-digit verification code to <strong>{formData.email}</strong>.
            </p>
            <input
              type="text" inputMode="numeric" autoComplete="one-time-code" maxLength={6}
              className="w-full p-3 border rounded text-center text-2xl tracking-widest focus:outline-none focus:ring-2 focus:ring-green-500"
              value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))} required
            />
            <SubmitButton disabled={submitting}>{submitting ? 'Verifying…' : 'Verify Email'}</SubmitButton>
            <button type="button" className="text-sm text-gray-500 hover:underline"
                    onClick={() => setStep(STEP_ACCOUNT)}>
              Use a different email
            </button>
          </form>
        )}

        {step === STEP_KYC && (
          <form onSubmit={submitKyc} className="flex flex-col gap-4">
            <p className="text-gray-600 text-center">
              Upload the front and back of your government-issued ID to finish your KYC verification.
            </p>
            <FileField label="ID — Front" file={frontFile} onChange={setFrontFile} />
            <FileField label="ID — Back" file={backFile} onChange={setBackFile} />
            <SubmitButton disabled={submitting}>{submitting ? 'Uploading…' : 'Submit Documents'}</SubmitButton>
            <button type="button" className="text-sm text-gray-500 hover:underline"
                    onClick={() => navigate('/')}>
              Skip for now
            </button>
          </form>
        )}

        {step === STEP_DONE && (
          <div className="flex flex-col gap-4 text-center">
            <p className="text-green-700 font-semibold">{kycMessage}</p>
            <p className="text-gray-600">You can continue using The Circle while we verify your identity.</p>
            <button onClick={() => navigate('/')}
                    className="bg-green-600 text-white font-bold p-3 rounded hover:bg-green-700 transition">
              Go to Home
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function StepIndicator({ step }) {
  const steps = [
    { id: STEP_ACCOUNT, label: 'Account' },
    { id: STEP_OTP, label: 'Verify Email' },
    { id: STEP_KYC, label: 'Identity' },
  ];
  const activeIndex = steps.findIndex((s) => s.id === step);
  return (
    <div className="flex justify-between items-center my-4">
      {steps.map((s, i) => {
        const done = i < activeIndex || step === STEP_DONE;
        const active = i === activeIndex && step !== STEP_DONE;
        return (
          <div key={s.id} className="flex-1 flex items-center">
            <div className={`w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-bold ${
              done ? 'bg-green-600' : active ? 'bg-green-500' : 'bg-gray-300'
            }`}>{i + 1}</div>
            <span className={`ml-2 text-sm ${active ? 'text-green-700 font-semibold' : 'text-gray-500'}`}>{s.label}</span>
            {i < steps.length - 1 && <div className="flex-1 h-0.5 bg-gray-200 mx-2" />}
          </div>
        );
      })}
    </div>
  );
}

function Field({ label, name, value, onChange, type = 'text' }) {
  return (
    <div>
      <label className="block text-gray-700 font-semibold mb-2">{label}</label>
      <input
        type={type} name={name} value={value} onChange={onChange} required
        className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-green-500"
      />
    </div>
  );
}

function FileField({ label, file, onChange }) {
  return (
    <div>
      <label className="block text-gray-700 font-semibold mb-2">{label}</label>
      <input
        type="file" accept="image/*,application/pdf" required
        onChange={(e) => onChange(e.target.files?.[0] || null)}
        className="w-full p-2 border rounded"
      />
      {file && <p className="text-xs text-gray-500 mt-1">{file.name}</p>}
    </div>
  );
}

function SubmitButton({ disabled, children }) {
  return (
    <button type="submit" disabled={disabled}
            className="bg-green-600 text-white font-bold p-3 rounded hover:bg-green-700 transition disabled:opacity-60">
      {children}
    </button>
  );
}

export default Register;
