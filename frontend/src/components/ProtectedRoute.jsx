import { useContext } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

// Guards routes that require an authenticated user. Logged-out visitors are
// redirected to the login screen, which shows an explanatory message based on
// the `authRequired` reason and returns them to `next` after they sign in.
function ProtectedRoute({ children, reason }) {
  const { isAuthenticated } = useContext(AuthContext);
  const location = useLocation();

  if (!isAuthenticated) {
    const params = new URLSearchParams();
    if (reason) params.set('authRequired', reason);
    params.set('next', location.pathname + location.search);
    return <Navigate to={`/login?${params.toString()}`} replace />;
  }

  return children;
}

export default ProtectedRoute;
