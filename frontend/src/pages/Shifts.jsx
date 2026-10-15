import { useEffect, useState } from 'react';
import api from '../api';
import { useAuth } from '../AuthContext';

const iso = (d) => d.toLocaleDateString('en-CA');
const addDays = (n) => {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return iso(d);
};
const hhmm = (t) => (t ? String(t).slice(0, 5) : '');
const dayLabel = (s) =>
  new Date(`${s}T00:00:00`).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });

function datesBetween(from, to) {
  const out = [];
  const d = new Date(`${from}T00:00:00`);
  const end = new Date(`${to}T00:00:00`);
  while (d <= end && out.length < 62) {
    out.push(iso(d));
    d.setDate(d.getDate() + 1);
  }
  return out;
}

export default function Shifts() {
  const { user } = useAuth();
  const isManager = ['ADMIN', 'HR', 'MANAGER'].includes(user.role);
  const canGenerate = ['ADMIN', 'HR'].includes(user.role);

  const [from, setFrom] = useState(addDays(0));
  const [to, setTo] = useState(addDays(6));
  const [maxPerWeek, setMaxPerWeek] = useState(5);
  const [shifts, setShifts] = useState([]);
  const [edits, setEdits] = useState({});
  const [roster, setRoster] = useState([]);
  const [mine, setMine] = useState([]);
  const [stats, setStats] = useState(null);
  const [msg, setMsg] = useState(null);
  const [busy, setBusy] = useState(false);

  async function loadShifts() {
    try { setShifts((await api.get('/api/shifts')).data); } catch (e) { /* ignore */ }
  }

  async function loadRoster() {
    if (isManager) {
      try { setRoster((await api.get('/api/shifts/roster', { params: { from, to } })).data); }
      catch (e) { setRoster([]); }
    }
    try { setMine((await api.get('/api/shifts/my', { params: { from, to } })).data); }
    catch (e) { setMine([]); }
  }

  useEffect(() => { loadShifts(); loadRoster(); }, []);

  async function generate() {
    setMsg(null);
    setBusy(true);
    try {
      const { data } = await api.post('/api/shifts/generate', {
        startDate: from,
        endDate: to,
        maxShiftsPerWeek: Number(maxPerWeek),
      });
      setStats(data.shiftsPerEmployee || null);
      setMsg({ ok: true, text: `Roster created: ${data.saved} shifts assigned (solved in ${data.solveTimeSeconds}s)` });
      loadRoster();
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Could not generate the roster' });
    } finally {
      setBusy(false);
    }
  }

  async function saveRequired(id) {
    setMsg(null);
    try {
      await api.put(`/api/shifts/${id}`, { required: Number(edits[id]) });
      setEdits((e) => { const c = { ...e }; delete c[id]; return c; });
      setMsg({ ok: true, text: 'Staff requirement saved' });
      loadShifts();
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Could not save' });
    }
  }

  const days = datesBetween(from, to);

  return (
    <div>
      <h1>Shifts</h1>
      {msg && <div className={msg.ok ? 'success' : 'error'}>{msg.text}</div>}

      <div className="panel">
        <h3>Date range</h3>
        <div className="actions" style={{ alignItems: 'center', marginBottom: 0 }}>
          <input style={{ width: 170 }} type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          <input style={{ width: 170 }} type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          <button onClick={loadRoster}>Show</button>
          {canGenerate && (
            <>
              <label style={{ whiteSpace: 'nowrap' }}>Max shifts / week</label>
              <input style={{ width: 70 }} type="number" min="1" max="7" value={maxPerWeek}
                     onChange={(e) => setMaxPerWeek(e.target.value)} />
              <button disabled={busy} onClick={generate}>
                {busy ? 'Generating...' : 'Generate roster'}
              </button>
            </>
          )}
        </div>
      </div>

      {canGenerate && (
        <div className="panel">
          <h3>Staff needed per shift (every day)</h3>
          <div className="actions" style={{ alignItems: 'center', marginBottom: 0 }}>
            {shifts.map((s) => (
              <div key={s.id} style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                <span>{s.name} ({hhmm(s.startTime)}-{hhmm(s.endTime)})</span>
                <input style={{ width: 64 }} type="number" min="1" max="20"
                       value={edits[s.id] ?? s.requiredStaff}
                       onChange={(e) => setEdits({ ...edits, [s.id]: e.target.value })} />
                {edits[s.id] !== undefined && <button onClick={() => saveRequired(s.id)}>Save</button>}
              </div>
            ))}
          </div>
        </div>
      )}

      {stats && (
        <div className="panel">
          <h3>Shifts per employee (fairness)</h3>
          {Object.entries(stats).map(([name, n]) => (
            <span className="chip chip0" key={name}>{name}: {n}</span>
          ))}
        </div>
      )}

      {mine.length > 0 && (
        <>
          <h2>My shifts</h2>
          <table>
            <thead><tr><th>Date</th><th>Shift</th><th>Time</th></tr></thead>
            <tbody>
              {mine.map((r) => {
                const s = shifts.find((x) => x.id === r.shiftId);
                return (
                  <tr key={r.id}>
                    <td>{dayLabel(r.date)}</td>
                    <td>{r.shiftName}</td>
                    <td>{s ? `${hhmm(s.startTime)} - ${hhmm(s.endTime)}` : ''}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </>
      )}

      {isManager && (
        <>
          <h2>Roster</h2>
          <table>
            <thead>
              <tr>
                <th>Date</th>
                {shifts.map((s) => (
                  <th key={s.id}>{s.name}<div className="muted">{hhmm(s.startTime)}-{hhmm(s.endTime)}</div></th>
                ))}
              </tr>
            </thead>
            <tbody>
              {days.map((day) => (
                <tr key={day}>
                  <td>{dayLabel(day)}</td>
                  {shifts.map((s, i) => {
                    const names = roster.filter((r) => r.date === day && r.shiftId === s.id).map((r) => r.employeeName);
                    return (
                      <td key={s.id}>
                        {names.length === 0 && <span className="muted">-</span>}
                        {names.map((n, k) => <span className={`chip chip${i % 3}`} key={`${n}-${k}`}>{n}</span>)}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {!isManager && mine.length === 0 && (
        <p className="muted">You have no shifts in this range.</p>
      )}
    </div>
  );
}
