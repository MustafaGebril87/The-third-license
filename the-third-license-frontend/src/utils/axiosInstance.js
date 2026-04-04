// src/utils/axiosInstance.js (or wherever it's defined)
import axios from 'axios';

const axiosInstance = axios.create({
  baseURL: 'http://localhost:8080/api',
   withCredentials: true, 
});

axiosInstance.interceptors.request.use(config => {
  const token = localStorage.getItem('token'); // ✅ changed from 'authToken' to 'token'
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  } else {
    console.warn("⚠️ No auth token found in localStorage");
  }
  return config;
});

export default axiosInstance;
