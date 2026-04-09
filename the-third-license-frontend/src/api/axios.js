import axios from 'axios';

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  // Send HttpOnly cookies on every cross-origin request
  withCredentials: true,
});

export default instance;
