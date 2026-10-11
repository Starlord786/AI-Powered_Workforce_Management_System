import { useEffect, useState } from 'react';
import api from '../api';
import { useAuth } from '../AuthContext';

const COLUMNS = [
  { key: 'TODO', label: 'To do' },
  { key: 'IN_PROGRESS', label: 'In progress' },
  { key: 'DONE', label: 'Done' },
];

const MOVES = {
  TODO: [{ to: 'IN_PROGRESS', label: 'Start' }],
  IN_PROGRESS: [{ to: 'DONE', label: 'Mark done' }, { to: 'TODO', label: 'Back' }],
  DONE: [{ to: 'IN_PROGRESS', label: 'Reopen' }],
};

const emptyForm = { title: '', description: '', assignedToId: '', priority: 'MEDIUM', dueDate: '' };

export default function Tasks() {
  const { user } = useAuth();
  const isApprover = ['ADMIN', 'HR', 'MANAGER'].includes(user.role);
  const [tasks, setTasks] = useState([]);
  const [employees, setEmployees] = useState([]);
  const [form, setForm] = useState(emptyForm);
  const [msg, setMsg] = useState(null);
  const today = new Date().toLocaleDateString('en-CA');

  async function load() {
    try {
      const url = isApprover ? '/api/tasks' : '/api/tasks/my';
      setTasks((await api.get(url)).data);
    } catch (e) { /* account not linked to an employee */ }
    if (isApprover) {
      try { setEmployees((await api.get('/api/employees')).data); } catch (e) { /* ignore */ }
    }
  }

  useEffect(() => { load(); }, []);

  function change(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function create(e) {
    e.preventDefault();
    setMsg(null);
    try {
      await api.post('/api/tasks', {
        title: form.title,
        description: form.description,
        assignedToId: Number(form.assignedToId),
        priority: form.priority,
        dueDate: form.dueDate || null,
      });
      setForm(emptyForm);
      setMsg({ ok: true, text: 'Task created' });
      load();
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Could not create the task' });
    }
  }

  async function move(id, status) {
    setMsg(null);
    try {
      await api.put(`/api/tasks/${id}/status`, { status });
      load();
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Could not update the task' });
    }
  }

  async function remove(id) {
    if (!window.confirm('Delete this task?')) return;
    await api.delete(`/api/tasks/${id}`);
    load();
  }

  return (
    <div>
      <h1>Tasks</h1>
      {msg && <div className={msg.ok ? 'success' : 'error'}>{msg.text}</div>}

      {isApprover && (
        <form className="panel" onSubmit={create}>
          <h3>Assign a task</h3>
          <div className="grid">
            <input name="title" placeholder="Title" value={form.title} onChange={change} required />
            <select name="assignedToId" value={form.assignedToId} onChange={change} required>
              <option value="">Assign to</option>
              {employees.map((e) => (
                <option key={e.id} value={e.id}>{e.firstName} {e.lastName}</option>
              ))}
            </select>
            <select name="priority" value={form.priority} onChange={change}>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
            </select>
            <input name="dueDate" type="date" value={form.dueDate} onChange={change} />
            <input name="description" placeholder="Description (optional)" value={form.description} onChange={change} />
          </div>
          <button type="submit">Create task</button>
        </form>
      )}

      <div className="board">
        {COLUMNS.map((col) => {
          const items = tasks.filter((t) => t.status === col.key);
          return (
            <div className="column" key={col.key}>
              <h3>{col.label} ({items.length})</h3>
              {items.map((t) => {
                const overdue = t.dueDate && t.dueDate < today && t.status !== 'DONE';
                return (
                  <div className={`task-card${overdue ? ' overdue' : ''}`} key={t.id}>
                    <strong>{t.title}</strong>
                    <div>
                      <span className={`tag ${t.priority}`}>{t.priority}</span>
                    </div>
                    {t.description && <div className="muted">{t.description}</div>}
                    {isApprover && (
                      <div className="muted">
                        For: {t.assignedTo.firstName} {t.assignedTo.lastName}
                      </div>
                    )}
                    <div className="muted">
                      Due: {t.dueDate || 'no date'}{overdue ? ' (overdue)' : ''}
                    </div>
                    <div className="task-actions">
                      {MOVES[t.status].map((m) => (
                        <button key={m.to} onClick={() => move(t.id, m.to)}>{m.label}</button>
                      ))}
                      {isApprover && (
                        <button className="danger" onClick={() => remove(t.id)}>Delete</button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}
