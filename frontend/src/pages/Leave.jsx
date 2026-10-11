import { useEffect, useState } from 'react';
import api from '../api';
import { useAuth } from '../AuthContext';

const emptyForm = { leaveTypeId: '', startDate: '', endDate: '', reason: '' };

export default function Leave() {
  const { user } = useAuth();
  const isApprover = ['ADMIN', 'HR', 'MANAGER'].includes(user.role);
  const [types, setTypes] = useState([]);
  const [balance, setBalance] = useState([]);
  const [mine, setMine] = useState([]);
  const [all, setAll] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [msg, setMsg] = useState(null);

  async function load() {
    try { setTypes((await api.get('/api/leave-types')).data); } catch (e) { /* ignore */ }
    try { setBalance((await api.get('/api/leaves/balance')).data); } catch (e) { /* not linked */ }
    try { setMine((await api.get('/api/leaves/my')).data); } catch (e) { /* not linked */ }
    if (isApprover) {
      try { setAll((await api.get('/api/leaves')).data); } catch (e) { /* ignore */ }
    }
  }

  useEffect(() => { load(); }, []);

  function change(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function apply(e) {
    e.preventDefault();
    setMsg(null);
    try {
      await api.post('/api/leaves', {
        leaveTypeId: Number(form.leaveTypeId),
        startDate: form.startDate,
        endDate: form.endDate,
        reason: form.reason,
      });
      setForm(emptyForm);
      setMsg({ ok: true, text: 'Leave request submitted' });
      load();
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Could not submit the request' });
    }
  }

  async function decide(id, action) {
    setMsg(null);
    try {
      await api.put(`/api/leaves/${id}/${action}`);
      load();
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Action failed' });
    }
  }

  const pending = all.filter((r) => r.status === 'PENDING');

  return (
    <div>
      <h1>Leave</h1>
      {msg && <div className={msg.ok ? 'success' : 'error'}>{msg.text}</div>}

      {balance.length > 0 && (
        <div className="cards">
          {balance.map((b) => (
            <div className="card" key={b.leaveTypeId}>
              <div className="num">{b.remaining}</div>
              {b.leaveType} days left
              <div className="muted">{b.used} of {b.allowed} used</div>
            </div>
          ))}
        </div>
      )}

      <form className="panel" onSubmit={apply} style={{ marginTop: 20 }}>
        <h3>Apply for leave</h3>
        <div className="grid">
          <select name="leaveTypeId" value={form.leaveTypeId} onChange={change} required>
            <option value="">Leave type</option>
            {types.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
          </select>
          <input name="startDate" type="date" value={form.startDate} onChange={change} required />
          <input name="endDate" type="date" value={form.endDate} onChange={change} required />
          <input name="reason" placeholder="Reason" value={form.reason} onChange={change} />
        </div>
        <button type="submit">Submit request</button>
      </form>

      <h2>My requests</h2>
      <table>
        <thead>
          <tr><th>Type</th><th>From</th><th>To</th><th>Days</th><th>Reason</th><th>Status</th></tr>
        </thead>
        <tbody>
          {mine.map((r) => (
            <tr key={r.id}>
              <td>{r.leaveType.name}</td>
              <td>{r.startDate}</td>
              <td>{r.endDate}</td>
              <td>{r.days}</td>
              <td>{r.reason || '-'}</td>
              <td>{r.status}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {isApprover && (
        <>
          <h2>Pending approvals</h2>
          <table>
            <thead>
              <tr><th>Employee</th><th>Type</th><th>From</th><th>To</th><th>Days</th><th>Reason</th><th></th></tr>
            </thead>
            <tbody>
              {pending.length === 0 && (
                <tr><td colSpan="7" className="muted">No pending requests</td></tr>
              )}
              {pending.map((r) => (
                <tr key={r.id}>
                  <td>{r.employee.firstName} {r.employee.lastName}</td>
                  <td>{r.leaveType.name}</td>
                  <td>{r.startDate}</td>
                  <td>{r.endDate}</td>
                  <td>{r.days}</td>
                  <td>{r.reason || '-'}</td>
                  <td>
                    <button onClick={() => decide(r.id, 'approve')}>Approve</button>{' '}
                    <button className="danger" onClick={() => decide(r.id, 'reject')}>Reject</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}
