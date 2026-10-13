import { useEffect, useState } from 'react';
import api from '../api';
import { useAuth } from '../AuthContext';
import WebcamCapture from '../components/WebcamCapture';

const time = (t) =>
  t ? new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '-';

export default function Attendance() {
  const { user } = useAuth();
  const canSeeAll = ['ADMIN', 'HR', 'MANAGER'].includes(user.role);
  const [mine, setMine] = useState([]);
  const [all, setAll] = useState([]);
  const [msg, setMsg] = useState(null);
  const [faceMode, setFaceMode] = useState(null); // null | 'check-in' | 'check-out'
  const [busy, setBusy] = useState(false);

  async function load() {
    try {
      const r = await api.get('/api/attendance/my');
      setMine(r.data);
    } catch (e) { /* account not linked to an employee */ }
    if (canSeeAll) {
      try {
        const r = await api.get('/api/attendance');
        setAll(r.data);
      } catch (e) { /* ignore */ }
    }
  }

  useEffect(() => { load(); }, []);

  async function act(path) {
    setMsg(null);
    try {
      await api.post(`/api/attendance/${path}`);
      setMsg({ ok: true, text: path === 'check-in' ? 'Checked in' : 'Checked out' });
      load();
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Something went wrong' });
    }
  }

  async function faceAct(blob) {
    setBusy(true);
    setMsg(null);
    const data = new FormData();
    data.append('file', blob, 'face.jpg');
    try {
      const r = await api.post(`/api/attendance/face-${faceMode}`, data);
      const pct = Math.round(r.data.confidence * 100);
      setMsg({
        ok: true,
        text: `${faceMode === 'check-in' ? 'Checked in' : 'Checked out'}: ${r.data.employee} (${pct}% match)`,
      });
      setFaceMode(null);
      load();
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Face check failed' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h1>Attendance</h1>

      {msg && <div className={msg.ok ? 'success' : 'error'}>{msg.text}</div>}

      <div className="actions">
        <button onClick={() => act('check-in')}>Check in</button>
        <button onClick={() => act('check-out')}>Check out</button>
        <button onClick={() => setFaceMode('check-in')}>Face check-in</button>
        <button onClick={() => setFaceMode('check-out')}>Face check-out</button>
      </div>

      {faceMode && (
        <div className="panel">
          <h3>Face {faceMode}</h3>
          <p className="muted">Look at the camera, one person only, then press the button.</p>
          <WebcamCapture
            onCapture={faceAct}
            busy={busy}
            buttonText={faceMode === 'check-in' ? 'Scan & check in' : 'Scan & check out'}
          />
          <button className="secondary" style={{ marginTop: 10 }} onClick={() => setFaceMode(null)}>
            Cancel
          </button>
        </div>
      )}

      <h2>My records</h2>
      <table>
        <thead>
          <tr><th>Date</th><th>Check in</th><th>Check out</th><th>Hours</th><th>Status</th><th>Method</th></tr>
        </thead>
        <tbody>
          {mine.map((a) => (
            <tr key={a.id}>
              <td>{a.workDate}</td>
              <td>{time(a.checkIn)}</td>
              <td>{time(a.checkOut)}</td>
              <td>{a.workHours ?? '-'}</td>
              <td>{a.status}</td>
              <td>{a.method}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {canSeeAll && (
        <>
          <h2>All employees</h2>
          <table>
            <thead>
              <tr><th>Date</th><th>Employee</th><th>Check in</th><th>Check out</th><th>Hours</th><th>Status</th><th>Method</th></tr>
            </thead>
            <tbody>
              {all.map((a) => (
                <tr key={a.id}>
                  <td>{a.workDate}</td>
                  <td>{a.employee?.firstName} {a.employee?.lastName}</td>
                  <td>{time(a.checkIn)}</td>
                  <td>{time(a.checkOut)}</td>
                  <td>{a.workHours ?? '-'}</td>
                  <td>{a.status}</td>
                  <td>{a.method}{a.confidence ? ` (${Math.round(a.confidence * 100)}%)` : ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}
