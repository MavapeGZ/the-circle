import React from 'react';
import { Link } from 'react-router-dom';

function Home() {
  return (
    <div className="flex flex-col min-h-[calc(100vh-72px)] bg-gray-50">
      
      {/* HERO SECTION */}
      <section className="bg-gradient-to-br from-blue-700 via-blue-600 to-indigo-800 text-white py-20 px-4 shadow-inner">
        <div className="max-w-5xl mx-auto text-center">
          <h1 className="text-5xl md:text-6xl font-extrabold tracking-tight mb-6">
            Welcome to <span className="text-blue-200">The Circle</span>
          </h1>
          <p className="text-xl md:text-2xl text-blue-100 mb-10 max-w-3xl mx-auto font-light leading-relaxed">
            A solidary community where you can give a second life to your belongings or find what you need. Share, connect, and build a more sustainable world.
          </p>
          
          <div className="flex flex-col sm:flex-row justify-center items-center gap-4">
            <Link 
              to="/catalog" 
              className="w-full sm:w-auto px-8 py-4 bg-white text-blue-700 font-bold rounded-full shadow-lg hover:bg-blue-50 hover:scale-105 transition-all duration-300"
            >
              Browse Catalog
            </Link>
            <Link 
              to="/create" 
              className="w-full sm:w-auto px-8 py-4 bg-transparent border-2 border-white text-white font-bold rounded-full hover:bg-white hover:text-blue-700 hover:scale-105 transition-all duration-300"
            >
              Publish an Article
            </Link>
          </div>
        </div>
      </section>

      {/* FEATURES SECTION (HOW IT WORKS) */}
      <section className="py-20 px-4 max-w-6xl mx-auto flex-grow">
        <div className="text-center mb-16">
          <h2 className="text-3xl font-extrabold text-gray-800 mb-4">How it works</h2>
          <p className="text-gray-500 max-w-2xl mx-auto">Join our circular economy platform in three simple steps.</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-10">
          
          {/* Card 1: Offer */}
          <div className="bg-white p-8 rounded-2xl shadow-sm border border-gray-100 hover:shadow-xl transition-shadow text-center flex flex-col items-center">
            <div className="w-16 h-16 bg-green-100 text-green-600 rounded-full flex items-center justify-center mb-6">
              <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 8v13m0-13V6a2 2 0 112 2h-2zm0 0V5.5A2.5 2.5 0 109.5 8H12zm-7 4h14M5 12a2 2 0 110-4h14a2 2 0 110 4M5 12v7a2 2 0 002 2h10a2 2 0 002-2v-7" />
              </svg>
            </div>
            <h3 className="text-xl font-bold text-gray-800 mb-3">1. Offer what you don't use</h3>
            <p className="text-gray-600 leading-relaxed">
              Have a bike, books, or clothes gathering dust? Publish them as a free donation or a symbolic rental.
            </p>
          </div>

          {/* Card 2: Demand */}
          <div className="bg-white p-8 rounded-2xl shadow-sm border border-gray-100 hover:shadow-xl transition-shadow text-center flex flex-col items-center">
            <div className="w-16 h-16 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center mb-6">
              <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>
            <h3 className="text-xl font-bold text-gray-800 mb-3">2. Find what you need</h3>
            <p className="text-gray-600 leading-relaxed">
              Search our catalog for items offered by the community. If you can't find it, publish a Demand.
            </p>
          </div>

          {/* Card 3: Connect */}
          <div className="bg-white p-8 rounded-2xl shadow-sm border border-gray-100 hover:shadow-xl transition-shadow text-center flex flex-col items-center">
            <div className="w-16 h-16 bg-purple-100 text-purple-600 rounded-full flex items-center justify-center mb-6">
              <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 8h2a2 2 0 012 2v6a2 2 0 01-2 2h-2v4l-4-4H9a1.994 1.994 0 01-1.414-.586m0 0L11 14h4a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2v4l.586-.586z" />
              </svg>
            </div>
            <h3 className="text-xl font-bold text-gray-800 mb-3">3. Connect & Meet</h3>
            <p className="text-gray-600 leading-relaxed">
              Contact the author securely. Agree on a meeting point to hand over the item.
            </p>
          </div>

        </div>
      </section>

      {/* FOOTER SIMPLE */}
      <footer className="bg-gray-800 text-gray-400 py-8 text-center mt-auto">
        <p>© {new Date().getFullYear()} The Circle. Master's Final Project.</p>
      </footer>
    </div>
  );
}

export default Home;