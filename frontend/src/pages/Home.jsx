import React, { useContext } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { AuthContext } from '../context/AuthContext';
import usePageTitle from '../hooks/usePageTitle';

// Shortcut cards in the white section. Beta users tried to click the old
// "how it works" cards, so now they really are links into the menu sections.
// Items needing a session route through the login wall with the proper reason
// (mirrors the gating used by the navbar / ProtectedRoute) when logged out.
function buildShortcuts(isAuthenticated) {
  const gated = (path, reason) =>
    isAuthenticated ? path : `/login?authRequired=${reason}&next=${path}`;

  return [
    {
      key: 'catalog',
      to: '/catalog',
      accent: 'text-sky-600 bg-sky-100',
      ring: 'hover:border-sky-300 hover:shadow-sky-100',
      icon: (
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
      ),
    },
    {
      key: 'publish',
      to: gated('/create', 'publish-article'),
      accent: 'text-emerald-600 bg-emerald-100',
      ring: 'hover:border-emerald-300 hover:shadow-emerald-100',
      icon: (
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 4v16m8-8H4" />
      ),
    },
    {
      key: 'contracts',
      to: gated('/contracts', 'view-contracts'),
      accent: 'text-amber-600 bg-amber-100',
      ring: 'hover:border-amber-300 hover:shadow-amber-100',
      icon: (
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
      ),
    },
    {
      key: 'messages',
      to: gated('/messages', 'view-messages'),
      accent: 'text-orange-600 bg-orange-100',
      ring: 'hover:border-orange-300 hover:shadow-orange-100',
      icon: (
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 8h2a2 2 0 012 2v6a2 2 0 01-2 2h-2v4l-4-4H9a1.994 1.994 0 01-1.414-.586m0 0L11 14h4a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2v4l.586-.586z" />
      ),
    },
    {
      key: 'profile',
      to: '/profile',
      accent: 'text-rose-600 bg-rose-100',
      ring: 'hover:border-rose-300 hover:shadow-rose-100',
      icon: (
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
      ),
    },
    {
      key: 'settings',
      to: gated('/settings', 'settings'),
      accent: 'text-slate-600 bg-slate-100',
      ring: 'hover:border-slate-300 hover:shadow-slate-100',
      icon: (
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
      ),
    },
  ];
}

function Home() {
  usePageTitle();
  const { t } = useTranslation();
  const { isAuthenticated } = useContext(AuthContext);
  const shortcuts = buildShortcuts(isAuthenticated);
  const publishTo = isAuthenticated
    ? '/create'
    : '/login?authRequired=publish-article&next=/create';

  return (
    <div className="flex flex-col min-h-[calc(100vh-72px)] bg-gray-50">

      {/* HERO SECTION — emerald/teal so it reads clearly apart from the blue navbar. */}
      <section className="relative overflow-hidden bg-gradient-to-br from-indigo-700 via-violet-600 to-purple-700 text-white py-20 px-4">
        {/* Soft decorative glow, keeps focus on the centered content. */}
        <div className="pointer-events-none absolute -top-24 -right-24 w-96 h-96 bg-white/10 rounded-full blur-3xl" />
        <div className="pointer-events-none absolute -bottom-24 -left-24 w-96 h-96 bg-fuchsia-400/20 rounded-full blur-3xl" />

        <div className="relative max-w-5xl mx-auto text-center">
          {/* Brand emblem (logo without letters) on a clean white disc. */}
          <img
            src="/img/logo.png"
            alt="The Circle"
            className="mx-auto mb-8 w-24 h-24 rounded-full bg-white p-3 shadow-xl ring-4 ring-white/30"
          />

          <h1 className="text-5xl md:text-6xl font-extrabold tracking-tight mb-6">
            {t('home.welcomeTo')} <span className="text-indigo-200">The Circle</span>
          </h1>
          <p className="text-xl md:text-2xl text-indigo-50/90 mb-10 max-w-3xl mx-auto font-light leading-relaxed">
            {t('home.subtitle')}
          </p>

          {/* Single, focused call to action. */}
          <Link
            to={publishTo}
            className="inline-block px-10 py-4 bg-amber-400 text-indigo-950 font-extrabold rounded-full shadow-lg hover:bg-amber-300 hover:scale-105 transition-all duration-300"
          >
            {t('home.cta')}
          </Link>
        </div>
      </section>

      {/* QUICK ACCESS — clickable shortcuts into the menu sections. */}
      <section className="py-20 px-4 max-w-6xl mx-auto flex-grow w-full">
        <div className="text-center mb-14">
          <h2 className="text-3xl font-extrabold text-gray-800 mb-4">{t('home.where')}</h2>
          <p className="text-gray-500 max-w-2xl mx-auto">{t('home.whereSub')}</p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-8">
          {shortcuts.map((s) => (
            <Link
              key={s.key}
              to={s.to}
              className={`group bg-white p-8 rounded-2xl shadow-sm border border-gray-100 transition-all hover:-translate-y-1 hover:shadow-xl ${s.ring} flex flex-col items-center text-center`}
            >
              <div className={`w-16 h-16 rounded-full flex items-center justify-center mb-6 ${s.accent}`}>
                <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  {s.icon}
                </svg>
              </div>
              <h3 className="text-xl font-bold text-gray-800 mb-3 inline-flex items-center gap-1.5">
                {t(`home.shortcut.${s.key}.title`)}
                <span className="text-gray-300 group-hover:text-gray-500 group-hover:translate-x-1 transition-all">→</span>
              </h3>
              <p className="text-gray-600 leading-relaxed">{t(`home.shortcut.${s.key}.text`)}</p>
            </Link>
          ))}
        </div>
      </section>

      {/* FOOTER SIMPLE */}
      <footer className="bg-gray-900 text-gray-400 py-10 mt-auto">
        <div className="max-w-6xl mx-auto px-4 flex flex-col items-center gap-5 text-center">
          {/* full-logo carries olive text — sits on a white pill so it reads on the dark bar. */}
          <img
            src="/img/full-logo.png"
            alt="The Circle"
            className="h-16 w-auto bg-white rounded-2xl px-5 py-3 shadow-sm"
          />
          <p className="text-sm">© {new Date().getFullYear()} The Circle. {t('home.footer.rights')}</p>
        </div>
      </footer>
    </div>
  );
}

export default Home;
