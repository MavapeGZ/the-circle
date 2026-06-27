import { useState, useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import api from '../services/api';
import { AuthContext } from '../context/AuthContext';
import { usePreferences } from '../context/PreferencesContext';
import { contractTypeLabel } from '../utils/contractType';
import { contractActionRequired } from '../utils/contractAction';
import usePageTitle from '../hooks/usePageTitle';

function MyContracts() {
  usePageTitle('title.contracts');
  const { t } = useTranslation();
  const { formatDate, formatPrice } = usePreferences();
  const navigate = useNavigate();
  const { refreshContracts } = useContext(AuthContext);

  const [me, setMe] = useState(null);
  const [contracts, setContracts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState('');
  const [itemTitles, setItemTitles] = useState({});
  const [userNames, setUserNames] = useState({});

  const loadContracts = async (userId) => {
    const res = await api.get(`/contracts/user/${userId}`);
    setContracts(res.data);
  };

  useEffect(() => {
    const init = async () => {
      try {
        const meRes = await api.get('/users/me');
        setMe(meRes.data);
        await loadContracts(meRes.data.id);
      } catch {
        setError(t('mc.loadError'));
      } finally {
        setLoading(false);
      }
    };
    init();
  }, []);

  // Resolve item titles and user names so each row shows people and products,
  // not raw ids. Each id is fetched once and cached.
  useEffect(() => {
    if (contracts.length === 0) return;

    const itemIds = [...new Set(contracts.map((c) => c.itemId).filter(Boolean))];
    itemIds.forEach((id) => {
      if (itemTitles[id] !== undefined) return;
      api.get(`/catalog/articles/${id}`)
        .then((r) => setItemTitles((prev) => ({ ...prev, [id]: r.data?.title || id })))
        .catch(() => setItemTitles((prev) => ({ ...prev, [id]: id })));
    });

    const userIds = [...new Set(contracts.flatMap((c) => [c.ownerId, c.receiverId]).filter(Boolean))];
    userIds.forEach((id) => {
      if (userNames[id] !== undefined) return;
      api.get(`/users/${id}`)
        .then((r) => setUserNames((prev) => ({ ...prev, [id]: r.data?.displayName || `User ${id}` })))
        .catch(() => setUserNames((prev) => ({ ...prev, [id]: `User ${id}` })));
    });
  }, [contracts]); // eslint-disable-line react-hooks/exhaustive-deps

  const isOwner = (c) => me != null && String(me.id) === String(c.ownerId);
  const isReceiver = (c) => me != null && String(me.id) === String(c.receiverId);
  // My role in this contract, or null if I am neither party.
  const myRole = (c) => (isOwner(c) ? 'OWNER' : isReceiver(c) ? 'RECEIVER' : null);
  // True when it is still my turn to sign (regardless of who signed first).
  const iNeedToSign = (c) => {
    if (c.status === 'ACTIVE' || c.status === 'DELIVERED' || c.status === 'COMPLETED') return false;
    if (isOwner(c)) return !c.ownerSignedAt;
    if (isReceiver(c)) return !c.receiverSignedAt;
    return false;
  };

  const goSign = (c) => {
    navigate(`/contracts/${c.id}/sign`, {
      state: { contract: c, signerEmail: me.email, role: myRole(c), from: '/contracts' },
    });
  };

  const settleGuarantee = async (c, action) => {
    setBusyId(c.id);
    setError('');
    try {
      await api.post(`/contracts/${c.id}/guarantee/${action}`);
      await loadContracts(me.id);
      refreshContracts();
    } catch {
      setError(t('mc.depositError'));
    } finally {
      setBusyId(null);
    }
  };

  const downloadSigned = async (c) => {
    if (!c.storedContractId) return;
    try {
      const res = await api.get(`/contracts/joint-rental/download/${c.storedContractId}`, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data);
      const a = document.createElement('a');
      a.href = url;
      a.download = `contract-${c.storedContractId}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setError(t('mc.downloadError'));
    }
  };

  const statusLabel = (c) => {
    if (c.status === 'ACTIVE') return { text: t('mc.status.active'), cls: 'bg-green-100 text-green-800' };
    if (c.status === 'DELIVERED') return { text: t('mc.status.delivered'), cls: 'bg-emerald-100 text-emerald-800' };
    if (c.status === 'COMPLETED') return { text: t('mc.status.completed', { status: c.guaranteeStatus?.toLowerCase() }), cls: 'bg-gray-200 text-gray-700' };
    if (iNeedToSign(c)) return { text: t('mc.status.awaitingYou'), cls: 'bg-yellow-100 text-yellow-800' };
    return { text: t('mc.status.awaitingOther'), cls: 'bg-indigo-100 text-indigo-800' };
  };

  if (loading) return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">{t('mc.loading')}</div>;

  return (
    <div className="max-w-4xl mx-auto mt-8 p-4">
      <h1 className="text-3xl font-extrabold text-gray-900 mb-6">{t('mc.title')}</h1>

      {error && (
        <div className="p-4 mb-6 rounded-lg text-sm font-bold bg-red-100 text-red-700">{error}</div>
      )}

      {contracts.length === 0 ? (
        <p className="text-gray-500">{t('mc.none')}</p>
      ) : (
        <ul className="space-y-4">
          {contracts.map((c) => {
            const label = statusLabel(c);
            const mineAsOwner = isOwner(c);
            const canSign = iNeedToSign(c);
            const counterpartId = mineAsOwner ? c.receiverId : c.ownerId;
            const canSettle = mineAsOwner && c.status === 'ACTIVE' && c.guaranteeStatus === 'DEPOSITED';
            const needsAction = contractActionRequired(c, me?.id);
            return (
              <li key={c.id} className="bg-white p-5 rounded-2xl shadow border border-gray-100">
                <div className="flex justify-between items-start gap-4">
                  <div className="text-sm text-gray-600 space-y-1">
                    <p className="font-bold text-gray-900 text-base flex items-center gap-2">
                      {needsAction && (
                        <span className="bg-red-500 text-white text-[10px] font-bold rounded-full px-2 py-0.5 leading-none uppercase tracking-wide">
                          {t('mc.actionRequired')}
                        </span>
                      )}
                      <span>{contractTypeLabel(c.type, t)} · {mineAsOwner ? t('mc.youAreOwner') : t('mc.youAreReceiver')}</span>
                    </p>
                    <p>{t('mc.item', { title: itemTitles[c.itemId] || c.itemId })}</p>
                    <p>{t('mc.with', { name: userNames[counterpartId] || counterpartId })}</p>
                    <p>{t('mc.created', { date: c.createdAt ? formatDate(c.createdAt, { dateStyle: 'medium', timeStyle: 'short' }) : '—' })}</p>
                    {c.guaranteeStatus && c.guaranteeStatus !== 'NONE' && (
                      <p>{t('mc.deposit', { amount: formatPrice(c.guaranteeAmount), status: c.guaranteeStatus.toLowerCase() })}</p>
                    )}
                  </div>
                  <span className={`text-xs font-bold px-3 py-1 rounded-full whitespace-nowrap ${label.cls}`}>
                    {label.text}
                  </span>
                </div>

                <div className="flex flex-wrap gap-3 mt-4 justify-end">
                  {canSign && (
                    <button
                      onClick={() => goSign(c)}
                      className="px-6 py-2 bg-indigo-600 text-white font-bold rounded shadow hover:bg-indigo-700 transition"
                    >
                      {t('mc.sign')}
                    </button>
                  )}
                  {canSettle && (
                    <>
                      <button
                        onClick={() => settleGuarantee(c, 'release')}
                        disabled={busyId === c.id}
                        className="px-5 py-2 bg-green-600 text-white font-bold rounded shadow hover:bg-green-700 transition disabled:opacity-50"
                      >
                        {t('mc.releaseDeposit')}
                      </button>
                      <button
                        onClick={() => settleGuarantee(c, 'claim')}
                        disabled={busyId === c.id}
                        className="px-5 py-2 bg-red-600 text-white font-bold rounded shadow hover:bg-red-700 transition disabled:opacity-50"
                      >
                        {t('mc.claimDeposit')}
                      </button>
                    </>
                  )}
                  {c.storedContractId && (
                    <>
                      <button
                        onClick={() => navigate(`/contracts/${c.id}`)}
                        className="px-5 py-2 bg-indigo-50 text-indigo-700 font-bold rounded shadow hover:bg-indigo-100 transition"
                      >
                        {t('mc.viewDetail')}
                      </button>
                      <button
                        onClick={() => downloadSigned(c)}
                        className="px-5 py-2 bg-gray-100 text-gray-700 font-bold rounded shadow hover:bg-gray-200 transition"
                      >
                        {t('mc.downloadPdf')}
                      </button>
                    </>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

export default MyContracts;
