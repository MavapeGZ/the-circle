import React from 'react';
import { Link } from 'react-router-dom';
import { resolveAssetUrl } from '../services/api';
import { ZONE_OPTIONS } from '../constants/zones';

function zoneLabel(zone) {
  return ZONE_OPTIONS.find((option) => option.value === zone)?.label || zone || 'Not shared';
}

function initials(name) {
  return (name || 'S')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
}

export default function ArticleCard({ article, sellerProfile }) {
  const renderBadge = () => {
    switch (article.productType) {
      case 'DONATION':
        return (
          <span className="bg-purple-100 text-purple-800 text-xs font-extrabold px-3 py-1 rounded-full border border-purple-300 shadow-sm">
            🎁 Donation — Free
          </span>
        );
      case 'DEMAND':
        return (
          <span className="bg-blue-100 text-blue-800 text-xs font-bold px-3 py-1 rounded-full border border-blue-300">
            DEMAND
          </span>
        );
      case 'SYMBOLIC_SALE':
        return (
          <span className="bg-green-100 text-green-800 text-xs font-bold px-3 py-1 rounded-full border border-green-300">
            SALE
          </span>
        );
      case 'SYMBOLIC_RENTAL':
        return (
          <span className="bg-green-100 text-green-800 text-xs font-bold px-3 py-1 rounded-full border border-green-300">
            RENTAL
          </span>
        );
      default:
        return null;
    }
  };
    
  const priceDisplay = article.price == null || article.price === 0 ? 'Free' : `${article.price} €`;
  const sellerName = sellerProfile?.displayName || `User ${article.authorId || article.id}`;
  const sellerInitials = initials(sellerProfile?.displayName || sellerName);
  const sellerZone = zoneLabel(sellerProfile?.approximateZone);

  // Format the date (comes in ISO format from OpenSearch)
  const formattedDate = new Date(article.createdAt).toLocaleDateString('en-US', {
    day: '2-digit', month: 'short', year: 'numeric'
  });

  return (
    <div className="bg-white rounded-xl shadow-sm hover:shadow-lg transition-shadow duration-300 border border-gray-100 overflow-hidden flex flex-col h-full">
      <div className="p-4 border-b border-gray-100 flex items-center justify-between gap-3">
        {article.authorId && sellerProfile?.publicId ? (
          <Link to={`/users/${sellerProfile.publicId}`} className="flex items-center gap-3 min-w-0 group">
            <div className="w-10 h-10 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center font-black overflow-hidden shrink-0">
              {sellerProfile?.avatarUrl ? (
                <img src={resolveAssetUrl(sellerProfile.avatarUrl)} alt={sellerName} className="h-full w-full object-cover" />
              ) : (
                <span>{sellerInitials}</span>
              )}
            </div>
            <div className="min-w-0">
              <p className="font-bold text-gray-900 truncate group-hover:text-blue-700">{sellerName}</p>
              <p className="text-xs text-gray-500 truncate">{sellerZone}</p>
            </div>
          </Link>
        ) : (
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-10 h-10 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center font-black overflow-hidden shrink-0">
              <span>{sellerInitials}</span>
            </div>
            <div className="min-w-0">
              <p className="font-bold text-gray-900 truncate">{sellerName}</p>
              <p className="text-xs text-gray-500 truncate">{sellerZone}</p>
            </div>
          </div>
        )}

        <span className="text-gray-400 text-xs whitespace-nowrap">{formattedDate}</span>
      </div>

      <Link to={`/catalog/${article.id}`} className="block flex-grow">
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
            <div className="flex gap-2 flex-wrap">
              {renderBadge()}
              <span className="bg-gray-100 text-gray-700 text-xs font-semibold px-3 py-1 rounded-full border border-gray-200">
                {zoneLabel(article.zone)}
              </span>
              {article.status === 'RESERVED' && (
                <span className="bg-yellow-100 text-yellow-800 text-xs font-bold px-3 py-1 rounded-full border border-yellow-300">
                  Reserved
                </span>
              )}
            </div>
          </div>

          <h3 className="text-xl font-bold text-gray-800 mb-2 line-clamp-2">
            {article.title}
          </h3>

          <p className="text-gray-600 text-sm line-clamp-3 mb-4">
            {article.description}
          </p>
        </div>
      </Link>

      <div className="bg-gray-50 p-4 border-t border-gray-100 flex justify-between items-center mt-auto">
        {article.category ? (
          <span className="bg-gray-200 text-gray-700 text-xs font-semibold px-2 py-1 rounded">
            {article.category}
          </span>
        ) : <span />}
        <span className="font-extrabold text-lg text-gray-800">
          {priceDisplay}
        </span>
      </div>
    </div>
  );
}