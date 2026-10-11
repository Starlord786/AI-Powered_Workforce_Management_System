import { useEffect, useState } from 'react';
import api from '../api';
import { useAuth } from '../AuthContext';

export default function Dashboard() {
  const { user } = useAuth();
  const [stats, setStats] = useState(null);

  useEffect(() => {
    Promise.all([api.get('/api/employees'), api.get('/api/departments')])
      .then(([emp, dep]) => setStats({ employees: emp.data.length, departments: dep.data.length }))
      .catch(() => setStats(null));
  }, []);

  return (
    <div>
      <h1>Dashboard</h1>
      <p className="muted">Welcome, {user.email} ({user.role})</p>
      {stats ? (
        <div className="cards">
          <div className="card"><div className="num">{stats.employees}</div>Employees</div>
          <div className="card"><div className="num">{stats.departments}</div>Departments</div>
        </div>
      ) : (
        <p className="muted">No dashboard data available for your role yet.</p>
      )}
    </div>
  );
}
