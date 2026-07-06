import { useState, useEffect, useContext, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api, { resolveAssetUrl, extractApiError } from '../services/api';
import { AuthContext } from '../context/AuthContext';
import { usePreferences } from '../context/PreferencesContext';
import { SUPPORTED_CURRENCIES } from '../utils/currency';
import { ZONE_OPTIONS } from '../constants/zones';
import usePageTitle from '../hooks/usePageTitle';

const TABS = ['profile', 'security', 'payments', 'verification', 'notifications', 'preferences', 'account'];
const TIMEZONE_OPTIONS = ['Europe/Madrid', 'Europe/London', 'Atlantic/Canary', 'UTC', 'America/New_York', 'America/Los_Angeles'];
const LANGUAGE_OPTIONS = ['es', 'en'];

function Settings() {
  usePageTitle('title.settings');
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();
  const { language, currency, timezone, updatePreferences } = usePreferences();
  const { user, logout, uploadKycDocuments, updateUser } = useContext(AuthContext);

  const initialTab = location.state?.tab && TABS.includes(location.state.tab)
    ? location.state.tab
    : 'profile';
  const [activeTab, setActiveTab] = useState(initialTab);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState({ text: '', type: '' });

  const [settings, setSettings] = useState({
    firstName: '',
    lastName: '',
    email: '',
    address: '',
    zone: '',
    idNumber: '',
    ibanLast4: null,
    avatarUrl: null,
    marketingEmailsOptIn: true,
    systemEmailsOptIn: true
  });

  const [passwords, setPasswords] = useState({ current: '', new: '' });
  const [devices, setDevices] = useState([]);
  const [ibanInput, setIbanInput] = useState('');
  const avatarInputRef = useRef(null);
  const [avatarUploading, setAvatarUploading] = useState(false);

  // KYC (identity verification) state for the verification tab.
  const [kycStatus, setKycStatus] = useState('UNVERIFIED');
  const [kycFront, setKycFront] = useState(null);
  const [kycBack, setKycBack] = useState(null);
  const [kycSubmitting, setKycSubmitting] = useState(false);

  // Mirror the card-number UX: strip non-alphanumerics, uppercase, regroup in 4s.
  // IBANs are at most 34 chars so the cap leaves room for the spaces.
  const formatIban = (raw) => (raw || '')
    .replace(/[^a-zA-Z0-9]/g, '')
    .toUpperCase()
    .slice(0, 34)
    .replace(/(.{4})/g, '$1 ')
    .trim();

  useEffect(() => {
    fetchSettings();
    fetchDevices();
    fetchKycStatus();
  }, []);

  const fetchKycStatus = async () => {
    if (!user?.id) return;
    try {
      const res = await api.get(`/users/${user.id}/kyc/status`);
      // The endpoint reports the status string in `message`.
      if (res.data?.message) setKycStatus(res.data.message);
    } catch (err) {
      console.error('Error fetching KYC status:', err);
    }
  };

  const fetchSettings = async () => {
    try {
      const res = await api.get('/users/me/settings');
      setSettings(res.data);
    } catch (err) {
      console.error('Error fetching settings:', err);
    } finally {
      setLoading(false);
    }
  };

  const fetchDevices = async () => {
    try {
      const res = await api.get('/users/me/devices');
      setDevices(res.data);
    } catch (err) {
      console.error('Error fetching devices:', err);
    }
  };

  const showMessage = (text, type = 'success') => {
    setMessage({ text, type });
    setTimeout(() => setMessage({ text: '', type: '' }), 4000);
  };

  // Switching section discards any edits that were never saved: reload the
  // server's canonical settings and clear the transient inputs (password,
  // IBAN, KYC files) so a half-filled form never bleeds across tabs.
  const handleTabChange = (tab) => {
    if (tab === activeTab) return;
    fetchSettings();
    setPasswords({ current: '', new: '' });
    setIbanInput('');
    setKycFront(null);
    setKycBack(null);
    setMessage({ text: '', type: '' });
    setActiveTab(tab);
  };

  // --- HANDLERS ---

  const handleProfileUpdate = async (e) => {
    e.preventDefault();
    try {
      await api.patch('/users/me', {
        firstName: settings.firstName,
        lastName: settings.lastName,
        address: settings.address,
        zone: settings.zone,
        idNumber: settings.idNumber
      });
      showMessage('Profile updated successfully!');
    } catch (err) {
      showMessage(extractApiError(err, 'Failed to update profile.'), 'error');
    }
  };

  const handleNotificationsUpdate = async (e) => {
    e.preventDefault();
    try {
      await api.patch('/users/me', {
        marketingEmailsOptIn: settings.marketingEmailsOptIn,
        systemEmailsOptIn: settings.systemEmailsOptIn
      });
      showMessage('Notification preferences saved!');
    } catch (err) {
      showMessage(extractApiError(err, 'Failed to update notifications.'), 'error');
    }
  };

  // Mirror the global preferences locally so the selects are editable, then push
  // all three at once. updatePreferences persists to the account + localStorage
  // and re-localizes the app immediately.
  const [prefForm, setPrefForm] = useState({ language, currency, timezone });
  useEffect(() => {
    setPrefForm({ language, currency, timezone });
  }, [language, currency, timezone]);

  const handlePreferencesUpdate = async (e) => {
    e.preventDefault();
    try {
      await updatePreferences(prefForm);
      showMessage(t('settings.preferences.saved'));
    } catch (err) {
      showMessage(extractApiError(err, t('settings.preferences.saveError')), 'error');
    }
  };

  const handlePasswordChange = async (e) => {
    e.preventDefault();
    try {
      await api.post('/users/me/change-password', {
        currentPassword: passwords.current,
        newPassword: passwords.new
      });
      setPasswords({ current: '', new: '' });
      showMessage('Password changed successfully!');
    } catch (err) {
      showMessage(extractApiError(err, 'Incorrect current password.'), 'error');
    }
  };

  const handleIbanUpdate = async (e) => {
    e.preventDefault();
    try {
      const res = await api.patch('/users/me/iban', { iban: ibanInput });
      setSettings({ ...settings, ibanLast4: res.data.ibanLast4 });
      setIbanInput('');
      showMessage(res.data.ibanLast4 ? 'Payout IBAN saved.' : 'Payout IBAN cleared.');
    } catch (err) {
      showMessage(extractApiError(err, 'Invalid IBAN. Please double-check the digits.'), 'error');
    }
  };

  const handleAvatarSelected = async (file) => {
    if (!file) return;
    setAvatarUploading(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      const res = await api.post('/users/me/avatar', fd);
      setSettings((prev) => ({ ...prev, avatarUrl: res.data.avatarUrl }));
      updateUser({ avatarUrl: res.data.avatarUrl });
      showMessage('Profile picture updated!');
    } catch (err) {
      showMessage(extractApiError(err, 'Could not upload the picture.'), 'error');
    } finally {
      setAvatarUploading(false);
    }
  };

  const handleAvatarRemove = async () => {
    setAvatarUploading(true);
    try {
      await api.delete('/users/me/avatar');
      setSettings((prev) => ({ ...prev, avatarUrl: null }));
      updateUser({ avatarUrl: null });
      showMessage('Profile picture removed.');
    } catch (err) {
      showMessage('Could not remove the picture.', 'error');
    } finally {
      setAvatarUploading(false);
    }
  };

  const handleKycSubmit = async (e) => {
    e.preventDefault();
    if (!kycFront || !kycBack) {
      showMessage('Both front and back of your ID are required.', 'error');
      return;
    }
    setKycSubmitting(true);
    try {
      const result = await uploadKycDocuments(user.id, kycFront, kycBack);
      setKycFront(null);
      setKycBack(null);
      showMessage(result?.message || 'Documents received.');
      await fetchKycStatus();
    } catch (err) {
      showMessage(err.response?.data?.message || 'Could not upload your documents. Try again.', 'error');
    } finally {
      setKycSubmitting(false);
    }
  };

  const handleRevokeDevice = async (deviceId) => {
    try {
      await api.delete(`/users/me/devices/${deviceId}`);
      setDevices(devices.filter(d => d.id !== deviceId));
      showMessage('Device revoked.');
    } catch (err) {
      showMessage('Failed to revoke device.', 'error');
    }
  };

  const handleDeleteAccount = async () => {
    const confirmed = window.confirm(
      'Are you absolutely sure? This will delete your profile and log you out immediately. This cannot be undone.'
    );
    if (confirmed) {
      try {
        await api.delete('/users/me');
        await logout();
        navigate('/login');
      } catch (err) {
        showMessage('Failed to delete account.', 'error');
      }
    }
  };

  if (loading) return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">{t('settings.loading')}</div>;

  return (
    <div className="max-w-4xl mx-auto mt-8 p-4">
      <h1 className="text-3xl font-extrabold text-gray-900 mb-8">{t('settings.title')}</h1>

      {message.text && (
        <div className={`p-4 mb-6 rounded-lg text-sm font-bold ${message.type === 'error' ? 'bg-red-100 text-red-700' : 'bg-green-100 text-green-700'}`}>
          {message.text}
        </div>
      )}

      <div className="flex flex-col md:flex-row gap-8">
        
        {/* TABS SIDEBAR */}
        <div className="w-full md:w-64 flex flex-col gap-2">
          {TABS.map((tab) => (
            <button
              key={tab}
              onClick={() => handleTabChange(tab)}
              data-testid={`settings-tab-${tab}`}
              className={`text-left px-4 py-3 rounded-lg font-bold capitalize transition-colors ${
                activeTab === tab ? 'bg-indigo-600 text-white shadow-md' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {t(`settings.tab.${tab}`)}
            </button>
          ))}
        </div>

        {/* CONTENT AREA */}
        <div className="flex-1 bg-white p-6 rounded-2xl shadow-lg border border-gray-100">
          
          {/* PROFILE TAB */}
          {activeTab === 'profile' && (
            <form onSubmit={handleProfileUpdate} className="space-y-6">
              <h2 className="text-2xl font-bold text-gray-800 border-b pb-2">Profile Information</h2>

              {/* PROFILE PICTURE */}
              <div className="flex items-center gap-5">
                <div className="h-20 w-20 rounded-full bg-gray-100 border border-gray-200 flex items-center justify-center overflow-hidden shrink-0">
                  {settings.avatarUrl ? (
                    <img src={resolveAssetUrl(settings.avatarUrl)} alt="Profile" className="h-full w-full object-cover" />
                  ) : (
                    <span className="text-2xl font-black text-gray-400">
                      {(settings.firstName?.[0] || '').toUpperCase() || 'U'}
                    </span>
                  )}
                </div>
                <div className="flex flex-col gap-2">
                  <input
                    ref={avatarInputRef}
                    type="file"
                    accept="image/png,image/jpeg"
                    className="hidden"
                    onChange={(e) => {
                      handleAvatarSelected(e.target.files?.[0] || null);
                      e.target.value = '';
                    }}
                  />
                  <div className="flex gap-2">
                    <button
                      type="button"
                      disabled={avatarUploading}
                      onClick={() => avatarInputRef.current?.click()}
                      className="bg-indigo-600 text-white font-bold py-2 px-4 rounded-lg hover:bg-indigo-700 transition disabled:opacity-60"
                    >
                      {avatarUploading ? 'Uploading…' : settings.avatarUrl ? 'Change picture' : 'Upload picture'}
                    </button>
                    {settings.avatarUrl && (
                      <button
                        type="button"
                        disabled={avatarUploading}
                        onClick={handleAvatarRemove}
                        className="bg-gray-100 text-gray-700 font-bold py-2 px-4 rounded-lg hover:bg-gray-200 transition disabled:opacity-60"
                      >
                        Remove
                      </button>
                    )}
                  </div>
                  <p className="text-xs text-gray-500">JPEG, JPG or PNG. Max 5 MB.</p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-bold text-gray-700 mb-1">First Name</label>
                  <input
                    type="text"
                    value={settings.firstName}
                    onChange={(e) => setSettings({ ...settings, firstName: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-bold text-gray-700 mb-1">Last Name</label>
                  <input
                    type="text"
                    value={settings.lastName}
                    onChange={(e) => setSettings({ ...settings, lastName: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500"
                    required
                  />
                </div>
              </div>
              <div>
                <label className="block text-sm font-bold text-gray-700 mb-1">Email Address</label>
                <input
                  type="email"
                  value={settings.email}
                  disabled
                  className="w-full border border-gray-200 bg-gray-50 text-gray-500 rounded-lg p-2.5 cursor-not-allowed"
                />
              </div>
              <div>
                <label className="block text-sm font-bold text-gray-700 mb-1">ID Number</label>
                <input
                  type="text"
                  value={settings.idNumber || ''}
                  onChange={(e) => setSettings({ ...settings, idNumber: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500"
                  placeholder="National ID / passport number"
                />
                <p className="text-xs text-gray-500 mt-1">Required on signed contracts.</p>
              </div>
              <div>
                <label className="block text-sm font-bold text-gray-700 mb-1">Address</label>
                <input
                  type="text"
                  value={settings.address || ''}
                  onChange={(e) => setSettings({ ...settings, address: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500"
                  placeholder="Street, number, city, postal code"
                />
                <p className="text-xs text-gray-500 mt-1">Required on signed contracts.</p>
              </div>
              <div>
                <label className="block text-sm font-bold text-gray-700 mb-1">Approximate Area</label>
                <select
                  value={settings.zone || ''}
                  onChange={(e) => setSettings({ ...settings, zone: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500 bg-white"
                >
                  <option value="">Select your area</option>
                  {ZONE_OPTIONS.map((zone) => (
                    <option key={zone.value} value={zone.value}>
                      {zone.label}
                    </option>
                  ))}
                </select>
                <p className="text-xs text-gray-500 mt-1">This is a broad area used for catalog search filtering, not an exact location.</p>
              </div>
              <button type="submit" className="bg-indigo-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-indigo-700 transition">
                Save Profile
              </button>
            </form>
          )}

          {/* SECURITY TAB */}
          {activeTab === 'security' && (
            <div className="space-y-10">
              <form onSubmit={handlePasswordChange} className="space-y-6">
                <h2 className="text-2xl font-bold text-gray-800 border-b pb-2">Change Password</h2>
                <div>
                  <label className="block text-sm font-bold text-gray-700 mb-1">Current Password</label>
                  <input
                    type="password"
                    value={passwords.current}
                    onChange={(e) => setPasswords({ ...passwords, current: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-bold text-gray-700 mb-1">New Password</label>
                  <input
                    type="password"
                    value={passwords.new}
                    onChange={(e) => setPasswords({ ...passwords, new: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500"
                    required
                  />
                </div>
                <button type="submit" className="bg-indigo-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-indigo-700 transition">
                  Update Password
                </button>
              </form>

              <div>
                <h2 className="text-2xl font-bold text-gray-800 border-b pb-2 mb-4">Trusted Devices</h2>
                {devices.length === 0 ? (
                  <p className="text-gray-500">No trusted devices found.</p>
                ) : (
                  <ul className="divide-y divide-gray-100">
                    {devices.map(device => (
                      <li key={device.id} className="py-4 flex justify-between items-center">
                        <div>
                          <p className="font-bold text-gray-800">{device.userAgent || 'Unknown Device'}</p>
                          <p className="text-sm text-gray-500">Last seen: {new Date(device.lastSeenAt).toLocaleString()}</p>
                        </div>
                        <button 
                          onClick={() => handleRevokeDevice(device.id)}
                          className="text-red-600 font-bold bg-red-50 px-3 py-1.5 rounded hover:bg-red-100 transition"
                        >
                          Revoke
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          )}

          {/* PAYMENTS TAB */}
          {activeTab === 'payments' && (
            <form onSubmit={handleIbanUpdate} className="space-y-6" data-testid="settings-payments-form">
              <h2 className="text-2xl font-bold text-gray-800 border-b pb-2">Payout Account</h2>
              <p className="text-sm text-gray-600">
                When someone pays for one of your listings, funds are released to this IBAN once both
                parties have signed. Only the last 4 digits are shown after saving; the full IBAN is
                stored encrypted.
              </p>
              {settings.ibanLast4 ? (
                <div className="p-4 bg-green-50 border border-green-100 rounded-lg flex justify-between items-center" data-testid="settings-iban-current">
                  <div>
                    <p className="text-sm text-gray-500">Current payout IBAN</p>
                    <p className="font-mono text-lg text-gray-800">•••• {settings.ibanLast4}</p>
                  </div>
                </div>
              ) : (
                <div className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg text-sm text-yellow-800">
                  No payout IBAN on file. You need one before publishing paid items (sales or rentals).
                </div>
              )}
              <div>
                <label className="block text-sm font-bold text-gray-700 mb-1">
                  {settings.ibanLast4 ? 'Replace IBAN' : 'Add IBAN'}
                </label>
                <input
                  type="text"
                  value={ibanInput}
                  onChange={(e) => setIbanInput(formatIban(e.target.value))}
                  placeholder="ES00 0000 0000 0000 0000 0000"
                  data-testid="settings-iban-input"
                  className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500 font-mono tracking-wider"
                />
                <p className="text-xs text-gray-500 mt-1">
                  Spaces are ignored. Leave empty and submit to remove the saved IBAN.
                </p>
              </div>
              <button type="submit" data-testid="settings-iban-submit" className="bg-indigo-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-indigo-700 transition">
                {settings.ibanLast4 ? 'Update IBAN' : 'Save IBAN'}
              </button>
            </form>
          )}

          {/* VERIFICATION (KYC) TAB */}
          {activeTab === 'verification' && (
            <div className="space-y-6">
              <h2 className="text-2xl font-bold text-gray-800 border-b pb-2">Identity Verification</h2>

              <div className="flex items-center gap-3">
                <span className="text-sm font-bold text-gray-700">Current status:</span>
                <KycStatusBadge status={kycStatus} />
              </div>

              {kycStatus === 'VERIFIED' && (
                <div className="p-4 bg-green-50 border border-green-100 rounded-lg text-sm text-green-800">
                  Your identity has been verified. Nothing more to do here.
                </div>
              )}

              {kycStatus === 'PENDING_REVIEW' && (
                <div className="p-4 bg-indigo-50 border border-indigo-100 rounded-lg text-sm text-indigo-800">
                  Your documents have been received and are under review. You can keep using The Circle
                  in the meantime.
                </div>
              )}

              {(kycStatus === 'UNVERIFIED' || kycStatus === 'REJECTED') && (
                <form onSubmit={handleKycSubmit} className="space-y-4" data-testid="settings-kyc-form">
                  {kycStatus === 'REJECTED' && (
                    <div className="p-4 bg-red-50 border border-red-100 rounded-lg text-sm text-red-800">
                      Your previous submission was rejected. Please upload clear photos of your ID and try again.
                    </div>
                  )}
                  <p className="text-sm text-gray-600">
                    If you skipped identity verification at sign-up, you can complete it now. Upload the
                    front and back of your government-issued ID.
                  </p>
                  <div>
                    <label className="block text-sm font-bold text-gray-700 mb-1">ID — Front</label>
                    <input
                      type="file" accept="image/*,application/pdf" data-testid="settings-kyc-front"
                      onChange={(e) => setKycFront(e.target.files?.[0] || null)}
                      className="w-full text-sm text-gray-600 file:mr-3 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-indigo-600 file:text-white file:font-bold hover:file:bg-indigo-700"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-bold text-gray-700 mb-1">ID — Back</label>
                    <input
                      type="file" accept="image/*,application/pdf" data-testid="settings-kyc-back"
                      onChange={(e) => setKycBack(e.target.files?.[0] || null)}
                      className="w-full text-sm text-gray-600 file:mr-3 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-indigo-600 file:text-white file:font-bold hover:file:bg-indigo-700"
                    />
                  </div>
                  <button type="submit" disabled={kycSubmitting} data-testid="settings-kyc-submit"
                    className="bg-indigo-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-indigo-700 transition disabled:opacity-60">
                    {kycSubmitting ? 'Uploading…' : 'Submit Documents'}
                  </button>
                </form>
              )}
            </div>
          )}

          {/* NOTIFICATIONS TAB */}
          {activeTab === 'notifications' && (
            <form onSubmit={handleNotificationsUpdate} className="space-y-6">
              <h2 className="text-2xl font-bold text-gray-800 border-b pb-2">Email Preferences</h2>
              
              <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg border border-gray-100">
                <div>
                  <h4 className="font-bold text-gray-800">Marketing & Promotional</h4>
                  <p className="text-sm text-gray-500">Receive updates, offers, and platform news.</p>
                </div>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input 
                    type="checkbox" 
                    className="sr-only peer" 
                    checked={settings.marketingEmailsOptIn}
                    onChange={(e) => setSettings({ ...settings, marketingEmailsOptIn: e.target.checked })}
                  />
                  <div className="w-11 h-6 bg-gray-200 rounded-full peer peer-checked:bg-indigo-600 peer-focus:ring-4 peer-focus:ring-indigo-300 transition-all after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:after:translate-x-full peer-checked:after:border-white"></div>
                </label>
              </div>

              <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg border border-gray-100">
                <div>
                  <h4 className="font-bold text-gray-800">System & Security</h4>
                  <p className="text-sm text-gray-500">Essential alerts like password changes and login attempts.</p>
                </div>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input 
                    type="checkbox" 
                    className="sr-only peer" 
                    checked={settings.systemEmailsOptIn}
                    onChange={(e) => setSettings({ ...settings, systemEmailsOptIn: e.target.checked })}
                  />
                  <div className="w-11 h-6 bg-gray-200 rounded-full peer peer-checked:bg-indigo-600 peer-focus:ring-4 peer-focus:ring-indigo-300 transition-all after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:after:translate-x-full peer-checked:after:border-white"></div>
                </label>
              </div>

              <button type="submit" className="bg-indigo-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-indigo-700 transition">
                Save Preferences
              </button>
            </form>
          )}

          {/* PREFERENCES TAB */}
          {activeTab === 'preferences' && (
            <form onSubmit={handlePreferencesUpdate} className="space-y-6">
              <h2 className="text-2xl font-bold text-gray-800 border-b pb-2">{t('settings.preferences.title')}</h2>
              <p className="text-sm text-gray-600">{t('settings.preferences.intro')}</p>

              <div>
                <label className="block text-sm font-bold text-gray-700 mb-1">{t('settings.preferences.language')}</label>
                <select
                  value={prefForm.language}
                  onChange={(e) => setPrefForm({ ...prefForm, language: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500 bg-white"
                >
                  {LANGUAGE_OPTIONS.map((code) => (
                    <option key={code} value={code}>{t(`lang.${code}`)}</option>
                  ))}
                </select>
                <p className="text-xs text-gray-500 mt-1">{t('settings.preferences.languageHelp')}</p>
              </div>

              <div>
                <label className="block text-sm font-bold text-gray-700 mb-1">{t('settings.preferences.currency')}</label>
                <select
                  value={prefForm.currency}
                  onChange={(e) => setPrefForm({ ...prefForm, currency: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500 bg-white"
                >
                  {SUPPORTED_CURRENCIES.map((code) => (
                    <option key={code} value={code}>{code}</option>
                  ))}
                </select>
                <p className="text-xs text-gray-500 mt-1">{t('settings.preferences.currencyHelp')}</p>
              </div>

              <div>
                <label className="block text-sm font-bold text-gray-700 mb-1">{t('settings.preferences.timezone')}</label>
                <select
                  value={prefForm.timezone}
                  onChange={(e) => setPrefForm({ ...prefForm, timezone: e.target.value })}
                  className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-indigo-500 bg-white"
                >
                  {TIMEZONE_OPTIONS.map((tz) => (
                    <option key={tz} value={tz}>{t(`tz.${tz}`)}</option>
                  ))}
                </select>
                <p className="text-xs text-gray-500 mt-1">{t('settings.preferences.timezoneHelp')}</p>
              </div>

              <button type="submit" className="bg-indigo-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-indigo-700 transition">
                {t('settings.preferences.save')}
              </button>
            </form>
          )}

          {/* ACCOUNT TAB */}
          {activeTab === 'account' && (
            <div className="space-y-6">
              <h2 className="text-2xl font-bold text-red-600 border-b border-red-100 pb-2">Danger Zone</h2>
              <div className="bg-red-50 p-6 rounded-xl border border-red-100">
                <h3 className="font-bold text-red-800 text-lg mb-2">Delete Account</h3>
                <p className="text-red-600 mb-6">
                  Once you delete your account, there is no going back. Please be certain. All your personal data will be anonymized and you will lose access immediately.
                </p>
                <button 
                  onClick={handleDeleteAccount}
                  className="bg-red-600 text-white font-extrabold py-3 px-6 rounded-lg hover:bg-red-700 transition w-full md:w-auto"
                >
                  Permanently Delete My Account
                </button>
              </div>
            </div>
          )}

        </div>
      </div>
    </div>
  );
}

function KycStatusBadge({ status }) {
  const map = {
    VERIFIED: { label: 'Verified', cls: 'bg-green-100 text-green-700' },
    PENDING_REVIEW: { label: 'Pending review', cls: 'bg-indigo-100 text-indigo-700' },
    REJECTED: { label: 'Rejected', cls: 'bg-red-100 text-red-700' },
    UNVERIFIED: { label: 'Not verified', cls: 'bg-gray-100 text-gray-600' },
  };
  const { label, cls } = map[status] || map.UNVERIFIED;
  return <span className={`px-3 py-1 rounded-full text-xs font-bold ${cls}`}>{label}</span>;
}

export default Settings;