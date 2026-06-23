import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

// Catch-all for unknown routes. Without it React Router renders nothing and the
// page goes blank, which reads as a broken app rather than a missing page.
export default function NotFound() {
  const { t } = useTranslation();
  return (
    <div className="max-w-xl mx-auto mt-24 p-8 text-center">
      <p className="text-7xl font-black text-indigo-600">404</p>
      <h1 className="mt-4 text-2xl font-extrabold text-gray-900">{t('notfound.title')}</h1>
      <p className="mt-2 text-gray-600">
        {t('notfound.text')}
      </p>
      <div className="mt-8 flex flex-wrap gap-3 justify-center">
        <Link
          to="/"
          className="px-6 py-3 rounded-full bg-indigo-600 text-white font-bold shadow hover:bg-indigo-700 transition"
        >
          {t('notfound.home')}
        </Link>
        <Link
          to="/catalog"
          className="px-6 py-3 rounded-full bg-white text-indigo-700 font-bold border border-indigo-300 shadow-sm hover:bg-indigo-50 transition"
        >
          {t('notfound.catalog')}
        </Link>
      </div>
    </div>
  );
}
