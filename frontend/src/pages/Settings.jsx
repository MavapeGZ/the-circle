import { useState, useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';

function Settings() {
  const navigate = useNavigate();
  const { user, logout } = useContext(AuthContext);

  const [activeTab, setActiveTab] = useState('profile');
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState({ text: '', type: '' });

  const [settings, setSettings] = useState({
    firstName: '',
    lastName: '',
    email: '',
    marketingEmailsOptIn: true,
    systemEmailsOptIn: true
  });

  const [passwords, setPasswords] = useState({ current: '', new: '' });
  const [devices, setDevices] = useState([]);

  useEffect(() => {
    fetchSettings();
    fetchDevices();
  }, []);

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

  // --- HANDLERS ---

  const handleProfileUpdate = async (e) => {
    e.preventDefault();
    try {
      await api.patch('/users/me', {
        firstName: settings.firstName,
        lastName: settings.lastName
      });
      showMessage('Profile updated successfully!');
    } catch (err) {
      showMessage('Failed to update profile.', 'error');
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
      showMessage('Failed to update notifications.', 'error');
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
      showMessage(err.response?.data?.message || 'Incorrect current password.', 'error');
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
        logout();
        navigate('/login');
      } catch (err) {
        showMessage('Failed to delete account.', 'error');
      }
    }
  };

  if (loading) return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">Loading settings...</div>;

  return (
    <div className="max-w-4xl mx-auto mt-8 p-4">
      <h1 className="text-3xl font-extrabold text-gray-900 mb-8">Account Settings</h1>

      {message.text && (
        <div className={`p-4 mb-6 rounded-lg text-sm font-bold ${message.type === 'error' ? 'bg-red-100 text-red-700' : 'bg-green-100 text-green-700'}`}>
          {message.text}
        </div>
      )}

      <div className="flex flex-col md:flex-row gap-8">
        
        {/* TABS SIDEBAR */}
        <div className="w-full md:w-64 flex flex-col gap-2">
          {['profile', 'security', 'notifications', 'account'].map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`text-left px-4 py-3 rounded-lg font-bold capitalize transition-colors ${
                activeTab === tab ? 'bg-blue-600 text-white shadow-md' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {tab}
            </button>
          ))}
        </div>

        {/* CONTENT AREA */}
        <div className="flex-1 bg-white p-6 rounded-2xl shadow-lg border border-gray-100">
          
          {/* PROFILE TAB */}
          {activeTab === 'profile' && (
            <form onSubmit={handleProfileUpdate} className="space-y-6">
              <h2 className="text-2xl font-bold text-gray-800 border-b pb-2">Profile Information</h2>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-bold text-gray-700 mb-1">First Name</label>
                  <input
                    type="text"
                    value={settings.firstName}
                    onChange={(e) => setSettings({ ...settings, firstName: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-blue-500"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-bold text-gray-700 mb-1">Last Name</label>
                  <input
                    type="text"
                    value={settings.lastName}
                    onChange={(e) => setSettings({ ...settings, lastName: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-blue-500"
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
                <p className="text-xs text-gray-500 mt-1">Email changing is out of scope for the current version.</p>
              </div>
              <button type="submit" className="bg-blue-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-blue-700 transition">
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
                    className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-blue-500"
                    required
                  />
                </div>
                <div>
                  <label className="block text-sm font-bold text-gray-700 mb-1">New Password</label>
                  <input
                    type="password"
                    value={passwords.new}
                    onChange={(e) => setPasswords({ ...passwords, new: e.target.value })}
                    className="w-full border border-gray-300 rounded-lg p-2.5 focus:ring-2 focus:ring-blue-500"
                    required
                  />
                </div>
                <button type="submit" className="bg-blue-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-blue-700 transition">
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
                  <div className="w-11 h-6 bg-gray-200 rounded-full peer peer-checked:bg-blue-600 peer-focus:ring-4 peer-focus:ring-blue-300 transition-all after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:after:translate-x-full peer-checked:after:border-white"></div>
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
                  <div className="w-11 h-6 bg-gray-200 rounded-full peer peer-checked:bg-blue-600 peer-focus:ring-4 peer-focus:ring-blue-300 transition-all after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:after:translate-x-full peer-checked:after:border-white"></div>
                </label>
              </div>

              <button type="submit" className="bg-blue-600 text-white font-bold py-2.5 px-6 rounded-lg hover:bg-blue-700 transition">
                Save Preferences
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

export default Settings;