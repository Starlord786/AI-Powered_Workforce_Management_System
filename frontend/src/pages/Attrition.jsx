import { useEffect, useState } from 'react';
import api from '../api';
import { useAuth } from '../AuthContext';
import '../components/face.css';
import './attrition.css';

const FIELDS = [
  ['age', 'Age', 16, 80],
  ['distanceFromHome', 'Distance from home (km)', 0, 200],
  ['jobLevel', 'Job level (1-5)', 1, 5],
  ['jobSatisfaction', 'Job satisfaction (1-4)', 1, 4],
  ['workLifeBalance', 'Work-life balance (1-4)', 1, 4],
  ['environmentSatisfaction', 'Environment satisfaction (1-4)', 1, 4],
  ['yearsAtCompany', 'Years at company', 0, 60],
  ['yearsSinceLastPromotion', 'Years since last promotion', 0, 60],
  ['totalWorkingYears', 'Total working years', 0, 60],
  ['numCompaniesWorked', 'Companies worked for', 0, 30],
];

const BUCKETS = ['0-20%', '20-40%', '40-60%', '60-80%', '80-100%'];

export default function Attrition() {
  const { user } = useAuth();
  const allowed = ['ADMIN', 'HR'].includes(user.role);

  const [rows, setRows] = useState([]);
  const [info, setInfo] = useState(null);
  const [msg, setMsg] = useState(null);
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState(null); // { id, name, form }

  async function load() {
    try {
      const r = await api.get('/api/ai/attrition/results');
      setRows(r.data);
    } catch (err) { /* ignore */ }
    try {
      const r = await api.get('/api/ai/attrition/model-info');
      setInfo(r.data);
    } catch (err) { setInfo(null); }
  }

  useEffect(() => { if (allowed) load(); }, []);

  if (!allowed) {
    return (
      <div>
        <h1>Attrition risk</h1>
        <div className="error">Only HR and admins can see this page.</div>
      </div>
    );
  }

  async function run() {
    setBusy(true);
    setMsg(null);
    try {
      const r = await api.post('/api/ai/attrition/run');
      setMsg({ ok: true, text: `Predicted ${r.data.predicted} employees, ${r.data.highRisk} at high risk.` });
      load();
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Could not run the prediction' });
    } finally {
      setBusy(false);
    }
  }

  async function openEdit(row) {
    try {
      const r = await api.get(`/api/ai/attrition/metrics/${row.employeeId}`);
      setEditing({ id: row.employeeId, name: row.employeeName, form: { ...r.data } });
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Could not load the employee data' });
    }
  }

  function setField(key, value) {
    setEditing((e) => ({ ...e, form: { ...e.form, [key]: value } }));
  }

  async function save() {
    const f = editing.form;
    const body = { overtime: f.overtime === true || f.overtime === 'true' };
    FIELDS.forEach(([key]) => { body[key] = f[key] === '' ? null : Number(f[key]); });
    try {
      await api.put(`/api/ai/attrition/metrics/${editing.id}`, body);
      setEditing(null);
      setMsg({ ok: true, text: 'Saved. Click "Run prediction" to update the risk.' });
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Could not save' });
    }
  }

  const count = (level) => rows.filter((r) => r.riskLevel === level).length;
  const hist = BUCKETS.map((_, i) =>
    rows.filter((r) => Math.min(4, Math.floor(r.riskScore * 5)) === i).length);
  const maxHist = Math.max(1, ...hist);

  return (
    <div>
      <h1>Attrition risk</h1>
      <p className="muted">
        Predicts how likely each employee is to resign. It supports HR decisions and does not make them.
      </p>

      {msg && <div className={msg.ok ? 'success' : 'error'}>{msg.text}</div>}

      <div className="actions">
        <button onClick={run} disabled={busy}>{busy ? 'Predicting...' : 'Run prediction'}</button>
      </div>

      {info && (
        <p className="muted">
          Model: Random Forest. Accuracy {Math.round(info.accuracy * 100)}%,
          recall {Math.round(info.recall * 100)}%, precision {Math.round(info.precision * 100)}%,
          AUC {info.auc}. Trained on the public IBM HR dataset.
        </p>
      )}

      {rows.length > 0 && (
        <>
          <div className="stat-cards">
            <div className="card"><div className="num">{count('HIGH')}</div>High risk</div>
            <div className="card"><div className="num">{count('MEDIUM')}</div>Medium risk</div>
            <div className="card"><div className="num">{count('LOW')}</div>Low risk</div>
          </div>

          <div className="panel">
            <h3>Risk distribution</h3>
            <div className="hist">
              {hist.map((n, i) => (
                <div className="hist-col" key={BUCKETS[i]}>
                  <span>{n}</span>
                  <div className="hist-bar" style={{ height: `${(n / maxHist) * 85}%` }} />
                  <span className="muted">{BUCKETS[i]}</span>
                </div>
              ))}
            </div>
          </div>

          <table>
            <thead>
              <tr><th>Employee</th><th>Department</th><th>Risk</th><th>Level</th><th>Main reasons</th><th>Suggested action</th><th></th></tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.employeeId}>
                  <td>{r.employeeName}<br /><small className="muted">{r.jobTitle}</small></td>
                  <td>{r.department || '-'}</td>
                  <td className="risk-cell">
                    <span className="risk-track">
                      <span className={`risk-fill ${r.riskLevel}`} style={{ width: `${Math.round(r.riskScore * 100)}%` }} />
                    </span>
                    {Math.round(r.riskScore * 100)}%
                  </td>
                  <td><span className={`tag ${r.riskLevel}`}>{r.riskLevel}</span></td>
                  <td className="why">
                    {r.factors.length === 0 ? <span className="muted">-</span>
                      : r.factors.map((f) => <span className="chip" key={f}>{f}</span>)}
                  </td>
                  <td>{r.action}</td>
                  <td><button style={{ padding: '4px 10px', fontSize: 12 }} onClick={() => openEdit(r)}>Edit data</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {rows.length === 0 && <p className="muted">No predictions yet. Click "Run prediction".</p>}

      {editing && (
        <div className="modal-backdrop">
          <div className="modal wide">
            <h3>HR data: {editing.name}</h3>
            {editing.form.demoData && (
              <p className="muted">These values were generated automatically for the demo. Saving replaces them with real ones.</p>
            )}
            <div className="form-grid">
              {FIELDS.map(([key, label, min, max]) => (
                <label key={key}>
                  {label}
                  <input type="number" min={min} max={max} value={editing.form[key] ?? ''}
                         onChange={(e) => setField(key, e.target.value)} />
                </label>
              ))}
              <label>
                Works overtime
                <select value={String(editing.form.overtime === true || editing.form.overtime === 'true')}
                        onChange={(e) => setField('overtime', e.target.value)}>
                  <option value="false">No</option>
                  <option value="true">Yes</option>
                </select>
              </label>
            </div>
            <div className="modal-actions">
              <button onClick={save}>Save</button>
              <button className="secondary" onClick={() => setEditing(null)}>Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
