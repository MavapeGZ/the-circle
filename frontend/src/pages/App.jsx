import { BrowserRouter, Routes, Route, Link, useNavigate } from 'react-router-dom';
import { useContext, useState, useEffect } from 'react';
import { AuthContext } from '../context/AuthContext';
import api, { resolveAssetUrl } from '../services/api';
import { countContractsNeedingAction } from '../utils/contractAction';
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
import Messages from './Messages';
import Checkout from './Checkout';
import PaymentReceipt from './PaymentReceipt';
import Settings from './Settings';
import ProfilePage from './ProfilePage';
import NotFound from './NotFound';
import ProtectedRoute from '../components/ProtectedRoute';

// Navigation lives inside BrowserRouter so it can use useNavigate to redirect.
function NavBar() {
  const { user, logout, contractsRefreshNonce } = useContext(AuthContext);
  const navigate = useNavigate();
  // Mobile menu toggle. On small screens the inline links would crowd into each
  // other (e.g. "Catalog" colliding with "Login"), so they collapse behind a
  // hamburger and stack vertically when opened.
  const [menuOpen, setMenuOpen] = useState(false);

  // Total unread chat messages, polled so the navbar badge stays current while the
  // user is on any page. Same red pill as an unread conversation in the inbox.
  const [unreadTotal, setUnreadTotal] = useState(0);
  useEffect(() => {
    if (!user?.id) { setUnreadTotal(0); return undefined; }
    let active = true;
    const load = async () => {
      try {
        const { data } = await api.get('/chat/conversations');
        if (!active) return;
        setUnreadTotal((data || []).reduce((sum, c) => sum + (c.unreadCount || 0), 0));
      } catch { /* keep the previous count on transient errors */ }
    };
    load();
    const t = setInterval(load, 15000);
    return () => { active = false; clearInterval(t); };
  }, [user?.id]);

  // Contracts that need my action (sign / confirm delivery / settle deposit).
  // Same red pill as the chat badge; new or changed contracts surface here.
  const [contractsPending, setContractsPending] = useState(0);
  useEffect(() => {
    if (!user?.id) { setContractsPending(0); return undefined; }
    let active = true;
    const load = async () => {
      try {
        const { data } = await api.get(`/contracts/user/${user.id}`);
        if (!active) return;
        setContractsPending(countContractsNeedingAction(data || [], user.id));
      } catch { /* keep the previous count on transient errors */ }
    };
    load();
    const t = setInterval(load, 15000);
    return () => { active = false; clearInterval(t); };
  }, [user?.id, contractsRefreshNonce]);

  const closeMenu = () => setMenuOpen(false);

  const handleLogout = async () => {
    closeMenu();
    await logout();
    navigate('/');
  };

  const navLinkClass = 'font-bold hover:text-indigo-200 transition-colors';

  // Initials shown when the user has no profile picture yet.
  const userInitials = user
    ? `${user.firstName || ''} ${user.lastName || ''}`
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0].toUpperCase())
        .join('') || 'U'
    : '';

  // Shared link set, reused for the desktop row and the mobile dropdown.
  const mainLinks = (
    <>
      <Link to="/" onClick={closeMenu} className={navLinkClass}>Home</Link>
      <Link to="/catalog" onClick={closeMenu} className={navLinkClass}>Catalog</Link>
      {user && <Link to="/create" onClick={closeMenu} className={navLinkClass}>Publish</Link>}
      {user && (
        <Link to="/contracts" onClick={closeMenu} className={`${navLinkClass} inline-flex items-center gap-1.5`}>
          Contracts
          {contractsPending > 0 && (
            <span className="bg-red-500 text-white text-xs font-bold rounded-full px-2 py-0.5 leading-none">
              {contractsPending > 99 ? '99+' : contractsPending}
            </span>
          )}
        </Link>
      )}
      {user && (
        <Link to="/messages" onClick={closeMenu} className={`${navLinkClass} inline-flex items-center gap-1.5`}>
          Messages
          {unreadTotal > 0 && (
            <span className="bg-red-500 text-white text-xs font-bold rounded-full px-2 py-0.5 leading-none">
              {unreadTotal > 99 ? '99+' : unreadTotal}
            </span>
          )}
        </Link>
      )}
    </>
  );

  const authLinks = user ? (
    <>
      <Link to="/profile" onClick={closeMenu} aria-label="Profile" title="Profile" className="flex items-center gap-2">
        <span className="w-9 h-9 rounded-full bg-indigo-100 text-indigo-800 flex items-center justify-center text-sm font-black overflow-hidden shrink-0 ring-2 ring-white/70 hover:ring-white transition-colors">
          {user.avatarUrl ? (
            <img src={resolveAssetUrl(user.avatarUrl)} alt="Profile" className="h-full w-full object-cover" />
          ) : (
            <span>{userInitials}</span>
          )}
        </span>
        <span className="md:hidden font-bold">Profile</span>
      </Link>
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
      <Link to="/register" onClick={closeMenu} className="font-bold bg-amber-400 text-indigo-950 px-4 py-2 rounded hover:bg-amber-300 transition-colors shadow-sm">Register</Link>
    </>
  );

  return (
    <nav className="bg-indigo-800 text-white shadow-md relative z-10">
      <div className="p-4 flex justify-between items-center">
        <Link to="/" onClick={closeMenu} className="flex items-center gap-2.5 font-extrabold text-xl tracking-wider">
          <img src="/img/logo.png" alt="" aria-hidden="true" className="h-9 w-9 rounded-full bg-white p-0.5 shadow-sm" />
          THE CIRCLE
        </Link>

        {/* Desktop: inline links. Hidden below md, where the hamburger takes over. */}
        <div className="hidden md:flex gap-4 items-center">{mainLinks}</div>
        <div className="hidden md:flex gap-4 items-center">{authLinks}</div>

        {/* Mobile: hamburger toggle. */}
        <button
          type="button"
          aria-label="Toggle navigation menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
          className="md:hidden inline-flex items-center justify-center p-2 rounded hover:bg-indigo-900 transition-colors"
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
        <div className="md:hidden px-4 pb-4 flex flex-col gap-3 border-t border-indigo-700">
          <div className="flex flex-col gap-3 pt-3">{mainLinks}</div>
          <div className="flex flex-col gap-3 pt-3 border-t border-indigo-700">{authLinks}</div>
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
            path="/messages"
            element={
              <ProtectedRoute reason="view-messages">
                <Messages />
              </ProtectedRoute>
            }
          />
          <Route
            path="/messages/:conversationId"
            element={
              <ProtectedRoute reason="view-messages">
                <Messages />
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
          {/* Catch-all: any unmatched path renders the 404 page instead of blank. */}
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}

export default App;