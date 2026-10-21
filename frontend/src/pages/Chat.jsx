import { useEffect, useRef, useState } from 'react';
import api from '../api';
import './chat.css';

const SUGGESTIONS = [
  'How many annual leave days do I have left?',
  'What shift am I on this week?',
  'Was I late this month?',
  'What is the policy for sick leave?',
];

export default function Chat() {
  const [messages, setMessages] = useState([
    { role: 'assistant', content: 'Hi! Ask me about your leave, attendance, shifts or company policies.' },
  ]);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [waited, setWaited] = useState(0);
  const endRef = useRef(null);

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages, busy]);

  useEffect(() => {
    if (!busy) { setWaited(0); return; }
    const id = setInterval(() => setWaited((w) => w + 1), 1000);
    return () => clearInterval(id);
  }, [busy]);

  async function send(question) {
    const q = question.trim();
    if (!q || busy) return;
    // only the last finished question + answer, so follow-ups work but old questions are not answered again
    let history = [];
    for (let i = messages.length - 2; i >= 0; i--) {
      if (messages[i].role === 'user' && messages[i + 1].role === 'assistant' && !messages[i + 1].error) {
        history = [messages[i], messages[i + 1]].map(({ role, content }) => ({ role, content }));
        break;
      }
    }
    setMessages((m) => [...m, { role: 'user', content: q }]);
    setText('');
    setBusy(true);
    try {
      const r = await api.post('/api/chat', { question: q, history });
      setMessages((m) => [...m, { role: 'assistant', content: r.data.answer, seconds: r.data.seconds }]);
    } catch (err) {
      setMessages((m) => [...m, {
        role: 'assistant', error: true,
        content: err.response?.data?.error || 'Something went wrong. Please try again.',
      }]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h1>HR assistant</h1>
      <p className="muted">Answers come from your own HR data and company policies. It can make mistakes, so check important things with HR.</p>

      <div className="chat-box">
        {messages.map((m, i) => (
          <div key={i} className={`bubble ${m.error ? 'err' : m.role === 'user' ? 'user' : 'bot'}`}>
            {m.content}
            {m.seconds && <small>answered in {m.seconds}s</small>}
          </div>
        ))}
        {busy && <div className="bubble bot">Thinking... {waited}s</div>}
        <div ref={endRef} />
      </div>

      <div className="suggestions">
        {SUGGESTIONS.map((s) => (
          <button key={s} onClick={() => send(s)} disabled={busy}>{s}</button>
        ))}
      </div>

      <div className="chat-input">
        <input
          value={text}
          placeholder="Type your question..."
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && send(text)}
          maxLength={500}
        />
        <button onClick={() => send(text)} disabled={busy || !text.trim()}>Send</button>
      </div>
    </div>
  );
}
