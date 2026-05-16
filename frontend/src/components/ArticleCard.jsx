import React from 'react';
import { Link } from 'react-router-dom';

export default function ArticleCard({ article }) {
  // Check if it's an offer or demand to change colors
  const isOffer = article.type === 'OFFER';
  
  const badgeColors = isOffer 
    ? 'bg-green-100 text-green-800 border-green-300' 
    : 'bg-blue-100 text-blue-800 border-blue-300';
    
  const priceDisplay = article.price === 0 ? 'Free' : `${article.price} €`;

  // Format the date (comes in ISO format from OpenSearch)
  const formattedDate = new Date(article.createdAt).toLocaleDateString('en-US', {
    day: '2-digit', month: 'short', year: 'numeric'
  });

  return (
    <Link to={`/catalog/${article.id}`} className="block h-full">
      <div className="bg-white rounded-xl shadow-sm hover:shadow-lg transition-shadow duration-300 border border-gray-100 overflow-hidden flex flex-col h-full">
        {/* Image section */}
        {article.imageBase64 ? (
          <img 
            src={article.imageBase64} 
            alt={article.title} 
            className="w-full h-48 object-cover border-b border-gray-100"
          />
        ) : (
          <div className="w-full h-48 bg-gray-50 flex items-center justify-center text-gray-300 border-b border-gray-100">
            <svg className="w-12 h-12 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
          </div>
        )}

        <div className="p-5 flex-grow">
          <div className="flex justify-between items-start mb-3">
            <span className={`text-xs font-bold px-3 py-1 rounded-full border ${badgeColors}`}>
              {isOffer ? 'OFFER' : 'DEMAND'}
            </span>
            <span className="text-gray-400 text-xs">{formattedDate}</span>
          </div>
          
          <h3 className="text-xl font-bold text-gray-800 mb-2 line-clamp-2">
            {article.title}
          </h3>
          
          <p className="text-gray-600 text-sm line-clamp-3 mb-4">
            {article.description}
          </p>
        </div>

        <div className="bg-gray-50 p-4 border-t border-gray-100 flex justify-between items-center mt-auto">
          <span className="bg-gray-200 text-gray-700 text-xs font-semibold px-2 py-1 rounded">
            {article.category}
          </span>
          <span className="font-extrabold text-lg text-gray-800">
            {priceDisplay}
          </span>
        </div>
      </div>
    </Link>
  );
}