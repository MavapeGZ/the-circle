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
      try {
        // Silent probe: handle an expired token here (clear it) rather than letting
        // the global interceptor redirect a passive visitor away from a public page.
        const { data } = await api.get('/users/me', { skipAuthRedirect: true });
        setUser({ ...data, token });
      } catch (error) {
        const status = error?.response?.status;
        if (status === 401 || status === 403) {
          localStorage.removeItem('token');
          setUser(null);
        } else {
          setUser({ token });
        }
      } finally {
        setLoading(false);
      }
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

  return (
    <AuthContext.Provider value={{
      user, isAuthenticated, loading,
      register, verifyEmail,
      login, verifyLoginOtp,
      fetchMe, uploadKycDocuments,
      logout,
    }}>
      {!loading && children}
    </AuthContext.Provider>
  );
};
