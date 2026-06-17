import { BrowserRouter, Routes, Route, Link, useNavigate } from 'react-router-dom';
import { useContext, useState } from 'react';
import { AuthContext } from '../context/AuthContext';
import Login from './Login';
import Register from './Register';
import ForgotPassword from './ForgotPassword';
import Catalog from './Catalog';
import CreateArticle from './CreateArticle';
import ArticleDetail from './ArticleDetail';
import EditArticle from './EditArticle';
import Home from './Home';
import SignContract from './SignContract';
import MyContracts from './MyContracts';
import ContractDetail from './ContractDetail';
import Checkout from './Checkout';
import PaymentReceipt from './PaymentReceipt';
import Settings from './Settings';
import ProfilePage from './ProfilePage';
import ProtectedRoute from '../components/ProtectedRoute';

// Navigation lives inside BrowserRouter so it can use useNavigate to redirect.
function NavBar() {
  const { user, logout } = useContext(AuthContext);
  const navigate = useNavigate();
  // Mobile menu toggle. On small screens the inline links would crowd into each
  // other (e.g. "Catalog" colliding with "Login"), so they collapse behind a
  // hamburger and stack vertically when opened.
  const [menuOpen, setMenuOpen] = useState(false);

  const closeMenu = () => setMenuOpen(false);

  const handleLogout = () => {
    closeMenu();
    logout();
    navigate('/');
  };

  const navLinkClass = 'font-bold hover:text-blue-200 transition-colors';

  // Shared link set, reused for the desktop row and the mobile dropdown.
  const mainLinks = (
    <>
      <Link to="/" onClick={closeMenu} className={navLinkClass}>Home</Link>
      <Link to="/catalog" onClick={closeMenu} className={navLinkClass}>Catalog</Link>
      {user && <Link to="/profile" onClick={closeMenu} className={navLinkClass}>Profile</Link>}
      {user && <Link to="/create" onClick={closeMenu} className={navLinkClass}>Publish</Link>}
      {user && <Link to="/contracts" onClick={closeMenu} className={navLinkClass}>Contracts</Link>}
    </>
  );

  const authLinks = user ? (
    <>
      <Link to="/settings" onClick={closeMenu} className={navLinkClass}>Settings</Link>
      <button
        onClick={handleLogout}
        className="font-bold bg-red-500 px-4 py-2 rounded hover:bg-red-600 transition-colors shadow-sm text-left"
      >
        Logout
      </button>
    </>
  ) : (
    <>
      <Link to="/login" onClick={closeMenu} className={navLinkClass}>Login</Link>
      <Link to="/register" onClick={closeMenu} className="font-bold bg-green-500 px-4 py-2 rounded hover:bg-green-600 transition-colors shadow-sm">Register</Link>
    </>
  );

  return (
    <nav className="bg-blue-600 text-white shadow-md relative z-10">
      <div className="p-4 flex justify-between items-center">
        <Link to="/" onClick={closeMenu} className="font-extrabold text-xl tracking-wider">THE CIRCLE</Link>

        {/* Desktop: inline links. Hidden below md, where the hamburger takes over. */}
        <div className="hidden md:flex gap-4 items-center">{mainLinks}</div>
        <div className="hidden md:flex gap-4 items-center">{authLinks}</div>

        {/* Mobile: hamburger toggle. */}
        <button
          type="button"
          aria-label="Toggle navigation menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
          className="md:hidden inline-flex items-center justify-center p-2 rounded hover:bg-blue-700 transition-colors"
        >
          <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            {menuOpen ? (
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            ) : (
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
            )}
          </svg>
        </button>
      </div>

      {/* Mobile: stacked dropdown, only when open. */}
      {menuOpen && (
        <div className="md:hidden px-4 pb-4 flex flex-col gap-3 border-t border-blue-500">
          <div className="flex flex-col gap-3 pt-3">{mainLinks}</div>
          <div className="flex flex-col gap-3 pt-3 border-t border-blue-500">{authLinks}</div>
        </div>
      )}
    </nav>
  );
}

function App() {
  return (
    <BrowserRouter>
      <NavBar />

      {/* Application routes */}
      <main className="flex-grow bg-gray-50">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/catalog" element={<Catalog />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route
            path="/create"
            element={
              <ProtectedRoute reason="publish-article">
                <CreateArticle />
              </ProtectedRoute>
            }
          />
          <Route path="/catalog/:id" element={<ArticleDetail />} />
          <Route
            path="/catalog/edit/:id"
            element={
              <ProtectedRoute reason="edit-article">
                <EditArticle />
              </ProtectedRoute>
            }
          />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/profile/:id" element={<ProfilePage />} />
          <Route path="/users/:id" element={<ProfilePage />} />
          <Route
            path="/contracts"
            element={
              <ProtectedRoute reason="view-contracts">
                <MyContracts />
              </ProtectedRoute>
            }
          />
          <Route
            path="/contracts/:contractId/sign"
            element={
              <ProtectedRoute reason="view-contracts">
                <SignContract />
              </ProtectedRoute>
            }
          />
          <Route
            path="/contracts/:contractId/checkout"
            element={
              <ProtectedRoute reason="view-contracts">
                <Checkout />
              </ProtectedRoute>
            }
          />
          <Route
            path="/contracts/:contractId/payments/:paymentId/receipt"
            element={
              <ProtectedRoute reason="view-contracts">
                <PaymentReceipt />
              </ProtectedRoute>
            }
          />
          <Route
            path="/contracts/:contractId/payments/:paymentId/failure"
            element={
              <ProtectedRoute reason="view-contracts">
                <PaymentReceipt />
              </ProtectedRoute>
            }
          />
          <Route
            path="/contracts/:contractId"
            element={
              <ProtectedRoute reason="view-contracts">
                <ContractDetail />
              </ProtectedRoute>
            }
          />
          <Route
            path="/settings"
            element={
              <ProtectedRoute reason="settings">
                <Settings />
              </ProtectedRoute>
            }
          />
        </Routes>
      </main>
    </BrowserRouter>
  );
}

export default App;