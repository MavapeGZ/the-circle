import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { useContext } from 'react';
import { AuthContext } from '../context/AuthContext';
import Login from './Login';
import Register from './Register';

function App() {
  const { user, logout } = useContext(AuthContext);

  return (
    <BrowserRouter>
      {/* Navigation menu with Tailwind */}
      <nav className="bg-blue-600 p-4 text-white shadow-md flex gap-4 items-center">
        {/* Left side: Brand and main links */}
        <div className="flex gap-4 items-center">
          {/* Here we could use our logo from public/img/logo.png in the future */}
          <span className="font-extrabold text-xl tracking-wider">THE CIRCLE</span>
          <Link to="/" className="font-bold hover:text-blue-200 transition-colors ml-4">Home</Link>
          <Link to="/catalog" className="font-bold hover:text-blue-200 transition-colors">Catalog</Link>
        </div>

        {/* Right side: Authentication links */}
        <div className="flex gap-4 items-center">
          {user ? (
            // If the user is logged in, show the Logout button
            <button
              onClick={logout}
              className="font-bold bg-red-500 px-4 py-2 rounded hover:bg-red-600 transition-colors"
            >
              Logout
            </button>
          ) : (
            // If the user is NOT logged in, show Login and Register links
            <>
              <Link to="/login" className="font-bold hover:text-blue-200 transition-colors">Login</Link>
              <Link to="/register" className="font-bold bg-green-500 px-4 py-2 rounded hover:bg-green-600 transition-colors">Register</Link>
            </>
          )}
        </div>
      </nav>

      {/* Application routes */}
      <main className="p-8">
        <Routes>
          <Route path="/" element={
            <div className="text-center mt-10">
              <h1 className="text-4xl font-bold text-gray-800">Welcome to The Circle</h1>
              <p className="mt-4 text-gray-600 text-lg">React Router and Tailwind CSS are working perfectly.</p>
            </div>
          } />

          <Route path="/catalog" element={
            <div className="mt-10">
              <h2 className="text-3xl font-bold text-gray-800 border-b-2 border-blue-500 pb-2 inline-block">Solidary Catalog</h2>
              <p className="mt-4 text-gray-600">Here we will connect the endpoint of the search engine we made in the backend.</p>
            </div>
          } />

          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}

export default App;

