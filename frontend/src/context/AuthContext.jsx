import { createContext, useState, useEffect } from 'react';
import api from '../services/api';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // A verified session has a full profile (i.e. an `id` from `/users/me`).
  // The bare `{ token }` fallback below is an unverified, optimistic state used
  // only to avoid logging users out on transient probe failures — it must not
  // be treated as authenticated by route guards.
  const isAuthenticated = Boolean(user?.id);

  useEffect(() => {
    const bootstrap = async () => {
      const token = localStorage.getItem('token');
      if (!token) {
        setLoading(false);
        return;
      }

      // Silent probe for the persisted session. On first load the backend may still
      // be warming up (cold containers, gateway not ready yet), so /users/me fails
      // with a network/5xx error. Retry a few times before giving up: otherwise we
      // fall through to the optimistic { token } state, which renders a logged-in
      // avatar with no profile (generic "User" placeholder instead of the real name)
      // until the next reload. An expired token (401/403) is handled here instead —
      // we clear it rather than let the interceptor redirect a passive visitor.
      const probe = async (attempt = 0) => {
        try {
          const { data } = await api.get('/users/me', { skipAuthRedirect: true });
          setUser({ ...data, token });
        } catch (error) {
          const status = error?.response?.status;
          if (status === 401 || status === 403) {
            localStorage.removeItem('token');
            setUser(null);
            return;
          }
          // Transient (network / 5xx): back off and retry so a cold backend on the
          // first request doesn't strand us in the name-less degraded state.
          if (attempt < 4) {
            await new Promise((resolve) => setTimeout(resolve, 400 * (attempt + 1)));
            return probe(attempt + 1);
          }
          // Still failing after retries: stay optimistically logged in (don't log the
          // user out over a transient outage), accepting the degraded state as a last
          // resort rather than the first.
          setUser({ token });
        }
      };

      await probe();
      setLoading(false);
    };
    bootstrap();
  }, []);

  const storeToken = async (token) => {
    localStorage.setItem('token', token);
    try {
      const { data } = await api.get('/users/me');
      setUser({ ...data, token });
    } catch (error) {
      setUser({ token });
    }
  };

  // Step 1 of signup: create user, server emails OTP. Returns { sessionId }.
  const register = async (userData) => {
    const { data } = await api.post('/auth/register', userData);
    return data; // { sessionId, requiresEmailVerification, message }
  };

  // Step 2 of signup: confirm OTP, server returns JWT.
  const verifyEmail = async (sessionId, otp) => {
    const { data } = await api.post('/auth/verify-email', { sessionId, otp });
    if (data.token) await storeToken(data.token);
    return data;
  };

  // Login. Server may return { token } (trusted device) or
  // { sessionId, requiresOtp } (new device → OTP step required).
  const login = async (email, password) => {
    const { data } = await api.post('/auth/login', { email, password });
    if (data.token) await storeToken(data.token);
    return data;
  };

  // Step 2 of login (only when requiresOtp): confirm OTP, server returns JWT
  // and sets the device-trust cookie.
  const verifyLoginOtp = async (sessionId, otp) => {
    const { data } = await api.post('/auth/login-otp', { sessionId, otp });
    if (data.token) await storeToken(data.token);
    return data;
  };

  const fetchMe = async () => {
    const { data } = await api.get('/users/me');
    return data; // { id, email, firstName, lastName, kycStatus }
  };

  const uploadKycDocuments = async (userId, frontFile, backFile) => {
    const fd = new FormData();
    fd.append('front', frontFile);
    fd.append('back', backFile);
    const { data } = await api.post(`/users/${userId}/kyc`, fd);
    if (data.jwt) await storeToken(data.jwt);
    return data;
  };

  const logout = async () => {
    // Revoke the refresh token server-side and clear its httpOnly cookie so it
    // cannot mint new sessions. Best-effort: the local session is cleared even
    // if the call fails (offline, expired cookie, etc.).
    try {
      await api.post('/auth/logout', null, { skipAuthRedirect: true });
    } catch {
      // ignore — clearing local state below is what matters for the user.
    }
    localStorage.removeItem('token');
    setUser(null);
  };

  // Patch the in-memory user so chrome that reads the context (e.g. the navbar
  // avatar) reflects a change instantly, without a full re-fetch or reload.
  const updateUser = (partial) => {
    setUser((prev) => (prev ? { ...prev, ...partial } : prev));
  };

  // Bumped whenever a contract action completes (sign / confirm delivery /
  // settle deposit) so the navbar's pending-action badge re-counts immediately
  // instead of waiting for its poll interval.
  const [contractsRefreshNonce, setContractsRefreshNonce] = useState(0);
  const refreshContracts = () => setContractsRefreshNonce((n) => n + 1);

  return (
    <AuthContext.Provider value={{
      user, isAuthenticated, loading,
      register, verifyEmail,
      login, verifyLoginOtp,
      fetchMe, uploadKycDocuments,
      updateUser,
      contractsRefreshNonce, refreshContracts,
      logout,
    }}>
      {!loading && children}
    </AuthContext.Provider>
  );
};
