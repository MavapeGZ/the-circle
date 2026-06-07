import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { useContext } from 'react';
import { AuthContext } from '../context/AuthContext';
import Login from './Login';
import Register from './Register';
import Catalog from './Catalog';
import CreateArticle from './CreateArticle';
import ArticleDetail from './ArticleDetail';
import EditArticle from './EditArticle';
import Home from './Home';
import SignContract from './SignContract';
import MyContracts from './MyContracts';
import Settings from './Settings';

function App() {
  const { user, logout } = useContext(AuthContext);

  return (
    <BrowserRouter>
      {/* Navigation menu with Tailwind */}
      <nav className="bg-blue-600 p-4 text-white shadow-md flex justify-between items-center relative z-10">
        {/* Left side: Brand and main links */}
        <div className="flex gap-4 items-center">
          <span className="font-extrabold text-xl tracking-wider">THE CIRCLE</span>
          <Link to="/" className="font-bold hover:text-blue-200 transition-colors ml-4">Home</Link>
          <Link to="/catalog" className="font-bold hover:text-blue-200 transition-colors">Catalog</Link>
          {user && (
            <Link to="/create" className="font-bold hover:text-blue-200 transition-colors">Publish</Link>
          )}
          {user && (
            <Link to="/contracts" className="font-bold hover:text-blue-200 transition-colors">Contracts</Link>
          )}
        </div>

        {/* Right side: Authentication links */}
        <div className="flex gap-4 items-center">
          {user ? (
            <>
              {/* Setting for logged-in users */}
              <Link to="/settings" className="font-bold hover:text-blue-200 transition-colors mr-2">
                Settings
              </Link>
              <button
                onClick={logout}
                className="font-bold bg-red-500 px-4 py-2 rounded hover:bg-red-600 transition-colors shadow-sm"
              >
                Logout
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="font-bold hover:text-blue-200 transition-colors">Login</Link>
              <Link to="/register" className="font-bold bg-green-500 px-4 py-2 rounded hover:bg-green-600 transition-colors shadow-sm">Register</Link>
            </>
          )}
        </div>
      </nav>

      {/* Application routes */}
      <main className="flex-grow bg-gray-50">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/catalog" element={<Catalog />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/create" element={<CreateArticle />} />
          <Route path="/catalog/:id" element={<ArticleDetail />} />
          <Route path="/catalog/edit/:id" element={<EditArticle />} />
          <Route path="/contracts" element={<MyContracts />} />
          <Route path="/contracts/:contractId/sign" element={<SignContract />} />
          <Route path="/settings" element={<Settings />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}

export default App;