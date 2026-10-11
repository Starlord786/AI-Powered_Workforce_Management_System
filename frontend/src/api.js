import axios from 'axios';

const api = axios.create({ baseURL: 'http://localhost:8080' });

api.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('token');
  if (token) cfg.headers.Authorization = `Bearer ${token}`;
  return cfg;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    const isLogin = err.config?.url?.includes('/api/auth/login');
    if (err.response?.status === 401 && !isLogin) {
      localStorage.clear();
      window.location.hash = '#/login';
      window.location.reload();
    }
    return Promise.reject(err);
  }
);

export default api;
