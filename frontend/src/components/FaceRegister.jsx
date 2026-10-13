import { useState } from 'react';
import api from '../api';
import WebcamCapture from './WebcamCapture';

// Popup used by HR to register 3-5 photos of an employee.
export default function FaceRegister({ employee, onClose }) {
  const [count, setCount] = useState(0);
  const [msg, setMsg] = useState(null);
  const [busy, setBusy] = useState(false);

  async function upload(blob) {
    setBusy(true);
    setMsg(null);
    const data = new FormData();
    data.append('file', blob, 'face.jpg');
    try {
      await api.post(`/api/faces/register/${employee.id}`, data);
      setCount((c) => c + 1);
      setMsg({ ok: true, text: 'Photo saved. Slightly turn your head and take another.' });
    } catch (err) {
      setMsg({ ok: false, text: err.response?.data?.error || 'Could not register this photo' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop">
      <div className="modal">
        <h3>Register face: {employee.firstName} {employee.lastName}</h3>
        <p className="muted">
          Take 3 to 5 photos: straight, slightly left, slightly right. Only one face in view, good light.
        </p>
        {msg && <div className={msg.ok ? 'success' : 'error'}>{msg.text}</div>}
        <WebcamCapture onCapture={upload} busy={busy} buttonText={`Take photo (${count} saved)`} />
        <div className="modal-actions" style={{ marginTop: 14 }}>
          <button type="button" className="secondary" onClick={() => onClose(count > 0)}>Done</button>
        </div>
      </div>
    </div>
  );
}
