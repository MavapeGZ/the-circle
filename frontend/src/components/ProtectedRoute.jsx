import { useContext } from 'react';
import { Navigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

// Guards routes that require an authenticated user. Logged-out visitors are
// redirected to the login screen, which shows an explanatory message based on
// the `authRequired` reason carried in the query string.
function ProtectedRoute({ children, reason = 'login-required' }) {
  const { user } = useContext(AuthContext);

  if (!user) {
    return <Navigate to={`/login?authRequired=${reason}`} replace />;
  }

  return children;
}

export default ProtectedRoute;
