import axios from 'axios';

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  // Send HttpOnly cookies on every cross-origin request
  withCredentials: true,
});

// ── Silent token refresh ────────────────────────────────────────────────────
// Access tokens live 15 minutes. On a 401 we call /auth/refresh once (shared by
// all requests that failed at the same time) and retry. If refresh fails the
// session is over and AuthContext is notified so the UI logs out.

let refreshPromise = null;
let onSessionExpired = () => {};

export const setSessionExpiredHandler = (handler) => {
  onSessionExpired = handler;
};

instance.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;
    const isAuthCall = original?.url?.startsWith('/auth/');

    if (status !== 401 || !original || original._retried || isAuthCall) {
      return Promise.reject(error);
    }

    original._retried = true;
    try {
      refreshPromise = refreshPromise || instance.post('/auth/refresh');
      await refreshPromise;
    } catch (refreshError) {
      onSessionExpired();
      return Promise.reject(error);
    } finally {
      refreshPromise = null;
    }
    return instance(original);
  }
);

export default instance;
