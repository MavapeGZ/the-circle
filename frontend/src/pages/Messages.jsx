import { useContext, useEffect, useRef, useState, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import api, { extractApiError } from '../services/api';
import { AuthContext } from '../context/AuthContext';
import usePageTitle from '../hooks/usePageTitle';

const POLL_MS = 5000;

// Transaction-type chip styling, mirroring the catalog badges.
const TYPE_BADGE = {
  DONATION: { label: 'Donation', cls: 'bg-purple-100 text-purple-800 border-purple-300' },
  DEMAND: { label: 'Demand', cls: 'bg-indigo-100 text-indigo-800 border-indigo-300' },
  SYMBOLIC_SALE: { label: 'Sale', cls: 'bg-green-100 text-green-800 border-green-300' },
  SYMBOLIC_RENTAL: { label: 'Rental', cls: 'bg-green-100 text-green-800 border-green-300' },
};

// Small image placeholder (same icon as the catalog cards) for articles with no photo.
function ArticleThumb({ image, title }) {
  if (image) {
    return <img src={image} alt={title || 'Article'} className="h-12 w-12 rounded-lg object-cover border border-gray-100" />;
  }
  return (
    <div className="h-12 w-12 rounded-lg bg-gray-50 border border-gray-100 flex items-center justify-center text-gray-300">
      <svg className="w-6 h-6 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
    </div>
  );
}

// Inbox + thread chat view. Conversations are about a catalog article and stay
// open before, during and after a deal. Real-time is approximated with polling.
export default function Messages() {
  usePageTitle('Messages');
  const { conversationId } = useParams();
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);
  const myId = user ? String(user.id) : null;

  const [conversations, setConversations] = useState([]);
  const [meta, setMeta] = useState({}); // conversationId -> { title, otherName }
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const bottomRef = useRef(null);

  const loadConversations = useCallback(async () => {
    try {
      const { data } = await api.get('/chat/conversations');
      setConversations(data || []);
      // Lazily resolve article title + other user's name for the inbox labels.
      (data || []).forEach((c) => {
        setMeta((prev) => {
          if (prev[c.id]) return prev;
          api.get(`/catalog/articles/${c.articleId}`).then((r) => {
            const a = r.data || {};
            setMeta((p) => ({ ...p, [c.id]: {
              ...(p[c.id] || {}),
              title: a.title || 'Article',
              image: a.imageBase64 || null,
              productType: a.productType || null,
              price: a.price,
            } }));
          }).catch(() => {});
          api.get(`/users/${c.otherUserId}`).then((r) => {
            setMeta((p) => ({ ...p, [c.id]: { ...(p[c.id] || {}), otherName: r.data?.displayName || `User ${c.otherUserId}` } }));
          }).catch(() => {});
          return { ...prev, [c.id]: prev[c.id] || {} };
        });
      });
    } catch {
      /* keep previous list on transient errors */
    }
  }, []);

  const loadMessages = useCallback(async (id) => {
    if (!id) return;
    try {
      const { data } = await api.get(`/chat/conversations/${id}/messages`);
      setMessages(data || []);
    } catch (err) {
      setError(extractApiError(err, 'Could not load this conversation.'));
    }
  }, []);

  // Poll the conversation list.
  useEffect(() => {
    loadConversations();
    const t = setInterval(loadConversations, POLL_MS);
    return () => clearInterval(t);
  }, [loadConversations]);

  // Poll the open thread.
  useEffect(() => {
    if (!conversationId) { setMessages([]); return undefined; }
    setError('');
    loadMessages(conversationId);
    const t = setInterval(() => loadMessages(conversationId), POLL_MS);
    return () => clearInterval(t);
  }, [conversationId, loadMessages]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const send = async (e) => {
    e.preventDefault();
    const body = draft.trim();
    if (!body || sending) return;
    setSending(true);
    try {
      await api.post(`/chat/conversations/${conversationId}/messages`, { body });
      setDraft('');
      await loadMessages(conversationId);
      loadConversations();
    } catch (err) {
      setError(extractApiError(err, 'Could not send the message.'));
    } finally {
      setSending(false);
    }
  };

  if (!user) {
    return (
      <div className="max-w-3xl mx-auto mt-16 p-4 text-center">
        <h1 className="text-3xl font-extrabold text-gray-900 mb-4">Messages</h1>
        <p className="text-gray-600 mb-6">Sign in to view your conversations.</p>
        <Link to="/login" className="inline-flex px-6 py-3 rounded-full bg-indigo-600 text-white font-bold shadow hover:bg-indigo-700">Go to login</Link>
      </div>
    );
  }

  const active = conversations.find((c) => c.id === conversationId) || null;

  return (
    <div className="max-w-6xl mx-auto mt-8 p-4">
      <h1 className="text-3xl font-extrabold text-gray-900 mb-4">Messages</h1>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 h-[70vh]">
        {/* Inbox */}
        <aside className="md:col-span-1 bg-white rounded-2xl shadow border border-gray-100 overflow-y-auto">
          {conversations.length === 0 ? (
            <p className="p-4 text-gray-500 text-sm">No conversations yet. Start one from an article page.</p>
          ) : (
            <ul>
              {conversations.map((c) => {
                const m = meta[c.id] || {};
                const isActive = c.id === conversationId;
                return (
                  <li key={c.id}>
                    <button
                      onClick={() => navigate(`/messages/${c.id}`)}
                      className={`w-full text-left px-4 py-3 border-b border-gray-50 hover:bg-gray-50 ${isActive ? 'bg-indigo-50' : ''}`}
                    >
                      <div className="flex justify-between items-center gap-2">
                        <span className="font-bold text-gray-800 truncate">{m.otherName || 'User'}</span>
                        {c.unreadCount > 0 && (
                          <span className="shrink-0 bg-red-500 text-white text-xs font-bold rounded-full px-2 py-0.5">{c.unreadCount}</span>
                        )}
                      </div>
                      <div className="text-xs text-gray-500 truncate">{m.title || 'Article'}</div>
                      {c.lastMessage && <div className="text-sm text-gray-600 truncate mt-0.5">{c.lastMessage}</div>}
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </aside>

        {/* Thread */}
        <section className="md:col-span-2 bg-white rounded-2xl shadow border border-gray-100 flex flex-col">
          {!conversationId ? (
            <div className="flex-1 flex items-center justify-center text-gray-400">Select a conversation</div>
          ) : (
            <>
              <header className="px-4 py-3 border-b border-gray-100 flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-bold text-gray-800 truncate">{meta[conversationId]?.otherName || 'Conversation'}</p>
                  {active && (
                    <Link to={`/catalog/${active.articleId}`} className="text-xs text-indigo-600 hover:underline truncate block">
                      About: {meta[conversationId]?.title || 'article'}
                    </Link>
                  )}
                </div>
                {active && (() => {
                  const m = meta[conversationId] || {};
                  const badge = TYPE_BADGE[m.productType];
                  const hasPrice = m.price != null && Number(m.price) > 0;
                  return (
                    <Link to={`/catalog/${active.articleId}`} className="flex items-center gap-3 shrink-0">
                      <div className="flex flex-col items-end gap-1">
                        {badge && (
                          <span className={`text-xs font-bold px-2.5 py-0.5 rounded-full border ${badge.cls}`}>{badge.label}</span>
                        )}
                        {hasPrice && <span className="text-sm font-extrabold text-gray-800">{m.price} €</span>}
                      </div>
                      <ArticleThumb image={m.image} title={m.title} />
                    </Link>
                  );
                })()}
              </header>

              <div className="flex-1 overflow-y-auto p-4 space-y-2">
                {messages.map((msg) => {
                  const mine = String(msg.senderId) === myId;
                  return (
                    <div key={msg.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
                      <div className={`max-w-[75%] rounded-2xl px-3 py-2 text-sm ${mine ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-800'}`}>
                        <p className="whitespace-pre-wrap break-words">{msg.body}</p>
                        <p className={`text-[10px] mt-1 ${mine ? 'text-indigo-100' : 'text-gray-400'}`}>
                          {msg.createdAt ? new Date(msg.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                        </p>
                      </div>
                    </div>
                  );
                })}
                <div ref={bottomRef} />
              </div>

              {error && <p className="px-4 text-sm text-red-600">{error}</p>}

              <form onSubmit={send} className="p-3 border-t border-gray-100 flex gap-2">
                <input
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  maxLength={4000}
                  placeholder="Write a message…"
                  className="flex-1 px-4 py-2 border border-gray-300 rounded-full focus:ring-2 focus:ring-indigo-500"
                />
                <button
                  type="submit"
                  disabled={sending || !draft.trim()}
                  className={`px-5 py-2 rounded-full font-bold text-white ${sending || !draft.trim() ? 'bg-indigo-300' : 'bg-indigo-600 hover:bg-indigo-700'}`}
                >
                  Send
                </button>
              </form>
            </>
          )}
        </section>
      </div>
    </div>
  );
}
