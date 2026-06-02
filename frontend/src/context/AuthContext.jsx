import { createContext, useState, useEffect } from 'react';
import api from '../services/api';

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // On app load, check if there is a saved token
  useEffect(() => {
    const checkUser = async () => {
      const token = localStorage.getItem('token');
      if (token) {
        try {
          const response = await api.get('/users/me');
          setUser({ ...response.data, token });
        } catch (error) {
          console.error('Failed to fetch user:', error);
          setUser({ token });
        }
      }
      setLoading(false);
    };
    checkUser();
  }, []);

  const login = async (email, password) => {
    const response = await api.post('/auth/login', { email, password });
    const { token } = response.data; // Adjust according to your JSON response structure
    
    localStorage.setItem('token', token);
    try {
      const userResponse = await api.get('/users/me');
      setUser({ ...userResponse.data, token });
    } catch (error) {
      setUser({ token });
    }
  };

  const register = async (userData) => {
    const response = await api.post('/auth/register', userData);
    const { token } = response.data; // Adjust according to your JSON response structure

    localStorage.setItem('token', token);
    try {
      const userResponse = await api.get('/users/me');
      setUser({ ...userResponse.data, token });
    } catch (error) {
      setUser({ token });
    }
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