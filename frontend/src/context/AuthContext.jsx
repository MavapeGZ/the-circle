import { createContext, useState, useEffect } from 'react';
import api from '../services/api';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) setUser({ token });
    setLoading(false);
  }, []);

  const storeToken = (token) => {
    localStorage.setItem('token', token);
    setUser({ token });
  };

  // Step 1 of signup: create user, server emails OTP. Returns { sessionId }.
  const register = async (userData) => {
    const { data } = await api.post('/auth/register', userData);
    return data; // { sessionId, requiresEmailVerification, message }
  };

  // Step 2 of signup: confirm OTP, server returns JWT.
  const verifyEmail = async (sessionId, otp) => {
    const { data } = await api.post('/auth/verify-email', { sessionId, otp });
    if (data.token) storeToken(data.token);
    return data;
  };

  // Login. Server may return { token } (trusted device) or
  // { sessionId, requiresOtp } (new device → OTP step required).
  const login = async (email, password) => {
    const { data } = await api.post('/auth/login', { email, password });
    if (data.token) storeToken(data.token);
    return data;
  };

  // Step 2 of login (only when requiresOtp): confirm OTP, server returns JWT
  // and sets the device-trust cookie.
  const verifyLoginOtp = async (sessionId, otp) => {
    const { data } = await api.post('/auth/login-otp', { sessionId, otp });
    if (data.token) storeToken(data.token);
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
    const { data } = await api.post(`/users/${userId}/kyc`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    if (data.jwt) storeToken(data.jwt);
    return data;
  };

  const logout = () => {
    localStorage.removeItem('token');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{
      user, loading,
      register, verifyEmail,
      login, verifyLoginOtp,
      fetchMe, uploadKycDocuments,
      logout,
    }}>
      {!loading && children}
    </AuthContext.Provider>
  );
};
