import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';

function App() {
  return (
    <BrowserRouter>
      {/* Navigation menu with Tailwind */}
      <nav className="bg-blue-600 p-4 text-white shadow-md flex gap-4 items-center">
        {/* Here we could use our logo from public/img/logo.png in the future */}
        <span className="font-extrabold text-xl tracking-wider">THE CIRCLE</span>
        <Link to="/" className="font-bold hover:text-blue-200 transition-colors ml-4">Home</Link>
        <Link to="/catalog" className="font-bold hover:text-blue-200 transition-colors">Catalog</Link>
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
        </Routes>
      </main>
    </BrowserRouter>
  );
}

export default App;

