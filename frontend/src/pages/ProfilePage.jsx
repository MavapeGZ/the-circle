import { useContext, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api, { resolveAssetUrl } from '../services/api';
import { AuthContext } from '../context/AuthContext';
import { usePreferences } from '../context/PreferencesContext';
import BadgeList from '../components/BadgeList';
import ReviewsSection from '../components/ReviewsSection';
import { ZONE_OPTIONS } from '../constants/zones';
import usePageTitle from '../hooks/usePageTitle';

function zoneLabel(zone, fallback) {
  return ZONE_OPTIONS.find((option) => option.value === zone)?.label || zone || fallback;
}

function initials(name) {
  return (name || 'U')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
}

export default function ProfilePage() {
  usePageTitle('title.profile');
  const { t } = useTranslation();
  const { formatDate } = usePreferences();
  const { id: profileId } = useParams();
  const { user } = useContext(AuthContext);

  // Profiles are addressed by opaque public id. The route param carries it for
  // other people's profiles; for my own (/profile) it comes from my session.
  const resolvedId = profileId || user?.publicId;
  const [profile, setProfile] = useState(null);
  const [allBadges, setAllBadges] = useState([]);
  const [loading, setLoading] = useState(Boolean(resolvedId));
  const [error, setError] = useState('');

  useEffect(() => {
    let mounted = true;

    const fetchProfile = async () => {
      if (!resolvedId) {
        setLoading(false);
        return;
      }

      setLoading(true);
      setError('');

      try {
        // Fetch the profile and the full badge catalogue together. The catalogue
        // lets us show every badge (dimmed until earned), not just earned ones.
        const [{ data }, catalogue] = await Promise.all([
          api.get(`/users/by-public-id/${resolvedId}`),
          api.get('/gamification/badges').then((r) => r.data).catch(() => []),
        ]);
        if (mounted) {
          setProfile(data);
          setAllBadges(catalogue);
        }
      } catch (err) {
        if (mounted) {
          const status = err?.response?.status;
          setError(status === 404 ? t('profile.notFound') : t('profile.loadError'));
        }
      } finally {
        if (mounted) setLoading(false);
      }
    };

    fetchProfile();

    return () => {
      mounted = false;
    };
  }, [resolvedId]);

  const isOwnProfile = user && profile && String(user.id) === String(profile.id);

  if (!user && !profileId) {
    return (
      <div className="max-w-3xl mx-auto mt-16 p-4 text-center">
        <h1 className="text-3xl font-extrabold text-gray-900 mb-4">{t('profile.yourTitle')}</h1>
        <p className="text-gray-600 mb-6">{t('profile.signInPrompt')}</p>
        <Link to="/login" className="inline-flex px-6 py-3 rounded-full bg-indigo-600 text-white font-bold shadow hover:bg-indigo-700 transition-colors">
          {t('profile.goLogin')}
        </Link>
      </div>
    );
  }

  if (loading) {
    return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">{t('profile.loading')}</div>;
  }

  if (error) {
    return <div className="text-center mt-20 text-xl text-red-600 font-bold">{error}</div>;
  }

  if (!profile) return null;

  const profileInitials = initials(profile.displayName);

  // Merge the earned badges into the full catalogue so every badge is shown:
  // earned ones keep their award date, the rest render dimmed as "not earned yet".
  const earnedByCode = new Map((profile.badges || []).map((b) => [b.code, b]));
  const earnedCount = profile.badges?.length || 0;
  const mergedBadges = (allBadges.length ? allBadges : profile.badges || []).map((badge) => {
    const earned = earnedByCode.get(badge.code);
    return { ...badge, earned: Boolean(earned), earnedAt: earned?.earnedAt || null };
  });

  return (
    <div className="max-w-5xl mx-auto mt-8 p-4 space-y-6">
      <div className="rounded-3xl bg-gradient-to-br from-slate-900 via-indigo-900 to-indigo-900 p-6 text-white shadow-2xl overflow-hidden relative">
        <div className="absolute inset-0 opacity-15 bg-[radial-gradient(circle_at_top_left,_white,_transparent_35%),radial-gradient(circle_at_bottom_right,_white,_transparent_30%)]" />
        <div className="relative flex flex-col lg:flex-row lg:items-center gap-6">
          <div className="h-24 w-24 rounded-3xl bg-white/15 border border-white/20 flex items-center justify-center text-3xl font-black overflow-hidden shrink-0">
            {profile.avatarUrl ? (
              <img src={resolveAssetUrl(profile.avatarUrl)} alt={profile.displayName} className="h-full w-full object-cover" />
            ) : (
              <span>{profileInitials}</span>
            )}
          </div>

          <div className="flex-1 space-y-3">
            <div>
              <p className="text-indigo-200 text-sm font-semibold uppercase tracking-[0.25em]">{t('profile.public')}</p>
              <h1 className="text-4xl font-extrabold mt-1">{profile.displayName}</h1>
            </div>

            <div className="flex flex-wrap gap-3 text-sm text-indigo-50">
              <span className="rounded-full bg-white/10 px-3 py-1 border border-white/10">{t('profile.zone', { zone: zoneLabel(profile.approximateZone, t('common.notShared')) })}</span>
              <span className="rounded-full bg-white/10 px-3 py-1 border border-white/10">{t('profile.points', { points: profile.points })}</span>
              <span className="rounded-full bg-white/10 px-3 py-1 border border-white/10">{profile.memberSince ? t('profile.memberSince', { date: formatDate(profile.memberSince) }) : t('profile.unknown')}</span>
            </div>

            {isOwnProfile && (
              <div className="flex gap-3 pt-2">
                <Link to="/settings" className="inline-flex items-center rounded-full bg-white text-indigo-900 px-4 py-2 font-bold shadow hover:bg-indigo-50 transition-colors">
                  {t('profile.editSettings')}
                </Link>
              </div>
            )}
          </div>
        </div>
      </div>

      <section className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <article className="lg:col-span-2 bg-white rounded-3xl shadow-lg border border-gray-100 p-6">
          <div className="flex items-center justify-between gap-4 mb-5">
            <div>
              <h2 className="text-2xl font-extrabold text-gray-900">{t('profile.badges')}</h2>
              <p className="text-sm text-gray-500">{t('profile.badgesHelp')}</p>
            </div>
            <span className="flex flex-col items-center justify-center shrink-0 rounded-2xl bg-gradient-to-br from-indigo-50 to-indigo-100 border border-indigo-100 px-4 py-2 text-center shadow-sm">
              <span className="text-2xl font-extrabold leading-none text-indigo-700">{earnedCount}</span>
              <span className="mt-0.5 text-[11px] font-semibold uppercase tracking-wide text-indigo-500">{t('profile.earned')}</span>
            </span>
          </div>

          <BadgeList badges={mergedBadges} emptyMessage={t('profile.noBadges')} />
        </article>

        <aside className="bg-white rounded-3xl shadow-lg border border-gray-100 p-6 space-y-5">
          <div>
            <h2 className="text-lg font-bold text-gray-900 mb-2">{t('profile.about')}</h2>
            <dl className="space-y-3 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-gray-500">{t('profile.aboutZone')}</dt>
                <dd className="font-semibold text-gray-900 text-right">{zoneLabel(profile.approximateZone, t('common.notShared'))}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-gray-500">{t('profile.aboutMemberSince')}</dt>
                <dd className="font-semibold text-gray-900 text-right">
                  {profile.memberSince ? formatDate(profile.memberSince) : t('profile.unknown')}
                </dd>
              </div>
            </dl>
          </div>

          {isOwnProfile && (
            <div className="rounded-2xl bg-indigo-50 border border-indigo-100 p-4 text-sm text-indigo-900">
              <p className="font-bold mb-1">{t('profile.ownProfileTitle')}</p>
              <p>{t('profile.ownProfileText')}</p>
            </div>
          )}
        </aside>
      </section>

      <ReviewsSection userId={profile.id} average={profile.reviewAverage} count={profile.reviewCount} />
    </div>
  );
}