import { useContext, useEffect, useRef, useState, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import api, { extractApiError } from '../services/api';
import { AuthContext } from '../context/AuthContext';

const POLL_MS = 5000;

// Inbox + thread chat view. Conversations are about a catalog article and stay
// open before, during and after a deal. Real-time is approximated with polling.
export default function Messages() {
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
            setMeta((p) => ({ ...p, [c.id]: { ...(p[c.id] || {}), title: r.data?.title || 'Article' } }));
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
        <Link to="/login" className="inline-flex px-6 py-3 rounded-full bg-blue-600 text-white font-bold shadow hover:bg-blue-700">Go to login</Link>
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
                      className={`w-full text-left px-4 py-3 border-b border-gray-50 hover:bg-gray-50 ${isActive ? 'bg-blue-50' : ''}`}
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
              <header className="px-4 py-3 border-b border-gray-100 flex items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-bold text-gray-800 truncate">{meta[conversationId]?.otherName || 'Conversation'}</p>
                  {active && (
                    <Link to={`/catalog/${active.articleId}`} className="text-xs text-blue-600 hover:underline truncate block">
                      About: {meta[conversationId]?.title || 'article'}
                    </Link>
                  )}
                </div>
              </header>

              <div className="flex-1 overflow-y-auto p-4 space-y-2">
                {messages.map((msg) => {
                  const mine = String(msg.senderId) === myId;
                  return (
                    <div key={msg.id} className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
                      <div className={`max-w-[75%] rounded-2xl px-3 py-2 text-sm ${mine ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-800'}`}>
                        <p className="whitespace-pre-wrap break-words">{msg.body}</p>
                        <p className={`text-[10px] mt-1 ${mine ? 'text-blue-100' : 'text-gray-400'}`}>
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
                  className="flex-1 px-4 py-2 border border-gray-300 rounded-full focus:ring-2 focus:ring-blue-500"
                />
                <button
                  type="submit"
                  disabled={sending || !draft.trim()}
                  className={`px-5 py-2 rounded-full font-bold text-white ${sending || !draft.trim() ? 'bg-blue-300' : 'bg-blue-600 hover:bg-blue-700'}`}
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
