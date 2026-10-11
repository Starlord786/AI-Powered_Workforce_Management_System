import { useEffect, useState } from 'react';
import api from '../api';
import { useAuth } from '../AuthContext';

const time = (t) =>
  t ? new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '-';

export default function Attendance() {
  const { user } = useAuth();
  const canSeeAll = ['ADMIN', 'HR', 'MANAGER'].includes(user.role);
  const [mine, setMine] = useState([]);
  const [all, setAll] = useState([]);
  const [msg, setMsg] = useState(null);

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

  return (
    <div>
      <h1>Attendance</h1>

      {msg && <div className={msg.ok ? 'success' : 'error'}>{msg.text}</div>}

      <div className="actions">
        <button onClick={() => act('check-in')}>Check in</button>
        <button onClick={() => act('check-out')}>Check out</button>
      </div>

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
              <tr><th>Date</th><th>Employee</th><th>Check in</th><th>Check out</th><th>Hours</th><th>Status</th></tr>
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
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}
