import { useEffect, useState } from 'react';
import api from '../api';
import { useAuth } from '../AuthContext';

const money = (n) =>
  Number(n || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const monthName = (m, y) => new Date(y, m - 1, 1).toLocaleString('default', { month: 'long' });

const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);

function Payslip({ p, onClose }) {
  const e = p.employee;
  return (
    <div className="panel payslip">
      <h2 style={{ marginTop: 0 }}>Payslip: {monthName(p.month, p.year)} {p.year}</h2>
      <p>
        <strong>{e.firstName} {e.lastName}</strong> ({e.employeeCode})<br />
        {e.jobTitle || ''}{e.department ? `, ${e.department.name}` : ''}
      </p>
      <table>
        <tbody>
          <tr><td>Base salary</td><td>{money(p.baseSalary)}</td></tr>
          <tr><td>Bonus</td><td>{money(p.bonus)}</td></tr>
          <tr><td>Absence deduction ({p.absentDays} day(s))</td><td>- {money(p.absenceDeduction)}</td></tr>
          <tr><td>Late deduction ({p.lateCount} late arrival(s))</td><td>- {money(p.lateDeduction)}</td></tr>
          <tr><td>Tax (10%)</td><td>- {money(p.tax)}</td></tr>
          <tr><th>Net salary</th><th>{money(p.netSalary)}</th></tr>
        </tbody>
      </table>
      <p className="muted">
        Working days: {p.workingDays} | Present: {p.presentDays} | On leave: {p.leaveDays} | Absent: {p.absentDays}
      </p>
      <div className="actions no-print">
        <button onClick={() => window.print()}>Print / Save as PDF</button>
        <button className="danger" onClick={onClose}>Close</button>
      </div>
    </div>
  );
}

export default function Payroll() {
  const { user } = useAuth();
  const canManage = ['ADMIN', 'HR'].includes(user.role);
  const now = new Date();
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [year, setYear] = useState(now.getFullYear());
  const [deduct, setDeduct] = useState(true);
  const [rows, setRows] = useState([]);
  const [mine, setMine] = useState([]);
  const [selected, setSelected] = useState(null);
  const [msg, setMsg] = useState(null);

  async function loadMonth() {
    if (!canManage) return;
    try {
      setRows((await api.get('/api/payroll', { params: { month, year } })).data);
    } catch (e) {
      setRows([]);
    }
  }

  async function loadMine() {
    try {
      setMine((await api.get('/api/payroll/my')).data);
    } catch (e) {
      setMine([]);
    }
  }

  useEffect(() => { loadMonth(); }, [month, year]);
  useEffect(() => { loadMine(); }, []);

  async function generate() {
    setMsg(null);
    try {
      const { data } = await api.post('/api/payroll/generate', { month, year, deductAbsences: deduct });
      setMsg({ ok: true, text: `Payroll generated for ${data.length} employee(s)` });
      loadMonth();
      loadMine();
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Could not generate payroll' });
    }
  }

  async function setBonus(p) {
    const value = window.prompt(`Bonus for ${p.employee.firstName} ${p.employee.lastName}`, p.bonus);
    if (value === null) return;
    const amount = Number(value);
    if (Number.isNaN(amount) || amount < 0) {
      setMsg({ ok: false, text: 'Enter a valid bonus amount' });
      return;
    }
    try {
      await api.put(`/api/payroll/${p.id}/bonus`, { bonus: amount });
      setSelected(null);
      loadMonth();
      loadMine();
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Could not set the bonus' });
    }
  }

  return (
    <div>
      <h1>Payroll</h1>
      {msg && <div className={msg.ok ? 'success' : 'error'}>{msg.text}</div>}

      {selected && <Payslip p={selected} onClose={() => setSelected(null)} />}

      {canManage && (
        <>
          <div className="panel">
            <h3>Generate payroll</h3>
            <div className="actions" style={{ alignItems: 'center', marginBottom: 0 }}>
              <select style={{ width: 160 }} value={month} onChange={(e) => setMonth(Number(e.target.value))}>
                {MONTHS.map((m) => <option key={m} value={m}>{monthName(m, 2000)}</option>)}
              </select>
              <input style={{ width: 100 }} type="number" value={year}
                     onChange={(e) => setYear(Number(e.target.value))} />
              <label style={{ display: 'flex', gap: 6, alignItems: 'center', whiteSpace: 'nowrap' }}>
                <input type="checkbox" style={{ width: 'auto' }} checked={deduct}
                       onChange={(e) => setDeduct(e.target.checked)} />
                Deduct absences and lateness
              </label>
              <button onClick={generate}>Generate</button>
            </div>
          </div>

          <h2>{monthName(month, year)} {year}</h2>
          <table>
            <thead>
              <tr>
                <th>Employee</th><th>Base</th><th>Bonus</th><th>Absent</th><th>Late</th>
                <th>Deductions</th><th>Tax</th><th>Net</th><th></th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr><td colSpan="9" className="muted">Nothing generated for this month yet</td></tr>
              )}
              {rows.map((p) => (
                <tr key={p.id}>
                  <td>{p.employee.firstName} {p.employee.lastName}</td>
                  <td>{money(p.baseSalary)}</td>
                  <td>{money(p.bonus)}</td>
                  <td>{p.absentDays}</td>
                  <td>{p.lateCount}</td>
                  <td>{money(Number(p.absenceDeduction) + Number(p.lateDeduction))}</td>
                  <td>{money(p.tax)}</td>
                  <td><strong>{money(p.netSalary)}</strong></td>
                  <td>
                    <button onClick={() => setSelected(p)}>Payslip</button>{' '}
                    <button onClick={() => setBonus(p)}>Bonus</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {mine.length > 0 && (
        <>
          <h2>My payslips</h2>
          <table>
            <thead>
              <tr><th>Month</th><th>Net salary</th><th></th></tr>
            </thead>
            <tbody>
              {mine.map((p) => (
                <tr key={p.id}>
                  <td>{monthName(p.month, p.year)} {p.year}</td>
                  <td>{money(p.netSalary)}</td>
                  <td><button onClick={() => setSelected(p)}>View payslip</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {!canManage && mine.length === 0 && (
        <p className="muted">No payslips yet. HR will generate them at month end.</p>
      )}
    </div>
  );
}
