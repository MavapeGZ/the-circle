import { useState, useContext, useEffect, useRef, Fragment } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AuthContext } from '../context/AuthContext';
import { extractApiError } from '../services/api';

const STEP_ACCOUNT = 'account';
const STEP_OTP = 'otp';
const STEP_KYC = 'kyc';
const STEP_DONE = 'done';

// Surfaces backend bean-validation field messages (e.g. weak password, name with
// '<>') instead of a generic fallback.
const describeError = (err, fallback) => extractApiError(err, fallback);

function Register() {
  const { t } = useTranslation();
  const { register, verifyEmail, fetchMe, uploadKycDocuments } = useContext(AuthContext);
  const navigate = useNavigate();

  const [step, setStep] = useState(STEP_ACCOUNT);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const [formData, setFormData] = useState({ firstName: '', lastName: '', email: '', password: '', address: '', idNumber: '' });
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
      setError(describeError(err, t('register.error.create')));
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
      setError(describeError(err, t('register.error.invalidCode')));
    } finally {
      setSubmitting(false);
    }
  };

  const submitKyc = async (e) => {
    e.preventDefault();
    setError('');
    if (!frontFile || !backFile) {
      setError(t('register.error.idRequired'));
      return;
    }
    setSubmitting(true);
    try {
      const result = await uploadKycDocuments(userId, frontFile, backFile);
      setKycMessage(result?.verified ? t('register.kyc.verified') : t('register.documentsReceived'));
      setStep(STEP_DONE);
    } catch (err) {
      setError(describeError(err, t('register.error.upload')));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex justify-center items-center mt-16">
      <div className="bg-white p-8 rounded-lg shadow-lg w-full max-w-lg border">
        <h2 className="text-3xl font-bold text-center text-green-600 mb-2">{t('register.title')}</h2>
        <StepIndicator step={step} />

        {error && <p className="bg-red-100 text-red-600 p-3 rounded my-3 text-center">{error}</p>}

        {step === STEP_ACCOUNT && (
          <form onSubmit={submitAccount} className="flex flex-col gap-4">
            <Field label={t('register.field.firstName')} name="firstName" value={formData.firstName} onChange={onAccountChange} />
            <Field label={t('register.field.lastName')} name="lastName" value={formData.lastName} onChange={onAccountChange} />
            <Field label={t('register.field.email')} name="email" type="email" value={formData.email} onChange={onAccountChange} />
            <Field label={t('register.field.idNumber')} name="idNumber" value={formData.idNumber} onChange={onAccountChange} />
            <Field label={t('register.field.address')} name="address" value={formData.address} onChange={onAccountChange} />
            <Field label={t('register.field.password')} name="password" type="password" value={formData.password} onChange={onAccountChange} minLength={8} />
            <SubmitButton disabled={submitting}>{submitting ? t('register.sendingCode') : t('register.continue')}</SubmitButton>
            <p className="text-center text-gray-600 text-sm">
              {t('register.haveAccount')} <Link to="/login" className="text-green-500 hover:underline">{t('register.signIn')}</Link>
            </p>
          </form>
        )}

        {step === STEP_OTP && (
          <form onSubmit={submitOtp} className="flex flex-col gap-4">
            <p className="text-gray-600 text-center">
              {t('register.otp.sentPrefix')} <strong>{formData.email}</strong>.
            </p>
            <input
              type="text" inputMode="numeric" autoComplete="one-time-code" maxLength={6}
              className="w-full p-3 border rounded text-center text-2xl tracking-widest focus:outline-none focus:ring-2 focus:ring-green-500"
              value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))} required
            />
            <SubmitButton disabled={submitting}>{submitting ? t('register.verifying') : t('register.verifyEmail')}</SubmitButton>
            <button type="button" className="text-sm text-gray-500 hover:underline"
                    onClick={() => setStep(STEP_ACCOUNT)}>
              {t('register.differentEmail')}
            </button>
          </form>
        )}

        {step === STEP_KYC && (
          <form onSubmit={submitKyc} className="flex flex-col gap-4">
            <p className="text-gray-600 text-center">
              {t('register.kyc.intro')}
            </p>
            <IdField label={t('register.kyc.front')} file={frontFile} onChange={setFrontFile} fallbackName="dni-front" />
            <IdField label={t('register.kyc.back')} file={backFile} onChange={setBackFile} fallbackName="dni-back" />
            <SubmitButton disabled={submitting}>{submitting ? t('register.uploading') : t('register.submitDocs')}</SubmitButton>
            <button type="button" className="text-sm text-gray-500 hover:underline"
                    onClick={() => navigate('/')}>
              {t('register.skip')}
            </button>
          </form>
        )}

        {step === STEP_DONE && (
          <div className="flex flex-col gap-4 text-center">
            <p className="text-green-700 font-semibold">{kycMessage}</p>
            <p className="text-gray-600">{t('register.done.continue')}</p>
            <button onClick={() => navigate('/')}
                    className="bg-green-600 text-white font-bold p-3 rounded hover:bg-green-700 transition">
              {t('register.goHome')}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function StepIndicator({ step }) {
  const { t } = useTranslation();
  const steps = [
    { id: STEP_ACCOUNT, label: t('register.step.account') },
    { id: STEP_OTP, label: t('register.step.verify') },
    { id: STEP_KYC, label: t('register.step.identity') },
  ];
  const activeIndex = steps.findIndex((s) => s.id === step);
  return (
    <div className="flex justify-between items-center my-4">
      {steps.map((s, i) => {
        const done = i < activeIndex || step === STEP_DONE;
        const active = i === activeIndex && step !== STEP_DONE;
        return (
          <Fragment key={s.id}>
            <div className="flex items-center">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-bold ${
                done ? 'bg-green-600' : active ? 'bg-green-500' : 'bg-gray-300'
              }`}>{i + 1}</div>
              <span className={`ml-2 text-sm ${active ? 'text-green-700 font-semibold' : 'text-gray-500'}`}>{s.label}</span>
            </div>
            {i < steps.length - 1 && <div className="flex-1 h-0.5 bg-gray-200 mx-2" />}
          </Fragment>
        );
      })}
    </div>
  );
}

function Field({ label, name, value, onChange, type = 'text', minLength }) {
  return (
    <div>
      <label className="block text-gray-700 font-semibold mb-2">{label}</label>
      <input
        type={type} name={name} value={value} onChange={onChange} required minLength={minLength}
        className="w-full p-3 border rounded focus:outline-none focus:ring-2 focus:ring-green-500"
      />
    </div>
  );
}

function IdField({ label, file, onChange, fallbackName }) {
  const { t } = useTranslation();
  const [mode, setMode] = useState('idle');
  const [cameraError, setCameraError] = useState('');
  const [previewUrl, setPreviewUrl] = useState(null);
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const fileInputRef = useRef(null);

  const stopStream = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
  };

  useEffect(() => {
    if (mode !== 'camera') {
      stopStream();
      return undefined;
    }
    setCameraError('');
    let cancelled = false;
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'environment' }, audio: false })
      .then((stream) => {
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
        }
      })
      .catch((err) => {
        setCameraError(err?.message || t('register.cameraUnavailable'));
        setMode('idle');
      });
    return () => {
      cancelled = true;
      stopStream();
    };
  }, [mode]);

  useEffect(() => {
    if (!file) {
      setPreviewUrl(null);
      return undefined;
    }
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const snapshot = () => {
    const video = videoRef.current;
    if (!video || !video.videoWidth) return;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext('2d').drawImage(video, 0, 0);
    canvas.toBlob((blob) => {
      if (!blob) return;
      const captured = new File([blob], `${fallbackName}-${Date.now()}.jpg`, { type: 'image/jpeg' });
      onChange(captured);
      stopStream();
      setMode('idle');
    }, 'image/jpeg', 0.92);
  };

  return (
    <div>
      <label className="block text-gray-700 font-semibold mb-2">{label}</label>
      <div className="flex gap-2 mb-2">
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          className="flex-1 p-2 rounded border bg-green-600 text-white border-green-600 hover:bg-green-700"
        >
          {t('register.uploadFile')}
        </button>
        <button
          type="button"
          onClick={() => setMode('camera')}
          className={`flex-1 p-2 rounded border ${mode === 'camera' ? 'bg-green-600 text-white border-green-600' : 'bg-white text-gray-700 border-gray-300'}`}
        >
          {t('register.takePhoto')}
        </button>
      </div>

      <input
        ref={fileInputRef}
        type="file" accept="image/*,application/pdf"
        onChange={(e) => {
          onChange(e.target.files?.[0] || null);
          e.target.value = '';
        }}
        className="hidden"
      />

      {mode === 'camera' && (
        <div className="flex flex-col gap-2">
          {cameraError && <p className="text-red-600 text-sm">{cameraError}</p>}
          <video ref={videoRef} autoPlay playsInline muted className="w-full rounded border bg-black" />
          <button
            type="button"
            onClick={snapshot}
            className="bg-green-600 text-white font-semibold p-2 rounded hover:bg-green-700"
          >
            {t('register.capture')}
          </button>
        </div>
      )}

      {file && (
        <div className="mt-2 flex items-center gap-2">
          {previewUrl && file.type?.startsWith('image/') && (
            <img src={previewUrl} alt={`${label} preview`} className="h-16 w-auto rounded border" />
          )}
          <p className="text-xs text-gray-500">{file.name}</p>
        </div>
      )}
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
