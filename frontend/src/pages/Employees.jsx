import { useEffect, useState } from 'react';
import api from '../api';
import { useAuth } from '../AuthContext';

const empty = {
  employeeCode: '', firstName: '', lastName: '', email: '',
  jobTitle: '', baseSalary: '', hireDate: '', departmentId: '',
};

export default function Employees() {
  const { user } = useAuth();
  const canEdit = user.role === 'ADMIN' || user.role === 'HR';
  const [employees, setEmployees] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [form, setForm] = useState(empty);
  const [error, setError] = useState('');
  const [denied, setDenied] = useState(false);

  async function load() {
    try {
      const [e, d] = await Promise.all([api.get('/api/employees'), api.get('/api/departments')]);
      setEmployees(e.data);
      setDepartments(d.data);
    } catch (err) {
      if (err.response?.status === 403) setDenied(true);
    }
  }

  useEffect(() => { load(); }, []);

  function change(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function add(e) {
    e.preventDefault();
    setError('');
    try {
      await api.post('/api/employees', {
        employeeCode: form.employeeCode,
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        jobTitle: form.jobTitle,
        baseSalary: form.baseSalary ? Number(form.baseSalary) : null,
        hireDate: form.hireDate || null,
        department: form.departmentId ? { id: Number(form.departmentId) } : null,
      });
      setForm(empty);
      load();
    } catch (err) {
      setError('Could not add employee. Check that the code and email are unique.');
    }
  }

  async function remove(id) {
    if (!window.confirm('Delete this employee?')) return;
    await api.delete(`/api/employees/${id}`);
    load();
  }

  if (denied) return <div><h1>Employees</h1><p className="muted">You do not have access to this page.</p></div>;

  return (
    <div>
      <h1>Employees</h1>

      {canEdit && (
        <form className="panel" onSubmit={add}>
          <h3>Add employee</h3>
          {error && <div className="error">{error}</div>}
          <div className="grid">
            <input name="employeeCode" placeholder="Code (EMP002)" value={form.employeeCode} onChange={change} required />
            <input name="firstName" placeholder="First name" value={form.firstName} onChange={change} required />
            <input name="lastName" placeholder="Last name" value={form.lastName} onChange={change} required />
            <input name="email" type="email" placeholder="Email" value={form.email} onChange={change} required />
            <input name="jobTitle" placeholder="Job title" value={form.jobTitle} onChange={change} />
            <input name="baseSalary" type="number" placeholder="Base salary" value={form.baseSalary} onChange={change} />
            <input name="hireDate" type="date" value={form.hireDate} onChange={change} />
            <select name="departmentId" value={form.departmentId} onChange={change}>
              <option value="">Department</option>
              {departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            </select>
          </div>
          <button type="submit">Add</button>
        </form>
      )}

      <table>
        <thead>
          <tr>
            <th>Code</th><th>Name</th><th>Email</th><th>Job title</th>
            <th>Department</th><th>Status</th>{canEdit && <th></th>}
          </tr>
        </thead>
        <tbody>
          {employees.map((e) => (
            <tr key={e.id}>
              <td>{e.employeeCode}</td>
              <td>{e.firstName} {e.lastName}</td>
              <td>{e.email}</td>
              <td>{e.jobTitle}</td>
              <td>{e.department?.name || '-'}</td>
              <td>{e.status}</td>
              {canEdit && <td><button className="danger" onClick={() => remove(e.id)}>Delete</button></td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
