import { createContext, useState, useEffect } from 'react';
import api from '../services/api';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // On app load, check if there is a saved token
  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) {
      // Here you could decode the token or call a /me endpoint to get data
      setUser({ token }); 
    }
    setLoading(false);
  }, []);

  const login = async (email, password) => {
    const response = await api.post('/auth/login', { email, password });
    const { token } = response.data; // Adjust according to your JSON response structure
    
    localStorage.setItem('token', token);
    setUser({ token });
  };

  const register = async (userData) => {
    await api.post('/auth/register', userData);
    // After registering, optionally perform automatic login:
    await login(userData.email, userData.password);
  };

  const logout = () => {
    localStorage.removeItem('token');
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, register, logout, loading }}>
      {!loading && children}
    </AuthContext.Provider>
  );
};