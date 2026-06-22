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

// Single-use refresh: exchanges the httpOnly tc_refresh cookie for a fresh
// access token. Concurrent 401s share one in-flight call so we don't fire N
// parallel rotations (each rotation invalidates the previous token). Returns
// the new token, or null when the refresh token is gone/expired.
let refreshPromise = null;
const refreshAccessToken = () => {
  if (!refreshPromise) {
    // The refresh call hits /auth/*, so the response interceptor's auth-endpoint
    // guard already stops it from recursing on its own 401.
    refreshPromise = api
      .post('/auth/refresh', null, { skipAuthRedirect: true })
      .then(({ data }) => {
        const token = data?.token || null;
        if (token) localStorage.setItem('token', token);
        return token;
      })
      .catch(() => null)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
};

// Interceptor: a 401 on a normal request means the short-lived access token
// expired. Try one silent refresh (rotating the httpOnly refresh cookie) and
// replay the original request; only if that fails do we clear the session and
// bounce to login. Skipped for:
//  - /auth/* requests, where 401 is a normal "bad credentials/OTP" form error
//    (the refresh call itself carries _isRefreshCall so it never recurses);
//  - requests opting out via { skipAuthRedirect: true } (e.g. the silent session
//    probe on app bootstrap, so anonymous visitors on public pages aren't kicked).
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status;
    const config = error?.config || {};
    const isAuthEndpoint = (config.url || '').includes('/auth/');
    const hadToken = !!localStorage.getItem('token');

    if (status === 401 && hadToken && !isAuthEndpoint && !config._retried) {
      // One refresh attempt, then replay the original request once.
      const newToken = await refreshAccessToken();
      if (newToken) {
        config._retried = true;
        config.headers = config.headers || {};
        config.headers.Authorization = `Bearer ${newToken}`;
        return api(config);
      }
      // Refresh failed: session is truly gone.
      localStorage.removeItem('token');
      if (!config.skipAuthRedirect && window.location.pathname !== '/login') {
        window.location.assign('/login?expired=1');
      }
    }
    return Promise.reject(error);
  }
);

// Turns an axios error into a human-readable message. Backend bean-validation
// failures come back as { error: "Invalid request", fields: [{field, message}] };
// without this, callers showing only `data.message` surface a useless "Invalid
// request" (or a wrong fallback) and the user never learns which field is wrong.
// Order: explicit message → joined per-field messages → network hint → fallback.
export const extractApiError = (err, fallback) => {
  const data = err?.response?.data;
  if (data?.message) return data.message;
  const fields = data?.fields;
  if (Array.isArray(fields) && fields.length) {
    const joined = fields.map((f) => f?.message).filter(Boolean).join(' ');
    if (joined) return joined;
  }
  if (err?.code === 'ERR_NETWORK') {
    return 'We could not connect to the server. Please try again in a moment.';
  }
  return fallback;
};

// Origin of the API (the gateway), without the trailing `/api`. The backend
// returns avatar URLs as gateway-relative paths like `/api/users/5/avatar`;
// resolveAssetUrl turns those into absolute URLs an <img> tag can load.
export const apiOrigin = API_URL.replace(/\/api\/?$/, '');

export const resolveAssetUrl = (path) => {
  if (!path) return null;
  if (/^https?:\/\//i.test(path)) return path;
  return `${apiOrigin}${path.startsWith('/') ? '' : '/'}${path}`;
};

export default api;
