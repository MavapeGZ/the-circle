import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';

function MyContracts() {
  const navigate = useNavigate();

  const [me, setMe] = useState(null);
  const [contracts, setContracts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState('');

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
        setError('No se pudieron cargar tus contratos.');
      } finally {
        setLoading(false);
      }
    };
    init();
  }, []);

  const isOwner = (c) => me != null && String(me.id) === String(c.ownerId);
  const awaitingOwner = (c) => c.receiverSignedAt && !c.ownerSignedAt;

  const goSignAsOwner = (c) => {
    navigate(`/contracts/${c.id}/sign`, {
      state: { contract: c, signerEmail: me.email, role: 'OWNER' },
    });
  };

  const settleGuarantee = async (c, action) => {
    setBusyId(c.id);
    setError('');
    try {
      await api.post(`/contracts/${c.id}/guarantee/${action}`);
      await loadContracts(me.id);
    } catch {
      setError('No se pudo actualizar la fianza.');
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
      setError('No se pudo descargar el contrato.');
    }
  };

  const statusLabel = (c) => {
    if (c.status === 'ACTIVE') return { text: 'Active (signed by both)', cls: 'bg-green-100 text-green-800' };
    if (c.status === 'COMPLETED') return { text: `Completed (deposit ${c.guaranteeStatus?.toLowerCase()})`, cls: 'bg-gray-200 text-gray-700' };
    if (awaitingOwner(c)) return { text: 'Awaiting owner signature', cls: 'bg-yellow-100 text-yellow-800' };
    return { text: 'Pending signatures', cls: 'bg-blue-100 text-blue-800' };
  };

  if (loading) return <div className="text-center mt-20 text-xl animate-pulse text-gray-500">Loading contracts...</div>;

  return (
    <div className="max-w-4xl mx-auto mt-8 p-4">
      <h1 className="text-3xl font-extrabold text-gray-900 mb-6">My Contracts</h1>

      {error && (
        <div className="p-4 mb-6 rounded-lg text-sm font-bold bg-red-100 text-red-700">{error}</div>
      )}

      {contracts.length === 0 ? (
        <p className="text-gray-500">You have no contracts yet.</p>
      ) : (
        <ul className="space-y-4">
          {contracts.map((c) => {
            const label = statusLabel(c);
            const mineAsOwner = isOwner(c);
            const canSignAsOwner = mineAsOwner && awaitingOwner(c);
            const canSettle = mineAsOwner && c.status === 'ACTIVE' && c.guaranteeStatus === 'DEPOSITED';
            return (
              <li key={c.id} className="bg-white p-5 rounded-2xl shadow border border-gray-100">
                <div className="flex justify-between items-start gap-4">
                  <div className="text-sm text-gray-600 space-y-1">
                    <p className="font-bold text-gray-900 text-base">{c.type} · {mineAsOwner ? 'You are the owner' : 'You are the receiver'}</p>
                    <p>Item: {c.itemId}</p>
                    <p>Created: {c.createdAt ? new Date(c.createdAt).toLocaleString() : '—'}</p>
                    {c.guaranteeStatus && c.guaranteeStatus !== 'NONE' && (
                      <p>Deposit: {c.guaranteeAmount} € ({c.guaranteeStatus.toLowerCase()})</p>
                    )}
                  </div>
                  <span className={`text-xs font-bold px-3 py-1 rounded-full whitespace-nowrap ${label.cls}`}>
                    {label.text}
                  </span>
                </div>

                <div className="flex flex-wrap gap-3 mt-4 justify-end">
                  {canSignAsOwner && (
                    <button
                      onClick={() => goSignAsOwner(c)}
                      className="px-6 py-2 bg-blue-600 text-white font-bold rounded shadow hover:bg-blue-700 transition"
                    >
                      Sign
                    </button>
                  )}
                  {canSettle && (
                    <>
                      <button
                        onClick={() => settleGuarantee(c, 'release')}
                        disabled={busyId === c.id}
                        className="px-5 py-2 bg-green-600 text-white font-bold rounded shadow hover:bg-green-700 transition disabled:opacity-50"
                      >
                        Release deposit
                      </button>
                      <button
                        onClick={() => settleGuarantee(c, 'claim')}
                        disabled={busyId === c.id}
                        className="px-5 py-2 bg-red-600 text-white font-bold rounded shadow hover:bg-red-700 transition disabled:opacity-50"
                      >
                        Claim deposit
                      </button>
                    </>
                  )}
                  {c.storedContractId && (
                    <button
                      onClick={() => downloadSigned(c)}
                      className="px-5 py-2 bg-gray-100 text-gray-700 font-bold rounded shadow hover:bg-gray-200 transition"
                    >
                      Download PDF
                    </button>
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
