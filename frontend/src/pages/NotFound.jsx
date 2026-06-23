import { Link } from 'react-router-dom';

// Catch-all for unknown routes. Without it React Router renders nothing and the
// page goes blank, which reads as a broken app rather than a missing page.
export default function NotFound() {
  return (
    <div className="max-w-xl mx-auto mt-24 p-8 text-center">
      <p className="text-7xl font-black text-blue-600">404</p>
      <h1 className="mt-4 text-2xl font-extrabold text-gray-900">Page not found</h1>
      <p className="mt-2 text-gray-600">
        The page you are looking for doesn’t exist or may have been moved.
      </p>
      <div className="mt-8 flex flex-wrap gap-3 justify-center">
        <Link
          to="/"
          className="px-6 py-3 rounded-full bg-blue-600 text-white font-bold shadow hover:bg-blue-700 transition"
        >
          Go home
        </Link>
        <Link
          to="/catalog"
          className="px-6 py-3 rounded-full bg-white text-blue-700 font-bold border border-blue-300 shadow-sm hover:bg-blue-50 transition"
        >
          Browse catalog
        </Link>
      </div>
    </div>
  );
}
