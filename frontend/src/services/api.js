import axios from 'axios';

// Configure the base URL of the API Gateway or ms-users here
const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_URL,
  withCredentials: true, // send/receive device-trust cookie for login OTP
});

// Interceptor: Before any request is sent, inject the Token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Interceptor: a 401 means the session is missing or expired (JWT TTL is short and
// there is no refresh flow). Clear the stale token and bounce to login with an
// "expired" hint, instead of letting callers surface a cryptic error. Skipped for:
//  - /auth/* requests, where 401 is a normal "bad credentials/OTP" form error;
//  - requests opting out via { skipAuthRedirect: true } (e.g. the silent session
//    probe on app bootstrap, so anonymous visitors on public pages aren't kicked).
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    const config = error?.config || {};
    const isAuthEndpoint = (config.url || '').includes('/auth/');
    const hadToken = !!localStorage.getItem('token');

    if (status === 401 && hadToken && !isAuthEndpoint && !config.skipAuthRedirect) {
      localStorage.removeItem('token');
      if (window.location.pathname !== '/login') {
        window.location.assign('/login?expired=1');
      }
    }
    return Promise.reject(error);
  }
);

export default api;
