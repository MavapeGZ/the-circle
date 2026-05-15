import React from 'react';

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
    <div className="bg-white rounded-xl shadow-sm hover:shadow-lg transition-shadow duration-300 border border-gray-100 overflow-hidden flex flex-col h-full">
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
  );
}