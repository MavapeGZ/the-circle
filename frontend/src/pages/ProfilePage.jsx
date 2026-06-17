import { useContext, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import api, { resolveAssetUrl } from '../services/api';
import { AuthContext } from '../context/AuthContext';
import BadgeList from '../components/BadgeList';
import { ZONE_OPTIONS } from '../constants/zones';

function zoneLabel(zone) {
  return ZONE_OPTIONS.find((option) => option.value === zone)?.label || zone || 'Not shared';
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
  const { id: profileId } = useParams();
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);

  const resolvedId = profileId || user?.id;
  const [profile, setProfile] = useState(null);
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
        const { data } = await api.get(`/users/${resolvedId}`);
        if (mounted) setProfile(data);
      } catch (err) {
        if (mounted) {
          const status = err?.response?.status;
          setError(status === 404 ? 'User not found.' : 'Could not load profile.');
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
        <h1 className="text-3xl font-extrabold text-gray-900 mb-4">Your profile</h1>
        <p className="text-gray-600 mb-6">Sign in to view and manage your own public profile.</p>
        <Link to="/login" className="inline-flex px-6 py-3 rounded-full bg-blue-600 text-white font-bold shadow hover:bg-blue-700 transition-colors">
          Go to login
        </Link>
      </div>
    );
  }

  if (loading) {
    return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">Loading profile...</div>;
  }

  if (error) {
    return <div className="text-center mt-20 text-xl text-red-600 font-bold">{error}</div>;
  }

  if (!profile) return null;

  const profileInitials = initials(profile.displayName);

  return (
    <div className="max-w-5xl mx-auto mt-8 p-4 space-y-6">
      <div className="rounded-3xl bg-gradient-to-br from-slate-900 via-blue-900 to-indigo-900 p-6 text-white shadow-2xl overflow-hidden relative">
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
              <p className="text-blue-200 text-sm font-semibold uppercase tracking-[0.25em]">Public profile</p>
              <h1 className="text-4xl font-extrabold mt-1">{profile.displayName}</h1>
            </div>

            <div className="flex flex-wrap gap-3 text-sm text-blue-50">
              <span className="rounded-full bg-white/10 px-3 py-1 border border-white/10">Zone: {zoneLabel(profile.approximateZone)}</span>
              <span className="rounded-full bg-white/10 px-3 py-1 border border-white/10">Points: {profile.points}</span>
              <span className="rounded-full bg-white/10 px-3 py-1 border border-white/10">Member since {profile.memberSince ? new Date(profile.memberSince).toLocaleDateString() : 'Unknown'}</span>
            </div>

            {isOwnProfile && (
              <div className="flex gap-3 pt-2">
                <Link to="/settings" className="inline-flex items-center rounded-full bg-white text-blue-900 px-4 py-2 font-bold shadow hover:bg-blue-50 transition-colors">
                  Edit settings
                </Link>
                <button
                  onClick={() => navigate('/settings', { state: { tab: 'profile' } })}
                  className="inline-flex items-center rounded-full border border-white/25 px-4 py-2 font-bold text-white hover:bg-white/10 transition-colors"
                >
                  Open profile form
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      <section className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <article className="lg:col-span-2 bg-white rounded-3xl shadow-lg border border-gray-100 p-6">
          <div className="flex items-center justify-between gap-4 mb-5">
            <div>
              <h2 className="text-2xl font-extrabold text-gray-900">Badges</h2>
              <p className="text-sm text-gray-500">Hover a badge to see its description and the date it was earned.</p>
            </div>
            <span className="rounded-full bg-gray-100 px-3 py-1 text-sm font-semibold text-gray-700">{profile.badges?.length || 0} earned</span>
          </div>

          <BadgeList badges={profile.badges} emptyMessage="This user has not earned any badges yet." />
        </article>

        <aside className="bg-white rounded-3xl shadow-lg border border-gray-100 p-6 space-y-5">
          <div>
            <h2 className="text-lg font-bold text-gray-900 mb-2">About</h2>
            <dl className="space-y-3 text-sm">
              <div className="flex justify-between gap-4">
                <dt className="text-gray-500">Profile ID</dt>
                <dd className="font-semibold text-gray-900">{profile.id}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-gray-500">Zone</dt>
                <dd className="font-semibold text-gray-900 text-right">{zoneLabel(profile.approximateZone)}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-gray-500">Points</dt>
                <dd className="font-semibold text-gray-900">{profile.points}</dd>
              </div>
              <div className="flex justify-between gap-4">
                <dt className="text-gray-500">Member since</dt>
                <dd className="font-semibold text-gray-900 text-right">
                  {profile.memberSince ? new Date(profile.memberSince).toLocaleDateString() : 'Unknown'}
                </dd>
              </div>
            </dl>
          </div>

          {isOwnProfile && (
            <div className="rounded-2xl bg-blue-50 border border-blue-100 p-4 text-sm text-blue-900">
              <p className="font-bold mb-1">This is your public profile</p>
              <p>Only your display name, zone and gamification info are shown here. Private contact details stay in Settings.</p>
            </div>
          )}
        </aside>
      </section>
    </div>
  );
}